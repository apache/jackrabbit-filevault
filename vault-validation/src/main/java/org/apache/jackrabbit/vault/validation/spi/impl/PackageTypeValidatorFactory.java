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

import javax.annotation.CheckForNull;

import org.apache.jackrabbit.vault.validation.spi.ValidationContext;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessageSeverity;
import org.apache.jackrabbit.vault.validation.spi.Validator;
import org.apache.jackrabbit.vault.validation.spi.ValidatorFactory;
import org.apache.jackrabbit.vault.validation.spi.ValidatorSettings;
import org.kohsuke.MetaInfServices;

@MetaInfServices
public final class PackageTypeValidatorFactory implements ValidatorFactory {

    /**
     * The option to specify the regex of the node paths which all OSGi bundles and configuration within packages must match
     * @see <a href="https://sling.apache.org/documentation/bundles/jcr-installer-provider.html">JCR Installer</a>
     */
    public static final String OPTION_JCR_INSTALLER_NODE_PATH_REGEX = "jcrInstallerNodePathRegex";

    static final Pattern DEFAULT_JCR_INSTALLER_NODE_PATH_REGEX = Pattern.compile("/([^/]*/){0,4}?(install|config)(\\.[^/]*)*/(\\d{1,3}/)?.+?\\.(jar|config|cfg|cfg\\.json|xml)$");

    @Override
    public @CheckForNull Validator createValidator(ValidationContext context, ValidatorSettings settings) {
        // evaluate options
        final Pattern jcrInstallerNodePathRegex;
        if (settings.getOptions().containsKey(OPTION_JCR_INSTALLER_NODE_PATH_REGEX)) {
            String optionValue = settings.getOptions().get(OPTION_JCR_INSTALLER_NODE_PATH_REGEX);
            jcrInstallerNodePathRegex = Pattern.compile(optionValue);
        } else {
            jcrInstallerNodePathRegex = DEFAULT_JCR_INSTALLER_NODE_PATH_REGEX;
        }
        return new PackageTypeValidator(settings.getDefaultSeverity(), ValidationMessageSeverity.WARN, context.getProperties().getPackageType(), jcrInstallerNodePathRegex, context.getContainerValidationContext());
    }

    @Override
    public boolean shouldValidateSubpackages() {
        return true; // sub packages also have constraints derived from the application type of the container package
    }

    @Override
    public String getId() {
        return ValidatorFactory.ID_PREFIX_JACKRABBIT + "packagetype";
    }

    @Override
    public int getServiceRanking() {
        return 0;
    }

    
}
