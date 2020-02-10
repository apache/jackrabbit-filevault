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
package org.apache.jackrabbit.vault.validation.impl.util;

import java.util.Collections;
import java.util.Map;

import org.apache.jackrabbit.vault.validation.spi.ValidationMessageSeverity;
import org.apache.jackrabbit.vault.validation.spi.ValidatorSettings;
import org.jetbrains.annotations.NotNull;

public class ValidatorSettingsImpl implements ValidatorSettings {
    private final boolean isDisabled;
    private final ValidationMessageSeverity defaultSeverity;
    private final Map<String, String> options;

    public ValidatorSettingsImpl(boolean isDisabled) {
        this(isDisabled, null, null);
    }

    public ValidatorSettingsImpl() {
        this(false, null, null);
    }

    public ValidatorSettingsImpl(ValidationMessageSeverity defaultSeverity) {
        this(false, defaultSeverity, null);
    }

    public ValidatorSettingsImpl(String optionKey, String optionValue) {
        this(false, null, Collections.singletonMap(optionKey, optionValue));
    }

    public ValidatorSettingsImpl(boolean isDisabled, ValidationMessageSeverity defaultSeverity, Map<String, String> options) {
        super();
        this.isDisabled = isDisabled;
        this.defaultSeverity = defaultSeverity;
        this.options = options;
    }

    @Override
    public @NotNull ValidationMessageSeverity getDefaultSeverity() {
        if (defaultSeverity == null) {
            return ValidationMessageSeverity.ERROR;
        }
        return defaultSeverity;
    }

    @Override
    public @NotNull Map<String, String> getOptions() {
        if (options == null) {
            return Collections.emptyMap();
        }
        return options;
    }

    @Override
    public boolean isDisabled() {
        return isDisabled;
    }
}
