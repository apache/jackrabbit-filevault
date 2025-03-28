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

import java.util.Locale;

import org.apache.jackrabbit.vault.validation.spi.ValidationContext;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessageSeverity;
import org.apache.jackrabbit.vault.validation.spi.Validator;
import org.apache.jackrabbit.vault.validation.spi.ValidatorFactory;
import org.apache.jackrabbit.vault.validation.spi.ValidatorSettings;
import org.jetbrains.annotations.NotNull;
import org.kohsuke.MetaInfServices;

@MetaInfServices
public class DocumentViewParserValidatorFactory implements ValidatorFactory {

    public static final String ID = ValidatorFactory.ID_PREFIX_JACKRABBIT + "docviewparser";

    public static final String OPTION_ALLOW_UNDECLARED_PREFIX_IN_FILE_NAME = "allowUndeclaredPrefixInFileName";

    public static final String OPTION_SEVERITY_FOR_UNUSED_CHARACTER_DATA = "severityForUnusedCharacterData";
    static final @NotNull ValidationMessageSeverity DEFAULT_SEVERITY_FOR_UNUSED_CHARACTER_DATA = ValidationMessageSeverity.ERROR;
    

    @Override
    public Validator createValidator(@NotNull ValidationContext context, @NotNull ValidatorSettings settings) {
        final boolean allowUndeclaredPrefixInFileName;
        if (settings.getOptions().containsKey(OPTION_ALLOW_UNDECLARED_PREFIX_IN_FILE_NAME)) {
            allowUndeclaredPrefixInFileName = Boolean.valueOf(settings.getOptions().get(OPTION_ALLOW_UNDECLARED_PREFIX_IN_FILE_NAME));
        } else {
            allowUndeclaredPrefixInFileName = true;
        }
        final @NotNull ValidationMessageSeverity severityForUnusedCharacterData;
        if (settings.getOptions().containsKey(OPTION_SEVERITY_FOR_UNUSED_CHARACTER_DATA)) {
            String optionValue = settings.getOptions().get(OPTION_SEVERITY_FOR_UNUSED_CHARACTER_DATA);
            severityForUnusedCharacterData = ValidationMessageSeverity.valueOf(optionValue.toUpperCase(Locale.ROOT));
        } else {
            severityForUnusedCharacterData = DEFAULT_SEVERITY_FOR_UNUSED_CHARACTER_DATA;
        }
        return new DocumentViewParserValidator(settings.getDefaultSeverity(), allowUndeclaredPrefixInFileName, severityForUnusedCharacterData);
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
        return Integer.MAX_VALUE;
    }

}
