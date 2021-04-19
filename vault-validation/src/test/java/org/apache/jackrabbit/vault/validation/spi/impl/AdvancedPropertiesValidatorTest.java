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

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.InvalidPropertiesFormatException;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.packaging.PackageProperties;
import org.apache.jackrabbit.vault.packaging.impl.DefaultPackageProperties;
import org.apache.jackrabbit.vault.validation.ValidationExecutorTest;
import org.apache.jackrabbit.vault.validation.ValidationViolation;
import org.apache.jackrabbit.vault.validation.spi.PropertiesValidator;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessage;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessageSeverity;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.xml.sax.SAXException;

@RunWith(MockitoJUnitRunner.class)
public class AdvancedPropertiesValidatorTest {

    private AdvancedPropertiesValidator validator;

    @Mock
    private PropertiesValidator propertiesValidator1;

    @Mock
    private PropertiesValidator propertiesValidator2;


    @Before
    public void setUp() {
        validator = new AdvancedPropertiesValidator(ValidationMessageSeverity.WARN);
        Map<String, PropertiesValidator> validatorsById = new HashMap<>();
        validatorsById.put("id1", propertiesValidator1);
        validatorsById.put("id2", propertiesValidator2);
        validator.setPropertiesValidators(validatorsById);
    }

    @Test
    public void testProperties()
            throws URISyntaxException, IOException, SAXException, ParserConfigurationException, ConfigurationException {
        Mockito.when(propertiesValidator1.validate(Mockito.any())).thenReturn(Collections.singleton(new ValidationMessage(ValidationMessageSeverity.ERROR, "error1")));
        Mockito.when(propertiesValidator2.validate(Mockito.any())).thenReturn(null);
        PackageProperties properties;
        try (InputStream input = this.getClass().getResourceAsStream("/simple-package/META-INF/vault/properties.xml");
                InputStream input2 = this.getClass().getResourceAsStream("/simple-package/META-INF/vault/properties.xml")) {
            Collection<ValidationMessage> messages = validator.validateMetaInfData(input, Paths.get("vault/properties.xml"));
            ValidationExecutorTest.assertViolation(messages,
                    new ValidationViolation("id1", ValidationMessageSeverity.ERROR, "error1"));
            properties = DefaultPackageProperties.fromInputStream(input2);
            Mockito.verify(propertiesValidator1).validate(properties);
        }
    }

    @Test
    public void testPropertiesWithInvalidElement()
            throws URISyntaxException, IOException, SAXException, ParserConfigurationException, ConfigurationException {
        try (InputStream input = this.getClass().getResourceAsStream("/invalid-package/META-INF/vault/properties.xml")) {
            Collection<ValidationMessage> messages = validator.validateMetaInfData(input, Paths.get("vault/properties.xml"));
            ValidationExecutorTest.assertViolation(messages,
                    new ValidationMessage(ValidationMessageSeverity.WARN,
                                   AdvancedPropertiesValidator.MESSAGE_INVALID_PROPERTIES_XML,
                            new InvalidPropertiesFormatException("org.xml.sax.SAXParseException; lineNumber: 35; columnNumber: 19; Element type \"someinvalidentry\" must be declared.")));
        }
    }

}
