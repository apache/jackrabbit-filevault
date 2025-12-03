/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.jackrabbit.vault.validation.spi.impl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.InvalidPropertiesFormatException;
import java.util.LinkedList;
import java.util.Map;

import org.apache.jackrabbit.vault.packaging.PackageProperties;
import org.apache.jackrabbit.vault.packaging.impl.DefaultPackageProperties;
import org.apache.jackrabbit.vault.util.Constants;
import org.apache.jackrabbit.vault.validation.ValidationViolation;
import org.apache.jackrabbit.vault.validation.spi.GenericMetaInfDataValidator;
import org.apache.jackrabbit.vault.validation.spi.PropertiesValidator;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessage;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessageSeverity;
import org.jetbrains.annotations.NotNull;

public final class AdvancedPropertiesValidator implements GenericMetaInfDataValidator {

    protected static final String MESSAGE_INVALID_PROPERTIES_XML = "Invalid properties.xml";

    static final Path PROPERTIES_XML_PATH = Paths.get(Constants.VAULT_DIR).resolve(Constants.PROPERTIES_XML);

    private final Map<String, PropertiesValidator> propertiesValidators;
    private final ValidationMessageSeverity severity;

    public AdvancedPropertiesValidator(ValidationMessageSeverity severity) {
        this.propertiesValidators = new HashMap<>();
        this.severity = severity;
    }

    public void setPropertiesValidators(Map<String, PropertiesValidator> propertiesValidators) {
        this.propertiesValidators.putAll(propertiesValidators);
    }

    @Override
    public Collection<ValidationMessage> done() {
        return null;
    }

    @Override
    public Collection<ValidationMessage> validateMetaInfData(@NotNull InputStream input, @NotNull Path filePath) {
        Collection<ValidationMessage> messages = new LinkedList<>();
        try {
            PackageProperties properties = DefaultPackageProperties.fromInputStream(input);
            // call all registered properties validators
            for (Map.Entry<String, PropertiesValidator> entry : propertiesValidators.entrySet()) {
                messages.add(new ValidationMessage(
                        ValidationMessageSeverity.DEBUG, "Validating with validator " + entry.getKey() + "..."));
                Collection<ValidationMessage> propertiesValidatorMessages =
                        entry.getValue().validate(properties);
                if (propertiesValidatorMessages != null) {
                    messages.addAll(ValidationViolation.wrapMessages(
                            entry.getKey(), propertiesValidatorMessages, null, null, null, 0, 0));
                }
            }
        } catch (InvalidPropertiesFormatException e) {
            messages.add(new ValidationMessage(severity, MESSAGE_INVALID_PROPERTIES_XML, e));
        } catch (IOException e) {
            throw new IllegalStateException("Could not read from input stream " + filePath, e);
        }
        return messages;
    }

    @Override
    public boolean shouldValidateMetaInfData(@NotNull Path filePath) {
        return PROPERTIES_XML_PATH.equals(filePath);
    }
}
