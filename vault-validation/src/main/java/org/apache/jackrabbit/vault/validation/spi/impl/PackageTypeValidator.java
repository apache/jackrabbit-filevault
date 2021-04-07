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
package org.apache.jackrabbit.vault.validation.spi.impl;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.packaging.PackageProperties;
import org.apache.jackrabbit.vault.packaging.PackageType;
import org.apache.jackrabbit.vault.util.Constants;
import org.apache.jackrabbit.vault.util.DocViewNode;
import org.apache.jackrabbit.vault.validation.spi.DocumentViewXmlValidator;
import org.apache.jackrabbit.vault.validation.spi.FilterValidator;
import org.apache.jackrabbit.vault.validation.spi.MetaInfPathValidator;
import org.apache.jackrabbit.vault.validation.spi.NodeContext;
import org.apache.jackrabbit.vault.validation.spi.NodePathValidator;
import org.apache.jackrabbit.vault.validation.spi.PropertiesValidator;
import org.apache.jackrabbit.vault.validation.spi.ValidationContext;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessage;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessageSeverity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Checks if the package type is correctly set for this package
 * 
 * @see <a href="https://issues.apache.org/jira/browse/JCRVLT-170">JCRVLT-170</a> */
public final class PackageTypeValidator implements NodePathValidator, FilterValidator, PropertiesValidator, MetaInfPathValidator, DocumentViewXmlValidator {

    protected static final String MESSAGE_FILTER_HAS_INCLUDE_EXCLUDES = "Package of type '%s' is not supposed to contain includes/excludes below any of its filters!";
    protected static final String MESSAGE_UNSUPPORTED_SUB_PACKAGE_OF_TYPE = "Package of type '%s' must only contain sub packages of type '%s' but found sub package of type '%s'!";
    protected static final String MESSAGE_UNSUPPORTED_SUB_PACKAGE = "Package of type '%s' is not supposed to contain any sub packages!";
    protected static final String MESSAGE_DEPENDENCY = "Package of type '%s' must not have package dependencies but found dependencies '%s'!";
    protected static final String MESSAGE_LEGACY_TYPE = "Package of type '%s' is legacy. Use one of the other types instead!";
    protected static final String MESSAGE_PACKAGE_HOOKS = "Package of type '%s' must not contain package hooks but has '%s'!";
    protected static final String MESSAGE_NO_PACKAGE_TYPE_SET = "No package type set, make sure that property 'packageType' is set in the properties.xml!";
    protected static final String MESSAGE_OSGI_BUNDLE_OR_CONFIG = "Package of type '%s' is not supposed to contain OSGi bundles or configurations!";
    protected static final String MESSAGE_NO_OSGI_BUNDLE_OR_CONFIG_OR_SUB_PACKAGE = "Package of type '%s' is not supposed to contain anything but OSGi bundles/configurations and sub packages!";
    protected static final String MESSAGE_APP_CONTENT = "Package of type '%s' is not supposed to contain content below root nodes %s!";
    protected static final String MESSAGE_NO_APP_CONTENT_FOUND = "Package of type '%s' is not supposed to contain content outside root nodes %s!";
    protected static final String MESSAGE_PROHIBITED_MUTABLE_PACKAGE_TYPE = "All mutable package types are prohibited and this package is of mutable type '%s'";
    protected static final String MESSAGE_PROHIBITED_IMMUTABLE_PACKAGE_TYPE = "All mutable package types are prohibited and this package is of mutable type '%s'";
    protected static final String SLING_OSGI_CONFIG = "sling:OsgiConfig";
    protected static final Path PATH_HOOKS = Paths.get(Constants.VAULT_DIR, Constants.HOOKS_DIR);
    private final @NotNull PackageType type;
    private final @NotNull ValidationMessageSeverity severity;
    private final @NotNull ValidationMessageSeverity severityForLegacyType;
    private final @NotNull Pattern jcrInstallerNodePathRegex;
    private final @NotNull Pattern additionalJcrInstallerFileNodePathRegex;
    private final @Nullable ValidationContext containerValidationContext;
    private final ValidationMessageSeverity severityForNoPackageType;
    private final boolean prohibitMutableContent;
    private final boolean prohibitImmutableContent;
    private final boolean allowComplexFilterRulesInApplicationPackages;
    private final boolean allowInstallHooksInApplicationPackages;
    private final @NotNull WorkspaceFilter filter;
    private final Set<String> immutableRootNodeNames;
    private List<String> validContainerNodePaths;
    private List<NodeContext> potentiallyDisallowedContainerNodes;

    public PackageTypeValidator(@NotNull WorkspaceFilter workspaceFilter, @NotNull ValidationMessageSeverity severity,
            @NotNull ValidationMessageSeverity severityForNoPackageType, @NotNull ValidationMessageSeverity severityForLegacyType,
            boolean prohibitMutableContent, boolean prohibitImmutableContent, boolean allowComplexFilterRulesInApplicationPackages,
            boolean allowInstallHooksInApplicationPackages, @NotNull PackageType type, @NotNull Pattern jcrInstallerNodePathRegex, 
            @NotNull Pattern additionalJcrInstallerFileNodePathRegex, @NotNull Set<String> immutableRootNodeNames, 
            @Nullable ValidationContext containerValidationContext) {
        this.type = type;
        this.severity = severity;
        this.severityForNoPackageType = severityForNoPackageType;
        this.severityForLegacyType = severityForLegacyType;
        this.prohibitMutableContent = prohibitMutableContent;
        this.prohibitImmutableContent = prohibitImmutableContent;
        this.allowComplexFilterRulesInApplicationPackages = allowComplexFilterRulesInApplicationPackages;
        this.allowInstallHooksInApplicationPackages = allowInstallHooksInApplicationPackages;
        this.jcrInstallerNodePathRegex = jcrInstallerNodePathRegex;
        this.additionalJcrInstallerFileNodePathRegex = additionalJcrInstallerFileNodePathRegex;
        this.immutableRootNodeNames = immutableRootNodeNames;
        this.containerValidationContext = containerValidationContext;
        this.filter = workspaceFilter;
        this.validContainerNodePaths = new LinkedList<>();
        this.potentiallyDisallowedContainerNodes = new LinkedList<>();
    }

    boolean isOsgiBundleOrConfiguration(String nodePath, boolean onlyFile) {
        if (jcrInstallerNodePathRegex.matcher(nodePath).matches()) {
            if (onlyFile) {
                return additionalJcrInstallerFileNodePathRegex.matcher(nodePath).matches();
            } else {
                return true;
            }
        }
        return false;
    }

    static boolean isSubPackage(String nodePath) {
        return (nodePath.endsWith(".zip"));
    }

    boolean isImmutableContent(String nodePath) {
        return immutableRootNodeNames.stream().anyMatch( 
                rootNodeName ->  ("/"+rootNodeName).equals(nodePath) || nodePath.startsWith( "/"+rootNodeName + "/"));
    }

    @Override
    public @Nullable Collection<ValidationMessage> done() {
        // check if questionable nodes are parents of valid nodes
        List<NodeContext> invalidNodes = potentiallyDisallowedContainerNodes.stream().filter(
                s -> validContainerNodePaths.stream().noneMatch(
                        p -> p.startsWith(s.getNodePath() + "/")))
                .collect(Collectors.toList());
        if (!invalidNodes.isEmpty()) {
            return invalidNodes.stream().map(
                    e -> new ValidationMessage(severity, String.format(MESSAGE_NO_OSGI_BUNDLE_OR_CONFIG_OR_SUB_PACKAGE, type), e.getNodePath(), e.getFilePath(), e.getBasePath(), null))
                    .collect(Collectors.toList());
        }
        return null;
    }

    @Override
    public @Nullable Collection<ValidationMessage> validate(@NotNull NodeContext nodeContext) {
        // ignore uncovered nodePaths
        if (!filter.covers(nodeContext.getNodePath())) {
            return null;
        }
        Collection<ValidationMessage> messages = new LinkedList<>();
        switch (type) {
        case CONTENT:
            if (isImmutableContent(nodeContext.getNodePath())) {
                messages.add(new ValidationMessage(severity, String.format(MESSAGE_APP_CONTENT, type, immutableRootNodeNames.stream().collect(Collectors.joining("' or '", "'", "'")))));
            }
            if (isOsgiBundleOrConfiguration(nodeContext.getNodePath(), true)) {
                messages.add(new ValidationMessage(severity, String.format(MESSAGE_OSGI_BUNDLE_OR_CONFIG, type)));
            }
            break;
        case APPLICATION:
            if (!isImmutableContent(nodeContext.getNodePath())) {
                messages.add(new ValidationMessage(severity, String.format(MESSAGE_NO_APP_CONTENT_FOUND, type, immutableRootNodeNames.stream().collect(Collectors.joining("' or '", "'", "'")))));
            }
            if (isOsgiBundleOrConfiguration(nodeContext.getNodePath(), true)) {
                messages.add(new ValidationMessage(severity, String.format(MESSAGE_OSGI_BUNDLE_OR_CONFIG, type)));
            }
            // sub packages are detected via validate(Properties) on the sub package
            break;
        case CONTAINER:
            // sling:OsgiConfig
            if (isOsgiBundleOrConfiguration(nodeContext.getNodePath(), false)) {
                validContainerNodePaths.add(nodeContext.getNodePath());
            }
            else if (isSubPackage(nodeContext.getNodePath())) {
                validContainerNodePaths.add(nodeContext.getNodePath());
            } else {
                // only potentially disallowed, as the node may be a parent of a sub package or osgi bundle, which is allowed as well
                potentiallyDisallowedContainerNodes.add(nodeContext);
            }
            break;
        case MIXED:
            // no validations currently as most relaxed type
            break;
        }
        return messages;
    }

    @Override
    public Collection<ValidationMessage> validate(@NotNull WorkspaceFilter filter) {
        switch (type) {
        case APPLICATION:
            if (!allowComplexFilterRulesInApplicationPackages && hasIncludesOrExcludes(filter)) {
                return Collections.singleton(new ValidationMessage(severity, String.format(MESSAGE_FILTER_HAS_INCLUDE_EXCLUDES, type)));
            }
            break;
        case CONTENT:
        case CONTAINER:
        case MIXED:
            break;
        }
        return null;
    }

    @Override
    public Collection<ValidationMessage> validate(@NotNull PackageProperties properties) {
        PackageType packageType = properties.getPackageType();
        if (packageType == null) {
            return Collections.singleton(new ValidationMessage(severityForNoPackageType, MESSAGE_NO_PACKAGE_TYPE_SET));
        }

        Collection<ValidationMessage> messages = new LinkedList<>();
        // is sub package?
        if (containerValidationContext != null) {
            messages.add(new ValidationMessage(ValidationMessageSeverity.DEBUG, "Found sub package"));
            ValidationMessage message = validateSubPackageType(properties.getPackageType(),
                    containerValidationContext.getProperties().getPackageType());
            if (message != null) {
                messages.add(message);
            }
        }

        switch (packageType) {
        case APPLICATION:
            // must not contain hooks (this detects external hooks)
            if (!properties.getExternalHooks().isEmpty() && !allowInstallHooksInApplicationPackages) {
                messages.add(new ValidationMessage(severity,
                        String.format(MESSAGE_PACKAGE_HOOKS, properties.getPackageType(), properties.getExternalHooks())));
            }
            if (prohibitImmutableContent) {
                messages.add(new ValidationMessage(severity,
                        String.format(MESSAGE_PROHIBITED_IMMUTABLE_PACKAGE_TYPE, properties.getPackageType())));
            }
            break;
        case CONTENT:
            if (prohibitMutableContent) {
                messages.add(new ValidationMessage(severity,
                        String.format(MESSAGE_PROHIBITED_MUTABLE_PACKAGE_TYPE, properties.getPackageType())));
            }
            break;
        case CONTAINER:
            // no dependencies
            if (properties.getDependencies() != null && properties.getDependencies().length > 0) {
                messages.add(new ValidationMessage(severity,
                        String.format(MESSAGE_DEPENDENCY, properties.getPackageType(), StringUtils.join(properties.getDependencies()))));
            }
            if (prohibitImmutableContent) {
                messages.add(new ValidationMessage(ValidationMessageSeverity.ERROR,
                        String.format(MESSAGE_PROHIBITED_IMMUTABLE_PACKAGE_TYPE, properties.getPackageType())));
            }
            break;
        case MIXED:
            messages.add(
                    new ValidationMessage(severityForLegacyType, String.format(MESSAGE_LEGACY_TYPE, properties.getPackageType())));
            if (prohibitImmutableContent) {
                messages.add(new ValidationMessage(ValidationMessageSeverity.ERROR,
                        String.format(MESSAGE_PROHIBITED_IMMUTABLE_PACKAGE_TYPE, properties.getPackageType())));
            }
            if (prohibitMutableContent) {
                messages.add(new ValidationMessage(ValidationMessageSeverity.ERROR,
                        String.format(MESSAGE_PROHIBITED_MUTABLE_PACKAGE_TYPE, properties.getPackageType())));
            }
            break;
        }
        return messages;
    }

    static boolean hasIncludesOrExcludes(WorkspaceFilter filter) {
        for (PathFilterSet set : filter.getFilterSets()) {
            if (!set.getEntries().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private ValidationMessage validateSubPackageType(PackageType packageType, @Nullable PackageType containerPackageType) {
        ValidationMessage message = null;
        if (containerPackageType == null) {
            return null;
        }
        switch (containerPackageType) {
        case APPLICATION:
            // no sub packages allowed
            message = new ValidationMessage(severity, String.format(MESSAGE_UNSUPPORTED_SUB_PACKAGE, containerPackageType));
            break;
        case CONTENT:
            if (packageType != PackageType.CONTENT) {
                message = new ValidationMessage(severity, String.format(MESSAGE_UNSUPPORTED_SUB_PACKAGE_OF_TYPE, containerPackageType,
                        PackageType.CONTENT.toString(), packageType));
            }
            break;
        case CONTAINER:
            if (packageType != PackageType.APPLICATION && packageType != PackageType.CONTAINER && packageType != PackageType.CONTENT) {
                message = new ValidationMessage(severity, String.format(MESSAGE_UNSUPPORTED_SUB_PACKAGE_OF_TYPE, containerPackageType,
                        StringUtils.join(new String[] { PackageType.APPLICATION.toString(), PackageType.CONTENT.toString(),
                                PackageType.CONTAINER.toString() }, ", "),
                        packageType));
            }
            break;
        case MIXED:
            break;
        }
        return message;
    }

    @Override
    public Collection<ValidationMessage> validateMetaInfPath(@NotNull Path filePath, @NotNull Path basePath, boolean isFolder) {
        switch (type) {
        case APPLICATION:
            if (filePath.startsWith(PATH_HOOKS) && !allowInstallHooksInApplicationPackages)
                // must not contain hooks (this detects internal hooks)
                return Collections.singleton(new ValidationMessage(severity, String.format(MESSAGE_PACKAGE_HOOKS, type, filePath)));
        default:
            break;
        }
        return null;
    }

    @Override
    public @Nullable Collection<ValidationMessage> validate(@NotNull DocViewNode node, @NotNull NodeContext nodeContext, boolean isRoot) {
        // check only type content and application
        switch (type) {
        case APPLICATION:
        case CONTENT:
            if (jcrInstallerNodePathRegex.matcher(nodeContext.getNodePath()).matches() && SLING_OSGI_CONFIG.equals(node.primary)) {
               return Collections.singleton(new ValidationMessage(severity, String.format(MESSAGE_OSGI_BUNDLE_OR_CONFIG, type)));
            }
            break;
        case CONTAINER:
        case MIXED:
            break;
        }
        return null;
    }
}
