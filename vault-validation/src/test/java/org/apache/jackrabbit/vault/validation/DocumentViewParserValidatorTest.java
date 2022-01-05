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

import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.spi.commons.name.NameConstants;
import org.apache.jackrabbit.spi.commons.name.NameFactoryImpl;
import org.apache.jackrabbit.vault.util.DocViewNode;
import org.apache.jackrabbit.vault.util.DocViewProperty;
import org.apache.jackrabbit.vault.util.JcrConstants;
import org.apache.jackrabbit.vault.validation.impl.util.DocumentViewXmlContentHandler;
import org.apache.jackrabbit.vault.validation.spi.DocumentViewXmlValidator;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessage;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessageSeverity;
import org.apache.jackrabbit.vault.validation.spi.impl.DocumentViewParserValidator;
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
        SAXParserFactory spf = SAXParserFactory.newInstance();
        spf.setNamespaceAware(true);
        spf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        validator = new DocumentViewParserValidator(spf.newSAXParser(), ValidationMessageSeverity.ERROR);
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
        Mockito.when(docViewXmlValidator.validate(Mockito.any(), Mockito.any(), Mockito.anyBoolean())).thenReturn(Collections.singleton(new ValidationMessage(ValidationMessageSeverity.ERROR, "startDocView")));
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
            Map<String, DocViewProperty> properties = new HashMap<>();
            properties.put(NameConstants.JCR_PRIMARYTYPE.toString(),
                    new DocViewProperty(NameConstants.JCR_PRIMARYTYPE.toString(), new String[] { "sling:Folder" }, false,
                            PropertyType.UNDEFINED));
            DocViewNode node = new DocViewNode("{}apps", "jc:root", null, properties, null, "sling:Folder");
            Mockito.verify(docViewXmlValidator).validate(node, new NodeContextImpl("/apps", Paths.get("apps", ".content.xml"), Paths.get("")), true);

            properties = new HashMap<>();
            properties.put(NameConstants.JCR_PRIMARYTYPE.toString(),
                    new DocViewProperty(NameConstants.JCR_PRIMARYTYPE.toString(), new String[] { JcrConstants.NT_UNSTRUCTURED }, false,
                            PropertyType.UNDEFINED));
            properties.put("{}attribute1", new DocViewProperty("{}attribute1", new String[] { "value1" }, false, PropertyType.UNDEFINED));
            node = new DocViewNode("{}somepath", "somepath", null, properties, null, JcrConstants.NT_UNSTRUCTURED);
            Mockito.verify(docViewXmlValidator).validate(node, new NodeContextImpl("/apps/somepath", Paths.get("apps", ".content.xml"), Paths.get("")), false);
            
            properties = new HashMap<>();
            properties.put(NameConstants.JCR_PRIMARYTYPE.toString(),
                    new DocViewProperty(NameConstants.JCR_PRIMARYTYPE.toString(), new String[] { JcrConstants.NT_UNSTRUCTURED }, false,
                            PropertyType.UNDEFINED));
            node = new DocViewNode("{http://www.jcp.org/jcr/1.0}content", "jc:content", null, properties, null, JcrConstants.NT_UNSTRUCTURED);
            Mockito.verify(docViewXmlValidator).validate(node, new NodeContextImpl("/apps/somepath/jc:content", Paths.get("apps", ".content.xml"), Paths.get("")), false);
        }
    }

    @Test
    public void testDocViewDotContentXmlOnRootLevel()
            throws ParserConfigurationException, SAXException, URISyntaxException, IOException, NamespaceException {
        Mockito.when(docViewXmlValidator.validate(Mockito.any(), Mockito.any(), Mockito.anyBoolean())).thenReturn(Collections.singleton(new ValidationMessage(ValidationMessageSeverity.ERROR, "startDocView")));
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
            Map<String, DocViewProperty> properties = new HashMap<>();
            properties.put(NameConstants.JCR_PRIMARYTYPE.toString(),
                    new DocViewProperty(NameConstants.JCR_PRIMARYTYPE.toString(), new String[] { "rep:root" }, false,
                            PropertyType.UNDEFINED));
            properties.put(NameConstants.JCR_MIXINTYPES.toString(), new DocViewProperty(NameConstants.JCR_MIXINTYPES.toString(), new String[] { "rep:AccessControllable" ,"rep:RepoAccessControllable" }, true, PropertyType.UNDEFINED));
            properties.put(NAME_SLING_RESOURCE_TYPE.toString(), new DocViewProperty(NAME_SLING_RESOURCE_TYPE.toString(), new String[] { "sling:redirect" }, false, PropertyType.UNDEFINED));
            properties.put(NAME_SLING_TARGET.toString(), new DocViewProperty(NAME_SLING_TARGET.toString(), new String[] { "/index.html" }, false, PropertyType.UNDEFINED));
            
            DocViewNode node = new DocViewNode(NameConstants.JCR_ROOT.toString(), "jcr:root", null, properties, new String[] { "rep:AccessControllable" ,"rep:RepoAccessControllable" }, "rep:root");
            Mockito.verify(docViewXmlValidator).validate(node, new NodeContextImpl("/", Paths.get(".content.xml"), Paths.get("")), true);
        }
    }
    
    @Test
    public void testDocViewWithEmptyElements() throws IOException {
        try (InputStream input = this.getClass().getResourceAsStream("/simple-package/jcr_root/apps/emptyelements/.content.xml")) {
            Collection<ValidationMessage> messages = validator.validateJcrData(input, Paths.get("apps", "emptyelements", ".content.xml"), Paths.get(""), nodePathsAndLineNumbers);
            MatcherAssert.assertThat(messages, AnyValidationMessageMatcher.noValidationInCollection());

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
            MatcherAssert.assertThat(messages, AnyValidationViolationMatcher.noValidationInCollection());

            Map<String, DocViewProperty> properties = new HashMap<>();
            properties.put(NameConstants.JCR_PRIMARYTYPE.toString(),
                    new DocViewProperty(NameConstants.JCR_PRIMARYTYPE.toString(), new String[] { "sling:Folder" }, false,
                            PropertyType.UNDEFINED));
            DocViewNode node = new DocViewNode("{}child1", "jcr:root", null, properties, null, "sling:Folder");
            Mockito.verify(docViewXmlValidator).validate(node, new NodeContextImpl("/apps/child1", Paths.get("apps", "child1.xml"), Paths.get("")), true);

            properties = new HashMap<>();
            properties.put(NameConstants.JCR_PRIMARYTYPE.toString(),
                    new DocViewProperty(NameConstants.JCR_PRIMARYTYPE.toString(), new String[] { JcrConstants.NT_UNSTRUCTURED }, false,
                            PropertyType.UNDEFINED));
            properties.put("{}attribute1", new DocViewProperty("{}attribute1", new String[] { "value1" }, false, PropertyType.UNDEFINED));
            node = new DocViewNode("{}somepath", "somepath", null, properties, null, JcrConstants.NT_UNSTRUCTURED);
            Mockito.verify(docViewXmlValidator).validate(node, new NodeContextImpl("/apps/child1/somepath", Paths.get("apps", "child1.xml"), Paths.get("")), false);

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
                    new ValidationViolation(ValidationMessageSeverity.ERROR, 
                    "Invalid XML found: Given root node name 'cq:child1' (implicitly given via filename) cannot be resolved. The prefix used in the filename must be declared as XML namespace in the child docview XML as well!",
                    Paths.get("apps", "_cq_child1.xml"), Paths.get(""), "/apps/cq:child1",  0,0, null));
        }
    }

    @Test
    public void testDocViewDotContentXmlWithRootElementDifferentThanJcrRoot()
            throws ParserConfigurationException, SAXException, URISyntaxException, IOException, NamespaceException {
        try (InputStream input = this.getClass().getResourceAsStream("/simple-package/jcr_root/apps/child2/.content.xml")) {
            Collection<ValidationMessage> messages = validator.validateJcrData(input, Paths.get("apps", "child2", ".content.xml"), Paths.get(""), nodePathsAndLineNumbers);
            MatcherAssert.assertThat(messages, AnyValidationViolationMatcher.noValidationInCollection());

            Map<String, DocViewProperty> properties = new HashMap<>();
            properties.put(NameConstants.JCR_PRIMARYTYPE.toString(),
                    new DocViewProperty(NameConstants.JCR_PRIMARYTYPE.toString(), new String[] { "sling:Folder" }, false,
                            PropertyType.UNDEFINED));
            DocViewNode node = new DocViewNode("{}child3", "child3", null, properties, null, "sling:Folder");
            Mockito.verify(docViewXmlValidator).validate(node, new NodeContextImpl("/apps/child3", Paths.get("apps", "child2", ".content.xml"), Paths.get("")), true);

            properties = new HashMap<>();
            properties.put(NameConstants.JCR_PRIMARYTYPE.toString(),
                    new DocViewProperty(NameConstants.JCR_PRIMARYTYPE.toString(), new String[] { JcrConstants.NT_UNSTRUCTURED }, false,
                            PropertyType.UNDEFINED));
            properties.put("{}attribute1", new DocViewProperty("{}attribute1", new String[] { "value1" }, false, PropertyType.UNDEFINED));
            node = new DocViewNode("{}somepath", "somepath", null, properties, null, JcrConstants.NT_UNSTRUCTURED);
            Mockito.verify(docViewXmlValidator).validate(node, new NodeContextImpl("/apps/child3/somepath", Paths.get("apps", "child2", ".content.xml"), Paths.get("")), false);

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
            Collection<ValidationMessage> messages = validator.validateJcrData(input, Paths.get("apps", "child2", "child1.xml"), Paths.get(""), nodePathsAndLineNumbers);
            MatcherAssert.assertThat(messages, AnyValidationMessageMatcher.noValidationInCollection());

            Mockito.verifyNoMoreInteractions(docViewXmlValidator);

            // verify node names
            Map<String, Integer> expectedNodePathsAndLineNumber = new HashMap<>();
            expectedNodePathsAndLineNumber.put("/apps/child2/child1.xml", 0);
            Assert.assertEquals(expectedNodePathsAndLineNumber, nodePathsAndLineNumbers);
        }
    }

    @Test
    public void testDocViewWithInvalidType() throws ParserConfigurationException, SAXException, URISyntaxException, IOException {
        try (InputStream input = this.getClass().getResourceAsStream("/simple-package/jcr_root/apps/invalid/wrongtype.xml")) {
            Collection<ValidationMessage> messages = validator.validateJcrData(input, Paths.get("apps", "invalid","wrongtype.xml"), Paths.get(""), nodePathsAndLineNumbers);

           ValidationExecutorTest.assertViolation(messages,
                    new ValidationViolation(ValidationMessageSeverity.ERROR,
                            String.format(DocumentViewXmlContentHandler.PARSE_VIOLATION_MESSAGE_STRING, "somepath",
                                    "unknown type: Invalid"), Paths.get("apps/invalid/wrongtype.xml"), Paths.get(""), "/apps/invalid/wrongtype/somepath", 24, 6,
                            new IllegalArgumentException("unknown type: Invalid")));
        }
    }

}
