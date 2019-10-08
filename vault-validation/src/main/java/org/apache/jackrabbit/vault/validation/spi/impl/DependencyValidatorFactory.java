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

import org.apache.jackrabbit.vault.validation.spi.ValidationContext;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessageSeverity;
import org.apache.jackrabbit.vault.validation.spi.Validator;
import org.apache.jackrabbit.vault.validation.spi.ValidatorFactory;
import org.apache.jackrabbit.vault.validation.spi.ValidatorSettings;
import org.kohsuke.MetaInfServices;

@MetaInfServices
public final class DependencyValidatorFactory implements ValidatorFactory {

    public static final String ID = ID_PREFIX_JACKRABBIT + "dependencies";
    
    public static final String OPTION_SEVERITY_FOR_UNRESOLVED_DEPENDENCIES = "severityForUnresolvedDependencies";
    private static final ValidationMessageSeverity DEFAULT_SEVERITY_FOR_UNRESOLVED_DEPENDENCIES = ValidationMessageSeverity.WARN;
    
    @Override
    public Validator createValidator(ValidationContext context, ValidatorSettings settings) {
        final ValidationMessageSeverity severityForUnresolvedDependencies;
        if (settings.getOptions().containsKey(OPTION_SEVERITY_FOR_UNRESOLVED_DEPENDENCIES)) {
            String optionValue = settings.getOptions().get(OPTION_SEVERITY_FOR_UNRESOLVED_DEPENDENCIES);
            severityForUnresolvedDependencies = ValidationMessageSeverity.valueOf(optionValue.toUpperCase());
        } else {
            severityForUnresolvedDependencies = DEFAULT_SEVERITY_FOR_UNRESOLVED_DEPENDENCIES;
        }
        return new DependencyValidator(settings.getDefaultSeverity(), severityForUnresolvedDependencies, context.getDependenciesMetaInfo());
    }

    @Override
    public boolean shouldValidateSubpackages() {
        return false;
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public int getServiceRanking() {
        return 0;
    }

}
