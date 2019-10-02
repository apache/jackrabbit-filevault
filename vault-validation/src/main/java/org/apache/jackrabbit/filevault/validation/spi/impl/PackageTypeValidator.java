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
package org.apache.jackrabbit.filevault.validation.spi.impl;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.regex.Pattern;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.filevault.validation.spi.FilterValidator;
import org.apache.jackrabbit.filevault.validation.spi.MetaInfPathValidator;
import org.apache.jackrabbit.filevault.validation.spi.NodePathValidator;
import org.apache.jackrabbit.filevault.validation.spi.PropertiesValidator;
import org.apache.jackrabbit.filevault.validation.spi.ValidationContext;
import org.apache.jackrabbit.filevault.validation.spi.ValidationMessage;
import org.apache.jackrabbit.filevault.validation.spi.ValidationMessageSeverity;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.packaging.PackageProperties;
import org.apache.jackrabbit.vault.packaging.PackageType;
import org.apache.jackrabbit.vault.util.Constants;

/** Checks if the package type is correctly set for this package
 * 
 * @see <a href="https://issues.apache.org/jira/browse/JCRVLT-170">JCRVLT-170</a> */
public final class PackageTypeValidator implements NodePathValidator, FilterValidator, PropertiesValidator, MetaInfPathValidator {

    protected static final String MESSAGE_FILTER_HAS_INCLUDE_EXCLUDES = "Package of type '%s' is not supposed to contain includes/excludes below any of its filters!";
    protected static final String MESSAGE_UNSUPPORTED_SUB_PACKAGE_OF_TYPE = "Package of type '%s' must only contain sub packages of type '%s' but found sub package of type '%s'!";
    protected static final String MESSAGE_UNSUPPORTED_SUB_PACKAGE = "Package of type '%s' is not supposed to contain any sub packages!";
    protected static final String MESSAGE_DEPENDENCY = "Package of type '%s' must not have package dependencies but found dependencies '%s'!";
    protected static final String MESSAGE_LEGACY_TYPE = "Package of type '%s' is legacy. Use one of the other types instead!";
    protected static final String MESSAGE_PACKAGE_HOOKS = "Package of type '%s' must not contain package hooks but has '%s'!";
    protected static final String MESSAGE_NO_PACKAGE_TYPE_SET = "No package type set, make sure that property 'packageType' is set in the properties.xml!";
    protected static final String MESSAGE_OSGI_BUNDLE_OR_CONFIG = "Package of type '%s' is not supposed to contain OSGi bundles or configuration but has '%s'!";
    protected static final String MESSAGE_NO_OSGI_BUNDLE_OR_CONFIG_OR_SUB_PACKAGE = "Package of type '%s' is not supposed to contain anything but OSGi bundles/configurations and sub packages but has '%s'!";
    protected static final String MESSAGE_APP_CONTENT = "Package of type '%s' is not supposed to contain content inside '/libs' and '/apps' but has '%s'!";
    protected static final String MESSAGE_NO_APP_CONTENT_FOUND = "Package of type '%s' is not supposed to contain content outside '/libs' and '/apps' but has '%s'!";
    protected static final String MESSAGE_INDEX_DEFINITIONS = "Package of type '%s' is not supposed to contain Oak index definitions but has 'allowIndexDefinitions' set to true.";

    protected static final Path PATH_HOOKS = Paths.get(Constants.VAULT_DIR, Constants.HOOKS_DIR);
    private final PackageType type;
    private final ValidationMessageSeverity severity;
    private final ValidationMessageSeverity severityForLegacyType;
    private final Pattern jcrInstallerNodePathRegex;
    private final ValidationContext containerValidationContext;

    public PackageTypeValidator(@Nonnull ValidationMessageSeverity severity, @Nonnull ValidationMessageSeverity severityForLegacyType,
            PackageType type, @Nonnull Pattern jcrInstallerNodePathRegex,
            ValidationContext containerValidationContext) {
        this.type = type;
        this.severity = severity;
        this.severityForLegacyType = severityForLegacyType;
        this.jcrInstallerNodePathRegex = jcrInstallerNodePathRegex;
        this.containerValidationContext = containerValidationContext;
    }

    boolean isOsgiBundleOrConfiguration(String nodePath) {
        return jcrInstallerNodePathRegex.matcher(nodePath).matches();
    }

    static boolean isSubPackage(String nodePath) {
        return (nodePath.endsWith(".zip"));
    }

    static boolean isAppContent(String nodePath) {
        return "/apps".equals(nodePath) || nodePath.startsWith("/apps/") || "/libs".equals(nodePath) || nodePath.startsWith("/libs/");
    }

    @Override
    public @CheckForNull Collection<ValidationMessage> done() {
        return null;
    }

    @Override
    public @CheckForNull Collection<ValidationMessage> validate(String nodePath) {
        if (type == null) {
            return null;
        }
        Collection<ValidationMessage> messages = new LinkedList<>();
        switch (type) {
        case CONTENT:
            if (isAppContent(nodePath)) {
                messages.add(new ValidationMessage(severity, String.format(MESSAGE_APP_CONTENT, type, nodePath)));
            }
            if (isOsgiBundleOrConfiguration(nodePath)) {
                messages.add(new ValidationMessage(severity, String.format(MESSAGE_OSGI_BUNDLE_OR_CONFIG, type, nodePath)));
            }
            break;
        case APPLICATION:
            if (!isAppContent(nodePath)) {
                messages.add(new ValidationMessage(severity, String.format(MESSAGE_NO_APP_CONTENT_FOUND, type, nodePath)));
            }
            if (isOsgiBundleOrConfiguration(nodePath)) {
                messages.add(new ValidationMessage(severity, String.format(MESSAGE_OSGI_BUNDLE_OR_CONFIG, type, nodePath)));
            }
            // sub packages are detected via validate(Properties) on the sub package
            break;
        case CONTAINER:
            if (!isOsgiBundleOrConfiguration(nodePath) && !isSubPackage(nodePath)) {
                messages.add(
                        new ValidationMessage(severity, String.format(MESSAGE_NO_OSGI_BUNDLE_OR_CONFIG_OR_SUB_PACKAGE, type, nodePath)));
            }

            break;
        case MIXED:
            // no validations currently as most relaxed type
            break;
        }
        return messages;
    }

    @Override
    public Collection<ValidationMessage> validate(WorkspaceFilter filter) {
        if (type == null) {
            return null;
        }
        switch (type) {
        case APPLICATION:
            if (hasIncludesOrExcludes(filter)) {
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

    static boolean hasIncludesOrExcludes(WorkspaceFilter filter) {
        for (PathFilterSet set : filter.getFilterSets()) {
            if (!set.getEntries().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Collection<ValidationMessage> validate(PackageProperties properties) {
        if (properties.getPackageType() == null) {
            return Collections.singleton(new ValidationMessage(severityForLegacyType, MESSAGE_NO_PACKAGE_TYPE_SET));
        }
        Collection<ValidationMessage> messages = new LinkedList<>();
        // is sub package?
        if (containerValidationContext != null) {
            messages.add(new ValidationMessage(ValidationMessageSeverity.DEBUG, "Found sub package"));
            ValidationMessage message = validateSubPackageType(properties.getPackageType(), containerValidationContext.getPackageType());
            if (message != null) {
                messages.add(message);
            }
        }

        switch (properties.getPackageType()) {
        case APPLICATION:
            // must not contain hooks (this detects external hooks)
            if (!properties.getExternalHooks().isEmpty()) {
                messages.add(new ValidationMessage(severity,
                        String.format(MESSAGE_PACKAGE_HOOKS, properties.getPackageType(), properties.getExternalHooks())));
            }
            // must not include oak:index
            if (OakIndexDefinitionValidatorFactory.areIndexDefinitionsAllowed(properties)) {
                messages.add(new ValidationMessage(severity, String.format(MESSAGE_INDEX_DEFINITIONS, properties.getPackageType())));
            }
            break;
        case CONTENT:
            break;
        case CONTAINER:
            // no dependencies
            if (properties.getDependencies() != null && properties.getDependencies().length > 0) {
                messages.add(new ValidationMessage(severity,
                        String.format(MESSAGE_DEPENDENCY, properties.getPackageType(), StringUtils.join(properties.getDependencies()))));
            }
            break;
        case MIXED:
            messages.add(
                    new ValidationMessage(severityForLegacyType, String.format(MESSAGE_LEGACY_TYPE, properties.getPackageType())));
            break;
        }
        return messages;
    }

    private ValidationMessage validateSubPackageType(PackageType packageType, PackageType containerPackageType) {
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
            if (packageType != PackageType.APPLICATION) {
                message = new ValidationMessage(severity, String.format(MESSAGE_UNSUPPORTED_SUB_PACKAGE_OF_TYPE, containerPackageType,
                        PackageType.APPLICATION.toString(), packageType));
            }
            break;
        case MIXED:
            break;
        }
        return message;
    }

    
    @Override
    public Collection<ValidationMessage> validateMetaInfPath(Path filePath) {
        if (type == null) {
            return null;
        }
        switch (type) {
            case APPLICATION:
                if (filePath.startsWith(PATH_HOOKS))
                    // must not contain hooks (this detects internal hooks)
                    return Collections.singleton(new ValidationMessage(severity, String.format(MESSAGE_PACKAGE_HOOKS, type, filePath)));
            default:
                break;
        }
        return null;
    }

}
