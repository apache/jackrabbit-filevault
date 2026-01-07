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
package org.apache.jackrabbit.vault.validation.spi.impl.nodetype;

import javax.jcr.NamespaceException;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.UnsupportedRepositoryOperationException;
import javax.jcr.nodetype.InvalidNodeTypeDefinitionException;
import javax.jcr.nodetype.NoSuchNodeTypeException;
import javax.jcr.nodetype.NodeType;
import javax.jcr.nodetype.NodeTypeExistsException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Collection;

import org.apache.jackrabbit.commons.cnd.ParseException;
import org.apache.jackrabbit.oak.spi.nodetype.NodeTypeConstants;
import org.apache.jackrabbit.spi.commons.conversion.IllegalNameException;
import org.apache.jackrabbit.spi.commons.conversion.MalformedPathException;
import org.apache.jackrabbit.value.ValueFactoryImpl;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.util.StandaloneManagerProvider;
import org.apache.jackrabbit.vault.validation.AnyValidationViolationMessageMatcher;
import org.apache.jackrabbit.vault.validation.ValidationExecutorTest;
import org.apache.jackrabbit.vault.validation.spi.NodeContext;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessage;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessageSeverity;
import org.apache.jackrabbit.vault.validation.spi.util.NodeContextImpl;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class JcrNodeTypeMetaDataImplTest {

    private StandaloneManagerProvider ntManagerProvider;

    private JcrNodeTypeMetaDataImpl root;

    private static final String ROOT_NODE_TYPES =
            "rep:AccessControllable, rep:RepoAccessControllable, rep:root"; // alphabetical order
    // (i.e. primary type // last)

    @Before
    public void setUp() throws IOException, RepositoryException, ParseException {
        ntManagerProvider = new StandaloneManagerProvider();
        root = JcrNodeTypeMetaDataImpl.createRoot(false, ntManagerProvider.getEffectiveNodeTypeProvider());
    }

    static NodeContext createSimpleNodeContext(String nodePath) {
        return new NodeContextImpl(nodePath, Paths.get(""), Paths.get(""));
    }

    @Test
    public void testGetNode() throws RepositoryException {
        JcrNodeTypeMetaData child = root.addChildNode(
                ntManagerProvider.getNameResolver(),
                ntManagerProvider.getEffectiveNodeTypeProvider(),
                ntManagerProvider.getNodeTypeDefinitionProvider(),
                ntManagerProvider.getItemDefinitionProvider(),
                createSimpleNodeContext("my"),
                NodeType.NT_FOLDER);
        JcrNodeTypeMetaData grandChild = child.addChildNode(
                ntManagerProvider.getNameResolver(),
                ntManagerProvider.getEffectiveNodeTypeProvider(),
                ntManagerProvider.getNodeTypeDefinitionProvider(),
                ntManagerProvider.getItemDefinitionProvider(),
                createSimpleNodeContext("test"),
                NodeType.NT_FOLDER);
        JcrNodeTypeMetaData child2 = root.addChildNode(
                ntManagerProvider.getNameResolver(),
                ntManagerProvider.getEffectiveNodeTypeProvider(),
                ntManagerProvider.getNodeTypeDefinitionProvider(),
                ntManagerProvider.getItemDefinitionProvider(),
                createSimpleNodeContext("test2"),
                NodeType.NT_FOLDER);
        Assert.assertEquals(
                child2,
                grandChild
                        .getNode(ntManagerProvider.getNamePathResolver(), "/test2")
                        .get());
    }

    public void testGetNodeWithNonExistingPath()
            throws MalformedPathException, NamespaceException, IllegalArgumentException, PathNotFoundException,
                    RepositoryException {

        JcrNodeTypeMetaData child = root.addChildNode(
                ntManagerProvider.getNameResolver(),
                ntManagerProvider.getEffectiveNodeTypeProvider(),
                ntManagerProvider.getNodeTypeDefinitionProvider(),
                ntManagerProvider.getItemDefinitionProvider(),
                createSimpleNodeContext("my"),
                NodeType.NT_FOLDER);
        JcrNodeTypeMetaData grandChild = child.addChildNode(
                ntManagerProvider.getNameResolver(),
                ntManagerProvider.getEffectiveNodeTypeProvider(),
                ntManagerProvider.getNodeTypeDefinitionProvider(),
                ntManagerProvider.getItemDefinitionProvider(),
                createSimpleNodeContext("test"),
                NodeType.NT_FOLDER);
        Assert.assertFalse(grandChild
                .getNode(ntManagerProvider.getNamePathResolver(), "/test2")
                .isPresent());
    }

    @Test(expected = NamespaceExceptionInNodeName.class)
    public void testAddChildNodeWithUndeclaredNamespaceInName() throws RepositoryException {
        root.addChildNode(
                ntManagerProvider.getNamePathResolver(),
                ntManagerProvider.getEffectiveNodeTypeProvider(),
                ntManagerProvider.getNodeTypeDefinitionProvider(),
                ntManagerProvider.getItemDefinitionProvider(),
                createSimpleNodeContext("invalid:name"),
                NodeTypeConstants.NT_FOLDER);
    }

    @Test(expected = NamespaceException.class)
    public void testAddChildNodeWithUndeclaredNamespaceInType() throws RepositoryException {
        root.addChildNode(
                ntManagerProvider.getNamePathResolver(),
                ntManagerProvider.getEffectiveNodeTypeProvider(),
                ntManagerProvider.getNodeTypeDefinitionProvider(),
                ntManagerProvider.getItemDefinitionProvider(),
                createSimpleNodeContext("myname"),
                "my:nodeType1");
    }

    @Test(expected = NoSuchNodeTypeException.class)
    public void testAddChildNodeWithUndeclaredType() throws RepositoryException {
        root.addChildNode(
                ntManagerProvider.getNamePathResolver(),
                ntManagerProvider.getEffectiveNodeTypeProvider(),
                ntManagerProvider.getNodeTypeDefinitionProvider(),
                ntManagerProvider.getItemDefinitionProvider(),
                createSimpleNodeContext("myname"),
                "jcr:nodeType1");
    }

    @Test
    public void testChildNodeWithUnknownType()
            throws IllegalNameException, NamespaceExceptionInNodeName, RepositoryException {
        NodeContext nodeContext = createSimpleNodeContext("file");
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        filter.add(new PathFilterSet());
        // add node with mandatory child node
        nodeContext = createSimpleNodeContext("file/jcr:content");
        JcrNodeTypeMetaData file = root.addChildNode(
                ntManagerProvider.getNamePathResolver(),
                ntManagerProvider.getEffectiveNodeTypeProvider(),
                ntManagerProvider.getNodeTypeDefinitionProvider(),
                ntManagerProvider.getItemDefinitionProvider(),
                nodeContext,
                "nt:file");
        // mandatory child node has unknown node type
        JcrNodeTypeMetaData content =
                file.addUnknownChildNode(ntManagerProvider.getNameResolver(), nodeContext, "jcr:content");
        Collection<ValidationMessage> messages = file.finalizeValidation(
                ntManagerProvider.getNamePathResolver(),
                ntManagerProvider.getNodeTypeDefinitionProvider(),
                ntManagerProvider.getItemDefinitionProvider(),
                ValidationMessageSeverity.ERROR,
                ValidationMessageSeverity.ERROR,
                filter);
        ValidationExecutorTest.assertViolation(
                messages,
                new ValidationMessage(
                        ValidationMessageSeverity.ERROR,
                        String.format(
                                JcrNodeTypeMetaDataImpl.MESSAGE_MANDATORY_CHILD_NODE_MISSING,
                                "jcr:content [nt:base]",
                                "types [nt:file]"),
                        nodeContext));
    }

    @Test
    public void testAddChildNode()
            throws IOException, InvalidNodeTypeDefinitionException, NodeTypeExistsException,
                    UnsupportedRepositoryOperationException, ParseException, RepositoryException {
        try (InputStream input = this.getClass().getResourceAsStream("/simple-restricted-nodetypes.cnd");
                Reader reader = new InputStreamReader(input, StandardCharsets.US_ASCII)) {
            ntManagerProvider.registerNodeTypes(reader);
        }
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();

        // add node with mixin type as primary
        NodeContext nodeContext = createSimpleNodeContext("name");

        JcrNodeTypeMetaData node = root.addChildNode(
                ntManagerProvider.getNamePathResolver(),
                ntManagerProvider.getEffectiveNodeTypeProvider(),
                ntManagerProvider.getNodeTypeDefinitionProvider(),
                ntManagerProvider.getItemDefinitionProvider(),
                nodeContext,
                "mix:mimeType");
        ValidationExecutorTest.assertViolation(
                root.finalizeValidation(
                        ntManagerProvider.getNamePathResolver(),
                        ntManagerProvider.getNodeTypeDefinitionProvider(),
                        ntManagerProvider.getItemDefinitionProvider(),
                        ValidationMessageSeverity.ERROR,
                        ValidationMessageSeverity.ERROR,
                        filter),
                new ValidationMessage(
                        ValidationMessageSeverity.ERROR,
                        String.format(
                                JcrNodeTypeMetaDataImpl.MESSAGE_CHILD_NODE_NOT_ALLOWED,
                                "name",
                                "mix:mimeType",
                                "types [" + ROOT_NODE_TYPES + "]",
                                JcrNodeTypeMetaDataImpl.CONSTRAINT_MIXIN_TYPE_AS_PRIMARY_TYPE),
                        nodeContext));

        // add node with abstract type
        root = JcrNodeTypeMetaDataImpl.createRoot(false, ntManagerProvider.getEffectiveNodeTypeProvider());
        node = root.addChildNode(
                ntManagerProvider.getNamePathResolver(),
                ntManagerProvider.getEffectiveNodeTypeProvider(),
                ntManagerProvider.getNodeTypeDefinitionProvider(),
                ntManagerProvider.getItemDefinitionProvider(),
                nodeContext,
                "nt:hierarchyNode");
        ValidationExecutorTest.assertViolation(
                root.finalizeValidation(
                        ntManagerProvider.getNamePathResolver(),
                        ntManagerProvider.getNodeTypeDefinitionProvider(),
                        ntManagerProvider.getItemDefinitionProvider(),
                        ValidationMessageSeverity.ERROR,
                        ValidationMessageSeverity.ERROR,
                        filter),
                new ValidationMessage(
                        ValidationMessageSeverity.ERROR,
                        String.format(
                                JcrNodeTypeMetaDataImpl.MESSAGE_CHILD_NODE_NOT_ALLOWED,
                                "name",
                                "nt:hierarchyNode",
                                "types [" + ROOT_NODE_TYPES + "]",
                                JcrNodeTypeMetaDataImpl.CONSTRAINT_ABSTRACT_TYPE_AS_PRIMARY_TYPE),
                        nodeContext));

        // add node for version storage
        root = JcrNodeTypeMetaDataImpl.createRoot(false, ntManagerProvider.getEffectiveNodeTypeProvider());
        node = root.addChildNode(
                ntManagerProvider.getNamePathResolver(),
                ntManagerProvider.getEffectiveNodeTypeProvider(),
                ntManagerProvider.getNodeTypeDefinitionProvider(),
                ntManagerProvider.getItemDefinitionProvider(),
                createSimpleNodeContext("versionedNode"),
                "rep:versionStorage");
        // add child node with protected node which is ACL (i.e. accepted)
        node.addChildNode(
                ntManagerProvider.getNamePathResolver(),
                ntManagerProvider.getEffectiveNodeTypeProvider(),
                ntManagerProvider.getNodeTypeDefinitionProvider(),
                ntManagerProvider.getItemDefinitionProvider(),
                createSimpleNodeContext("rep:policy"),
                "rep:Policy");
        // add child node with protected node which is not ACL (i.e. not accepted)
        root = JcrNodeTypeMetaDataImpl.createRoot(false, ntManagerProvider.getEffectiveNodeTypeProvider());
        node.addChildNode(
                ntManagerProvider.getNamePathResolver(),
                ntManagerProvider.getEffectiveNodeTypeProvider(),
                ntManagerProvider.getNodeTypeDefinitionProvider(),
                ntManagerProvider.getItemDefinitionProvider(),
                nodeContext,
                "nt:versionHistory");
        ValidationExecutorTest.assertViolation(
                node.finalizeValidation(
                        ntManagerProvider.getNamePathResolver(),
                        ntManagerProvider.getNodeTypeDefinitionProvider(),
                        ntManagerProvider.getItemDefinitionProvider(),
                        ValidationMessageSeverity.ERROR,
                        ValidationMessageSeverity.ERROR,
                        filter),
                new ValidationMessage(
                        ValidationMessageSeverity.ERROR,
                        String.format(
                                JcrNodeTypeMetaDataImpl.MESSAGE_CHILD_NODE_NOT_ALLOWED,
                                "name",
                                "nt:versionHistory",
                                "types [rep:versionStorage]",
                                JcrNodeTypeMetaDataImpl.CONSTRAINT_CHILD_NODE_PROTECTED),
                        nodeContext));

        // add valid child node
        root = JcrNodeTypeMetaDataImpl.createRoot(false, ntManagerProvider.getEffectiveNodeTypeProvider());
        node = root.addChildNode(
                ntManagerProvider.getNamePathResolver(),
                ntManagerProvider.getEffectiveNodeTypeProvider(),
                ntManagerProvider.getNodeTypeDefinitionProvider(),
                ntManagerProvider.getItemDefinitionProvider(),
                createSimpleNodeContext("name"),
                "my:nodeType1");
        // below that add auto-created child node
        nodeContext = createSimpleNodeContext("my:autoCreatedChild1");
        node.addChildNode(
                ntManagerProvider.getNamePathResolver(),
                ntManagerProvider.getEffectiveNodeTypeProvider(),
                ntManagerProvider.getNodeTypeDefinitionProvider(),
                ntManagerProvider.getItemDefinitionProvider(),
                nodeContext,
                "my:nodeType2");
        // and next to it a child node which is not allowed
        NodeContext nodeContext2 = createSimpleNodeContext("name2");
        node.addChildNode(
                ntManagerProvider.getNamePathResolver(),
                ntManagerProvider.getEffectiveNodeTypeProvider(),
                ntManagerProvider.getNodeTypeDefinitionProvider(),
                ntManagerProvider.getItemDefinitionProvider(),
                nodeContext2,
                "my:nodeType1");
        ValidationExecutorTest.assertViolation(
                node.finalizeValidation(
                        ntManagerProvider.getNamePathResolver(),
                        ntManagerProvider.getNodeTypeDefinitionProvider(),
                        ntManagerProvider.getItemDefinitionProvider(),
                        ValidationMessageSeverity.ERROR,
                        ValidationMessageSeverity.ERROR,
                        filter),
                new ValidationMessage(
                        ValidationMessageSeverity.ERROR,
                        String.format(
                                JcrNodeTypeMetaDataImpl.MESSAGE_CHILD_NODE_NOT_ALLOWED,
                                "my:autoCreatedChild1",
                                "my:nodeType2",
                                "types [my:nodeType1]",
                                JcrNodeTypeMetaDataImpl.CONSTRAINT_CHILD_NODE_AUTO_CREATED),
                        nodeContext),
                new ValidationMessage(
                        ValidationMessageSeverity.ERROR,
                        String.format(
                                JcrNodeTypeMetaDataImpl.MESSAGE_CHILD_NODE_NOT_ALLOWED,
                                "name2",
                                "my:nodeType1",
                                "types [my:nodeType1]",
                                JcrNodeTypeMetaDataImpl.CONSTRAINT_CHILD_NODE_NOT_ALLOWED),
                        nodeContext2));
    }

    @Test
    public void testValidateMandatoryChildNodesAndProperties()
            throws IllegalNameException, NoSuchNodeTypeException, RepositoryException, IOException, ParseException {
        try (InputStream input = this.getClass().getResourceAsStream("/simple-restricted-nodetypes.cnd");
                Reader reader = new InputStreamReader(input, StandardCharsets.US_ASCII)) {
            ntManagerProvider.registerNodeTypes(reader);
        }

        // add valid node
        JcrNodeTypeMetaData node = root.addChildNode(
                ntManagerProvider.getNamePathResolver(),
                ntManagerProvider.getEffectiveNodeTypeProvider(),
                ntManagerProvider.getNodeTypeDefinitionProvider(),
                ntManagerProvider.getItemDefinitionProvider(),
                createSimpleNodeContext("name"),
                "my:nodeType1");

        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();

        // mandatory child node missing outside filter
        Collection<ValidationMessage> messages = root.finalizeValidation(
                ntManagerProvider.getNamePathResolver(),
                ntManagerProvider.getNodeTypeDefinitionProvider(),
                ntManagerProvider.getItemDefinitionProvider(),
                ValidationMessageSeverity.ERROR,
                ValidationMessageSeverity.ERROR,
                filter);
        MatcherAssert.assertThat(
                messages, AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());

        NodeContext nodeContext = createSimpleNodeContext("name2");
        node = root.addChildNode(
                ntManagerProvider.getNamePathResolver(),
                ntManagerProvider.getEffectiveNodeTypeProvider(),
                ntManagerProvider.getNodeTypeDefinitionProvider(),
                ntManagerProvider.getItemDefinitionProvider(),
                nodeContext,
                "my:nodeType1");

        // mandatory child node missing inside filter
        filter.add(new PathFilterSet("/"));
        messages = node.finalizeValidation(
                ntManagerProvider.getNamePathResolver(),
                ntManagerProvider.getNodeTypeDefinitionProvider(),
                ntManagerProvider.getItemDefinitionProvider(),
                ValidationMessageSeverity.ERROR,
                ValidationMessageSeverity.ERROR,
                filter);
        ValidationExecutorTest.assertViolation(
                messages,
                new ValidationMessage(
                        ValidationMessageSeverity.ERROR,
                        String.format(
                                JcrNodeTypeMetaDataImpl.MESSAGE_MANDATORY_CHILD_NODE_MISSING,
                                "my:namedChild1 [my:nodeType1]",
                                "types [my:nodeType1]",
                                "/name2"),
                        nodeContext));

        // calling a second time will not lead to anything
        messages = node.finalizeValidation(
                ntManagerProvider.getNamePathResolver(),
                ntManagerProvider.getNodeTypeDefinitionProvider(),
                ntManagerProvider.getItemDefinitionProvider(),
                ValidationMessageSeverity.ERROR,
                ValidationMessageSeverity.ERROR,
                filter);
        MatcherAssert.assertThat(
                messages, AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());

        // now add mandatory child node
        node = root.addChildNode(
                ntManagerProvider.getNamePathResolver(),
                ntManagerProvider.getEffectiveNodeTypeProvider(),
                ntManagerProvider.getNodeTypeDefinitionProvider(),
                ntManagerProvider.getItemDefinitionProvider(),
                createSimpleNodeContext("name3"),
                "my:nodeType1");

        node.addChildNode(
                ntManagerProvider.getNamePathResolver(),
                ntManagerProvider.getEffectiveNodeTypeProvider(),
                ntManagerProvider.getNodeTypeDefinitionProvider(),
                ntManagerProvider.getItemDefinitionProvider(),
                createSimpleNodeContext("my:namedChild1"),
                "my:nodeType1");
        messages = node.finalizeValidation(
                ntManagerProvider.getNamePathResolver(),
                ntManagerProvider.getNodeTypeDefinitionProvider(),
                ntManagerProvider.getItemDefinitionProvider(),
                ValidationMessageSeverity.ERROR,
                ValidationMessageSeverity.ERROR,
                new DefaultWorkspaceFilter());
        MatcherAssert.assertThat(
                messages, AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());

        // add arbitrary property to root
        MatcherAssert.assertThat(
                root.addProperty(
                        createSimpleNodeContext("/"),
                        ntManagerProvider.getNamePathResolver(),
                        ntManagerProvider.getEffectiveNodeTypeProvider(),
                        ntManagerProvider.getNodeTypeDefinitionProvider(),
                        ntManagerProvider.getItemDefinitionProvider(),
                        ValidationMessageSeverity.ERROR,
                        ValidationMessageSeverity.ERROR,
                        "property",
                        false,
                        ValueFactoryImpl.getInstance().createValue("foo")),
                AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());

        nodeContext = createSimpleNodeContext("nodeForMandatoryProperties");
        node = root.addChildNode(
                ntManagerProvider.getNamePathResolver(),
                ntManagerProvider.getEffectiveNodeTypeProvider(),
                ntManagerProvider.getNodeTypeDefinitionProvider(),
                ntManagerProvider.getItemDefinitionProvider(),
                nodeContext,
                "my:nodeType2");
        messages = node.finalizeValidation(
                ntManagerProvider.getNamePathResolver(),
                ntManagerProvider.getNodeTypeDefinitionProvider(),
                ntManagerProvider.getItemDefinitionProvider(),
                ValidationMessageSeverity.ERROR,
                ValidationMessageSeverity.ERROR,
                filter);
        ValidationExecutorTest.assertViolation(
                messages,
                new ValidationMessage(
                        ValidationMessageSeverity.ERROR,
                        String.format(
                                JcrNodeTypeMetaDataImpl.MESSAGE_MANDATORY_PROPERTY_MISSING,
                                "my:mandatoryProperty",
                                "types [my:nodeType2]",
                                "/nodeForMandatoryProperties"),
                        nodeContext));

        nodeContext = createSimpleNodeContext("nodeForMandatoryProperties2");
        node = root.addChildNode(
                ntManagerProvider.getNamePathResolver(),
                ntManagerProvider.getEffectiveNodeTypeProvider(),
                ntManagerProvider.getNodeTypeDefinitionProvider(),
                ntManagerProvider.getItemDefinitionProvider(),
                nodeContext,
                "my:nodeType2");
        // TODO: assert return value
        node.addProperty(
                nodeContext,
                ntManagerProvider.getNamePathResolver(),
                ntManagerProvider.getEffectiveNodeTypeProvider(),
                ntManagerProvider.getNodeTypeDefinitionProvider(),
                ntManagerProvider.getItemDefinitionProvider(),
                ValidationMessageSeverity.ERROR,
                ValidationMessageSeverity.ERROR,
                "my:mandatoryProperty",
                false,
                ValueFactoryImpl.getInstance().createValue("foo"));
        messages = node.finalizeValidation(
                ntManagerProvider.getNamePathResolver(),
                ntManagerProvider.getNodeTypeDefinitionProvider(),
                ntManagerProvider.getItemDefinitionProvider(),
                ValidationMessageSeverity.ERROR,
                ValidationMessageSeverity.ERROR,
                filter);
        ValidationExecutorTest.assertViolation(
                messages,
                new ValidationMessage(
                        ValidationMessageSeverity.ERROR,
                        String.format(
                                JcrNodeTypeMetaDataImpl.MESSAGE_MANDATORY_PROPERTY_WITH_WRONG_TYPE,
                                "my:mandatoryProperty",
                                "String",
                                "Date",
                                "types [my:nodeType2]"),
                        nodeContext));
    }

    @Test
    public void testValidateMandatoryChildNodesAndPropertiesDuringIncrementalBuild()
            throws InvalidNodeTypeDefinitionException, NodeTypeExistsException, UnsupportedRepositoryOperationException,
                    ParseException, RepositoryException, IOException {
        try (InputStream input = this.getClass().getResourceAsStream("/simple-restricted-nodetypes.cnd");
                Reader reader = new InputStreamReader(input, StandardCharsets.US_ASCII)) {
            ntManagerProvider.registerNodeTypes(reader);
        }
        // enable incremental validation
        root = JcrNodeTypeMetaDataImpl.createRoot(true, ntManagerProvider.getEffectiveNodeTypeProvider());
        // add valid node
        JcrNodeTypeMetaData node = root.addChildNode(
                ntManagerProvider.getNamePathResolver(),
                ntManagerProvider.getEffectiveNodeTypeProvider(),
                ntManagerProvider.getNodeTypeDefinitionProvider(),
                ntManagerProvider.getItemDefinitionProvider(),
                createSimpleNodeContext("name"),
                "my:nodeType1");

        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();

        node = root.addChildNode(
                ntManagerProvider.getNamePathResolver(),
                ntManagerProvider.getEffectiveNodeTypeProvider(),
                ntManagerProvider.getNodeTypeDefinitionProvider(),
                ntManagerProvider.getItemDefinitionProvider(),
                createSimpleNodeContext("name2"),
                "my:nodeType1");

        // mandatory child node missing but not reported due to incremental validation
        filter.add(new PathFilterSet("/"));
        Collection<ValidationMessage> messages = node.finalizeValidation(
                ntManagerProvider.getNamePathResolver(),
                ntManagerProvider.getNodeTypeDefinitionProvider(),
                ntManagerProvider.getItemDefinitionProvider(),
                ValidationMessageSeverity.ERROR,
                ValidationMessageSeverity.ERROR,
                filter);
        MatcherAssert.assertThat(
                messages, AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());

        // mandatory property missing but not reported due to incremental validation
        NodeContext nodeContext = createSimpleNodeContext("nodeForMandatoryProperties");
        node = root.addChildNode(
                ntManagerProvider.getNamePathResolver(),
                ntManagerProvider.getEffectiveNodeTypeProvider(),
                ntManagerProvider.getNodeTypeDefinitionProvider(),
                ntManagerProvider.getItemDefinitionProvider(),
                nodeContext,
                "my:nodeType2");
        messages = node.finalizeValidation(
                ntManagerProvider.getNamePathResolver(),
                ntManagerProvider.getNodeTypeDefinitionProvider(),
                ntManagerProvider.getItemDefinitionProvider(),
                ValidationMessageSeverity.ERROR,
                ValidationMessageSeverity.ERROR,
                filter);
        MatcherAssert.assertThat(
                messages, AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());
    }

    @Test(expected = IllegalNameException.class)
    public void testAddPropertyWithUndeclaredNamespace() throws RepositoryException {
        root.addProperty(
                createSimpleNodeContext("/"),
                ntManagerProvider.getNamePathResolver(),
                ntManagerProvider.getEffectiveNodeTypeProvider(),
                ntManagerProvider.getNodeTypeDefinitionProvider(),
                ntManagerProvider.getItemDefinitionProvider(),
                ValidationMessageSeverity.ERROR,
                ValidationMessageSeverity.ERROR,
                "invalid:property",
                false,
                ValueFactoryImpl.getInstance().createValue("foo"));
    }

    @Test
    public void testAddProperty() throws RepositoryException, ParseException, IOException {
        try (InputStream input = this.getClass().getResourceAsStream("/simple-restricted-nodetypes.cnd");
                Reader reader = new InputStreamReader(input, StandardCharsets.US_ASCII)) {
            ntManagerProvider.registerNodeTypes(reader);
        }

        NodeContext nodeContext = createSimpleNodeContext("/");
        // add arbitrary property to root
        MatcherAssert.assertThat(
                root.addProperty(
                        nodeContext,
                        ntManagerProvider.getNamePathResolver(),
                        ntManagerProvider.getEffectiveNodeTypeProvider(),
                        ntManagerProvider.getNodeTypeDefinitionProvider(),
                        ntManagerProvider.getItemDefinitionProvider(),
                        ValidationMessageSeverity.ERROR,
                        ValidationMessageSeverity.ERROR,
                        "property",
                        false,
                        ValueFactoryImpl.getInstance().createValue("foo")),
                AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());

        JcrNodeTypeMetaData node = root.addChildNode(
                ntManagerProvider.getNamePathResolver(),
                ntManagerProvider.getEffectiveNodeTypeProvider(),
                ntManagerProvider.getNodeTypeDefinitionProvider(),
                ntManagerProvider.getItemDefinitionProvider(),
                createSimpleNodeContext("child"),
                "my:nodeType3");

        // not allowed (wrong type)
        ValidationExecutorTest.assertViolation(
                node.addProperty(
                        nodeContext,
                        ntManagerProvider.getNamePathResolver(),
                        ntManagerProvider.getEffectiveNodeTypeProvider(),
                        ntManagerProvider.getNodeTypeDefinitionProvider(),
                        ntManagerProvider.getItemDefinitionProvider(),
                        ValidationMessageSeverity.ERROR,
                        ValidationMessageSeverity.ERROR,
                        "property",
                        false,
                        ValueFactoryImpl.getInstance().createValue("foo")),
                new ValidationMessage(
                        ValidationMessageSeverity.ERROR,
                        String.format(
                                JcrNodeTypeMetaDataImpl.MESSAGE_PROPERTY_NOT_ALLOWED,
                                "property",
                                "String",
                                "types [my:nodeType3]",
                                JcrNodeTypeMetaDataImpl.CONSTRAINT_PROPERTY_NOT_ALLOWED),
                        nodeContext));

        // protected but nevertheless allowed
        MatcherAssert.assertThat(
                node.addProperty(
                        nodeContext,
                        ntManagerProvider.getNamePathResolver(),
                        ntManagerProvider.getEffectiveNodeTypeProvider(),
                        ntManagerProvider.getNodeTypeDefinitionProvider(),
                        ntManagerProvider.getItemDefinitionProvider(),
                        ValidationMessageSeverity.ERROR,
                        ValidationMessageSeverity.ERROR,
                        "jcr:primaryType",
                        false,
                        ValueFactoryImpl.getInstance().createValue("foo")),
                AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());

        // protected
        ValidationExecutorTest.assertViolation(
                node.addProperty(
                        nodeContext,
                        ntManagerProvider.getNamePathResolver(),
                        ntManagerProvider.getEffectiveNodeTypeProvider(),
                        ntManagerProvider.getNodeTypeDefinitionProvider(),
                        ntManagerProvider.getItemDefinitionProvider(),
                        ValidationMessageSeverity.ERROR,
                        ValidationMessageSeverity.ERROR,
                        "my:protected",
                        false,
                        ValueFactoryImpl.getInstance().createValue("foo")),
                new ValidationMessage(
                        ValidationMessageSeverity.ERROR,
                        String.format(
                                JcrNodeTypeMetaDataImpl.MESSAGE_PROPERTY_NOT_ALLOWED,
                                "my:protected",
                                "String",
                                "types [my:nodeType3]",
                                JcrNodeTypeMetaDataImpl.CONSTRAINT_PROPERTY_PROTECTED),
                        nodeContext));

        // multi value where single value is required
        ValidationExecutorTest.assertViolation(
                node.addProperty(
                        nodeContext,
                        ntManagerProvider.getNamePathResolver(),
                        ntManagerProvider.getEffectiveNodeTypeProvider(),
                        ntManagerProvider.getNodeTypeDefinitionProvider(),
                        ntManagerProvider.getItemDefinitionProvider(),
                        ValidationMessageSeverity.ERROR,
                        ValidationMessageSeverity.ERROR,
                        "my:property1",
                        true,
                        ValueFactoryImpl.getInstance().createValue("foo"),
                        ValueFactoryImpl.getInstance().createValue("bar")),
                new ValidationMessage(
                        ValidationMessageSeverity.ERROR,
                        String.format(
                                JcrNodeTypeMetaDataImpl.MESSAGE_PROPERTY_NOT_ALLOWED,
                                "my:property1",
                                "String",
                                "types [my:nodeType3]",
                                JcrNodeTypeMetaDataImpl.CONSTRAINT_PROPERTY_NOT_ALLOWED),
                        nodeContext));

        // constrained property
        MatcherAssert.assertThat(
                node.addProperty(
                        nodeContext,
                        ntManagerProvider.getNamePathResolver(),
                        ntManagerProvider.getEffectiveNodeTypeProvider(),
                        ntManagerProvider.getNodeTypeDefinitionProvider(),
                        ntManagerProvider.getItemDefinitionProvider(),
                        ValidationMessageSeverity.ERROR,
                        ValidationMessageSeverity.ERROR,
                        "my:constrainedStringProperty",
                        false,
                        ValueFactoryImpl.getInstance().createValue("prefix1foo")),
                AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());

        MatcherAssert.assertThat(
                node.addProperty(
                        nodeContext,
                        ntManagerProvider.getNamePathResolver(),
                        ntManagerProvider.getEffectiveNodeTypeProvider(),
                        ntManagerProvider.getNodeTypeDefinitionProvider(),
                        ntManagerProvider.getItemDefinitionProvider(),
                        ValidationMessageSeverity.ERROR,
                        ValidationMessageSeverity.ERROR,
                        "my:constrainedStringProperty",
                        false,
                        ValueFactoryImpl.getInstance().createValue("foosuffix1")),
                AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());

        ValidationExecutorTest.assertViolation(
                node.addProperty(
                        nodeContext,
                        ntManagerProvider.getNamePathResolver(),
                        ntManagerProvider.getEffectiveNodeTypeProvider(),
                        ntManagerProvider.getNodeTypeDefinitionProvider(),
                        ntManagerProvider.getItemDefinitionProvider(),
                        ValidationMessageSeverity.ERROR,
                        ValidationMessageSeverity.ERROR,
                        "my:constrainedStringProperty",
                        false,
                        ValueFactoryImpl.getInstance().createValue("foo")),
                new ValidationMessage(
                        ValidationMessageSeverity.ERROR,
                        String.format(
                                JcrNodeTypeMetaDataImpl.MESSAGE_PROPERTY_NOT_ALLOWED,
                                "my:constrainedStringProperty",
                                "String",
                                "types [my:nodeType3]",
                                String.format(
                                        JcrNodeTypeMetaDataImpl.CONSTRAINT_PROPERTY_VALUE,
                                        "'foo' does not satisfy the constraint '.*suffix1'")),
                        nodeContext));
    }
}
