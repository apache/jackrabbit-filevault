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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TimeZone;

import javax.jcr.NamespaceException;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.ValueFormatException;
import javax.jcr.nodetype.ConstraintViolationException;
import javax.jcr.nodetype.NoSuchNodeTypeException;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.JcrConstants;
import org.apache.jackrabbit.commons.cnd.ParseException;
import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.spi.commons.conversion.IllegalNameException;
import org.apache.jackrabbit.spi.commons.conversion.MalformedPathException;
import org.apache.jackrabbit.spi.commons.conversion.NameParser;
import org.apache.jackrabbit.spi.commons.conversion.NameResolver;
import org.apache.jackrabbit.spi.commons.name.NameConstants;
import org.apache.jackrabbit.spi.commons.name.NameFactoryImpl;
import org.apache.jackrabbit.util.Text;
import org.apache.jackrabbit.value.BinaryValue;
import org.apache.jackrabbit.value.DateValue;
import org.apache.jackrabbit.value.StringValue;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.util.DocViewNode2;
import org.apache.jackrabbit.vault.util.DocViewProperty2;
import org.apache.jackrabbit.vault.util.StandaloneManagerProvider;
import org.apache.jackrabbit.vault.validation.ValidationExecutor;
import org.apache.jackrabbit.vault.validation.spi.DocumentViewXmlValidator;
import org.apache.jackrabbit.vault.validation.spi.GenericMetaInfDataValidator;
import org.apache.jackrabbit.vault.validation.spi.JcrPathValidator;
import org.apache.jackrabbit.vault.validation.spi.NodeContext;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessage;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessageSeverity;
import org.apache.jackrabbit.vault.validation.spi.util.NodeContextImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NodeTypeValidator implements DocumentViewXmlValidator, JcrPathValidator, GenericMetaInfDataValidator {
    static final String MESSAGE_INVALID_CND_IN_PACKAGE = "Invalid CND file found in package at %s: %s. Cannot consider it for node type validation.";
    static final String MESSAGE_REGISTERED_CND_IN_PACKAGE = "CND file '%s' registered for node type validation";
    static final String MESSAGE_INVALID_PROPERTY_VALUE = "Property %s does not have a valid value: %s";
    static final String MESSAGE_UNKNOWN_NODE_TYPE_OR_NAMESPACE = "%s Skip validation of nodes with that type/name";
    static final String MESSAGE_MISSING_PRIMARY_TYPE = "Mandatory jcr:primaryType missing on node '%s'";

    static final Value DUMMY_BINARY_VALUE = new BinaryValue("dummy binary");
    static final Value DUMMY_DATE_VALUE = new DateValue(Calendar.getInstance(TimeZone.getTimeZone(ZoneOffset.UTC), Locale.ROOT));
    static final Value DUMMY_STRING_VALUE = new StringValue("dummy string");

    private final WorkspaceFilter filter;
    private final ValidationMessageSeverity defaultSeverity;
    private final ValidationMessageSeverity severityForUnknownNodeTypes;
    private final ValidationMessageSeverity severityForDefaultNodeTypeViolations;
    private final DocViewPropertyValueFactory docViewPropertyValueFactory;
    private final StandaloneManagerProvider ntManagerProvider;
    private final Set<String> loggedUnknownNodeTypeMessages;

    private final @NotNull Name defaultType;
    private JcrNodeTypeMetaData currentNodeTypeMetaData;

    public NodeTypeValidator(boolean isIncremental, @NotNull WorkspaceFilter filter, @NotNull StandaloneManagerProvider ntManagerProvider,
            @NotNull Name defaultPrimaryNodeType, @NotNull ValidationMessageSeverity defaultSeverity,
            @NotNull ValidationMessageSeverity severityForUnknownNodeTypes, @NotNull ValidationMessageSeverity severityForDefaultNodeTypeViolations)
            throws ConstraintViolationException, NoSuchNodeTypeException {
        this.filter = filter;
        this.ntManagerProvider = ntManagerProvider;
        this.defaultType = defaultPrimaryNodeType;
        this.defaultSeverity = defaultSeverity;
        this.severityForUnknownNodeTypes = severityForUnknownNodeTypes;
        this.severityForDefaultNodeTypeViolations = severityForDefaultNodeTypeViolations;
        this.docViewPropertyValueFactory = new DocViewPropertyValueFactory();
        this.loggedUnknownNodeTypeMessages = new HashSet<>();

        this.currentNodeTypeMetaData = JcrNodeTypeMetaDataImpl.createRoot(isIncremental, ntManagerProvider.getEffectiveNodeTypeProvider());
    }

    static String getDocViewNodeLabel(DocViewNode2 node) {
        StringBuilder sb = new StringBuilder(node.getName().toString());
        sb.append(" [").append(node.getPrimaryType().orElse("-"));
        if (!node.getMixinTypes().isEmpty()) {
            sb.append(" (").append(StringUtils.join(node.getMixinTypes(), ", ")).append(")");
        }
        sb.append("]");
        return sb.toString();
    }

    @Override
    public @Nullable Collection<ValidationMessage> validateMetaInfData(@NotNull InputStream input, @NotNull Path filePath, @NotNull Path basePath) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            ntManagerProvider.registerNodeTypes(reader);
            return Collections.singleton(
                    new ValidationMessage(ValidationMessageSeverity.INFO, String.format(Locale.ENGLISH, MESSAGE_REGISTERED_CND_IN_PACKAGE, filePath)));
        } catch (RepositoryException | ParseException e) {
            return Collections.singleton(
                    new ValidationMessage(defaultSeverity, String.format(Locale.ENGLISH, MESSAGE_INVALID_CND_IN_PACKAGE, filePath, e.getMessage()), filePath, basePath, 0, 0, e));
        }
    }

    @Override
    public boolean shouldValidateMetaInfData(@NotNull Path filePath, @NotNull Path basePath) {
        if (filePath.getFileName().toString().endsWith(".cnd")) {
            return true;
        }
        return false;
    }

    @Override
    public @Nullable Collection<ValidationMessage> validate(@NotNull DocViewNode2 node, @NotNull NodeContext nodeContext,
            boolean isRoot) {

        Optional<String> primaryType = node.getPrimaryType();
        if (!primaryType.isPresent()) {
            // only an issue if contained in the filter
            // if other properties are set this node is not only used for ordering purposes
            if (filter.contains(nodeContext.getNodePath()) && !node.getProperties().isEmpty()) {
                return Collections.singleton(
                        new ValidationMessage(defaultSeverity, String.format(Locale.ENGLISH, MESSAGE_MISSING_PRIMARY_TYPE, nodeContext.getNodePath())));
            } else {
                // order node only or outside filter
                return null;
            }
        }
        Collection<ValidationMessage> messages = new LinkedList<>();
        messages.addAll(getOrCreateNewNode(nodeContext, false, isImplicit(nodeContext.getNodePath()), false, primaryType.get(), node.getMixinTypes().toArray(new String[0])));

        for (DocViewProperty2 property : node.getProperties()) {
            try {
                messages.addAll(addProperty(nodeContext, property.getName().toString(), property.isMultiValue(), docViewPropertyValueFactory.getValues(property)));
            } catch (ValueFormatException e) {
                messages.add(new ValidationMessage(defaultSeverity,
                        String.format(Locale.ENGLISH, MESSAGE_INVALID_PROPERTY_VALUE, property.getName(), e.getLocalizedMessage())));
            }
        }

        // defer checking for missing mandatory properties (as those might be added by some other files)
        return messages;
    }

    private boolean isImplicit(String path) {
        return !filter.contains(path);
    }

    private Collection<ValidationMessage> addProperty(NodeContext nodeContext, String propertyName, boolean isMultiValue, Value... values) {
        Collection<ValidationMessage> messages = new ArrayList<>();
        try {
            messages.addAll(currentNodeTypeMetaData.addProperty(nodeContext, ntManagerProvider.getNamePathResolver(),
                    ntManagerProvider.getEffectiveNodeTypeProvider(), ntManagerProvider.getNodeTypeDefinitionProvider(),
                    ntManagerProvider.getItemDefinitionProvider(), defaultSeverity, severityForDefaultNodeTypeViolations, propertyName, isMultiValue, values));
        } catch (NoSuchNodeTypeException | NamespaceException e) {
            // log each unknown node type/namespace only once!
            if (!loggedUnknownNodeTypeMessages.contains(e.getMessage())) {
                messages.add(new ValidationMessage(severityForUnknownNodeTypes,
                        String.format(Locale.ENGLISH, MESSAGE_UNKNOWN_NODE_TYPE_OR_NAMESPACE, e.getMessage()), nodeContext, e));
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

    private @NotNull Collection<ValidationMessage> getOrCreateNewNode(NodeContext nodeContext, boolean isFolder, boolean isImplicit, boolean isFallbackPrimaryType, String primaryType,
            String... mixinTypes) {
        Optional<JcrNodeTypeMetaData> node = getNode(nodeContext.getNodePath());
        if (node.isPresent()) {
            currentNodeTypeMetaData = node.get();
            try {
                currentNodeTypeMetaData.setNodeTypes(ntManagerProvider.getNameResolver(), ntManagerProvider.getEffectiveNodeTypeProvider(), isFallbackPrimaryType,
                        primaryType, mixinTypes);
            } catch (NoSuchNodeTypeException | NamespaceException e) {
                currentNodeTypeMetaData.setUnknownNodeTypes();
                // log each unknown node type/namespace only once!
                if (!loggedUnknownNodeTypeMessages.contains(e.getMessage())) {
                    loggedUnknownNodeTypeMessages.add(e.getMessage());
                    return Collections.singleton(new ValidationMessage(severityForUnknownNodeTypes,
                            String.format(Locale.ENGLISH, MESSAGE_UNKNOWN_NODE_TYPE_OR_NAMESPACE, e.getMessage(), nodeContext)));
                }
            } catch (RepositoryException e) {
                throw new IllegalStateException(
                        "Could not create node type information for path '" + nodeContext.getNodePath() + "': " + e.getMessage(), e);
            }
            return Collections.emptyList();
        } else {
            return createNewNode(nodeContext, isFolder, isImplicit, primaryType, mixinTypes);
        }
    }

    private @NotNull Collection<ValidationMessage> createNewNode(NodeContext nodeContext, boolean isFolder, boolean isImplicit, String primaryType,
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
                    nodeContext, parentNodePath);
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
                            ntManagerProvider.getItemDefinitionProvider(), nodeContext, primaryType, mixinTypes);
                    
                }
            } catch (NoSuchNodeTypeException | NamespaceException e) {
                // TODO: NoSuchNodeTypeException might be thrown due to previous registration of the namespace for a node name
                
                // log each unknown node type/namespace only once!
                if (!loggedUnknownNodeTypeMessages.contains(e.getMessage())) {
                    messages.add(new ValidationMessage(severityForUnknownNodeTypes,
                            String.format(Locale.ENGLISH, MESSAGE_UNKNOWN_NODE_TYPE_OR_NAMESPACE, e.getMessage()), nodeContext, e));
                    loggedUnknownNodeTypeMessages.add(e.getMessage());
                }
                if (e instanceof NamespaceExceptionInNodeName) {
                    // now register namespace with an arbitrary namespace url
                    NameParser.parse(nodeName, new OnDemandRegisterNamespaceResolverWrapper(ntManagerProvider),
                            NameFactoryImpl.getInstance());
                    messages.addAll(createNewNode(nodeContext, isFolder, isImplicit, primaryType, mixinTypes));
                } else {
                    currentNodeTypeMetaData = parentNode.addUnknownChildNode(ntManagerProvider.getNameResolver(),nodeContext, nodeName);
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
        messages.add(new ValidationMessage(ValidationMessageSeverity.DEBUG, "Finalize validation for subtree at " + nodeContext));
        for (JcrNodeTypeMetaData child : node.getChildren()) {
            messages.addAll(finalizeValidationForSubtree(child, nodeContext));
            messages.addAll(child.finalizeValidation(ntManagerProvider.getNamePathResolver(), ntManagerProvider.getNodeTypeDefinitionProvider(),
                    ntManagerProvider.getItemDefinitionProvider(), defaultSeverity, severityForDefaultNodeTypeViolations, filter));
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
            messages.addAll(getOrCreateNewNode(nodeContext, isFolder, isImplicit, true, JcrConstants.NT_FOLDER));
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
                    messages.addAll(getOrCreateNewNode(nodeContext, isFolder, isImplicit, true, JcrConstants.NT_FOLDER));
                    String propertyName = fileName.substring(0, fileName.length() - ValidationExecutor.EXTENSION_BINARY.length());
                    messages.addAll(addProperty(nodeContext, propertyName, false, DUMMY_BINARY_VALUE));
                } else {
                    // if binary node is not yet there
                    messages.addAll(getOrCreateNewNode(nodeContext, isFolder, isImplicit, true, JcrConstants.NT_FILE));
                    // if a NT_FILE create a jcr:content sub node of type NT_RESOURCE
                    if (currentNodeTypeMetaData.getPrimaryNodeType().equals(NameConstants.NT_FILE)) {
                        // create new node context
                        nodeContext = new NodeContextImpl(nodeContext.getNodePath() + "/" + JcrConstants.JCR_CONTENT,
                                nodeContext.getFilePath(), nodeContext.getBasePath());
                        messages.addAll(
                                getOrCreateNewNode(nodeContext, isFolder, isImplicit(nodeContext.getNodePath()), true, JcrConstants.NT_RESOURCE));
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
