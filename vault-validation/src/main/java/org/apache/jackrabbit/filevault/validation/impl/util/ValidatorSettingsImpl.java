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
package org.apache.jackrabbit.filevault.validation.impl.util;

import java.util.Collections;
import java.util.Map;

import org.apache.jackrabbit.filevault.validation.spi.ValidationMessageSeverity;
import org.apache.jackrabbit.filevault.validation.spi.ValidatorSettings;

public class ValidatorSettingsImpl implements ValidatorSettings {
    private boolean isDisabled;
    private ValidationMessageSeverity defaultSeverity;
    private Map<String, String> options;

    public ValidatorSettingsImpl(boolean isDisabled) {
        super();
        this.isDisabled = isDisabled;
    }

    public ValidatorSettingsImpl() {
        
    }

    public ValidatorSettingsImpl(ValidationMessageSeverity defaultSeverity) {
        this.defaultSeverity = defaultSeverity;
    }

    public ValidatorSettingsImpl(String optionKey, String optionValue) {
        options = Collections.singletonMap(optionKey, optionValue);
    }

    @Override
    public ValidationMessageSeverity getDefaultSeverity() {
        if (defaultSeverity == null) {
            return ValidationMessageSeverity.ERROR;
        }
        return defaultSeverity;
    }

    @Override
    public Map<String, String> getOptions() {
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
