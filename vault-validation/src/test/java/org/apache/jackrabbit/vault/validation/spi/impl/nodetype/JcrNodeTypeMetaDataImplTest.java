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
package org.apache.jackrabbit.vault.validation.spi.impl.nodetype;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;

import javax.jcr.NamespaceException;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.UnsupportedRepositoryOperationException;
import javax.jcr.nodetype.InvalidNodeTypeDefinitionException;
import javax.jcr.nodetype.NoSuchNodeTypeException;
import javax.jcr.nodetype.NodeType;
import javax.jcr.nodetype.NodeTypeExistsException;

import org.apache.jackrabbit.commons.cnd.ParseException;
import org.apache.jackrabbit.oak.spi.nodetype.NodeTypeConstants;
import org.apache.jackrabbit.spi.commons.conversion.IllegalNameException;
import org.apache.jackrabbit.spi.commons.conversion.MalformedPathException;
import org.apache.jackrabbit.value.ValueFactoryImpl;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.validation.AnyValidationMessageMatcher;
import org.apache.jackrabbit.vault.validation.ValidationExecutorTest;
import org.apache.jackrabbit.vault.validation.spi.NodeContext;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessage;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessageSeverity;
import org.apache.jackrabbit.vault.validation.spi.util.NodeContextImpl;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class JcrNodeTypeMetaDataImplTest {

    private NodeTypeManagerProvider ntManagerProvider;

    private JcrNodeTypeMetaDataImpl root;

    private static final String ROOT_NODE_TYPES = "rep:AccessControllable, rep:RepoAccessControllable, rep:root"; // alphabetical order
    // (i.e. primary type // last)

    @Before
    public void setUp() throws IOException, RepositoryException, ParseException {
        ntManagerProvider = new NodeTypeManagerProvider();
        root = JcrNodeTypeMetaDataImpl.createRoot(ntManagerProvider.getEffectiveNodeTypeProvider());
    }

    static NodeContext createSimpleNodeContext(String nodePath) {
        return new NodeContextImpl(nodePath, Paths.get(""), Paths.get(""));
    }

    @Test
    public void testGetNode() throws RepositoryException {
        ValidationMessageSeverity severity = ValidationMessageSeverity.ERROR;
        JcrNodeTypeMetaData child = root.addChildNode(ntManagerProvider.getNameResolver(), ntManagerProvider.getEffectiveNodeTypeProvider(),
                ntManagerProvider.getNodeTypeDefinitionProvider(), ntManagerProvider.getItemDefinitionProvider(), severity,
                createSimpleNodeContext("my"),
                NodeType.NT_FOLDER);
        assertNoValidationErrors(child);
        JcrNodeTypeMetaData grandChild = child.addChildNode(ntManagerProvider.getNameResolver(),
                ntManagerProvider.getEffectiveNodeTypeProvider(),
                ntManagerProvider.getNodeTypeDefinitionProvider(), ntManagerProvider.getItemDefinitionProvider(), severity,
                createSimpleNodeContext("test"),
                NodeType.NT_FOLDER);
        assertNoValidationErrors(grandChild);
        JcrNodeTypeMetaData child2 = root.addChildNode(ntManagerProvider.getNameResolver(),
                ntManagerProvider.getEffectiveNodeTypeProvider(),
                ntManagerProvider.getNodeTypeDefinitionProvider(), ntManagerProvider.getItemDefinitionProvider(), severity,
                createSimpleNodeContext("test2"),
                NodeType.NT_FOLDER);
        assertNoValidationErrors(child2);
        Assert.assertEquals(child2,
                grandChild.getNode(ntManagerProvider.getNamePathResolver(), "/test2").get());
    }

    public void testGetNodeWithNonExistingPath() throws MalformedPathException, NamespaceException, IllegalArgumentException,
            PathNotFoundException, RepositoryException {

        ValidationMessageSeverity severity = ValidationMessageSeverity.ERROR;
        JcrNodeTypeMetaData child = root.addChildNode(ntManagerProvider.getNameResolver(), ntManagerProvider.getEffectiveNodeTypeProvider(),
                ntManagerProvider.getNodeTypeDefinitionProvider(), ntManagerProvider.getItemDefinitionProvider(), severity,
                createSimpleNodeContext("my"),
                NodeType.NT_FOLDER);
        JcrNodeTypeMetaData grandChild = child.addChildNode(ntManagerProvider.getNameResolver(),
                ntManagerProvider.getEffectiveNodeTypeProvider(), ntManagerProvider.getNodeTypeDefinitionProvider(),
                ntManagerProvider.getItemDefinitionProvider(), severity, createSimpleNodeContext("test"), NodeType.NT_FOLDER);
        Assert.assertFalse(grandChild.getNode(ntManagerProvider.getNamePathResolver(), "/test2").isPresent());
    }

    @Test(expected = NamespaceExceptionInNodeName.class)
    public void testAddChildNodeWithUndeclaredNamespaceInName() throws RepositoryException {
        root.addChildNode(ntManagerProvider.getNamePathResolver(), ntManagerProvider.getEffectiveNodeTypeProvider(),
                ntManagerProvider.getNodeTypeDefinitionProvider(), ntManagerProvider.getItemDefinitionProvider(),
                ValidationMessageSeverity.ERROR,
                createSimpleNodeContext("invalid:name"), NodeTypeConstants.NT_FOLDER);
    }

    @Test(expected = NamespaceException.class)
    public void testAddChildNodeWithUndeclaredNamespaceInType() throws RepositoryException {
        root.addChildNode(ntManagerProvider.getNamePathResolver(), ntManagerProvider.getEffectiveNodeTypeProvider(),
                ntManagerProvider.getNodeTypeDefinitionProvider(), ntManagerProvider.getItemDefinitionProvider(),
                ValidationMessageSeverity.ERROR,
                createSimpleNodeContext("myname"), "my:nodeType1");
    }

    @Test(expected = NoSuchNodeTypeException.class)
    public void testAddChildNodeWithUndeclaredType() throws RepositoryException {
        root.addChildNode(ntManagerProvider.getNamePathResolver(), ntManagerProvider.getEffectiveNodeTypeProvider(),
                ntManagerProvider.getNodeTypeDefinitionProvider(), ntManagerProvider.getItemDefinitionProvider(),
                ValidationMessageSeverity.ERROR,
                createSimpleNodeContext("myname"), "jcr:nodeType1");
    }

    @Test
    public void testAddChildNode() throws IOException, InvalidNodeTypeDefinitionException, NodeTypeExistsException,
            UnsupportedRepositoryOperationException, ParseException, RepositoryException {
        try (InputStream input = this.getClass().getResourceAsStream("/simple-restricted-nodetypes.cnd");
                Reader reader = new InputStreamReader(input,
                        StandardCharsets.US_ASCII)) {
            ntManagerProvider.registerNodeTypes(reader);
        }

        // add child node with mixin type as primary
        NodeContext nodeContext = createSimpleNodeContext("name");
        JcrNodeTypeMetaData node = root.addChildNode(ntManagerProvider.getNamePathResolver(),
                ntManagerProvider.getEffectiveNodeTypeProvider(), ntManagerProvider.getNodeTypeDefinitionProvider(),
                ntManagerProvider.getItemDefinitionProvider(), ValidationMessageSeverity.ERROR, nodeContext,
                "mix:mimeType");
        assertValidationMessage(node, new ValidationMessage(ValidationMessageSeverity.ERROR,
                String.format(JcrNodeTypeMetaDataImpl.MESSAGE_CHILD_NODE_NOT_ALLOWED, "name", "mix:mimeType", ROOT_NODE_TYPES,
                        JcrNodeTypeMetaDataImpl.CONSTRAINT_MIXIN_TYPE_AS_PRIMARY_TYPE),
                nodeContext));

        // add child node with abstract type
        node = root.addChildNode(ntManagerProvider.getNamePathResolver(),
                ntManagerProvider.getEffectiveNodeTypeProvider(), ntManagerProvider.getNodeTypeDefinitionProvider(),
                ntManagerProvider.getItemDefinitionProvider(), ValidationMessageSeverity.ERROR, nodeContext,
                "nt:hierarchyNode");
        assertValidationMessage(node, new ValidationMessage(ValidationMessageSeverity.ERROR,
                String.format(JcrNodeTypeMetaDataImpl.MESSAGE_CHILD_NODE_NOT_ALLOWED, "name", "nt:hierarchyNode", ROOT_NODE_TYPES,
                        JcrNodeTypeMetaDataImpl.CONSTRAINT_ABSTRACT_TYPE_AS_PRIMARY_TYPE),
                nodeContext));

        node = root.addChildNode(ntManagerProvider.getNamePathResolver(), ntManagerProvider.getEffectiveNodeTypeProvider(),
                ntManagerProvider.getNodeTypeDefinitionProvider(), ntManagerProvider.getItemDefinitionProvider(),
                ValidationMessageSeverity.ERROR,
                createSimpleNodeContext("versionedNode"), "rep:versionStorage");
        assertNoValidationErrors(node);

        // add child node with protected node which is ACL (i.e. accepted)
        JcrNodeTypeMetaData childNode = node.addChildNode(ntManagerProvider.getNamePathResolver(),
                ntManagerProvider.getEffectiveNodeTypeProvider(),
                ntManagerProvider.getNodeTypeDefinitionProvider(), ntManagerProvider.getItemDefinitionProvider(),
                ValidationMessageSeverity.ERROR,
                createSimpleNodeContext("rep:policy"), "rep:Policy");
        assertNoValidationErrors(childNode);

        // add child node with protected node which is not ACL (i.e. accepted)
        childNode = node.addChildNode(ntManagerProvider.getNamePathResolver(), ntManagerProvider.getEffectiveNodeTypeProvider(),
                ntManagerProvider.getNodeTypeDefinitionProvider(), ntManagerProvider.getItemDefinitionProvider(),
                ValidationMessageSeverity.ERROR,
                nodeContext, "nt:versionHistory");
        assertValidationMessage(childNode, new ValidationMessage(ValidationMessageSeverity.ERROR,
                String.format(JcrNodeTypeMetaDataImpl.MESSAGE_CHILD_NODE_NOT_ALLOWED, "name", "nt:versionHistory", "rep:versionStorage",
                        JcrNodeTypeMetaDataImpl.CONSTRAINT_CHILD_NODE_PROTECTED),
                nodeContext));

        // add valid child node
        node = root.addChildNode(ntManagerProvider.getNamePathResolver(),
                ntManagerProvider.getEffectiveNodeTypeProvider(), ntManagerProvider.getNodeTypeDefinitionProvider(),
                ntManagerProvider.getItemDefinitionProvider(), ValidationMessageSeverity.ERROR, createSimpleNodeContext("name"),
                "my:nodeType1");
        assertNoValidationErrors(node);

        // add auto-created child node
        nodeContext = createSimpleNodeContext("my:autoCreatedChild1");
        childNode = node.addChildNode(ntManagerProvider.getNamePathResolver(),
                ntManagerProvider.getEffectiveNodeTypeProvider(), ntManagerProvider.getNodeTypeDefinitionProvider(),
                ntManagerProvider.getItemDefinitionProvider(), ValidationMessageSeverity.ERROR,
                nodeContext, "my:nodeType2");
        assertValidationMessage(childNode, new ValidationMessage(ValidationMessageSeverity.ERROR,
                String.format(JcrNodeTypeMetaDataImpl.MESSAGE_CHILD_NODE_NOT_ALLOWED, "my:autoCreatedChild1", "my:nodeType2",
                        "my:nodeType1",
                        JcrNodeTypeMetaDataImpl.CONSTRAINT_CHILD_NODE_AUTO_CREATED),
                nodeContext));

        // below that add child node which is not allowed
        nodeContext = createSimpleNodeContext("name2");
        childNode = node.addChildNode(ntManagerProvider.getNamePathResolver(),
                ntManagerProvider.getEffectiveNodeTypeProvider(), ntManagerProvider.getNodeTypeDefinitionProvider(),
                ntManagerProvider.getItemDefinitionProvider(), ValidationMessageSeverity.ERROR, nodeContext,
                "my:nodeType1");
        assertValidationMessage(childNode, new ValidationMessage(ValidationMessageSeverity.ERROR,
                String.format(JcrNodeTypeMetaDataImpl.MESSAGE_CHILD_NODE_NOT_ALLOWED, "name2", "my:nodeType1", "my:nodeType1",
                        JcrNodeTypeMetaDataImpl.CONSTRAINT_CHILD_NODE_NOT_ALLOWED),
                nodeContext));
    }

    @Test
    public void testValidateMandatoryChildNode() throws IllegalNameException, NoSuchNodeTypeException, RepositoryException,
            IOException, ParseException {
        try (InputStream input = this.getClass().getResourceAsStream("/simple-restricted-nodetypes.cnd");
                Reader reader = new InputStreamReader(input, StandardCharsets.US_ASCII)) {
            ntManagerProvider.registerNodeTypes(reader);
        }

        // add valid node
        JcrNodeTypeMetaData node = root.addChildNode(ntManagerProvider.getNamePathResolver(),
                ntManagerProvider.getEffectiveNodeTypeProvider(), ntManagerProvider.getNodeTypeDefinitionProvider(),
                ntManagerProvider.getItemDefinitionProvider(), ValidationMessageSeverity.ERROR, createSimpleNodeContext("name"),
                "my:nodeType1");
        assertNoValidationErrors(node);

        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();

        // mandatory child node missing outside filter
        Collection<ValidationMessage> messages = node.finalizeValidation(ntManagerProvider.getNamePathResolver(),
                ValidationMessageSeverity.ERROR, filter);
        ValidationExecutorTest.assertViolation(messages, new ValidationMessage(ValidationMessageSeverity.ERROR,
                String.format(JcrNodeTypeMetaDataImpl.MESSAGE_MANDATORY_UNCONTAINED_CHILD_NODE_MISSING, "my:namedChild1 [my:nodeType1]", "my:nodeType1",
                        "/name")));

        node = root.addChildNode(ntManagerProvider.getNamePathResolver(),
                ntManagerProvider.getEffectiveNodeTypeProvider(), ntManagerProvider.getNodeTypeDefinitionProvider(),
                ntManagerProvider.getItemDefinitionProvider(), ValidationMessageSeverity.ERROR, createSimpleNodeContext("name2"),
                "my:nodeType1");
        
        // mandatory child node missing inside filter
        filter.add(new PathFilterSet("/"));
        messages = node.finalizeValidation(ntManagerProvider.getNamePathResolver(), ValidationMessageSeverity.ERROR, filter);
        ValidationExecutorTest.assertViolation(messages, new ValidationMessage(ValidationMessageSeverity.ERROR,
                String.format(JcrNodeTypeMetaDataImpl.MESSAGE_MANDATORY_CHILD_NODE_MISSING, "my:namedChild1 [my:nodeType1]", "my:nodeType1",
                        "/name2")));

        // calling a second time will not lead to anything
        messages = node.finalizeValidation(ntManagerProvider.getNamePathResolver(), ValidationMessageSeverity.ERROR, filter);
        MatcherAssert.assertThat(messages, AnyValidationMessageMatcher.noValidationInCollection());
        
        // now add mandatory child node
        node = root.addChildNode(ntManagerProvider.getNamePathResolver(),
                ntManagerProvider.getEffectiveNodeTypeProvider(), ntManagerProvider.getNodeTypeDefinitionProvider(),
                ntManagerProvider.getItemDefinitionProvider(), ValidationMessageSeverity.ERROR, createSimpleNodeContext("name3"),
                "my:nodeType1");
        
        node.addChildNode(ntManagerProvider.getNamePathResolver(),
                ntManagerProvider.getEffectiveNodeTypeProvider(), ntManagerProvider.getNodeTypeDefinitionProvider(),
                ntManagerProvider.getItemDefinitionProvider(), ValidationMessageSeverity.ERROR, createSimpleNodeContext("my:namedChild1"),
                "my:nodeType1");
        messages = node.finalizeValidation(ntManagerProvider.getNamePathResolver(), ValidationMessageSeverity.ERROR,
                new DefaultWorkspaceFilter());
        MatcherAssert.assertThat(messages, AnyValidationMessageMatcher.noValidationInCollection());

        // add arbitrary property to root
        root.addProperty(createSimpleNodeContext("/"), ntManagerProvider.getNamePathResolver(),
                ntManagerProvider.getEffectiveNodeTypeProvider(), ntManagerProvider.getNodeTypeDefinitionProvider(),
                ntManagerProvider.getItemDefinitionProvider(), ValidationMessageSeverity.ERROR, "property", false,
                ValueFactoryImpl.getInstance().createValue("foo"));
        assertNoValidationErrors(root);

        NodeContext nodeContext = createSimpleNodeContext("nodeForMandatoryProperties");
        node = root.addChildNode(ntManagerProvider.getNamePathResolver(),
                ntManagerProvider.getEffectiveNodeTypeProvider(), ntManagerProvider.getNodeTypeDefinitionProvider(),
                ntManagerProvider.getItemDefinitionProvider(), ValidationMessageSeverity.ERROR, nodeContext, "my:nodeType2");
        assertNoValidationErrors(node);
        messages = node.finalizeValidation(ntManagerProvider.getNamePathResolver(), ValidationMessageSeverity.ERROR, filter);
        ValidationExecutorTest.assertViolation(messages, new ValidationMessage(ValidationMessageSeverity.ERROR,
                String.format(JcrNodeTypeMetaDataImpl.MESSAGE_MANDATORY_PROPERTY_MISSING, "my:mandatoryProperty", "my:nodeType2",
                        "/nodeForMandatoryProperties")));

        nodeContext = createSimpleNodeContext("nodeForMandatoryProperties2");
        node = root.addChildNode(ntManagerProvider.getNamePathResolver(),
                ntManagerProvider.getEffectiveNodeTypeProvider(), ntManagerProvider.getNodeTypeDefinitionProvider(),
                ntManagerProvider.getItemDefinitionProvider(), ValidationMessageSeverity.ERROR, nodeContext, "my:nodeType2");
        assertNoValidationErrors(node);
        node.addProperty(nodeContext, ntManagerProvider.getNamePathResolver(), ntManagerProvider.getEffectiveNodeTypeProvider(),
                ntManagerProvider.getNodeTypeDefinitionProvider(), ntManagerProvider.getItemDefinitionProvider(),
                ValidationMessageSeverity.ERROR,
                "my:mandatoryProperty", false, ValueFactoryImpl.getInstance().createValue("foo"));
        messages = node.finalizeValidation(ntManagerProvider.getNamePathResolver(), ValidationMessageSeverity.ERROR, filter);
        ValidationExecutorTest.assertViolation(messages, new ValidationMessage(ValidationMessageSeverity.ERROR,
                String.format(JcrNodeTypeMetaDataImpl.MESSAGE_MANDATORY_PROPERTY_WITH_WRONG_TYPE, "my:mandatoryProperty", "String", "Date",
                        "my:nodeType2", "/nodeForMandatoryProperties2")));
    }

    @Test(expected = IllegalNameException.class)
    public void testAddPropertyWithUndeclaredNamespace() throws RepositoryException {
        root.addProperty(createSimpleNodeContext("/"), ntManagerProvider.getNamePathResolver(),
                ntManagerProvider.getEffectiveNodeTypeProvider(),
                ntManagerProvider.getNodeTypeDefinitionProvider(), ntManagerProvider.getItemDefinitionProvider(),
                ValidationMessageSeverity.ERROR,
                "invalid:property", false, ValueFactoryImpl.getInstance().createValue("foo"));
    }

    @Test
    public void testAddProperty() throws RepositoryException, ParseException, IOException {
        try (InputStream input = this.getClass().getResourceAsStream("/simple-restricted-nodetypes.cnd");
                Reader reader = new InputStreamReader(input,
                        StandardCharsets.US_ASCII)) {
            ntManagerProvider.registerNodeTypes(reader);
        }

        NodeContext nodeContext = createSimpleNodeContext("/");
        // add arbitrary property to root
        root.addProperty(nodeContext, ntManagerProvider.getNamePathResolver(),
                ntManagerProvider.getEffectiveNodeTypeProvider(), ntManagerProvider.getNodeTypeDefinitionProvider(),
                ntManagerProvider.getItemDefinitionProvider(), ValidationMessageSeverity.ERROR, "property", false,
                ValueFactoryImpl.getInstance().createValue("foo"));
        assertNoValidationErrors(root);

        JcrNodeTypeMetaData node = root.addChildNode(ntManagerProvider.getNamePathResolver(),
                ntManagerProvider.getEffectiveNodeTypeProvider(), ntManagerProvider.getNodeTypeDefinitionProvider(),
                ntManagerProvider.getItemDefinitionProvider(), ValidationMessageSeverity.ERROR, createSimpleNodeContext("child"),
                "my:nodeType3");
        assertNoValidationErrors(node);

        // not allowed (wrong type)
        node.addProperty(nodeContext, ntManagerProvider.getNamePathResolver(),
                ntManagerProvider.getEffectiveNodeTypeProvider(), ntManagerProvider.getNodeTypeDefinitionProvider(),
                ntManagerProvider.getItemDefinitionProvider(), ValidationMessageSeverity.ERROR, "property", false,
                ValueFactoryImpl.getInstance().createValue("foo"));
        assertValidationMessage(node,
                new ValidationMessage(ValidationMessageSeverity.ERROR,
                        String.format(JcrNodeTypeMetaDataImpl.MESSAGE_PROPERTY_NOT_ALLOWED, "property",
                                "String", "my:nodeType3", JcrNodeTypeMetaDataImpl.CONSTRAINT_PROPERTY_NOT_ALLOWED),
                        nodeContext));

        // protected but nevertheless allowed
        node.addProperty(nodeContext, ntManagerProvider.getNamePathResolver(),
                ntManagerProvider.getEffectiveNodeTypeProvider(), ntManagerProvider.getNodeTypeDefinitionProvider(),
                ntManagerProvider.getItemDefinitionProvider(), ValidationMessageSeverity.ERROR, "jcr:primaryType", false,
                ValueFactoryImpl.getInstance().createValue("foo"));
        assertNoValidationErrors(node);

        // protected
        node.addProperty(nodeContext, ntManagerProvider.getNamePathResolver(), ntManagerProvider.getEffectiveNodeTypeProvider(),
                ntManagerProvider.getNodeTypeDefinitionProvider(), ntManagerProvider.getItemDefinitionProvider(),
                ValidationMessageSeverity.ERROR,
                "my:protected", false, ValueFactoryImpl.getInstance().createValue("foo"));
        assertValidationMessage(node,
                new ValidationMessage(ValidationMessageSeverity.ERROR, String.format(JcrNodeTypeMetaDataImpl.MESSAGE_PROPERTY_NOT_ALLOWED,
                        "my:protected", "String", "my:nodeType3", JcrNodeTypeMetaDataImpl.CONSTRAINT_PROPERTY_PROTECTED), nodeContext));

        // multi value where single value is required
        node.addProperty(nodeContext, ntManagerProvider.getNamePathResolver(),
                ntManagerProvider.getEffectiveNodeTypeProvider(), ntManagerProvider.getNodeTypeDefinitionProvider(),
                ntManagerProvider.getItemDefinitionProvider(), ValidationMessageSeverity.ERROR, "my:property1", true,
                ValueFactoryImpl.getInstance().createValue("foo"), ValueFactoryImpl.getInstance().createValue("bar"));
        assertValidationMessage(node,
                new ValidationMessage(ValidationMessageSeverity.ERROR, String.format(JcrNodeTypeMetaDataImpl.MESSAGE_PROPERTY_NOT_ALLOWED,
                        "my:property1", "String", "my:nodeType3", JcrNodeTypeMetaDataImpl.CONSTRAINT_PROPERTY_NOT_ALLOWED), nodeContext));

        // constrained property
        node.addProperty(nodeContext, ntManagerProvider.getNamePathResolver(), ntManagerProvider.getEffectiveNodeTypeProvider(),
                ntManagerProvider.getNodeTypeDefinitionProvider(), ntManagerProvider.getItemDefinitionProvider(),
                ValidationMessageSeverity.ERROR,
                "my:constrainedStringProperty", false, ValueFactoryImpl.getInstance().createValue("prefix1foo"));
        assertNoValidationErrors(root);

        node.addProperty(nodeContext, ntManagerProvider.getNamePathResolver(), ntManagerProvider.getEffectiveNodeTypeProvider(),
                ntManagerProvider.getNodeTypeDefinitionProvider(), ntManagerProvider.getItemDefinitionProvider(),
                ValidationMessageSeverity.ERROR,
                "my:constrainedStringProperty", false, ValueFactoryImpl.getInstance().createValue("foosuffix1"));
        assertNoValidationErrors(root);

        node.addProperty(nodeContext, ntManagerProvider.getNamePathResolver(), ntManagerProvider.getEffectiveNodeTypeProvider(),
                ntManagerProvider.getNodeTypeDefinitionProvider(), ntManagerProvider.getItemDefinitionProvider(),
                ValidationMessageSeverity.ERROR,
                "my:constrainedStringProperty", false, ValueFactoryImpl.getInstance().createValue("foo"));
        assertValidationMessage(node,
                new ValidationMessage(ValidationMessageSeverity.ERROR, String.format(JcrNodeTypeMetaDataImpl.MESSAGE_PROPERTY_NOT_ALLOWED,
                        "my:constrainedStringProperty", "String", "my:nodeType3",
                        String.format(JcrNodeTypeMetaDataImpl.CONSTRAINT_PROPERTY_VALUE,
                                "'foo' does not satisfy the constraint '.*suffix1'")),
                        nodeContext));
    }

    private static void assertNoValidationErrors(JcrNodeTypeMetaData node) {
        Collection<ValidationMessage> messages = new ArrayList<>();
        node.fetchAndClearValidationMessages(messages);
        MatcherAssert.assertThat(messages, Matchers.empty());
    }

    private static void assertValidationMessage(JcrNodeTypeMetaData node, ValidationMessage... expectedMessages) {
        Collection<ValidationMessage> actualMessages = new ArrayList<>();
        node.fetchAndClearValidationMessages(actualMessages);
        MatcherAssert.assertThat(actualMessages, Matchers.contains(expectedMessages));
    }

}
