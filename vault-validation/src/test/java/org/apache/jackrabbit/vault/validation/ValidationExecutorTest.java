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
package org.apache.jackrabbit.vault.validation;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.validation.spi.DocumentViewXmlValidator;
import org.apache.jackrabbit.vault.validation.spi.GenericJcrDataValidator;
import org.apache.jackrabbit.vault.validation.spi.GenericMetaInfDataValidator;
import org.apache.jackrabbit.vault.validation.spi.JcrPathValidator;
import org.apache.jackrabbit.vault.validation.spi.MetaInfPathValidator;
import org.apache.jackrabbit.vault.validation.spi.NodeContext;
import org.apache.jackrabbit.vault.validation.spi.NodePathValidator;
import org.apache.jackrabbit.vault.validation.spi.PropertiesValidator;
import org.apache.jackrabbit.vault.validation.spi.ValidationContext;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessage;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessageSeverity;
import org.apache.jackrabbit.vault.validation.spi.Validator;
import org.apache.jackrabbit.vault.validation.spi.util.NodeContextImpl;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.xml.sax.SAXException;

@RunWith(MockitoJUnitRunner.class)
public class ValidationExecutorTest {

    @Mock
    private DocumentViewXmlValidator docViewXmlValidator;
    @Mock
    private PropertiesValidator propertiesValidator;
    @Mock
    private NodePathValidator nodePathValidator;
    @Mock
    private GenericJcrDataValidator genericJcrDataValidator;
    @Mock
    private GenericJcrDataValidator genericJcrDataValidator2;
    @Mock
    private GenericMetaInfDataValidator genericMetaInfDataValidator;
    @Mock
    private GenericMetaInfDataValidator genericMetaInfDataValidator2;
    @Mock
    private JcrPathValidator jcrPathValidator;
    @Mock
    private MetaInfPathValidator metaInfPathValidator;
    @Mock
    private Validator unusedValidator;
    @Mock
    private ValidationContext context;
    
    private ValidationExecutor executor;

    @Before
    public void setUp() throws ParserConfigurationException, SAXException {
        Map<String, Validator> validators = new LinkedHashMap<>();
        validators.put("docviewid", docViewXmlValidator);
        validators.put("propertiesid", propertiesValidator);
        validators.put("genericmetadataid", genericMetaInfDataValidator);
        validators.put("genericmetadataid2", genericMetaInfDataValidator2);
        validators.put("genericjcrdataid", genericJcrDataValidator);
        validators.put("genericjcrdataid2", genericJcrDataValidator2);
        validators.put("jcrpathid", jcrPathValidator);
        validators.put("metainfpathid", metaInfPathValidator);
        validators.put("nodepathid", nodePathValidator);
        validators.put("unusedid", unusedValidator);
        executor = new ValidationExecutor(validators);
    }

    @Test
    public void testUnusedValidators() throws ParserConfigurationException, SAXException {
        MatcherAssert.assertThat(executor.getUnusedValidatorsById(), Matchers.hasEntry("unusedid", unusedValidator));
        MatcherAssert.assertThat(executor.getUnusedValidatorsById(), Matchers.aMapWithSize(1));
    }

    @Test
    public void testValidateNodePath() throws ParserConfigurationException, SAXException, IOException, URISyntaxException {
        Mockito.when(nodePathValidator.validate(Mockito.argThat(new NodeContextNodePathMatcher("/apps/invalid/wrongtype.xml")))).thenReturn(Collections.singletonList(new ValidationMessage(ValidationMessageSeverity.ERROR, "Invalid node path")));
        try (InputStream input = this.getClass().getResourceAsStream("/simple-package/jcr_root/apps/invalid/wrongtype.xml")) {
            Collection<ValidationViolation> messages = validate(input, executor, Paths.get(""), "apps/invalid/wrongtype.xml", false);

            ValidationExecutorTest.assertViolation(messages,
                    new ValidationViolation("nodepathid", ValidationMessageSeverity.ERROR, "Invalid node path", Paths.get("apps/invalid/wrongtype.xml"), Paths.get(""), "/apps/invalid/wrongtype.xml", 0, 0, null));
        }
    }

    @Test
    public void testGenericMetaInfData()
            throws URISyntaxException, IOException, SAXException, ParserConfigurationException, ConfigurationException {
        Mockito.when(genericMetaInfDataValidator.shouldValidateMetaInfData(Mockito.any(), Mockito.any())).thenReturn(true);
        Mockito.when(genericMetaInfDataValidator2.shouldValidateMetaInfData(Mockito.any(), Mockito.any())).thenReturn(true);
        CapturingInputStreamFromArgumentAnswer<Void> answer = new CapturingInputStreamFromArgumentAnswer<>(StandardCharsets.US_ASCII, 0, null);
        Mockito.when(genericMetaInfDataValidator.validateMetaInfData(Mockito.any(), Mockito.any(), Mockito.any())).thenAnswer(answer);
        CapturingInputStreamFromArgumentAnswer<Collection<ValidationMessage>> answer2 = new CapturingInputStreamFromArgumentAnswer<>(StandardCharsets.US_ASCII, 0, Collections.singleton(new ValidationMessage(ValidationMessageSeverity.WARN, "error1")));
        Mockito.when(genericMetaInfDataValidator2.validateMetaInfData(Mockito.any(), Mockito.any(), Mockito.any())).thenAnswer(answer2);
        Mockito.when(metaInfPathValidator.validateMetaInfPath(Mockito.any(), Mockito.any(), Mockito.anyBoolean())).thenReturn(Collections.singleton(new ValidationMessage(ValidationMessageSeverity.ERROR, "patherror")));
        try (InputStream input = this.getClass().getResourceAsStream("/simple-package/META-INF/vault/genericfile.txt")) {
            Collection<ValidationViolation> messages = validate(input, executor, Paths.get(""), "vault/genericfile.txt", true);
            assertViolation(messages, 
                    new ValidationViolation("metainfpathid", ValidationMessageSeverity.ERROR, "patherror", Paths.get("vault","genericfile.txt"), Paths.get(""), null, 0, 0, null),
                    new ValidationViolation("genericmetadataid2", ValidationMessageSeverity.WARN, "error1", Paths.get("vault","genericfile.txt"), Paths.get(""), null, 0, 0, null));
            Assert.assertEquals("Test", answer.getValue());
            Assert.assertEquals("Test", answer2.getValue());
            Path expectedPath = Paths.get("vault/genericfile.txt");
            Mockito.verify(metaInfPathValidator).validateMetaInfPath(expectedPath, Paths.get(""), false);
            Mockito.verify(genericMetaInfDataValidator, Mockito.atLeastOnce()).shouldValidateMetaInfData(expectedPath, Paths.get(""));
            Mockito.verify(genericMetaInfDataValidator).validateMetaInfData(Mockito.any(), Mockito.eq(expectedPath), Mockito.eq(Paths.get("")));
            Mockito.verify(genericMetaInfDataValidator2, Mockito.atLeastOnce()).shouldValidateMetaInfData(expectedPath, Paths.get(""));
            Mockito.verify(genericMetaInfDataValidator2).validateMetaInfData(Mockito.any(), Mockito.eq(expectedPath), Mockito.eq(Paths.get("")));
        }
    }

    @Test
    public void testGenericMetaInfDataWithNotInterestedValidator()
            throws URISyntaxException, IOException, SAXException, ParserConfigurationException, ConfigurationException {
        try (InputStream input = this.getClass().getResourceAsStream("/simple-package/META-INF/vault/genericfile.txt")) {
            Collection<ValidationViolation> messages = validate(input, executor, Paths.get(""), "vault/genericfile.txt", true);
            MatcherAssert.assertThat(messages, AnyValidationViolationMatcher.noValidationInCollection());
            Mockito.verify(genericMetaInfDataValidator, Mockito.never()).validateMetaInfData(Mockito.any(), Mockito.any());
        }
    }

    @Test
    public void testMetaInfFolder() throws URISyntaxException, IOException, SAXException {
        Collection<ValidationViolation> messages = validateFolder(executor, Paths.get(""), "vault/genericfile.txt", true);
        MatcherAssert.assertThat(messages, AnyValidationViolationMatcher.noValidationInCollection());
        Mockito.verify(metaInfPathValidator).validateMetaInfPath(Paths.get("vault", "genericfile.txt"), Paths.get(""), true);
    }

    @Test
    public void testGenericJcrData()
            throws URISyntaxException, IOException, SAXException, ParserConfigurationException, ConfigurationException {
        Mockito.when(genericJcrDataValidator.shouldValidateJcrData(Mockito.any(), Mockito.any())).thenReturn(true);
        CapturingInputStreamFromArgumentAnswer<Collection<ValidationMessage>> answer = new CapturingInputStreamFromArgumentAnswer<>(StandardCharsets.US_ASCII, 0, Collections.singleton(new ValidationMessage(ValidationMessageSeverity.WARN, "error1")));
        Mockito.when(genericJcrDataValidator.validateJcrData(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any())).thenAnswer(answer);
        CapturingInputStreamFromArgumentAnswer<Void> answer2 = new CapturingInputStreamFromArgumentAnswer<>(StandardCharsets.US_ASCII, 0, null);
        Mockito.when(genericJcrDataValidator2.shouldValidateJcrData(Mockito.any(), Mockito.any())).thenReturn(true);
        Mockito.when(genericJcrDataValidator2.validateJcrData(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any())).thenAnswer(answer2);
        Mockito.when(jcrPathValidator.validateJcrPath(Mockito.any(), Mockito.anyBoolean(), Mockito.anyBoolean())).thenReturn(Collections.singleton(new ValidationMessage(ValidationMessageSeverity.ERROR, "patherror")));
        try (InputStream input = this.getClass().getResourceAsStream("/simple-package/jcr_root/apps/genericfile.xml")) {
            Collection<ValidationViolation> messages = validate(input, executor, Paths.get(""), "apps/genericfile.xml", false);
            assertViolation(messages, 
                    new ValidationViolation("genericjcrdataid", ValidationMessageSeverity.WARN, "error1", Paths.get("apps","genericfile.xml"), Paths.get(""), null, 0, 0, null),
                    new ValidationViolation("jcrpathid", ValidationMessageSeverity.ERROR, "patherror", Paths.get("apps","genericfile.xml"), Paths.get(""), null, 0, 0, null));
            Assert.assertEquals("Test", answer.getValue());
            Assert.assertEquals("Test", answer2.getValue());
            Path expectedPath = Paths.get("apps/genericfile.xml");
            NodeContext expectedNodeContext = new NodeContextImpl("/apps/genericfile.xml", expectedPath,  Paths.get(""));
            Mockito.verify(jcrPathValidator).validateJcrPath(expectedNodeContext, false, false);
            Mockito.verify(genericJcrDataValidator, Mockito.atLeastOnce()).shouldValidateJcrData(expectedPath, Paths.get(""));
            Mockito.verify(genericJcrDataValidator).validateJcrData(Mockito.any(), Mockito.eq(expectedPath), Mockito.eq(Paths.get("")), Mockito.any());
            Mockito.verify(genericJcrDataValidator2, Mockito.atLeastOnce()).shouldValidateJcrData(expectedPath, Paths.get(""));
            Mockito.verify(genericJcrDataValidator2).validateJcrData(Mockito.any(), Mockito.eq(expectedPath), Mockito.eq(Paths.get("")), Mockito.any());
        }
    }

    @Test
    public void testGenericJcrDataWithNotInterestedValidator()
            throws URISyntaxException, IOException, SAXException, ParserConfigurationException, ConfigurationException {
        try (InputStream input = this.getClass().getResourceAsStream("/simple-package/jcr_root/apps/genericfile.xml")) {
            Collection<ValidationViolation> messages = validate(input, executor, Paths.get(""), "apps/genericfile.xml", false);
            MatcherAssert.assertThat(messages, AnyValidationViolationMatcher.noValidationInCollection());
            Mockito.verify(genericJcrDataValidator, Mockito.never()).validateJcrData(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        }
    }

    @Test
    public void testJcrRootFolder() throws URISyntaxException, IOException, SAXException {
        Collection<ValidationViolation> messages = validateFolder(executor, Paths.get(""), "apps.dir", false);
        MatcherAssert.assertThat(messages, AnyValidationViolationMatcher.noValidationInCollection());
        NodeContext expectedNodeContext = new NodeContextImpl("/apps", Paths.get("apps.dir"), Paths.get(""));
        Mockito.verify(jcrPathValidator).validateJcrPath(expectedNodeContext, true, true);
        Mockito.verify(nodePathValidator).validate(expectedNodeContext);
    }

    @Test
    public void testDone() {
        Mockito.when(genericJcrDataValidator.done()).thenReturn(Collections.singleton(new ValidationMessage(ValidationMessageSeverity.ERROR, "test1")));
        Mockito.when(genericJcrDataValidator2.done()).thenReturn(Collections.singleton(new ValidationMessage(ValidationMessageSeverity.WARN, "test2")));
        
        assertViolation(executor.done(), new ValidationViolation("genericjcrdataid", ValidationMessageSeverity.ERROR, "test1"), new ValidationViolation("genericjcrdataid2", ValidationMessageSeverity.WARN, "test2")); 
    }

    private Collection<ValidationViolation> validate(InputStream input, ValidationExecutor executor, Path basePath, String resourcePath,
            boolean isMetaInf) throws URISyntaxException, IOException, SAXException {
        if (input == null) {
            throw new IllegalArgumentException("Invalid input stream given");
        }
        final Collection<ValidationViolation> messages;
        if (isMetaInf) {
            messages = executor.validateMetaInf(input, Paths.get(resourcePath), basePath);
        } else {
            messages = executor.validateJcrRoot(input, Paths.get(resourcePath), basePath);
        }
        return messages;
    }
    
    private Collection<ValidationViolation> validateFolder(ValidationExecutor executor, Path basePath, String resourcePath,
            boolean isMetaInf) throws URISyntaxException, IOException, SAXException {
        final Collection<ValidationViolation> messages;
        if (isMetaInf) {
            messages = executor.validateMetaInf(null, Paths.get(resourcePath), basePath);
        } else {
            messages = executor.validateJcrRoot(null, Paths.get(resourcePath), basePath);
        }
        return messages;
    }

    @Test
    public void testFilePathToNodePath() {
        Assert.assertEquals("/apps/test", ValidationExecutor.filePathToNodePath(Paths.get("apps", "test")));
        Assert.assertEquals("/some/other/path", ValidationExecutor.filePathToNodePath(Paths.get("some", "other", "path")));
        Assert.assertEquals("/apps/test", ValidationExecutor.filePathToNodePath(Paths.get("apps", "test", "property.binary")));
        Assert.assertEquals("/", ValidationExecutor.filePathToNodePath(Paths.get("")));
    }

    public static void assertViolation(Collection<? extends ValidationMessage> messages, ValidationMessageSeverity thresholdSeverity, ValidationMessage... violations) {
        if (messages == null) {
            Assert.fail("No violations found at all!");
        } else {
            List<ValidationMessage> filteredMessages = messages.stream()
                    .filter(m -> m.getSeverity().ordinal() >= thresholdSeverity.ordinal()).collect(Collectors.toList());
            MatcherAssert.assertThat(filteredMessages, Matchers.contains(violations));
        }
    }

    public static void assertViolation(Collection<? extends ValidationMessage> messages, ValidationMessage... violations) {
        ValidationExecutorTest.assertViolation(messages, ValidationMessageSeverity.WARN, violations);
    }
}
