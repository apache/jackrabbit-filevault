/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.vault.validation.spi.impl.nodetype;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.jcr.NamespaceException;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.ValueFactory;
import javax.jcr.ValueFormatException;
import javax.jcr.nodetype.ConstraintViolationException;
import javax.jcr.nodetype.NoSuchNodeTypeException;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.jcr2spi.nodetype.EffectiveNodeType;
import org.apache.jackrabbit.jcr2spi.nodetype.ItemDefinitionProvider;
import org.apache.jackrabbit.jcr2spi.nodetype.NodeTypeDefinitionProvider;
import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.spi.QNodeDefinition;
import org.apache.jackrabbit.spi.QNodeTypeDefinition;
import org.apache.jackrabbit.spi.QPropertyDefinition;
import org.apache.jackrabbit.spi.QValue;
import org.apache.jackrabbit.spi.QValueFactory;
import org.apache.jackrabbit.spi.commons.conversion.IllegalNameException;
import org.apache.jackrabbit.spi.commons.conversion.NamePathResolver;
import org.apache.jackrabbit.spi.commons.conversion.NameResolver;
import org.apache.jackrabbit.spi.commons.name.NameConstants;
import org.apache.jackrabbit.spi.commons.name.NameFactoryImpl;
import org.apache.jackrabbit.spi.commons.nodetype.constraint.ValueConstraint;
import org.apache.jackrabbit.spi.commons.value.ValueFormat;
import org.apache.jackrabbit.value.ValueHelper;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.spi.ACLManagement;
import org.apache.jackrabbit.vault.fs.spi.UserManagement;
import org.apache.jackrabbit.vault.fs.spi.impl.jcr20.JackrabbitUserManagement;
import org.apache.jackrabbit.vault.fs.spi.impl.jcr20.JcrACLManagement;
import org.apache.jackrabbit.vault.util.DocViewNode;
import org.apache.jackrabbit.vault.util.DocViewProperty;
import org.apache.jackrabbit.vault.util.Text;
import org.apache.jackrabbit.vault.validation.spi.DocumentViewXmlValidator;
import org.apache.jackrabbit.vault.validation.spi.NodeContext;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessage;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessageSeverity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NodeTypeValidator implements DocumentViewXmlValidator {

    static final String MESSAGE_MANDATORY_CHILD_NODE_MISSING = "Mandatory child node missing: %s";
    static final String MESSAGE_PROPERTY_ERROR = "Error while retrieving property '%s': %s";
    static final String MESSAGE_UNKNOWN_NODE_TYPE_OR_NAMESPACE = "Unknown node type or namespace: %s";
    static final String MESSAGE_MISSING_PRIMARY_TYPE = "Mandatory jcr:primaryType missing on node '%s'";
    static final String MESSAGE_PROPERTY_NOT_ALLOWED = "Property '%s' is not allowed in node with types '[%s]': %s";
    static final String MESSAGE_MANDATORY_PROPERTY_MISSING = "Mandatory property '%s' missing in node with types [%s]";
    static final String MESSAGE_CHILD_NODE_OF_NOT_CONTAINED_PARENT_POTENTIALLY_NOT_ALLOWED = "Node '%s' is not allowed as child of not contained node with potential default types '[%s]': %s";
    static final String MESSAGE_CHILD_NODE_NOT_ALLOWED = "Node '%s' is not allowed as child of node with types '[%s]': %s";
    private final WorkspaceFilter filter;
    private final ValidationMessageSeverity defaultSeverity;
    private final ValidationMessageSeverity severityForUnknownNodeTypes;
    private final DocViewPropertyValueFactory docViewPropertyValueFactory;
    private final NodeTypeManagerProvider ntManagerProvider;
    private final Set<String> loggedUnknownNodeTypeMessages;

    private final EffectiveNodeType defaultType;
    private final UserManagement userManagement;
    private final ACLManagement aclManagement;
    private NodeContext protectedNodeContext;
    private NodeNameAndType currentNodeNameAndType = null;

    private static final Collection<Name> ALLOWED_PROTECTED_PROPERTIES = Arrays.asList(NameConstants.JCR_PRIMARYTYPE,
            NameConstants.JCR_MIXINTYPES);
    
    // properties being set by the {@link FileArtifactHandler} (they are part of another file) are ignored
    private static final Map<Name, List<Name>> IGNORED_MANDATORY_PROPERTIES_PER_NODE_TYPE = Stream.of(
            new SimpleEntry<>(NameConstants.NT_RESOURCE, 
                    Arrays.asList(NameConstants.JCR_DATA)))
            .collect(Collectors.toMap(SimpleEntry::getKey, SimpleEntry::getValue));

    public NodeTypeValidator(@NotNull WorkspaceFilter filter, @NotNull NodeTypeManagerProvider ntManagerProvider,
            @NotNull EffectiveNodeType defaultEffectiveNodeType, @NotNull ValidationMessageSeverity defaultSeverity,
            @NotNull ValidationMessageSeverity severityForUnknownNodeTypes) {
        this.filter = filter;
        this.ntManagerProvider = ntManagerProvider;
        this.defaultType = defaultEffectiveNodeType;
        this.defaultSeverity = defaultSeverity;
        this.severityForUnknownNodeTypes = severityForUnknownNodeTypes;
        this.docViewPropertyValueFactory = new DocViewPropertyValueFactory();
        this.userManagement = new JackrabbitUserManagement();
        this.aclManagement = new JcrACLManagement();
        this.loggedUnknownNodeTypeMessages = new HashSet<>();
    }

    static String getDocViewNodeLabel(DocViewNode node) {
        StringBuilder sb = new StringBuilder(node.name);
        sb.append(" [").append(node.primary);
        if (node.mixins != null && node.mixins.length > 0) {
            sb.append(" (").append(StringUtils.join(node.mixins, ", ")).append(")");
        }
        sb.append("]");
        return sb.toString();
    }

    @Override
    public @Nullable Collection<ValidationMessage> validate(@NotNull DocViewNode node, @NotNull NodeContext nodeContext,
            boolean isRoot) {

        if (node.primary == null) {
            // only an issue if contained in the filter
            // if other properties are set this node is not only used for ordering purposes
            if (filter.contains(nodeContext.getNodePath()) && !node.props.isEmpty()) {
                return Collections.singleton(
                        new ValidationMessage(defaultSeverity, String.format(MESSAGE_MISSING_PRIMARY_TYPE, nodeContext.getNodePath())));
            } else {
                // order node only or outside filter
                return null;
            }
        }

        // special handling for users and acls
        if (aclManagement.isACLNodeType(node.primary) || userManagement.isAuthorizableNodeType(node.primary)) {
            protectedNodeContext = nodeContext;
        }

        boolean allowProtectedSubNodesAndProperties = protectedNodeContext != null;

        Collection<ValidationMessage> messages = new LinkedList<>();

        try {
            // check node itself against parent node type
            if (!aclManagement.isACLNodeType(node.primary)) {
                final EffectiveNodeType parentNodeType;
                final boolean useDefaultNodeType;
                String parentNodePath = Text.getRelativeParent(nodeContext.getNodePath(), 1);

                if (currentNodeNameAndType == null || !filter.contains(parentNodePath)) {
                    parentNodeType = defaultType;
                    useDefaultNodeType = true;
                } else {
                    parentNodeType = currentNodeNameAndType.getEffectiveNodeType();
                    useDefaultNodeType = false;
                }

                String constraintViolation = getChildNodeConstraintViolation(node, parentNodeType,
                        ntManagerProvider.getNodeTypeDefinitionProvider(),
                        ntManagerProvider.getNameResolver(), ntManagerProvider.getItemDefinitionProvider(),
                        allowProtectedSubNodesAndProperties);
                if (constraintViolation != null) {
                    messages.add(new ValidationMessage(defaultSeverity,
                            String.format(
                                    useDefaultNodeType ? MESSAGE_CHILD_NODE_OF_NOT_CONTAINED_PARENT_POTENTIALLY_NOT_ALLOWED
                                            : MESSAGE_CHILD_NODE_NOT_ALLOWED,
                                    getDocViewNodeLabel(node),
                                    effectiveNodeTypeToString(ntManagerProvider.getNameResolver(), parentNodeType),
                                    constraintViolation)));

                }
            }

            // get current node's node type and name and register in tree
            NodeNameAndType newNodeNameAndType = new NodeNameAndType(currentNodeNameAndType, ntManagerProvider.getNameResolver(),
                    ntManagerProvider.getEffectiveNodeTypeProvider(), node);

            // check all properties
            Collection<Name> foundProperties = new ArrayList<>(node.props.size());
            for (DocViewProperty property : node.props.values()) {
                String constraintViolation = getPropertyConstraintViolation(property, newNodeNameAndType.getEffectiveNodeType(),
                        allowProtectedSubNodesAndProperties);
                if (constraintViolation != null) {
                    messages.add(new ValidationMessage(defaultSeverity, String.format(MESSAGE_PROPERTY_NOT_ALLOWED, property,
                            effectiveNodeTypeToString(ntManagerProvider.getNameResolver(), newNodeNameAndType.getEffectiveNodeType()),
                            constraintViolation)));
                }
                foundProperties.add(NameFactoryImpl.getInstance().create(property.name));
            }
            // are all mandatory properties covered?
            for (QPropertyDefinition mandatoryPropertyDefinition : newNodeNameAndType.getEffectiveNodeType()
                    .getMandatoryQPropertyDefinitions()) {
                // ignore auto-created properties as they are created on-demand
                if (!mandatoryPropertyDefinition.isAutoCreated() && !foundProperties.contains(mandatoryPropertyDefinition.getName())) {
                    
                    // ignore propertes which may be provided by the {@link FileArtifactHandler} (they are part of another file)
                    List<Name> ignoredProperties = IGNORED_MANDATORY_PROPERTIES_PER_NODE_TYPE.get(mandatoryPropertyDefinition.getDeclaringNodeType());
                    if (ignoredProperties != null && ignoredProperties.contains(mandatoryPropertyDefinition.getName())) {
                        // TODO: skipping for now as validating those from other files requires major effort
                        continue;
                    }
                    messages.add(new ValidationMessage(defaultSeverity,
                            String.format(MESSAGE_MANDATORY_PROPERTY_MISSING, mandatoryPropertyDefinition.getName(),
                                    effectiveNodeTypeToString(ntManagerProvider.getNameResolver(),
                                            newNodeNameAndType.getEffectiveNodeType()))));
                }
            }

            currentNodeNameAndType = newNodeNameAndType;
        } catch (NoSuchNodeTypeException | IllegalNameException | NamespaceException e) {
            // log each unknown node type/namespace only once!
            if (!loggedUnknownNodeTypeMessages.contains(e.getMessage())) {
                messages.add(new ValidationMessage(severityForUnknownNodeTypes,
                        String.format(MESSAGE_UNKNOWN_NODE_TYPE_OR_NAMESPACE, e.getMessage()), e));
                loggedUnknownNodeTypeMessages.add(e.getMessage());
            }
        } catch (RepositoryException e) {
            throw new IllegalStateException("Could not validate nodes/properties against node types: " + e.getMessage(), e);
        }
        return messages;
    }

    @Override
    public @Nullable Collection<ValidationMessage> validateEnd(@NotNull DocViewNode node, @NotNull NodeContext nodeContext,
            boolean isRoot) {

        if (nodeContext.equals(protectedNodeContext)) {
            protectedNodeContext = null;
        }

        try {
            if (currentNodeNameAndType != null) {
                Collection<ValidationMessage> messages = new LinkedList<>();
                for (QNodeDefinition mandatoryNodeType : currentNodeNameAndType.getEffectiveNodeType().getMandatoryQNodeDefinitions()) {
                    boolean foundRequiredChildNode = currentNodeNameAndType.getChildren().stream()
                            .anyMatch(childNamesAndTypes -> childNamesAndTypes.fulfillsNodeDefinition(mandatoryNodeType));
                    if (!foundRequiredChildNode) {
                        try {
                            messages.add(new ValidationMessage(defaultSeverity, String.format(MESSAGE_MANDATORY_CHILD_NODE_MISSING,
                                    nodeDefinitionToString(ntManagerProvider.getNameResolver(), mandatoryNodeType))));
                        } catch (NamespaceException e) {
                            throw new IllegalStateException("Could not give out node types and name for " + mandatoryNodeType, e);
                        }
                    }
                }
                return messages;
            } else {
                return null;
            }
        } finally {
            if (currentNodeNameAndType != null) {
                currentNodeNameAndType = currentNodeNameAndType.getParent();
            }
        }
    }

    static String effectiveNodeTypeToString(NameResolver nameResolver, EffectiveNodeType nodeType) throws NamespaceException {
        return joinAsQualifiedJcrName(nameResolver, nodeType.getMergedNodeTypes());
    }

    static String nodeDefinitionToString(NameResolver nameResolver, QNodeDefinition nodeDefinition) throws NamespaceException {
        return nameResolver.getJCRName(nodeDefinition.getName()) + " ["
                + joinAsQualifiedJcrName(nameResolver, nodeDefinition.getRequiredPrimaryTypes()) + "]";
    }

    private static String joinAsQualifiedJcrName(NameResolver nameResolver, Name[] names) throws NamespaceException {
        StringBuilder types = new StringBuilder();
        String delimiter = "";
        for (Name name : names) {
            types.append(delimiter).append(nameResolver.getJCRName(name));
            delimiter = ", ";
        }
        return types.toString();
    }

    private static QPropertyDefinition getPropertyDefinition(Name name, int type, EffectiveNodeType effectiveNodeType,
            ItemDefinitionProvider itemDefinitionProvider, boolean multiValued)
            throws NoSuchNodeTypeException, ConstraintViolationException {
        QPropertyDefinition def;
        try {
            def = itemDefinitionProvider.getQPropertyDefinition(effectiveNodeType.getAllNodeTypes(), name, type,
                    multiValued);
        } catch (ConstraintViolationException e) {
            if (type != PropertyType.UNDEFINED) {
                def = itemDefinitionProvider.getQPropertyDefinition(effectiveNodeType.getAllNodeTypes(), name, PropertyType.UNDEFINED,
                        multiValued);
            } else {
                throw e;
            }
        }
        return def;
    }

    private static void validateValueConstraints(Value value, QPropertyDefinition def, ValueFactory valueFactory,
            QValueFactory qValueFactory, NamePathResolver namePathResolver) throws ValueFormatException, RepositoryException {
        final Value v;
        if (def.getRequiredType() != 0 && def.getRequiredType() != value.getType()) {
            v = ValueHelper.convert(value, def.getRequiredType(), valueFactory);
        } else {
            v = value;
        }
        QValue qValue = ValueFormat.getQValue(v, namePathResolver, qValueFactory);
        ValueConstraint.checkValueConstraints(def, new QValue[] { qValue });
    }

    String getPropertyConstraintViolation(DocViewProperty property, EffectiveNodeType effectiveNodeType, boolean allowProtected)
            throws RepositoryException {
        Name name = ntManagerProvider.getNameResolver().getQName(property.name);

        try {
            if (property.isMulti) {
                return getPropertyConstraintViolation(name, docViewPropertyValueFactory.getValues(property), effectiveNodeType,
                        ntManagerProvider.getItemDefinitionProvider(), ntManagerProvider.getJcrValueFactory(),
                        ntManagerProvider.getQValueFactory(), ntManagerProvider.getNamePathResolver(), allowProtected);
            } else {
                return getPropertyConstraintViolation(name, docViewPropertyValueFactory.getValue(property), effectiveNodeType,
                        ntManagerProvider.getItemDefinitionProvider(), ntManagerProvider.getJcrValueFactory(),
                        ntManagerProvider.getQValueFactory(), ntManagerProvider.getNamePathResolver(), allowProtected);
            }
        } catch (RepositoryException e) {
            throw new RepositoryException(String.format(MESSAGE_PROPERTY_ERROR, property.name, e.getMessage()), e);
        }
    }

    static String getPropertyConstraintViolation(Name name, Value value, EffectiveNodeType effectiveNodeType,
            ItemDefinitionProvider itemDefinitionProvider, ValueFactory valueFactory, QValueFactory qValueFactory,
            NamePathResolver namePathResolver, boolean allowProtected) throws RepositoryException {
        QPropertyDefinition def;
        try {
            def = getPropertyDefinition(name, value.getType(), effectiveNodeType, itemDefinitionProvider, false);
        } catch (ConstraintViolationException t) {
            return "No property definition found for name!";
        }

        if (def.isProtected() && !allowProtected && !ALLOWED_PROTECTED_PROPERTIES.contains(name)) {
            return "Property is protected!";
        }

        // single values are valid for multi and single value
        try {
            validateValueConstraints(value, def, valueFactory, qValueFactory, namePathResolver);
        } catch (ConstraintViolationException e) {
            return "Property value does not satisfy constraints: " + e.getLocalizedMessage();
        } catch (ValueFormatException e) {
            return "Cannot convert property into type '" + def.getRequiredType() + "': " + e.getLocalizedMessage();
        }
        return null;
    }

    static String getPropertyConstraintViolation(Name name, Value[] values, EffectiveNodeType effectiveNodeType,
            ItemDefinitionProvider itemDefinitionProvider, ValueFactory valueFactory, QValueFactory qValueFactory,
            NamePathResolver namePathResolver, boolean allowProtected) throws RepositoryException {
        QPropertyDefinition def;
        int type = values.length > 0 ? values[0].getType() : PropertyType.UNDEFINED;
        try {
            def = getPropertyDefinition(name, type, effectiveNodeType, itemDefinitionProvider, true);
        } catch (ConstraintViolationException t) {
            return "No property definition found for name!";
        }
        if (def.isProtected() && !allowProtected && !ALLOWED_PROTECTED_PROPERTIES.contains(name)) {
            return "Property is protected!";
        }
        if (!def.isMultiple()) {
            return "Property must be single-value!";
        }
        for (Value value : values) {
            try {
                validateValueConstraints(value, def, valueFactory, qValueFactory, namePathResolver);
            } catch (ConstraintViolationException e) {
                return "Property value does not satisfy constraints: " + e.getLocalizedMessage();
            } catch (ValueFormatException e) {
                return "Cannot convert property into type '" + def.getRequiredType() + "': " + e.getLocalizedMessage();
            }
        }
        return null;
    }

    static String getChildNodeConstraintViolation(DocViewNode node, EffectiveNodeType nodeType,
            NodeTypeDefinitionProvider nodeTypeDefinitionProvider,
            NameResolver nameResolver, ItemDefinitionProvider itemDefinitionProvider, boolean allowProtected)
            throws RepositoryException {
        Name nodeName = nameResolver.getQName(node.name);
        QNodeTypeDefinition nodeTypeDefinition = nodeTypeDefinitionProvider.getNodeTypeDefinition(nameResolver.getQName(node.primary));
        if (nodeTypeDefinition.isAbstract()) {
            return "Not allowed to add node with abstract node type as primary type";
        }
        if (nodeTypeDefinition.isMixin()) {
            return "Not allowed to add node with a mixin as primary node type";
        }
        try {
            QNodeDefinition nd = itemDefinitionProvider.getQNodeDefinition(nodeType, nodeName, nodeTypeDefinition.getName());

            if (!allowProtected && nd.isProtected()) {
                return "Node is protected and can not be manually added";
            }

            if (nd.isAutoCreated()) {
                return "Node is auto-created and can not be manually added";
            }
        } catch (ConstraintViolationException e) {
            return "Could not find matching child node definition in parent's node type";
        }

        return null;
    }

    @Override
    public @Nullable Collection<ValidationMessage> done() {
        return null;
    }

}
