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

import javax.jcr.PropertyType;
import javax.xml.parsers.ParserConfigurationException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.AbstractMap.SimpleEntry;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;

import org.apache.jackrabbit.spi.NameFactory;
import org.apache.jackrabbit.spi.commons.name.NameConstants;
import org.apache.jackrabbit.spi.commons.name.NameFactoryImpl;
import org.apache.jackrabbit.util.ISO8601;
import org.apache.jackrabbit.vault.util.DocViewNode2;
import org.apache.jackrabbit.vault.util.DocViewProperty2;
import org.apache.jackrabbit.vault.util.UncheckedRepositoryException;
import org.apache.jackrabbit.vault.validation.ValidationExecutorTest;
import org.apache.jackrabbit.vault.validation.spi.OsgiConfigurationValidator;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessage;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessageSeverity;
import org.apache.jackrabbit.vault.validation.spi.impl.OsgiConfigurationParserValidator.OsgiConfigurationSerializationFormat;
import org.apache.jackrabbit.vault.validation.spi.util.NodeContextImpl;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.xml.sax.SAXException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

@RunWith(MockitoJUnitRunner.class)
public class OsgiConfigurationParserValidatorTest {

    private OsgiConfigurationParserValidator validator;

    @Mock
    private OsgiConfigurationValidator mockValidator;

    @Before
    public void setUp() throws ParserConfigurationException, SAXException {
        validator = new OsgiConfigurationParserValidator();
        Map<String, OsgiConfigurationValidator> validators = new HashMap<>();
        validators.put("mock", mockValidator);
        Mockito.when(mockValidator.validateConfig(
                        Mockito.anyMap(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
                .thenReturn(
                        Collections.singleton(new ValidationMessage(ValidationMessageSeverity.ERROR, "mock message")));
        validator.setOsgiConfigurationValidators(validators);
    }

    @Test
    public void testExtractPidAndSubnameFromName() {
        assertEquals(
                new SimpleEntry<String, String>("my.factory.pid", "subname1"),
                OsgiConfigurationParserValidator.extractPidAndSubnameFromName(
                        "my.factory.pid-subname1.cfg.json", OsgiConfigurationSerializationFormat.CFG_JSON));
        assertEquals(
                new SimpleEntry<String, String>("my.factory.pid", "subname1"),
                OsgiConfigurationParserValidator.extractPidAndSubnameFromName(
                        "my.factory.pid~subname1.config", OsgiConfigurationSerializationFormat.CONFIG));
        assertEquals(
                new SimpleEntry<String, String>("my.factory.pid", null),
                OsgiConfigurationParserValidator.extractPidAndSubnameFromName(
                        "my.factory.pid.cfg", OsgiConfigurationSerializationFormat.CFG));
        assertEquals(
                new SimpleEntry<String, String>("my.factory.pid", "subname1"),
                OsgiConfigurationParserValidator.extractPidAndSubnameFromName(
                        "my.factory.pid~subname1", OsgiConfigurationSerializationFormat.NT_OSGI_CONFIG));
    }

    @Test
    public void testConvertValue() {
        assertEquals(Optional.of("test"), OsgiConfigurationParserValidator.convertValue("test", PropertyType.STRING));
        assertEquals(
                Optional.of(Boolean.TRUE), OsgiConfigurationParserValidator.convertValue("true", PropertyType.BOOLEAN));
        assertEquals(
                Optional.of(ISO8601.parse("2023-01-01T06:01:22.000Z")),
                OsgiConfigurationParserValidator.convertValue("2023-01-01T06:01:22.000Z", PropertyType.DATE));
        assertEquals(
                Optional.of(Double.valueOf(1.0)),
                OsgiConfigurationParserValidator.convertValue("1.0", PropertyType.DOUBLE));
        assertEquals(
                Optional.of(Long.valueOf(1l)), OsgiConfigurationParserValidator.convertValue("1", PropertyType.LONG));
        // unsupported type
        assertEquals(Optional.empty(), OsgiConfigurationParserValidator.convertValue("name", PropertyType.NAME));
        // unconvertible type
        assertThrows(
                UncheckedRepositoryException.class,
                () -> OsgiConfigurationParserValidator.convertValue("invalid-date", PropertyType.DATE));
    }

    @Test
    public void testValidateOsgiConfig() {
        NameFactory nameFactory = NameFactoryImpl.getInstance();
        DocViewNode2 node = new DocViewNode2(
                nameFactory.create("{}myPid~subname"),
                Arrays.asList(
                        new DocViewProperty2(NameConstants.JCR_PRIMARYTYPE, "sling:OsgiConfig"),
                        new DocViewProperty2(
                                nameFactory.create("{}prop1"), Arrays.asList("value1", "value2"), PropertyType.STRING),
                        new DocViewProperty2(nameFactory.create("{}prop2"), "100", PropertyType.LONG)));
        Collection<ValidationMessage> messages = validator.validate(
                node,
                new NodeContextImpl(
                        "/apps/config/myPid~subname", Paths.get("apps", "config", ".content.xml"), Paths.get("")),
                true);
        ValidationExecutorTest.assertViolation(
                messages, new ValidationMessage(ValidationMessageSeverity.ERROR, "mock message"));
        Map<String, Object> configuration = new HashMap<>();
        configuration.put("prop1", new String[] {"value1", "value2"});
        configuration.put("prop2", Long.valueOf(100l));
        Mockito.verify(mockValidator)
                .validateConfig(
                        Mockito.argThat(new DeepMapMatcher(configuration)),
                        Mockito.eq("myPid"),
                        Mockito.eq("subname"),
                        Mockito.eq("/apps/config/myPid~subname"));
    }

    @Test
    public void testValidateCfgJson() throws IOException {
        Path basePath = Paths.get("");
        Path filePath = Paths.get("apps", "config", "myPid~subname.cfg.json");
        Assert.assertTrue(validator.shouldValidateJcrData(filePath, basePath));
        try (InputStream input = this.getClass().getResourceAsStream("/osgi-configuration/myPid~subname.cfg.json")) {
            Collection<ValidationMessage> messages =
                    validator.validateJcrData(input, filePath, basePath, new HashMap<String, Integer>());
            ValidationExecutorTest.assertViolation(
                    messages, new ValidationMessage(ValidationMessageSeverity.ERROR, "mock message"));
            Map<String, Object> configuration = new HashMap<>();
            configuration.put("prop1", new String[] {"value1", "value2"});
            configuration.put("prop2", Long.valueOf(100l));
            Mockito.verify(mockValidator)
                    .validateConfig(
                            Mockito.argThat(new DeepMapMatcher(configuration)),
                            Mockito.eq("myPid"),
                            Mockito.eq("subname"),
                            Mockito.eq("/apps/config/myPid~subname.cfg.json"));
        }
    }

    @Test
    public void testValidateCfg() throws IOException {
        Path basePath = Paths.get("");
        Path filePath = Paths.get("apps", "config", "myPid~subname.cfg");
        Assert.assertTrue(validator.shouldValidateJcrData(filePath, basePath));
        try (InputStream input = this.getClass().getResourceAsStream("/osgi-configuration/myPid~subname.cfg")) {
            Collection<ValidationMessage> messages =
                    validator.validateJcrData(input, filePath, basePath, new HashMap<String, Integer>());
            ValidationExecutorTest.assertViolation(
                    messages, new ValidationMessage(ValidationMessageSeverity.ERROR, "mock message"));
            Map<String, Object> configuration = new HashMap<>();
            configuration.put("prop1", "value1");
            configuration.put("prop2", "value2");
            Mockito.verify(mockValidator)
                    .validateConfig(
                            Mockito.argThat(new DeepMapMatcher(configuration)),
                            Mockito.eq("myPid"),
                            Mockito.eq("subname"),
                            Mockito.eq("/apps/config/myPid~subname.cfg"));
        }
    }

    @Test
    public void testValidateConfig() throws IOException {
        Path basePath = Paths.get("");
        Path filePath = Paths.get("apps", "config", "myPid~subname.config");
        Assert.assertTrue(validator.shouldValidateJcrData(filePath, basePath));
        try (InputStream input = this.getClass().getResourceAsStream("/osgi-configuration/myPid~subname.config")) {
            Collection<ValidationMessage> messages =
                    validator.validateJcrData(input, filePath, basePath, new HashMap<String, Integer>());
            ValidationExecutorTest.assertViolation(
                    messages, new ValidationMessage(ValidationMessageSeverity.ERROR, "mock message"));
            Map<String, Object> configuration = new HashMap<>();
            configuration.put("prop1", new String[] {"value1", "value2"});
            configuration.put("prop2", Long.valueOf(100l));
            Mockito.verify(mockValidator)
                    .validateConfig(
                            Mockito.argThat(new DeepMapMatcher(configuration)),
                            Mockito.eq("myPid"),
                            Mockito.eq("subname"),
                            Mockito.eq("/apps/config/myPid~subname.config"));
        }
    }

    private class DeepMapMatcher implements ArgumentMatcher<Map<String, Object>> {
        private Map<String, Object> map;

        public DeepMapMatcher(Map<String, Object> map) {
            this.map = map;
        }

        @Override
        public boolean matches(Map<String, Object> argument) {
            // similar to AbstractMap.equals
            if (argument == map) return true;

            if (!(argument instanceof Map)) return false;
            Map<?, ?> m = (Map<?, ?>) argument;
            if (m.size() != map.size()) {
                return false;
            }
            try {
                Iterator<Entry<String, Object>> i = map.entrySet().iterator();
                while (i.hasNext()) {
                    Entry<String, Object> e = i.next();
                    String key = e.getKey();
                    Object value = e.getValue();
                    Objects.deepEquals(value, m.get(key));
                }
            } catch (ClassCastException unused) {
                return false;
            } catch (NullPointerException unused) {
                return false;
            }

            return true;
        }
    }
}
