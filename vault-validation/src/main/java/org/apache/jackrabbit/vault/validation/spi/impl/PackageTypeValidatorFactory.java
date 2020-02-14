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

import java.util.regex.Pattern;

import org.apache.jackrabbit.vault.validation.spi.ValidationContext;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessageSeverity;
import org.apache.jackrabbit.vault.validation.spi.Validator;
import org.apache.jackrabbit.vault.validation.spi.ValidatorFactory;
import org.apache.jackrabbit.vault.validation.spi.ValidatorSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.kohsuke.MetaInfServices;

@MetaInfServices
public final class PackageTypeValidatorFactory implements ValidatorFactory {

    /**
     * The option to specify the regex of the node paths which all OSGi bundles and configuration within packages must match
     * @see <a href="https://sling.apache.org/documentation/bundles/jcr-installer-provider.html">JCR Installer</a>
     */
    public static final String OPTION_JCR_INSTALLER_NODE_PATH_REGEX = "jcrInstallerNodePathRegex";

    /**
     * The option to specify the regex of the file node paths which all OSGi bundles and configuration within packages must match
     * @see <a href="https://sling.apache.org/documentation/bundles/jcr-installer-provider.html">JCR Installer</a>
     * 
     * Some artifacts are not based on file nodes (e.g. sling:OsgiConfig nodes).
     */
    public static final String OPTION_ADDITIONAL_JCR_INSTALLER_FILE_NODE_PATH_REGEX = "additionalJcrInstallerFileNodePathRegex";

    public static final String OPTION_SEVERITY_FOR_LEGACY_TYPE = "legacyTypeSeverity";

    public static final String OPTION_SEVERITY_FOR_NO_TYPE = "noTypeSeverity";

    public static final String OPTION_PROHIBIT_MUTABLE_CONTENT = "prohibitMutableContent";

    public static final String OPTION_PROHIBIT_IMMUTABLE_CONTENT = "prohibitImmutableContent";

    static final Pattern DEFAULT_JCR_INSTALLER_NODE_PATH_REGEX = Pattern.compile("/([^/]*/){0,4}?(install|config)(\\.[^/]*)*/(\\d{1,3}/)?.+?");

    static final Pattern DEFAULT_ADDITIONAL_JCR_INSTALLER_FILE_NODE_PATH_REGEX = Pattern.compile(".+?\\.(jar|config|cfg|cfg\\.json)");

    private static final ValidationMessageSeverity DEFAULT_SEVERITY_FOR_LEGACY_TYPE = ValidationMessageSeverity.WARN;
    private static final ValidationMessageSeverity DEFAULT_SEVERITY_FOR_NO_TYPE = ValidationMessageSeverity.WARN;
    
    @Override
    public @Nullable Validator createValidator(@NotNull ValidationContext context, @NotNull ValidatorSettings settings) {
        // evaluate options
        final Pattern jcrInstallerNodePathRegex;
        if (settings.getOptions().containsKey(OPTION_JCR_INSTALLER_NODE_PATH_REGEX)) {
            String optionValue = settings.getOptions().get(OPTION_JCR_INSTALLER_NODE_PATH_REGEX);
            jcrInstallerNodePathRegex = Pattern.compile(optionValue);
        } else {
            jcrInstallerNodePathRegex = DEFAULT_JCR_INSTALLER_NODE_PATH_REGEX;
        }
        final Pattern additionalJcrInstallerFileNodePathRegex;
        if (settings.getOptions().containsKey(OPTION_ADDITIONAL_JCR_INSTALLER_FILE_NODE_PATH_REGEX)) {
            String optionValue = settings.getOptions().get(OPTION_ADDITIONAL_JCR_INSTALLER_FILE_NODE_PATH_REGEX);
            additionalJcrInstallerFileNodePathRegex = Pattern.compile(optionValue);
        } else {
            additionalJcrInstallerFileNodePathRegex = DEFAULT_ADDITIONAL_JCR_INSTALLER_FILE_NODE_PATH_REGEX;
        }
        final ValidationMessageSeverity severityForNoType;
        if (settings.getOptions().containsKey(OPTION_SEVERITY_FOR_NO_TYPE)) {
            String optionValue = settings.getOptions().get(OPTION_SEVERITY_FOR_NO_TYPE);
            severityForNoType = ValidationMessageSeverity.valueOf(optionValue.toUpperCase());
        } else {
            severityForNoType = DEFAULT_SEVERITY_FOR_NO_TYPE;
        }
        final ValidationMessageSeverity severityForLegacyType;
        if (settings.getOptions().containsKey(OPTION_SEVERITY_FOR_LEGACY_TYPE)) {
            String optionValue = settings.getOptions().get(OPTION_SEVERITY_FOR_LEGACY_TYPE);
            severityForLegacyType = ValidationMessageSeverity.valueOf(optionValue.toUpperCase());
        } else {
            severityForLegacyType = DEFAULT_SEVERITY_FOR_LEGACY_TYPE;
        }
        final boolean prohibitMutableContent;
        if (settings.getOptions().containsKey(OPTION_PROHIBIT_MUTABLE_CONTENT)) {
            prohibitMutableContent = Boolean.valueOf(settings.getOptions().get(OPTION_PROHIBIT_MUTABLE_CONTENT));
        } else {
            prohibitMutableContent = false;
        }
        final boolean prohibitImmutableContent;
        if (settings.getOptions().containsKey(OPTION_PROHIBIT_IMMUTABLE_CONTENT)) {
            prohibitImmutableContent = Boolean.valueOf(settings.getOptions().get(OPTION_PROHIBIT_IMMUTABLE_CONTENT));
        } else {
            prohibitImmutableContent = false;
        }
        return new PackageTypeValidator(settings.getDefaultSeverity(), severityForNoType, severityForLegacyType, prohibitMutableContent, prohibitImmutableContent, context.getProperties().getPackageType(), jcrInstallerNodePathRegex, additionalJcrInstallerFileNodePathRegex, context.getContainerValidationContext());
    }

    @Override
    public boolean shouldValidateSubpackages() {
        return true; // sub packages also have constraints derived from the application type of the container package
    }

    @Override
    public @NotNull String getId() {
        return ValidatorFactory.ID_PREFIX_JACKRABBIT + "packagetype";
    }

    @Override
    public int getServiceRanking() {
        return 0;
    }

    
}
