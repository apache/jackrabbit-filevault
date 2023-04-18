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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.jcr.NamespaceException;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.spi.NameFactory;
import org.apache.jackrabbit.spi.commons.name.NameConstants;
import org.apache.jackrabbit.spi.commons.name.NameFactoryImpl;
import org.apache.jackrabbit.vault.fs.io.DocViewParser;
import org.apache.jackrabbit.vault.fs.io.DocViewParser.XmlParseException;
import org.apache.jackrabbit.vault.util.DocViewNode2;
import org.apache.jackrabbit.vault.util.DocViewProperty2;
import org.apache.jackrabbit.vault.util.JcrConstants;
import org.apache.jackrabbit.vault.validation.impl.util.ValidatorDocViewParserHandler;
import org.apache.jackrabbit.vault.validation.spi.DocumentViewXmlValidator;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessage;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessageSeverity;
import org.apache.jackrabbit.vault.validation.spi.impl.DocumentViewParserValidator;
import org.apache.jackrabbit.vault.validation.spi.impl.DocumentViewParserValidatorFactory;
import org.apache.jackrabbit.vault.validation.spi.util.NodeContextImpl;
import org.apache.sling.api.SlingConstants;
import org.apache.sling.jcr.resource.JcrResourceConstants;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.xml.sax.SAXException;

@RunWith(MockitoJUnitRunner.class)
public class DocumentViewParserValidatorTest {

    private DocumentViewParserValidator validator;
    private Map<String, Integer> nodePathsAndLineNumbers;

    private static final Name NAME_SLING_RESOURCE_TYPE = NameFactoryImpl.getInstance().create(JcrResourceConstants.SLING_NAMESPACE_URI, SlingConstants.PROPERTY_RESOURCE_TYPE);
    private static final Name NAME_SLING_TARGET= NameFactoryImpl.getInstance().create(JcrResourceConstants.SLING_NAMESPACE_URI, "target");

    @Mock
    private DocumentViewXmlValidator docViewXmlValidator;

    @Before
    public void setUp() throws ParserConfigurationException, SAXException, IOException {
        validator = new DocumentViewParserValidator(ValidationMessageSeverity.ERROR, false);
        nodePathsAndLineNumbers = new HashMap<>();
        validator.setDocumentViewXmlValidators(Collections.singletonMap("docviewid", docViewXmlValidator));
    }

    @Test
    public void testShouldValidate() {
        Assert.assertTrue(validator.shouldValidateJcrData(Paths.get("some", "file.xml"), Paths.get("")));
        Assert.assertFalse(validator.shouldValidateJcrData(Paths.get("some", "other", "file.json"), Paths.get("")));
    }

    @Test
    public void testDocViewDotContentXml()
            throws ParserConfigurationException, SAXException, URISyntaxException, IOException, NamespaceException {
        Mockito.when(docViewXmlValidator.validate(Mockito.any(DocViewNode2.class), Mockito.any(), Mockito.anyBoolean())).thenReturn(Collections.singleton(new ValidationMessage(ValidationMessageSeverity.ERROR, "startDocView")));
        try (InputStream input = this.getClass().getResourceAsStream("/simple-package/jcr_root/apps/.content.xml")) {
            Collection<ValidationMessage> messages = validator.validateJcrData(input, Paths.get("apps", ".content.xml"), Paths.get(""), nodePathsAndLineNumbers);
            // filter
            ValidationExecutorTest.assertViolation(messages,
                    new ValidationViolation("docviewid", ValidationMessageSeverity.ERROR, "startDocView", Paths.get("apps/.content.xml"), Paths.get(""), "/apps", 19, 35, null
                            ),
                    new ValidationViolation("docviewid", ValidationMessageSeverity.ERROR,
                            "startDocView", Paths.get("apps/.content.xml"), Paths.get(""), "/apps/somepath", 21, 29, null),
                    new ValidationViolation("docviewid", ValidationMessageSeverity.ERROR,
                            "startDocView", Paths.get("apps/.content.xml"), Paths.get(""), "/apps/somepath/jc:content", 22, 54, null),
                    new ValidationViolation("docviewid", ValidationMessageSeverity.ERROR,
                            "startDocView", Paths.get("apps/.content.xml"), Paths.get(""), "/apps/0123_sample.jpg", 25, 29, null),
                    new ValidationViolation("docviewid", ValidationMessageSeverity.ERROR,
                            "startDocView", Paths.get("apps/.content.xml"), Paths.get(""), "/apps/01234_sample.jpg", 26, 55, null));

            // verify node names
            Map<String, Integer> expectedNodePathsAndLineNumber = new HashMap<>();
            expectedNodePathsAndLineNumber.put("/apps", 19);
            expectedNodePathsAndLineNumber.put("/apps/somepath", 21);
            expectedNodePathsAndLineNumber.put("/apps/somepath/jc:content", 22);
            expectedNodePathsAndLineNumber.put("/apps/01234_sample.jpg", 26);
            Assert.assertEquals(expectedNodePathsAndLineNumber, nodePathsAndLineNumbers);
            Collection<DocViewProperty2> properties = new ArrayList<>();
            properties.add(
                    new DocViewProperty2(NameConstants.JCR_PRIMARYTYPE, "sling:Folder"));
            NameFactory nameFactory = NameFactoryImpl.getInstance();
            DocViewNode2 node = new DocViewNode2(nameFactory.create("{}apps"), properties);
            Mockito.verify(docViewXmlValidator).validate(node, new NodeContextImpl("/apps", Paths.get("apps", ".content.xml"), Paths.get(""), 19, 35), true);

            properties = new ArrayList<>();
            properties.add(
                    new DocViewProperty2(NameConstants.JCR_PRIMARYTYPE, JcrConstants.NT_UNSTRUCTURED));
            properties.add(
            		new DocViewProperty2(nameFactory.create("{}attribute1"), "value1"));
            node = new DocViewNode2(nameFactory.create("{}somepath"), properties);
            Mockito.verify(docViewXmlValidator).validate(node, new NodeContextImpl("/apps/somepath", Paths.get("apps", ".content.xml"), Paths.get(""), 21, 29), false);
            
            properties = new ArrayList<>();
            properties.add(
                    new DocViewProperty2(NameConstants.JCR_PRIMARYTYPE, JcrConstants.NT_UNSTRUCTURED));
            node = new DocViewNode2(NameConstants.JCR_CONTENT, properties);
            Mockito.verify(docViewXmlValidator).validate(node, new NodeContextImpl("/apps/somepath/jc:content", Paths.get("apps", ".content.xml"), Paths.get(""), 22, 54), false);
        }
    }

    @Test
    public void testDocViewDotContentXmlOnRootLevel()
            throws ParserConfigurationException, SAXException, URISyntaxException, IOException, NamespaceException {
        Mockito.when(docViewXmlValidator.validate(Mockito.any(DocViewNode2.class), Mockito.any(), Mockito.anyBoolean())).thenReturn(Collections.singleton(new ValidationMessage(ValidationMessageSeverity.ERROR, "startDocView")));
        try (InputStream input = this.getClass().getResourceAsStream("/simple-package/jcr_root/.content.xml")) {
            Collection<ValidationMessage> messages = validator.validateJcrData(input, Paths.get(".content.xml"), Paths.get(""), nodePathsAndLineNumbers);
            // filter
            ValidationExecutorTest.assertViolation(messages,
                    new ValidationViolation("docviewid", ValidationMessageSeverity.ERROR, "startDocView", Paths.get(".content.xml"), Paths.get(""), "/", 6, 32, null
                            ));

            // verify node names
            Map<String, Integer> expectedNodePathsAndLineNumber = new HashMap<>();
            expectedNodePathsAndLineNumber.put("/", 6);
            Assert.assertEquals(expectedNodePathsAndLineNumber, nodePathsAndLineNumbers);
            Collection<DocViewProperty2> properties = new ArrayList<>();
            properties.add(new DocViewProperty2(NameConstants.JCR_PRIMARYTYPE, "rep:root"));
            properties.add(new DocViewProperty2(NameConstants.JCR_MIXINTYPES, Arrays.asList("rep:AccessControllable" ,"rep:RepoAccessControllable")));
            properties.add(new DocViewProperty2(NAME_SLING_RESOURCE_TYPE, "sling:redirect"));
            properties.add(new DocViewProperty2(NAME_SLING_TARGET, "/index.html"));
            
            DocViewNode2 node = new DocViewNode2(NameConstants.ROOT, properties);
            Mockito.verify(docViewXmlValidator).validate(node, new NodeContextImpl("/", Paths.get(".content.xml"), Paths.get(""), 6, 32), true);
        }
    }

    @Test
    public void testDocViewWithNamespacedFilename()
            throws ParserConfigurationException, SAXException, URISyntaxException, IOException, NamespaceException {
        Path filePath = Paths.get("apps", "_cq_content.xml");
        String nodePath = "/apps/cq:content";
        String message = "Unknown namespace prefix used in file name 'cq:content'";
        // fail during parsing due to unknown namespace in filename 
        try (InputStream input = this.getClass().getResourceAsStream("/simple-package/jcr_root/apps/_cq_content.xml")) {
            Collection<ValidationMessage> messages = validator.validateJcrData(input, filePath, Paths.get(""), nodePathsAndLineNumbers);
            ValidationExecutorTest.assertViolation(messages,
                    new ValidationViolation(DocumentViewParserValidatorFactory.ID, ValidationMessageSeverity.ERROR, 
                            "Could not parse FileVault Document View XML: " + message,
                            filePath, Paths.get(""), nodePath, 19, 36, new DocViewParser.XmlParseException(message, nodePath, 19, 36)
            ));
        }
        validator = new DocumentViewParserValidator(ValidationMessageSeverity.ERROR, true);
        try (InputStream input = this.getClass().getResourceAsStream("/simple-package/jcr_root/apps/_cq_content.xml")) {
            Collection<ValidationMessage> messages = validator.validateJcrData(input, filePath, Paths.get(""), nodePathsAndLineNumbers);
            // filter
            MatcherAssert.assertThat(messages, AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());
        }
    }

    @Test
    public void testDocViewWithEmptyElements() throws IOException {
        try (InputStream input = this.getClass().getResourceAsStream("/simple-package/jcr_root/apps/emptyelements/.content.xml")) {
            Collection<ValidationMessage> messages = validator.validateJcrData(input, Paths.get("apps", "emptyelements", ".content.xml"), Paths.get(""), nodePathsAndLineNumbers);
            MatcherAssert.assertThat(messages, AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());

            // verify node names
            Map<String, Integer> expectedNodePathsAndLineNumber = new HashMap<>();
            expectedNodePathsAndLineNumber.put("/apps/emptyelements", 20);
            expectedNodePathsAndLineNumber.put("/apps/emptyelements/nonemptyelement", 23);
            Assert.assertEquals(expectedNodePathsAndLineNumber, nodePathsAndLineNumbers);
        }
    }
    
    @Test
    public void testDocViewWithRegularFileName()
            throws ParserConfigurationException, SAXException, URISyntaxException, IOException, NamespaceException {

        try (InputStream input = this.getClass().getResourceAsStream("/simple-package/jcr_root/apps/child1.xml")) {
            Collection<ValidationMessage> messages = validator.validateJcrData(input, Paths.get("apps", "child1.xml"), Paths.get(""), nodePathsAndLineNumbers);
            MatcherAssert.assertThat(messages, AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());

            NameFactory nameFactory = NameFactoryImpl.getInstance();
            Collection<DocViewProperty2> properties = new ArrayList<>();
            properties.add(new DocViewProperty2(NameConstants.JCR_PRIMARYTYPE, "sling:Folder"));
            DocViewNode2 node = new DocViewNode2(nameFactory.create("{}child1"), properties);
            Mockito.verify(docViewXmlValidator).validate(node, new NodeContextImpl("/apps/child1", Paths.get("apps", "child1.xml"), Paths.get(""), 20, 36), true);

            properties = new ArrayList<>();
            properties.add(new DocViewProperty2(NameConstants.JCR_PRIMARYTYPE, JcrConstants.NT_UNSTRUCTURED));
            properties.add(new DocViewProperty2(nameFactory.create("{}attribute1"), "value1"));
            node = new DocViewNode2(nameFactory.create("{}somepath"), properties);
            Mockito.verify(docViewXmlValidator).validate(node, new NodeContextImpl("/apps/child1/somepath", Paths.get("apps", "child1.xml"), Paths.get(""), 23, 6), false);

            // verify node names
            Map<String, Integer> expectedNodePathsAndLineNumber = new HashMap<>();
            expectedNodePathsAndLineNumber.put("/apps/child1", 20);
            expectedNodePathsAndLineNumber.put("/apps/child1/somepath", 23);
            Assert.assertEquals(expectedNodePathsAndLineNumber, nodePathsAndLineNumbers);
        }
    }

    @Test
    public void testDocViewWithRegularFileNameAndUndeclaredNamespacePrefixInFilename()
            throws ParserConfigurationException, SAXException, URISyntaxException, IOException, NamespaceException {

        try (InputStream input = this.getClass().getResourceAsStream("/simple-package/jcr_root/apps/child1.xml")) {
            Collection<ValidationMessage> messages = validator.validateJcrData(input, Paths.get("apps", "_cq_child1.xml"), Paths.get(""), nodePathsAndLineNumbers);
           
            ValidationExecutorTest.assertViolation(messages, 
                    new ValidationViolation(DocumentViewParserValidatorFactory.ID, ValidationMessageSeverity.ERROR, 
                    "Could not parse FileVault Document View XML: Unknown namespace prefix used in file name 'cq:child1'",
                    Paths.get("apps", "_cq_child1.xml"), Paths.get(""), "/apps/cq:child1", 20, 36, 
                    new XmlParseException("Unknown namespace prefix used in file name 'cq:child1'", "/apps/cq:child1", 20, 36)));
        }
    }

    @Test
    public void testDocViewDotContentXmlWithRootElementDifferentThanJcrRoot()
            throws ParserConfigurationException, SAXException, URISyntaxException, IOException, NamespaceException {
        try (InputStream input = this.getClass().getResourceAsStream("/simple-package/jcr_root/apps/child2/.content.xml")) {
            Collection<ValidationMessage> messages = validator.validateJcrData(input, Paths.get("apps", "child2", ".content.xml"), Paths.get(""), nodePathsAndLineNumbers);
            MatcherAssert.assertThat(messages, AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());

            NameFactory nameFactory = NameFactoryImpl.getInstance();
            Collection<DocViewProperty2> properties = new ArrayList<>();
            properties.add(new DocViewProperty2(NameConstants.JCR_PRIMARYTYPE, "sling:Folder"));
            DocViewNode2 node = new DocViewNode2(nameFactory.create("{}child3"), properties);
            Mockito.verify(docViewXmlValidator).validate(node, new NodeContextImpl("/apps/child3", Paths.get("apps", "child2", ".content.xml"), Paths.get(""), 20, 36), true);

            properties.clear();
            properties.add(new DocViewProperty2(NameConstants.JCR_PRIMARYTYPE, JcrConstants.NT_UNSTRUCTURED));
            properties.add(new DocViewProperty2(nameFactory.create("{}attribute1"), "value1"));
            node = new DocViewNode2(nameFactory.create("{}somepath"), properties);
            Mockito.verify(docViewXmlValidator).validate(node, new NodeContextImpl("/apps/child3/somepath", Paths.get("apps", "child2", ".content.xml"), Paths.get(""), 23, 6), false);

            // verify node names
            Map<String, Integer> expectedNodePathsAndLineNumber = new HashMap<>();
            expectedNodePathsAndLineNumber.put("/apps/child3", 20);
            expectedNodePathsAndLineNumber.put("/apps/child3/somepath", 23);
            Assert.assertEquals(expectedNodePathsAndLineNumber, nodePathsAndLineNumbers);
        }
    }

    @Test
    public void testDocViewWithRegularFileNameWithRootElementDifferentThanJcrRoot() throws IOException {
        // https://issues.apache.org/jira/browse/JCRVLT-358 and https://issues.apache.org/jira/browse/JCRVLT-637
        try (InputStream input = this.getClass().getResourceAsStream("/simple-package/jcr_root/apps/child2/child1.xml")) {
            Collection<ValidationMessage> messages = validator.validateJcrData(input, Paths.get("apps", "child2", "child1.xml"), Paths.get(""), nodePathsAndLineNumbers);
            MatcherAssert.assertThat(messages, AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());

            Mockito.verifyNoMoreInteractions(docViewXmlValidator);

            // verify node names in case this is no docview xml
            Assert.assertEquals(new HashMap<>(), nodePathsAndLineNumbers);
        }
    }

    @Test
    public void testDocViewWithUnknownType() throws ParserConfigurationException, SAXException, URISyntaxException, IOException {
        try (InputStream input = this.getClass().getResourceAsStream("/simple-package/jcr_root/apps/invalid/wrongtype.xml")) {
            Collection<ValidationMessage> messages = validator.validateJcrData(input, Paths.get("apps", "invalid","wrongtype.xml"), Paths.get(""), nodePathsAndLineNumbers);

           ValidationExecutorTest.assertViolation(messages,
                    new ValidationViolation(DocumentViewParserValidatorFactory.ID,
                            ValidationMessageSeverity.ERROR,
                            "Could not parse FileVault Document View XML: unknown type: Invalid", Paths.get("apps/invalid/wrongtype.xml"), Paths.get(""), "/apps/invalid/wrongtype/somepath", 24, 6,
                            new XmlParseException(new IllegalArgumentException("unknown type: Invalid"), "/apps/invalid/wrongtype/somepath", 24, 6)));
        }
    }

    @Test
    public void testDocViewWithInvalidStringSerializationForType() throws ParserConfigurationException, SAXException, URISyntaxException, IOException {
        try (InputStream input = this.getClass().getResourceAsStream("/simple-package/jcr_root/apps/invalid/inconvertibletypes.xml")) {
            Collection<ValidationMessage> messages = validator.validateJcrData(input, Paths.get("apps", "invalid","inconvertibletypes.xml"), Paths.get(""), nodePathsAndLineNumbers);

           ValidationExecutorTest.assertViolation(messages,
                    new ValidationViolation(DocumentViewParserValidatorFactory.ID,
                            ValidationMessageSeverity.ERROR,
                            String.format(ValidatorDocViewParserHandler.MESSAGE_INVALID_STRING_SERIALIZATION, "Long", "attribute2", "1.0"),
                            Paths.get("apps/invalid/inconvertibletypes.xml"), Paths.get(""), "/apps/invalid/inconvertibletypes/somepath", 28, 6, null),
                    new ValidationViolation(DocumentViewParserValidatorFactory.ID,
                            ValidationMessageSeverity.ERROR,
                            String.format(ValidatorDocViewParserHandler.MESSAGE_INVALID_STRING_SERIALIZATION, "Date", "attribute1", "somedate"),
                            Paths.get("apps/invalid/inconvertibletypes.xml"), Paths.get(""), "/apps/invalid/inconvertibletypes/somepath", 28, 6, null) 
                   );
        }
    }
}
