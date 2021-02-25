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
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.jcr.NamespaceException;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.ValueFormatException;
import javax.jcr.nodetype.ConstraintViolationException;
import javax.jcr.nodetype.NoSuchNodeTypeException;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.JcrConstants;
import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.spi.commons.conversion.IllegalNameException;
import org.apache.jackrabbit.spi.commons.conversion.MalformedPathException;
import org.apache.jackrabbit.spi.commons.conversion.NameParser;
import org.apache.jackrabbit.spi.commons.conversion.NameResolver;
import org.apache.jackrabbit.spi.commons.name.NameConstants;
import org.apache.jackrabbit.spi.commons.name.NameFactoryImpl;
import org.apache.jackrabbit.value.BinaryValue;
import org.apache.jackrabbit.value.DateValue;
import org.apache.jackrabbit.value.StringValue;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.util.DocViewNode;
import org.apache.jackrabbit.vault.util.DocViewProperty;
import org.apache.jackrabbit.util.Text;
import org.apache.jackrabbit.vault.validation.ValidationExecutor;
import org.apache.jackrabbit.vault.validation.spi.DocumentViewXmlValidator;
import org.apache.jackrabbit.vault.validation.spi.JcrPathValidator;
import org.apache.jackrabbit.vault.validation.spi.NodeContext;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessage;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessageSeverity;
import org.apache.jackrabbit.vault.validation.spi.util.NodeContextImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NodeTypeValidator implements DocumentViewXmlValidator, JcrPathValidator {
    static final String MESSAGE_INVALID_PROPERTY_VALUE = "Property %s does not have a valid value: %s";
    static final String MESSAGE_UNKNOWN_NODE_TYPE_OR_NAMESPACE = "%s Skip validation of nodes with that type/name";
    static final String MESSAGE_MISSING_PRIMARY_TYPE = "Mandatory jcr:primaryType missing on node '%s'";

    static final Value DUMMY_BINARY_VALUE = new BinaryValue("dummy binary");
    static final Value DUMMY_DATE_VALUE = new DateValue(Calendar.getInstance());
    static final Value DUMMY_STRING_VALUE = new StringValue("dummy string");

    private final WorkspaceFilter filter;
    private final ValidationMessageSeverity defaultSeverity;
    private final ValidationMessageSeverity severityForUnknownNodeTypes;
    private final DocViewPropertyValueFactory docViewPropertyValueFactory;
    private final NodeTypeManagerProvider ntManagerProvider;
    private final Set<String> loggedUnknownNodeTypeMessages;

    private final @NotNull Name defaultType;
    private JcrNodeTypeMetaData currentNodeTypeMetaData;

    public NodeTypeValidator(@NotNull WorkspaceFilter filter, @NotNull NodeTypeManagerProvider ntManagerProvider,
            @NotNull Name defaultPrimaryNodeType, @NotNull ValidationMessageSeverity defaultSeverity,
            @NotNull ValidationMessageSeverity severityForUnknownNodeTypes)
            throws IllegalNameException, ConstraintViolationException, NoSuchNodeTypeException {
        this.filter = filter;
        this.ntManagerProvider = ntManagerProvider;
        this.defaultType = defaultPrimaryNodeType;
        this.defaultSeverity = defaultSeverity;
        this.severityForUnknownNodeTypes = severityForUnknownNodeTypes;
        this.docViewPropertyValueFactory = new DocViewPropertyValueFactory();
        this.loggedUnknownNodeTypeMessages = new HashSet<>();

        this.currentNodeTypeMetaData = JcrNodeTypeMetaDataImpl.createRoot(ntManagerProvider.getEffectiveNodeTypeProvider());
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
        Collection<ValidationMessage> messages = new LinkedList<>();
        messages.addAll(getOrCreateNewNode(nodeContext, isImplicit(nodeContext.getNodePath()), node.primary, node.mixins));

        for (DocViewProperty property : node.props.values()) {
            try {
                messages.addAll(addProperty(nodeContext, property.name, property.isMulti, docViewPropertyValueFactory.getValues(property)));
            } catch (ValueFormatException e) {
                messages.add(new ValidationMessage(defaultSeverity,
                        String.format(MESSAGE_INVALID_PROPERTY_VALUE, property.name, e.getLocalizedMessage())));
            }
        }
        // emit messages
        currentNodeTypeMetaData.fetchAndClearValidationMessages(messages);

        // defer checking for missing mandatory properties (as those might be added by some other files)
        return messages;
    }

    private boolean isImplicit(String path) {
        return !filter.contains(path);
    }

    private Collection<ValidationMessage> addProperty(NodeContext nodeContext, String propertyName, boolean isMultiValue, Value... values) {
        Collection<ValidationMessage> messages = new ArrayList<>();
        try {
            currentNodeTypeMetaData.addProperty(nodeContext, ntManagerProvider.getNamePathResolver(),
                    ntManagerProvider.getEffectiveNodeTypeProvider(), ntManagerProvider.getNodeTypeDefinitionProvider(),
                    ntManagerProvider.getItemDefinitionProvider(), defaultSeverity, propertyName, isMultiValue, values);
        } catch (NoSuchNodeTypeException | NamespaceException e) {
            // log each unknown node type/namespace only once!
            if (!loggedUnknownNodeTypeMessages.contains(e.getMessage())) {
                messages.add(new ValidationMessage(severityForUnknownNodeTypes,
                        String.format(MESSAGE_UNKNOWN_NODE_TYPE_OR_NAMESPACE, e.getMessage()), nodeContext, e));
                loggedUnknownNodeTypeMessages.add(e.getMessage());
            }
        } catch (RepositoryException e) {
            throw new IllegalStateException("Could not validate property against node types: " + e.getMessage(), e);
        }
        return messages;
    }

    private Optional<JcrNodeTypeMetaData> getNode(String nodePath) {
        try {
            return currentNodeTypeMetaData.getNode(ntManagerProvider.getNamePathResolver(), nodePath);
        } catch (NamespaceException e) {
            return Optional.empty();
        } catch (RepositoryException e) {
            throw new IllegalStateException("Could not get node types for path '" + nodePath + "': " + e.getMessage(), e);
        }
    }

    private @NotNull Collection<ValidationMessage> getOrCreateNewNode(NodeContext nodeContext, boolean isImplicit, String primaryType,
            String... mixinTypes) {
        Optional<JcrNodeTypeMetaData> node = getNode(nodeContext.getNodePath());
        if (node.isPresent()) {
            currentNodeTypeMetaData = node.get();
            try {
                currentNodeTypeMetaData.setNodeTypes(ntManagerProvider.getNameResolver(), ntManagerProvider.getEffectiveNodeTypeProvider(),
                        primaryType, mixinTypes);
            } catch (NoSuchNodeTypeException | NamespaceException e) {
                currentNodeTypeMetaData.setUnknownNodeTypes();
                // log each unknown node type/namespace only once!
                if (!loggedUnknownNodeTypeMessages.contains(e.getMessage())) {
                    loggedUnknownNodeTypeMessages.add(e.getMessage());
                    return Collections.singleton(new ValidationMessage(severityForUnknownNodeTypes,
                            String.format(MESSAGE_UNKNOWN_NODE_TYPE_OR_NAMESPACE, e.getMessage(), nodeContext)));
                }
            } catch (RepositoryException e) {
                throw new IllegalStateException(
                        "Could not create node type information for path '" + nodeContext.getNodePath() + "': " + e.getMessage(), e);
            }
            return Collections.emptyList();
        } else {
            return createNewNode(nodeContext, isImplicit, primaryType, mixinTypes);
        }
    }

    private @NotNull Collection<ValidationMessage> createNewNode(NodeContext nodeContext, boolean isImplicit, String primaryType,
            String... mixinTypes) {
        Collection<ValidationMessage> messages = new ArrayList<>();
        String nodePath = nodeContext.getNodePath();
        if (nodePath.equals("/") && !isImplicit) {
            throw new IllegalStateException("Can not create non implicit root node with path \"/\"");
        }
        String parentNodePath = Text.getRelativeParent(nodePath, 1);
        String nodeName = Text.getName(nodePath);
        try {
            //
            JcrNodeTypeMetaData parentNode = currentNodeTypeMetaData.getOrCreateNode(ntManagerProvider.getNamePathResolver(),
                    parentNodePath);
            try {
                if (isImplicit) {
                    if (!nodePath.equals("/")) {
                        currentNodeTypeMetaData = parentNode.addImplicitChildNode(ntManagerProvider.getNameResolver(),
                                ntManagerProvider.getEffectiveNodeTypeProvider(), ntManagerProvider.getNodeTypeDefinitionProvider(),
                                ntManagerProvider.getItemDefinitionProvider(), nodeContext, defaultType);
                    } else {
                        // root node cannot be replaced
                        currentNodeTypeMetaData = parentNode;
                    }
                } else {
                    currentNodeTypeMetaData = parentNode.addChildNode(ntManagerProvider.getNameResolver(),
                            ntManagerProvider.getEffectiveNodeTypeProvider(), ntManagerProvider.getNodeTypeDefinitionProvider(),
                            ntManagerProvider.getItemDefinitionProvider(), defaultSeverity, nodeContext, primaryType, mixinTypes);
                }
            } catch (NoSuchNodeTypeException | NamespaceException e) {
                // TODO: NoSuchNodeTypeException might be thrown due to previous registration of the namespace for a node name
                
                // log each unknown node type/namespace only once!
                if (!loggedUnknownNodeTypeMessages.contains(e.getMessage())) {
                    messages.add(new ValidationMessage(severityForUnknownNodeTypes,
                            String.format(MESSAGE_UNKNOWN_NODE_TYPE_OR_NAMESPACE, e.getMessage()), nodeContext, e));
                    loggedUnknownNodeTypeMessages.add(e.getMessage());
                }
                if (e instanceof NamespaceExceptionInNodeName) {
                    // now register namespace with an arbitrary namespace url
                    NameParser.parse(nodeName, new OnDemandRegisterNamespaceResolverWrapper(ntManagerProvider),
                            NameFactoryImpl.getInstance());
                    messages.addAll(createNewNode(nodeContext, isImplicit, primaryType, mixinTypes));
                } else {
                    currentNodeTypeMetaData = parentNode.addUnknownChildNode(ntManagerProvider.getNameResolver(), nodeName);
                }
            }
        } catch (RepositoryException e) {
            throw new IllegalStateException("Could not create node type information for path '" + nodePath + "': " + e.getMessage(), e);
        }
        return messages;
    }

    /** Called whenever some subtree was fully visited
     * 
     * @param nodePath
     * @return
     * @throws RepositoryException
     * @throws PathNotFoundException
     * @throws IllegalArgumentException
     * @throws NamespaceException
     * @throws IllegalNameException
     * @throws MalformedPathException */
    private @Nullable Collection<ValidationMessage> finalizeValidationForSiblings(NodeContext nodeContext) {
        String parentNodePath = Text.getRelativeParent(nodeContext.getNodePath(), 1);
        String nodeName = Text.getName(nodeContext.getNodePath());
        Collection<ValidationMessage> messages = new ArrayList<>();
        Optional<JcrNodeTypeMetaData> parentNode = getNode(parentNodePath);
        if (!parentNode.isPresent()) {
            throw new IllegalArgumentException("Could not find parent node definition at " + parentNodePath);
        }
        String path = parentNodePath + "/" + nodeName;
        for (JcrNodeTypeMetaData sibling : parentNode.get().getChildren()) {
            try {
                if (sibling.getQualifiedPath(ntManagerProvider.getNamePathResolver()).equals(path)) {
                    continue;
                }
                for (JcrNodeTypeMetaData siblingChild : sibling.getChildren()) {
                    messages.addAll(finalizeValidationForSubtree(siblingChild, nodeContext));
                }
            } catch (NamespaceException e) {
                throw new IllegalStateException("Can not print qualified path for " + path, e);
            }
        }
        return messages;
    }

    private @Nullable Collection<ValidationMessage> finalizeValidationForSubtree(JcrNodeTypeMetaData node, NodeContext nodeContext) throws NamespaceException {
        Collection<ValidationMessage> messages = new ArrayList<>();
        for (JcrNodeTypeMetaData child : node.getChildren()) {
            messages.addAll(finalizeValidationForSubtree(child, nodeContext));
            messages.addAll(child.finalizeValidation(ntManagerProvider.getNamePathResolver(), defaultSeverity, filter));
        }
        return messages;
    }

    @Override
    public @Nullable Collection<ValidationMessage> validateJcrPath(@NotNull NodeContext nodeContext, boolean isFolder,
            boolean isDocViewXml) {
        // track folders
        List<ValidationMessage> messages = new ArrayList<>();
        boolean isImplicit = isImplicit(nodeContext.getNodePath());
        if (isFolder) {
            messages.addAll(getOrCreateNewNode(nodeContext, isImplicit, JcrConstants.NT_FOLDER));
            //
            if (!nodeContext.getNodePath().equals("/")) {
                messages.addAll(finalizeValidationForSiblings(nodeContext));
            }
        } else {
            // for all files which are not docview
            if (!isDocViewXml) {
                String fileName = nodeContext.getFilePath().getFileName().toString();
                // https://jackrabbit.apache.org/filevault/vaultfs.html#Binary_Properties
                if (fileName.endsWith(ValidationExecutor.EXTENSION_BINARY)) {
                    // create parent if it does not exist yet
                    messages.addAll(getOrCreateNewNode(nodeContext, isImplicit, JcrConstants.NT_FOLDER));
                    String propertyName = fileName.substring(0, fileName.length() - ValidationExecutor.EXTENSION_BINARY.length());
                    messages.addAll(addProperty(nodeContext, propertyName, false, DUMMY_BINARY_VALUE));
                } else {
                    // if binary node is not yet there
                    messages.addAll(getOrCreateNewNode(nodeContext, isImplicit, JcrConstants.NT_FILE));
                    // if a NT_FILE create a jcr:content sub node of type NT_RESOURCE
                    if (currentNodeTypeMetaData.getPrimaryNodeType().equals(NameConstants.NT_FILE)) {
                        // create new node context
                        nodeContext = new NodeContextImpl(nodeContext.getNodePath() + "/" + JcrConstants.JCR_CONTENT,
                                nodeContext.getFilePath(), nodeContext.getBasePath());
                        messages.addAll(
                                getOrCreateNewNode(nodeContext, isImplicit(nodeContext.getNodePath()), JcrConstants.NT_RESOURCE));
                    }
                    messages.addAll(addProperty(nodeContext, JcrConstants.JCR_DATA, false, DUMMY_BINARY_VALUE));
                    messages.addAll(addProperty(nodeContext, JcrConstants.JCR_MIMETYPE, false, DUMMY_STRING_VALUE));
                    messages.addAll(addProperty(nodeContext, JcrConstants.JCR_LASTMODIFIED, false, DUMMY_DATE_VALUE));
                }
            }
        }

        return messages;
    }

    static String joinAsQualifiedJcrName(NameResolver nameResolver, Name[] names) throws NamespaceException {
        StringBuilder types = new StringBuilder();
        String delimiter = "";
        for (Name name : names) {
            types.append(delimiter).append(nameResolver.getJCRName(name));
            delimiter = ", ";
        }
        return types.toString();
    }

    @Override
    public @Nullable Collection<ValidationMessage> done() {
        // validate any outstanding nodes
        try {
            return finalizeValidationForSubtree(getNode("/").orElseThrow(() -> new IllegalStateException("Cannot get root node")), new NodeContextImpl("/", Paths.get("/"), Paths.get("/")));
        } catch (NamespaceException e) {
            throw new IllegalStateException("Can not print qualified path", e);
        }
    }

}
