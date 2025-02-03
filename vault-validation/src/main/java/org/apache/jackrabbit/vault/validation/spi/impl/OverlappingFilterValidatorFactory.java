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
import java.util.Locale;

import org.apache.jackrabbit.vault.validation.spi.ValidationContext;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessageSeverity;
import org.apache.jackrabbit.vault.validation.spi.Validator;
import org.apache.jackrabbit.vault.validation.spi.ValidatorFactory;
import org.apache.jackrabbit.vault.validation.spi.ValidatorSettings;
import org.jetbrains.annotations.NotNull;
import org.kohsuke.MetaInfServices;

@MetaInfServices
public final class OverlappingFilterValidatorFactory implements ValidatorFactory {

    public static final String ID = ValidatorFactory.ID_PREFIX_JACKRABBIT + "overlappingfilter";

    /**
     * Severity for validation messages regarding overlapping filter rules only affect a single node (not a full subtree).
     * This pattern is often used in overlay scenarios to enforce certain ancestor nodes.
     * By default those overlaps should do not lead to a failure.
     */
    public static final String OPTION_SEVERITY_FOR_OVERLAPPING_SINGLE_NODE_PATTERNS = "severityForOverlappingSingleNodePatterns";

    @Override
    public Validator createValidator(@NotNull ValidationContext context, @NotNull ValidatorSettings settings) {
        final OverlappingFilterValidator validator;
        if (context.getContainerValidationContext() == null) {
            final ValidationMessageSeverity severityForOverlappingSingleNodePatterns;
            if (settings.getOptions().containsKey(OPTION_SEVERITY_FOR_OVERLAPPING_SINGLE_NODE_PATTERNS)) {
                String optionValue = settings.getOptions().get(OPTION_SEVERITY_FOR_OVERLAPPING_SINGLE_NODE_PATTERNS);
                severityForOverlappingSingleNodePatterns = ValidationMessageSeverity.valueOf(optionValue.toUpperCase(Locale.ROOT));
            } else {
                severityForOverlappingSingleNodePatterns = ValidationMessageSeverity.WARN;
            }
            // create new validator for every root container (which may contain additional subpackages)
            validator = new OverlappingFilterValidator(settings.getDefaultSeverity(), severityForOverlappingSingleNodePatterns);
            // store in context
            context.setAttribute("overlappingValidator", validator);
            // add filter
            validator.addFilter(context.getFilter(), getRelativePath(context.getPackageRootPath()));
            return validator;
        } else {
            // for each sub package (even nested ones)
            // find topmost container
            validator = (OverlappingFilterValidator)getRootContainerContext(context).getAttribute("overlappingValidator");
            // add filter
            validator.addFilter(context.getFilter(), getRelativePath(context.getPackageRootPath()));
        }
        return null;
    }

    private static String getRelativePath(Path path) {
        Path workingDirectory = Paths.get("").toAbsolutePath();
        return workingDirectory.relativize(path).toString();
    }

    private static ValidationContext getRootContainerContext(final ValidationContext context) {
        ValidationContext rootContext = context;
        while (rootContext.getContainerValidationContext() != null) {
            rootContext = rootContext.getContainerValidationContext();
        }
        return rootContext;
    }

    @Override
    public boolean shouldValidateSubpackages() {
        return true;
    }

    @Override
    public @NotNull String getId() {
        return ID;
    }

    @Override
    public int getServiceRanking() {
        return 0;
    }

}
