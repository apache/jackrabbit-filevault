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

import javax.annotation.CheckForNull;

import org.apache.jackrabbit.vault.packaging.PackageProperties;
import org.apache.jackrabbit.vault.validation.spi.ValidationContext;
import org.apache.jackrabbit.vault.validation.spi.Validator;
import org.apache.jackrabbit.vault.validation.spi.ValidatorFactory;
import org.apache.jackrabbit.vault.validation.spi.ValidatorSettings;
import org.kohsuke.MetaInfServices;

@MetaInfServices
public final class OakIndexDefinitionValidatorFactory implements ValidatorFactory {

    @Override
    public @CheckForNull Validator createValidator(ValidationContext context, ValidatorSettings settings) {
        // only validate in case allowIndexDefinitions is not set for any of the validated packages
        Path path = getPathOfNotAllowedIndexDefinition(context);
        if (path != null) {
            return new OakIndexDefinitionValidator(path, settings.getDefaultSeverity());
        }
        return null;
    }

    private static Path getPathOfNotAllowedIndexDefinition(ValidationContext context) {
        if (!areIndexDefinitionsAllowed(context.getProperties()))  {
            return context.getPackageRootPath();
        } else {
            ValidationContext containerContext = context.getContainerValidationContext();
            if (containerContext != null) {
                return getPathOfNotAllowedIndexDefinition(containerContext);
            } else {
                return null;
            }
        }
    }
    
    static boolean areIndexDefinitionsAllowed(PackageProperties properties) {
        return "true".equals(properties.getProperty(PackageProperties.NAME_ALLOW_INDEX_DEFINITIONS));
    }

    @Override
    public boolean shouldValidateSubpackages() {
        return true;
    }

    @Override
    public String getId() {
        return ValidatorFactory.ID_PREFIX_JACKRABBIT + "oakindex";
    }

    @Override
    public int getServiceRanking() {
        return 0;
    }
    
    
}
