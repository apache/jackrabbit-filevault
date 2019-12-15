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
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.jcr.NamespaceException;
import javax.jcr.PropertyType;
import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import org.apache.jackrabbit.spi.commons.name.NameConstants;
import org.apache.jackrabbit.vault.util.DocViewNode;
import org.apache.jackrabbit.vault.util.DocViewProperty;
import org.apache.jackrabbit.vault.util.JcrConstants;
import org.apache.jackrabbit.vault.validation.ValidationViolation;
import org.apache.jackrabbit.vault.validation.impl.util.DocumentViewXmlContentHandler;
import org.apache.jackrabbit.vault.validation.spi.DocumentViewXmlValidator;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessage;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessageSeverity;
import org.apache.jackrabbit.vault.validation.spi.impl.DocumentViewParserValidator;
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

    @Mock
    private DocumentViewXmlValidator docViewXmlValidator;

    @Before
    public void setUp() throws ParserConfigurationException, SAXException {
        SAXParserFactory spf = SAXParserFactory.newInstance();
        spf.setNamespaceAware(true);
        spf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        validator = new DocumentViewParserValidator(spf.newSAXParser(), ValidationMessageSeverity.ERROR);
        nodePathsAndLineNumbers = new HashMap<>();
        validator.setDocumentViewXmlValidators(Collections.singletonMap("docviewid", docViewXmlValidator));
    }

    @Test
    public void testShouldValidate() {
        Assert.assertTrue(validator.shouldValidateJcrData(Paths.get("some", "file.xml")));
        Assert.assertFalse(validator.shouldValidateJcrData(Paths.get("some", "other", "file.json")));
    }

    @Test
    public void testDocViewDotContentXml()
            throws ParserConfigurationException, SAXException, URISyntaxException, IOException, NamespaceException {
        Mockito.when(docViewXmlValidator.validate(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyBoolean())).thenReturn(Collections.singleton(new ValidationMessage(ValidationMessageSeverity.ERROR, "startDocView")));
        try (InputStream input = this.getClass().getResourceAsStream("/simple-package/jcr_root/apps/.content.xml")) {
            Collection<ValidationMessage> messages = validator.validateJcrData(input, Paths.get("apps", ".content.xml"), nodePathsAndLineNumbers);
            // filter
            ValidationExecutorTest.assertViolation(messages,
                    new ValidationViolation("docviewid", ValidationMessageSeverity.ERROR, "startDocView", Paths.get("apps/.content.xml"), Paths.get(""), "/apps", 19, 36, null
                            ),
                    new ValidationViolation("docviewid", ValidationMessageSeverity.ERROR,
                            "startDocView", Paths.get("apps/.content.xml"), Paths.get(""), "/apps/somepath", 22, 6, null));

            // verify node names
            Map<String, Integer> expectedNodePathsAndLineNumber = new HashMap<>();
            expectedNodePathsAndLineNumber.put("/apps", 19);
            expectedNodePathsAndLineNumber.put("/apps/somepath", 22);
            Assert.assertEquals(expectedNodePathsAndLineNumber, nodePathsAndLineNumbers);
            Map<String, DocViewProperty> properties = new HashMap<>();
            properties.put(NameConstants.JCR_PRIMARYTYPE.toString(),
                    new DocViewProperty(NameConstants.JCR_PRIMARYTYPE.toString(), new String[] { "sling:Folder" }, false,
                            PropertyType.UNDEFINED));
            DocViewNode node = new DocViewNode("apps", "apps", null, properties, null, "sling:Folder");
            Mockito.verify(docViewXmlValidator).validate(node, "/apps", Paths.get("apps", ".content.xml"), true);

            properties = new HashMap<>();
            properties.put(NameConstants.JCR_PRIMARYTYPE.toString(),
                    new DocViewProperty(NameConstants.JCR_PRIMARYTYPE.toString(), new String[] { JcrConstants.NT_UNSTRUCTURED }, false,
                            PropertyType.UNDEFINED));
            properties.put("{}attribute1", new DocViewProperty("{}attribute1", new String[] { "value1" }, false, PropertyType.UNDEFINED));
            node = new DocViewNode("somepath", "somepath", null, properties, null, JcrConstants.NT_UNSTRUCTURED);
            Mockito.verify(docViewXmlValidator).validate(node, "/apps/somepath", Paths.get("apps", ".content.xml"), false);
        }
    }

    @Test
    public void testDocViewWithEmptyElements() throws IOException {
        try (InputStream input = this.getClass().getResourceAsStream("/simple-package/jcr_root/apps/emptyelements/.content.xml")) {
            Collection<ValidationMessage> messages = validator.validateJcrData(input, Paths.get("apps", "emptyelements", ".content.xml"), nodePathsAndLineNumbers);
            Assert.assertThat(messages, AnyValidationMessageMatcher.noValidationInCollection());

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
            Collection<ValidationMessage> messages = validator.validateJcrData(input, Paths.get("apps", "child1.xml"), nodePathsAndLineNumbers);
            Assert.assertThat(messages, AnyValidationViolationMatcher.noValidationInCollection());

            Map<String, DocViewProperty> properties = new HashMap<>();
            properties.put(NameConstants.JCR_PRIMARYTYPE.toString(),
                    new DocViewProperty(NameConstants.JCR_PRIMARYTYPE.toString(), new String[] { "sling:Folder" }, false,
                            PropertyType.UNDEFINED));
            DocViewNode node = new DocViewNode("child1", "child1", null, properties, null, "sling:Folder");
            Mockito.verify(docViewXmlValidator).validate(node, "/apps/child1", Paths.get("apps", "child1.xml"), true);

            properties = new HashMap<>();
            properties.put(NameConstants.JCR_PRIMARYTYPE.toString(),
                    new DocViewProperty(NameConstants.JCR_PRIMARYTYPE.toString(), new String[] { JcrConstants.NT_UNSTRUCTURED }, false,
                            PropertyType.UNDEFINED));
            properties.put("{}attribute1", new DocViewProperty("{}attribute1", new String[] { "value1" }, false, PropertyType.UNDEFINED));
            node = new DocViewNode("somepath", "somepath", null, properties, null, JcrConstants.NT_UNSTRUCTURED);
            Mockito.verify(docViewXmlValidator).validate(node, "/apps/child1/somepath", Paths.get("apps", "child1.xml"), false);

            // verify node names
            Map<String, Integer> expectedNodePathsAndLineNumber = new HashMap<>();
            expectedNodePathsAndLineNumber.put("/apps/child1", 20);
            expectedNodePathsAndLineNumber.put("/apps/child1/somepath", 23);
            Assert.assertEquals(expectedNodePathsAndLineNumber, nodePathsAndLineNumbers);
        }
    }

    @Test
    public void testDocViewDotContentXmlWithRootElementDifferentThanJcrRoot()
            throws ParserConfigurationException, SAXException, URISyntaxException, IOException, NamespaceException {
        try (InputStream input = this.getClass().getResourceAsStream("/simple-package/jcr_root/apps/child2/.content.xml")) {
            Collection<ValidationMessage> messages = validator.validateJcrData(input, Paths.get("apps", "child2", ".content.xml"), nodePathsAndLineNumbers);
            Assert.assertThat(messages, AnyValidationViolationMatcher.noValidationInCollection());

            Map<String, DocViewProperty> properties = new HashMap<>();
            properties.put(NameConstants.JCR_PRIMARYTYPE.toString(),
                    new DocViewProperty(NameConstants.JCR_PRIMARYTYPE.toString(), new String[] { "sling:Folder" }, false,
                            PropertyType.UNDEFINED));
            DocViewNode node = new DocViewNode("child3", "child3", null, properties, null, "sling:Folder");
            Mockito.verify(docViewXmlValidator).validate(node, "/apps/child3", Paths.get("apps", "child2", ".content.xml"), true);

            properties = new HashMap<>();
            properties.put(NameConstants.JCR_PRIMARYTYPE.toString(),
                    new DocViewProperty(NameConstants.JCR_PRIMARYTYPE.toString(), new String[] { JcrConstants.NT_UNSTRUCTURED }, false,
                            PropertyType.UNDEFINED));
            properties.put("{}attribute1", new DocViewProperty("{}attribute1", new String[] { "value1" }, false, PropertyType.UNDEFINED));
            node = new DocViewNode("somepath", "somepath", null, properties, null, JcrConstants.NT_UNSTRUCTURED);
            Mockito.verify(docViewXmlValidator).validate(node, "/apps/child3/somepath", Paths.get("apps", "child2", ".content.xml"), false);

            // verify node names
            Map<String, Integer> expectedNodePathsAndLineNumber = new HashMap<>();
            expectedNodePathsAndLineNumber.put("/apps/child3", 20);
            expectedNodePathsAndLineNumber.put("/apps/child3/somepath", 23);
            Assert.assertEquals(expectedNodePathsAndLineNumber, nodePathsAndLineNumbers);
        }
    }

    @Test
    public void testDocViewWithRegularFileNameWithRootElementDifferentThanJcrRoot() throws IOException {
        // https://issues.apache.org/jira/browse/JCRVLT-358"
        try (InputStream input = this.getClass().getResourceAsStream("/simple-package/jcr_root/apps/child2/child1.xml")) {
            Collection<ValidationMessage> messages = validator.validateJcrData(input, Paths.get("apps", "child2", "child1.xml"), nodePathsAndLineNumbers);
            Assert.assertThat(messages, AnyValidationMessageMatcher.noValidationInCollection());

            Mockito.verifyZeroInteractions(docViewXmlValidator);

            // verify node names
            Map<String, Integer> expectedNodePathsAndLineNumber = new HashMap<>();
            expectedNodePathsAndLineNumber.put("/apps/child2/child1.xml", 0);
            Assert.assertEquals(expectedNodePathsAndLineNumber, nodePathsAndLineNumbers);
        }
    }

    @Test
    public void testDocViewWithInvalidType() throws ParserConfigurationException, SAXException, URISyntaxException, IOException {
        try (InputStream input = this.getClass().getResourceAsStream("/simple-package/jcr_root/apps/invalid/wrongtype.xml")) {
            Collection<ValidationMessage> messages = validator.validateJcrData(input, Paths.get("apps", "invalid","wrongtype.xml"), nodePathsAndLineNumbers);

           ValidationExecutorTest.assertViolation(messages,
                    new ValidationViolation(ValidationMessageSeverity.ERROR,
                            String.format(DocumentViewXmlContentHandler.PARSE_VIOLATION_MESSAGE_STRING, "somepath",
                                    "unknown type: Invalid"), Paths.get("apps/invalid/wrongtype.xml"), Paths.get(""), "/apps/invalid/wrongtype/somepath", 24, 6,
                            new IllegalArgumentException("unknown type: Invalid")));
        }
    }

}
