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

import java.util.Collection;
import java.util.Collections;

import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.PackageProperties;
import org.apache.jackrabbit.vault.validation.spi.PropertiesValidator;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessage;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessageSeverity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PackageIdValidator implements PropertiesValidator {

    private static final String PACKAGE_ID_DOC_URL =
            "https://jackrabbit.apache.org/filevault/properties.html#Package_ID";
    protected static final String MESSAGE_INVALID_OR_MISSING_PACKAGE_ID =
            "Package ID is missing or invalid. Make sure 'group', 'name' and optional 'version' in the 'properties.xml' are set to valid values as defined in "
                    + PACKAGE_ID_DOC_URL + ".";

    private final ValidationMessageSeverity severity;

    public PackageIdValidator(@NotNull ValidationMessageSeverity severity) {
        this.severity = severity;
    }

    @Override
    public @Nullable Collection<ValidationMessage> validate(@NotNull PackageProperties properties) {
        PackageId id = properties.getId();
        if (id == null || !id.isValid()) {
            // TODO: improve error message to validate each part of a package id individually and emit valid format for
            // each part
            return Collections.singleton(new ValidationMessage(severity, MESSAGE_INVALID_OR_MISSING_PACKAGE_ID));
        }
        return null;
    }

    @Override
    public Collection<ValidationMessage> done() {
        return null;
    }
}
