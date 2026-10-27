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

import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.PackageProperties;
import org.apache.jackrabbit.vault.validation.AnyValidationViolationMessageMatcher;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessage;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessageSeverity;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PackageIdValidatorTest {

    private PackageIdValidator validator;

    @Mock
    private PackageProperties properties;

    @Before
    public void setUp() {
        validator = new PackageIdValidator(ValidationMessageSeverity.ERROR);
    }

    @Test
    public void testValidateWithValidPackageId() {
        Mockito.when(properties.getId()).thenReturn(PackageId.fromString("group:name:1.0.0"));

        MatcherAssert.assertThat(
                validator.validate(properties),
                AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());
    }

    @Test
    public void testValidateWithMissingPackageId() {
        Mockito.when(properties.getId()).thenReturn(null);

        MatcherAssert.assertThat(
                validator.validate(properties),
                Matchers.contains(new ValidationMessage(
                        ValidationMessageSeverity.ERROR, PackageIdValidator.MESSAGE_INVALID_OR_MISSING_PACKAGE_ID)));
    }

    @Test
    public void testValidateWithInvalidPackageId() {
        Mockito.when(properties.getId()).thenReturn(new PackageId("group", "invalid/name", "1.0.0"));

        MatcherAssert.assertThat(
                validator.validate(properties),
                Matchers.contains(new ValidationMessage(
                        ValidationMessageSeverity.ERROR, PackageIdValidator.MESSAGE_INVALID_OR_MISSING_PACKAGE_ID)));
    }
}
