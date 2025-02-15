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

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import javax.jcr.NamespaceException;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.nodetype.ConstraintViolationException;
import javax.jcr.nodetype.NoSuchNodeTypeException;

import org.apache.jackrabbit.jcr2spi.nodetype.EffectiveNodeType;
import org.apache.jackrabbit.jcr2spi.nodetype.EffectiveNodeTypeProvider;
import org.apache.jackrabbit.jcr2spi.nodetype.ItemDefinitionProvider;
import org.apache.jackrabbit.jcr2spi.nodetype.NodeTypeDefinitionProvider;
import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.spi.Path;
import org.apache.jackrabbit.spi.Path.Element;
import org.apache.jackrabbit.spi.QNodeDefinition;
import org.apache.jackrabbit.spi.QNodeTypeDefinition;
import org.apache.jackrabbit.spi.QPropertyDefinition;
import org.apache.jackrabbit.spi.QValue;
import org.apache.jackrabbit.spi.QValueFactory;
import org.apache.jackrabbit.spi.commons.conversion.IllegalNameException;
import org.apache.jackrabbit.spi.commons.conversion.MalformedPathException;
import org.apache.jackrabbit.spi.commons.conversion.NamePathResolver;
import org.apache.jackrabbit.spi.commons.conversion.NameResolver;
import org.apache.jackrabbit.spi.commons.name.NameConstants;
import org.apache.jackrabbit.spi.commons.name.NameFactoryImpl;
import org.apache.jackrabbit.spi.commons.name.PathBuilder;
import org.apache.jackrabbit.spi.commons.name.PathFactoryImpl;
import org.apache.jackrabbit.spi.commons.nodetype.constraint.ValueConstraint;
import org.apache.jackrabbit.spi.commons.value.QValueFactoryImpl;
import org.apache.jackrabbit.spi.commons.value.ValueFormat;
import org.apache.jackrabbit.util.Text;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.validation.spi.NodeContext;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessage;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessageSeverity;
import org.apache.jackrabbit.vault.validation.spi.util.NodeContextImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** This class encapsulates node type related data of a node. It uses expanded names/paths internally. */
public class JcrNodeTypeMetaDataImpl implements JcrNodeTypeMetaData {

    static final String EXCEPTION_MESSAGE_INVALID_NAME = "Invalid %s '%s': %s";
    static final String CONSTRAINT_PROPERTY_VALUE = "Value constraint violation: %s";
    static final String CONSTRAINT_PROPERTY_PROTECTED = "Property is protected!";
    static final String CONSTRAINT_PROPERTY_AUTO_CREATED = "Property is auto-created and can not be manually added";
    static final String CONSTRAINT_PROPERTY_NOT_ALLOWED = "No applicable property definition found for name and type!";
    static final String CONSTRAINT_CHILD_NODE_AUTO_CREATED = "Node is auto-created and can not be manually added";
    static final String CONSTRAINT_CHILD_NODE_PROTECTED = "Node is protected and can not be manually added";
    static final String CONSTRAINT_MIXIN_TYPE_AS_PRIMARY_TYPE = "Given node type is a mixin and cannot be used as primary node type.";
    static final String CONSTRAINT_ABSTRACT_TYPE_AS_PRIMARY_TYPE = "Given node type is abstract and cannot be used as primary node type.";
    static final String CONSTRAINT_CHILD_NODE_NOT_ALLOWED = "Node type does not allow arbitrary child nodes and does not allow this specific name and node type either!";

    static final String MESSAGE_CHILD_NODE_NOT_ALLOWED = "Node '%s [%s]' is not allowed as child of node with %s: %s";
    static final String MESSAGE_PROPERTY_NOT_ALLOWED = "Property '%s' [%s] is not allowed in node with %s: %s";
    static final String MESSAGE_MANDATORY_CHILD_NODE_MISSING = "Mandatory child node missing: %s inside node with %s";
    static final String MESSAGE_MANDATORY_UNCONTAINED_CHILD_NODE_MISSING = "Mandatory child node missing: %s inside node with types [%s] (outside of filter rules)";
    static final String MESSAGE_MANDATORY_PROPERTY_MISSING = "Mandatory property '%s' missing in node with %s";
    static final String MESSAGE_MANDATORY_PROPERTY_WITH_WRONG_TYPE = "Mandatory property '%s' has type '%s' while it should have '%s' in node with %s";

    // do not validate protected JCR system properties that are handled by FileVault specially in https://github.com/apache/jackrabbit-filevault/blob/f785fcb24d4cbd01c734e9273310a925c29ae15b/vault-core/src/main/java/org/apache/jackrabbit/vault/fs/impl/io/DocViewSAXImporter.java#L123 and 
    // https://github.com/apache/jackrabbit-filevault/blob/f785fcb24d4cbd01c734e9273310a925c29ae15b/vault-core/src/main/java/org/apache/jackrabbit/vault/fs/impl/io/DocViewSAXImporter.java#L140
    private static final Collection<Name> JCR_SYSTEM_PROPERTIES = Arrays.asList(
            NameConstants.JCR_PRIMARYTYPE, NameConstants.JCR_MIXINTYPES, NameConstants.JCR_UUID,
            NameConstants.JCR_BASEVERSION, NameConstants.JCR_PREDECESSORS, NameConstants.JCR_SUCCESSORS, 
            NameConstants.JCR_VERSIONHISTORY, NameConstants.JCR_ISCHECKEDOUT, 
            NameFactoryImpl.getInstance().create("http://jackrabbit.apache.org/oak/ns/1.0", "counter"));

    private static final Name NT_REP_POLICY = NameFactoryImpl.getInstance().create(Name.NS_REP_URI, "Policy");
    private static final Name NT_REP_AUTHORIZABLE = NameFactoryImpl.getInstance().create(Name.NS_REP_URI, "Authorizable");
    private static final QValueFactory QVALUE_FACTORY = QValueFactoryImpl.getInstance();

    private final @NotNull Name name;
    private final @NotNull NodeContext context;
    private @Nullable Name primaryNodeType; // the effectiveNodeType does not remember which one was the primary one!
    private @Nullable EffectiveNodeType effectiveNodeType;
    private final @NotNull Map<Name, Integer> propertyTypesByName;
    private final @NotNull Map<Name, JcrNodeTypeMetaDataImpl> childNodesByName;
    private final @Nullable JcrNodeTypeMetaDataImpl parentNode;
    private boolean isAuthenticationOrAuthorizationContext;
    private final boolean isImplicit; // if this is true, the node type is set implicitly (not explicitly set in package, used as is in the
                                      // repository)
    private boolean isValidationDone;
    private final boolean isIncremental;

    private JcrNodeTypeMetaDataImpl(boolean isIncremental, @NotNull NodeContext context, @NotNull Name name, @Nullable Name primaryNodeType, @Nullable EffectiveNodeType effectiveNodeType,
            JcrNodeTypeMetaDataImpl parentNode, boolean isAuthenticationOrAuthorizationContext, boolean isImplicit) {
        super();
        this.context = context;
        this.name = name; // fully namespaced (taking into account local namespace declaration for Docview XML)
        this.primaryNodeType = primaryNodeType;
        this.effectiveNodeType = effectiveNodeType;
        this.parentNode = parentNode;
        this.propertyTypesByName = new HashMap<>();
        this.childNodesByName = new HashMap<>();
        this.isAuthenticationOrAuthorizationContext = isAuthenticationOrAuthorizationContext;
        this.isImplicit = isImplicit;
        this.isValidationDone = false;
        this.isIncremental = isIncremental;
    }

    @Override
    public String toString() {
        return "JcrNodeTypeMetaDataImpl [" + "name=" + name + ", "
                + "effectiveNodeType=" + effectiveNodeType + ", "
                + "propertyTypesByName=" + propertyTypesByName + ", "
                + "childNodes=" + childNodesByName.keySet() + ", "
                // + "parentNode path="+(parentNode != null ? + parentNode.getPath() + ", " : "")
                + "isAuthenticationOrAuthorizationContext=" + isAuthenticationOrAuthorizationContext + "]";
    }

    @Override
    public void setUnknownNodeTypes() {
        this.primaryNodeType = null;
        this.effectiveNodeType = null;
    }

    @Override
    public void setNodeTypes(@NotNull NameResolver nameResolver,
            @NotNull EffectiveNodeTypeProvider effectiveNodeTypeProvider, boolean isFallbackPrimaryType, @NotNull String primaryType, String... mixinTypes)
            throws IllegalNameException, ConstraintViolationException, NoSuchNodeTypeException, NamespaceException {
        List<Name> types = getTypes(nameResolver, primaryType, mixinTypes);
        if (effectiveNodeType == null || (!isFallbackPrimaryType && !effectiveNodeType.includesNodeTypes(types.toArray(new Name[0])))) {
            // only override if not a default node type
            this.primaryNodeType = types.get(0);
            this.effectiveNodeType = effectiveNodeTypeProvider.getEffectiveNodeType(types.toArray(new Name[0]));
            if (!isAuthenticationOrAuthorizationContext) {
                isAuthenticationOrAuthorizationContext = isAclOrAuthorizableNodeType(effectiveNodeType);
            }
        }
    }

    @Override
    public Name getPrimaryNodeType() {
        return primaryNodeType;
    }

    private static boolean isAclOrAuthorizableNodeType(EffectiveNodeType effectiveNodeType) {
        return effectiveNodeType.includesNodeType(NT_REP_AUTHORIZABLE) || effectiveNodeType.includesNodeType(NT_REP_POLICY);
    }

    private enum NameType {
        NODE_NAME("node name"), PRIMARY_TYPE("primary type"), MIXIN_TYPE("mixin type");

        private final String label;

        NameType(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    private static @NotNull Name getQName(@NotNull NameResolver nameResolver, @NotNull String name, @NotNull NameType type)
            throws IllegalNameException, NamespaceException {
        try {
            Name qName = nameResolver.getQName(name);
            // was it a namespace which has been generated on demand before?
            if (type != NameType.NODE_NAME
                    && qName.getNamespaceURI().startsWith(OnDemandRegisterNamespaceResolverWrapper.UNDECLARED_NAMESPACE_URI_PREFIX)) {
                int posColon = name.indexOf(':');
                // extract prefix
                String prefix = name.substring(0, posColon);
                throw new NamespaceException(prefix + ": is not a registered namespace prefix.");
            }
            return qName;
        } catch (NamespaceException e) {
            if (type == NameType.NODE_NAME) {
                throw new NamespaceExceptionInNodeName(
                        String.format(Locale.ENGLISH, EXCEPTION_MESSAGE_INVALID_NAME, type.getLabel(), name, e.getLocalizedMessage()), e);
            }
            throw new NamespaceException(String.format(Locale.ENGLISH, EXCEPTION_MESSAGE_INVALID_NAME, type.getLabel(), name, e.getLocalizedMessage()), e);
        } catch (IllegalNameException e) {
            throw new IllegalNameException(String.format(Locale.ENGLISH, EXCEPTION_MESSAGE_INVALID_NAME, type.getLabel(), name, e.getLocalizedMessage()),
                    e);
        }
    }

    @Override
    public @NotNull JcrNodeTypeMetaData addImplicitChildNode(@NotNull NameResolver nameResolver,
            @NotNull EffectiveNodeTypeProvider effectiveNodeTypeProvider,
            @NotNull NodeTypeDefinitionProvider nodeTypeDefinitionProvider, @NotNull ItemDefinitionProvider itemDefinitionProvider,
            @NotNull NodeContext nodeContext, @Nullable Name implicitNodeType) throws RepositoryException {
        JcrNodeTypeMetaDataImpl childNode = addChildNode(nameResolver, effectiveNodeTypeProvider, nodeTypeDefinitionProvider,
                itemDefinitionProvider, true, nodeContext, Text.getName(nodeContext.getNodePath()), implicitNodeType);
        return childNode;
    }

    @Override
    public @NotNull JcrNodeTypeMetaData addUnknownChildNode(@NotNull NameResolver nameResolver, @NotNull NodeContext context, @NotNull String name)
            throws IllegalNameException, NamespaceException {
        return addUnknownChildNode(context, getQName(nameResolver, name, NameType.NODE_NAME));
    }

    private @NotNull JcrNodeTypeMetaDataImpl addUnknownChildNode(@NotNull NodeContext context, @NotNull Name name) throws IllegalNameException {
        JcrNodeTypeMetaDataImpl childNode = new JcrNodeTypeMetaDataImpl(this.isIncremental, context, name, null, null, this, false, false);
        childNodesByName.put(name, childNode);
        return childNode;
    }

    @Override
    public @NotNull JcrNodeTypeMetaData addChildNode(@NotNull NameResolver nameResolver,
            @NotNull EffectiveNodeTypeProvider effectiveNodeTypeProvider,
            @NotNull NodeTypeDefinitionProvider nodeTypeDefinitionProvider, @NotNull ItemDefinitionProvider itemDefinitionProvider,
            @NotNull NodeContext nodeContext, @NotNull String primaryType, String... mixinTypes)
            throws IllegalNameException, RepositoryException, NamespaceExceptionInNodeName {

        List<Name> types = getTypes(nameResolver, primaryType, mixinTypes);
        String nodeName = Text.getName(nodeContext.getNodePath());
        JcrNodeTypeMetaDataImpl childNode = addChildNode(nameResolver, effectiveNodeTypeProvider, nodeTypeDefinitionProvider,
                itemDefinitionProvider, false, nodeContext, nodeName, types.toArray(new Name[0]));
        // defer validation
        return childNode;
    }

    private static List<Name> getTypes(@NotNull NameResolver nameResolver, @NotNull String primaryType, String... mixinTypes)
            throws IllegalNameException, NamespaceException {
        List<Name> types = new ArrayList<>();
        types.add(getQName(nameResolver, primaryType, NameType.PRIMARY_TYPE));
        if (mixinTypes != null) {
            for (String mixinType : mixinTypes) {
                types.add(getQName(nameResolver, mixinType, NameType.MIXIN_TYPE));
            }
        }
        return types;
    }

    private @NotNull JcrNodeTypeMetaDataImpl addChildNode(@NotNull NameResolver nameResolver,
            @NotNull EffectiveNodeTypeProvider effectiveNodeTypeProvider,
            @NotNull NodeTypeDefinitionProvider nodeTypeDefinitionProvider, @NotNull ItemDefinitionProvider itemDefinitionProvider,
            boolean isImplicit, @NotNull NodeContext context, @NotNull String name, @Nullable Name... nodeTypes)
            throws ConstraintViolationException, NoSuchNodeTypeException, NamespaceExceptionInNodeName, NamespaceException,
            IllegalNameException {

        final Name qName = getQName(nameResolver, name, NameType.NODE_NAME);

        // special handling for users and acls
        boolean isAuthenticationOrAuthorizationContext = false;
        final EffectiveNodeType newEffectiveNodeType;
        final Name newPrimaryNodeType;
        if (nodeTypes != null) {
            newEffectiveNodeType = effectiveNodeTypeProvider.getEffectiveNodeType(nodeTypes);
            newPrimaryNodeType = nodeTypes[0];
            isAuthenticationOrAuthorizationContext = isAclOrAuthorizableNodeType(newEffectiveNodeType);
        } else {
            newEffectiveNodeType = null;
            newPrimaryNodeType = null;
        }
        // special handling for users and acls
        if (!isAuthenticationOrAuthorizationContext) {
            isAuthenticationOrAuthorizationContext = this.isAuthenticationOrAuthorizationContext;
        }
        JcrNodeTypeMetaDataImpl newNode = new JcrNodeTypeMetaDataImpl(this.isIncremental, context, qName, newPrimaryNodeType, newEffectiveNodeType, this,
                isAuthenticationOrAuthorizationContext, isImplicit);
        childNodesByName.put(qName, newNode);
        return newNode;
    }

    /** Similar to
     * {@link EffectiveNodeType#checkAddNodeConstraints(Name, org.apache.jackrabbit.spi.QNodeTypeDefinition, ItemDefinitionProvider)}
     * 
     * @param parentEffectiveNodeType
     * @return constraints violation message
     * @throws RepositoryException */
    private Optional<String> validateAgainstParentNodeType(@NotNull EffectiveNodeType parentEffectiveNodeType,
            @NotNull NodeTypeDefinitionProvider nodeTypeDefinitionProvider,
            @NotNull ItemDefinitionProvider itemDefinitionProvider) throws RepositoryException {
        if (effectiveNodeType == null || primaryNodeType == null) {
            return Optional.empty();
        }
        // except for ACL node types (for which the mixin rep:AccessControllable is transparently added) everything must comply with the
        // parent node rules
        if (effectiveNodeType.includesNodeType(NT_REP_POLICY)) {
            return Optional.empty();
        }

        QNodeTypeDefinition primaryNodeTypeDefinition = nodeTypeDefinitionProvider.getNodeTypeDefinition(primaryNodeType);
        if (primaryNodeTypeDefinition.isAbstract()) {
            return Optional.of(CONSTRAINT_ABSTRACT_TYPE_AS_PRIMARY_TYPE);
        } else if (primaryNodeTypeDefinition.isMixin()) {
            return Optional.of(CONSTRAINT_MIXIN_TYPE_AS_PRIMARY_TYPE);
        }
        try {
            // get applicable node type from parent
            QNodeDefinition applicableParentNodeDefinition = itemDefinitionProvider.getQNodeDefinition(parentEffectiveNodeType, this.name,
                    primaryNodeType);
            if (!isAuthenticationOrAuthorizationContext && applicableParentNodeDefinition.isProtected()) {
                return Optional.of(CONSTRAINT_CHILD_NODE_PROTECTED);
            }
            if (applicableParentNodeDefinition.isAutoCreated()) {
                return Optional.of(CONSTRAINT_CHILD_NODE_AUTO_CREATED);
            }
        } catch (ConstraintViolationException e) {
            return Optional.of(CONSTRAINT_CHILD_NODE_NOT_ALLOWED);
        }
        return Optional.empty();
    }

    @Override
    public @NotNull Collection<ValidationMessage> finalizeValidation(@NotNull NamePathResolver namePathResolver,  @NotNull NodeTypeDefinitionProvider nodeTypeDefinitionProvider, @NotNull ItemDefinitionProvider itemDefinitionProvider,
            @NotNull ValidationMessageSeverity severity, @NotNull ValidationMessageSeverity severityForDefaultNodeTypeViolations, @NotNull WorkspaceFilter filter) throws NamespaceException {
        if (!isValidationDone) {
            Collection<ValidationMessage> messages = new LinkedList<>();
            // in incremental validations ignore missing mandatory properties and child nodes (as they might not be visible to the validator)
            if (!isIncremental) {
                messages.add(new ValidationMessage(ValidationMessageSeverity.DEBUG,
                        "Validate children and mandatory properties of " + getQualifiedPath(namePathResolver), context));
                messages.addAll(validateChildNodes(namePathResolver, nodeTypeDefinitionProvider, itemDefinitionProvider, severity, severityForDefaultNodeTypeViolations, filter));
                messages.addAll(validateMandatoryProperties(namePathResolver, severity, severityForDefaultNodeTypeViolations));
            }
            // only remove child nodes on 2nd level to be able to validate mandatory properties of parent
            childNodesByName.clear();
            isValidationDone = true;
            messages.add(new ValidationMessage(ValidationMessageSeverity.DEBUG,
                    "Remove node information of children of " + getQualifiedPath(namePathResolver), context));
            return messages;
        } else {
            return Collections.singletonList(new ValidationMessage(ValidationMessageSeverity.DEBUG,
                    "Already finalized validation of " + getQualifiedPath(namePathResolver), context));
        }
    }

    private Collection<ValidationMessage> validateChildNodes(@NotNull NamePathResolver namePathResolver,  @NotNull NodeTypeDefinitionProvider nodeTypeDefinitionProvider, @NotNull ItemDefinitionProvider itemDefinitionProvider,
            @NotNull ValidationMessageSeverity severity, @NotNull ValidationMessageSeverity severityForDefaultNodeTypeViolations, @NotNull WorkspaceFilter filter) {
        if (effectiveNodeType == null) {
            return Collections.emptyList();
        }

        Collection<ValidationMessage> messages = new LinkedList<>();
        // validate child nodes against parent node type definition
        for (JcrNodeTypeMetaDataImpl childNode : childNodesByName.values()) {
            Optional<String> constraintViolation;
            try {
                constraintViolation = childNode.validateAgainstParentNodeType(effectiveNodeType, nodeTypeDefinitionProvider,
                        itemDefinitionProvider);
                if (constraintViolation.isPresent()) {
                    messages.add(new ValidationMessage(isImplicit ? severityForDefaultNodeTypeViolations : severity,
                            String.format(Locale.ENGLISH, 
                                    MESSAGE_CHILD_NODE_NOT_ALLOWED,
                                    namePathResolver.getJCRName(childNode.name), namePathResolver.getJCRName(childNode.primaryNodeType),
                                    getEffectiveNodeTypeLabel(namePathResolver, effectiveNodeType),
                                    constraintViolation.get()), childNode.context));
        
                }
            } catch (RepositoryException e) {
                throw new IllegalStateException("Could not validate child node " + childNode.name + " against parent node definition", e);
            }
            
        }

        // validate mandatory child nodes of children
        for (QNodeDefinition mandatoryNodeType : effectiveNodeType.getMandatoryQNodeDefinitions()) {
            // skip auto created ones
            if (mandatoryNodeType.isAutoCreated()) {
                continue;
            }
            boolean foundRequiredChildNode = false;
            for (JcrNodeTypeMetaDataImpl child : childNodesByName.values()) {
                foundRequiredChildNode = child.fulfillsNodeDefinition(mandatoryNodeType);
            }

            if (!foundRequiredChildNode && !mandatoryNodeType.getName().equals(NameConstants.ANY_NAME)) {
                PathBuilder pathBuilder = new PathBuilder(this.getPath());
                pathBuilder.addLast(mandatoryNodeType.getName());
                try {
                    if (filter.contains(namePathResolver.getJCRPath(pathBuilder.getPath()))) {
                        messages.add(new ValidationMessage(isImplicit ? severityForDefaultNodeTypeViolations : severity,
                                String.format(Locale.ENGLISH, MESSAGE_MANDATORY_CHILD_NODE_MISSING,
                                getNodeDefinitionLabel(namePathResolver, mandatoryNodeType),
                                getEffectiveNodeTypeLabel(namePathResolver, effectiveNodeType)), context));
                    } else {
                        // if mandatory child nodes are missing outside filter rules, this is not an issue
                        messages.add(new ValidationMessage(ValidationMessageSeverity.DEBUG, String.format(Locale.ENGLISH, MESSAGE_MANDATORY_UNCONTAINED_CHILD_NODE_MISSING,
                                getNodeDefinitionLabel(namePathResolver, mandatoryNodeType),
                                getEffectiveNodeTypeLabel(namePathResolver, effectiveNodeType)), context));
                    }
                } catch (NamespaceException | MalformedPathException e) {
                    throw new IllegalStateException("Could not give out node types and name for " + mandatoryNodeType, e);
                }
            }
        }

        return messages;
    }

    private String getEffectiveNodeTypeLabel(NameResolver nameResolver, EffectiveNodeType nodeType) throws NamespaceException {
        String label;
        String types = joinAsQualifiedJcrName(nameResolver, nodeType.getMergedNodeTypes());
        if (isImplicit) 
            label = String.format(Locale.ENGLISH, "potential default types [%s]", types);
        else {
            label =  String.format(Locale.ENGLISH, "types [%s]", types);
        }
        return label;
    }

    private static String getNodeDefinitionLabel(NameResolver nameResolver, QNodeDefinition nodeDefinition) throws NamespaceException {
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

    @Override
    public @NotNull Collection<@NotNull ? extends JcrNodeTypeMetaData> getChildren() {
        return childNodesByName.values();
    }

    @Override
    public @NotNull JcrNodeTypeMetaData getOrCreateNode(NamePathResolver nameResolver, @NotNull NodeContext nodeContext, String path) throws RepositoryException {
        return getNode(nameResolver, nodeContext, path, true).get();
    }

    @Override
    public Optional<JcrNodeTypeMetaData> getNode(NamePathResolver nameResolver, String path) throws RepositoryException {
        return getNode(nameResolver, null, path, false);
    }

    private Optional<JcrNodeTypeMetaData> getNode(NamePathResolver nameResolver, @Nullable NodeContext nodeContext, String path, boolean shouldCreateIfMissing)
            throws RepositoryException {
        // convert to fully namespaced path
        Path qPath = nameResolver.getQPath(path);

        // navigate there
        Path qRelativePath = getPath().computeRelativePath(qPath);

        // first go up until you reach a common parent
        @NotNull
        JcrNodeTypeMetaDataImpl currentNode = this;
        for (Element element : qRelativePath.getElements()) {
            if (!element.denotesParent()) {
                break;
            } else {
                currentNode = currentNode.parentNode;
            }
        }
        qRelativePath = currentNode.getPath().computeRelativePath(qPath);

        // then go down until you match the path
        for (Element element : qRelativePath.getElements()) {
            if (element.denotesCurrent()) {
                continue;
            }
            JcrNodeTypeMetaDataImpl childNode = currentNode.childNodesByName.get(element.getName());
            if (childNode == null) {
                if (shouldCreateIfMissing) {
                    if (nodeContext == null) {
                        throw new IllegalArgumentException("Node context must be given in case node is created but is null");
                    }
                    childNode = currentNode.addUnknownChildNode(nodeContext, element.getName());
                } else {
                    return Optional.empty();
                }
            }
            currentNode = childNode;
        }
        return Optional.of(currentNode);
    }

    private Collection<ValidationMessage> validateMandatoryProperties(@NotNull NamePathResolver nameResolver,
            @NotNull ValidationMessageSeverity severity, @NotNull ValidationMessageSeverity severityForDefaultNodeTypeViolations) {

        if (effectiveNodeType == null) {
            return Collections.emptyList();
        }
        Collection<ValidationMessage> messages = new ArrayList<>();
        // are all mandatory properties covered?
        for (QPropertyDefinition mandatoryPropertyDefinition : effectiveNodeType.getMandatoryQPropertyDefinitions()) {
            // ignore auto-created properties as they are created on-demand
            if (mandatoryPropertyDefinition.isAutoCreated()) {
                continue;
            }
            // ignore certain properties which are handled specially in filevault
            if (JCR_SYSTEM_PROPERTIES.contains(mandatoryPropertyDefinition.getName()) ) {
                continue;
            }
            try {
                if (!propertyTypesByName.containsKey(mandatoryPropertyDefinition.getName())) {
                    messages.add(new ValidationMessage(isImplicit ? severityForDefaultNodeTypeViolations : severity,
                            String.format(Locale.ENGLISH, MESSAGE_MANDATORY_PROPERTY_MISSING,
                                    nameResolver.getJCRName(mandatoryPropertyDefinition.getName()),
                                    getEffectiveNodeTypeLabel(nameResolver, effectiveNodeType)), context));
                } else {
                    // check type
                    int actualPropertyType = propertyTypesByName.get(mandatoryPropertyDefinition.getName());
                    if (mandatoryPropertyDefinition.getRequiredType() != actualPropertyType) {
                        // check type
                        messages.add(new ValidationMessage(isImplicit ? severityForDefaultNodeTypeViolations : severity,
                                String.format(Locale.ENGLISH, MESSAGE_MANDATORY_PROPERTY_WITH_WRONG_TYPE,
                                        nameResolver.getJCRName(mandatoryPropertyDefinition.getName()),
                                        PropertyType.nameFromValue(actualPropertyType),
                                        PropertyType.nameFromValue(mandatoryPropertyDefinition.getRequiredType()),
                                        getEffectiveNodeTypeLabel(nameResolver, effectiveNodeType)), context));
                    }
                }
            } catch (NamespaceException e) {
                throw new IllegalStateException("Could not give out parent node types or property names for " + mandatoryPropertyDefinition,
                        e);
            }
        }
        return messages;
    }

    @Override
    public Collection<ValidationMessage> addProperty(@NotNull NodeContext nodeContext, @NotNull NamePathResolver namePathResolver,
            @NotNull EffectiveNodeTypeProvider effectiveNodeTypeProvider,
            @NotNull NodeTypeDefinitionProvider nodeTypeDefinitionProvider, @NotNull ItemDefinitionProvider itemDefinitionProvider,
            @NotNull ValidationMessageSeverity severity, @NotNull ValidationMessageSeverity severityForDefaultNodeTypeViolations, String name, boolean isMultiValue, Value... values) throws RepositoryException {
        Collection<ValidationMessage> messages = new ArrayList<>();
        // some sanity checks on multivalue
        if (!isMultiValue && values.length > 1) {
            throw new IllegalArgumentException("isMultiValue is only supposed to be false if exactly one value is passed but "
                    + values.length + " values were passed!");
        }

        if (values.length == 0) {
            // unable to proceed when no value is present
            return messages;
        }

        Name qName;
        try {
            qName = namePathResolver.getQName(name);
        } catch (IllegalNameException | NamespaceException e) {
            throw new IllegalNameException("Invalid property name " + name, e);
        }
        propertyTypesByName.put(qName, values[0].getType());

        // now check for validity
        Optional<String> constraintViolation = validatePropertyConstraints(namePathResolver, effectiveNodeTypeProvider,
                nodeTypeDefinitionProvider, itemDefinitionProvider, qName, values, isAuthenticationOrAuthorizationContext,
                isMultiValue);
        if (constraintViolation.isPresent()) {
            messages.add(new ValidationMessage(isImplicit ? severityForDefaultNodeTypeViolations : severity,
                    String.format(Locale.ENGLISH, 
                            MESSAGE_PROPERTY_NOT_ALLOWED,
                            namePathResolver.getJCRName(qName), PropertyType.nameFromValue(values[0].getType()),
                            getEffectiveNodeTypeLabel(namePathResolver, effectiveNodeType),
                            constraintViolation.get()),
                    nodeContext));
        }
        return messages;
    }

    private @NotNull Optional<String> validatePropertyConstraints(@NotNull NamePathResolver namePathResolver,
            @NotNull EffectiveNodeTypeProvider effectiveNodeTypeProvider,
            @NotNull NodeTypeDefinitionProvider nodeTypeDefinitionProvider, @NotNull ItemDefinitionProvider itemDefinitionProvider,
            Name name, Value[] values, boolean allowProtected, boolean isMultiValue) throws RepositoryException {
        if (effectiveNodeType == null) {
            return Optional.empty();
        }
        QPropertyDefinition applicablePropertyDefinition;
        try {
            applicablePropertyDefinition = getPropertyDefinition(name, values[0].getType(), effectiveNodeType, itemDefinitionProvider,
                    isMultiValue);
        } catch (ConstraintViolationException t) {
            return Optional.of(CONSTRAINT_PROPERTY_NOT_ALLOWED);
        }
        if (applicablePropertyDefinition.isProtected() && !allowProtected && !JCR_SYSTEM_PROPERTIES.contains(name)) {
            return Optional.of(CONSTRAINT_PROPERTY_PROTECTED);
        }

        for (Value value : values) {
            try {
                QValue qValue = ValueFormat.getQValue(value, namePathResolver, QVALUE_FACTORY);
                ValueConstraint.checkValueConstraints(applicablePropertyDefinition, new QValue[] { qValue });
            } catch (ConstraintViolationException e) {
                return Optional.of(String.format(Locale.ENGLISH, CONSTRAINT_PROPERTY_VALUE, e.getLocalizedMessage()));
            }
        }
        return Optional.empty();
    }

    private static QPropertyDefinition getPropertyDefinition(Name name, int type, EffectiveNodeType effectiveNodeType,
            ItemDefinitionProvider itemDefinitionProvider, boolean isMultiValue)
            throws NoSuchNodeTypeException, ConstraintViolationException {
        QPropertyDefinition def;
        try {
            def = itemDefinitionProvider.getQPropertyDefinition(effectiveNodeType.getAllNodeTypes(), name, type,
                    isMultiValue);
        } catch (ConstraintViolationException e) {
            if (type != PropertyType.UNDEFINED) {
                def = itemDefinitionProvider.getQPropertyDefinition(effectiveNodeType.getAllNodeTypes(), name, PropertyType.UNDEFINED,
                        isMultiValue);
            } else {
                throw e;
            }
        }
        return def;
    }

    private boolean fulfillsNodeDefinition(QNodeDefinition nodeDefinition) {
        // name must match
        if (!nodeDefinition.getName().equals(NameConstants.ANY_NAME) && !nodeDefinition.getName().equals(name)) {
            return false;
        }

        if (effectiveNodeType == null) {
            // if effective node type cannot be determined, assume the worst case (i.e. node does not match definition)
            return false;
        }
        for (Name requiredType : nodeDefinition.getRequiredPrimaryTypes()) {
            // type must match all of the given types
            if (!effectiveNodeType.includesNodeType(requiredType)) {
                return false;
            }
        }
        return true;
    }

    public static @NotNull JcrNodeTypeMetaDataImpl createRoot(boolean isIncremental, @NotNull EffectiveNodeTypeProvider effectiveNodeTypeProvider)
            throws ConstraintViolationException, NoSuchNodeTypeException {
        return new JcrNodeTypeMetaDataImpl(isIncremental, new NodeContextImpl("", Paths.get(""), Paths.get("")),
            NameConstants.ROOT, NameConstants.REP_ROOT, effectiveNodeTypeProvider.getEffectiveNodeType(
                new Name[] {
                        NameConstants.REP_ROOT,
                        NameConstants.REP_ACCESS_CONTROLLABLE,
                        NameConstants.REP_REPO_ACCESS_CONTROLLABLE }),
                null, false, false);
    }

    private Path getPath() {
        if (parentNode == null) {
            return PathFactoryImpl.getInstance().getRootPath();
        } else {
            PathBuilder pathBuilder = new PathBuilder(parentNode.getPath());
            pathBuilder.addLast(name);
            try {
                return pathBuilder.getPath();
            } catch (MalformedPathException e) {
                throw new IllegalStateException("Could not create path from parent and name", e);
            }
        }
    }

    @Override
    public String getQualifiedPath(NamePathResolver resolver) throws NamespaceException {
        return resolver.getJCRPath(getPath());
    }

}
