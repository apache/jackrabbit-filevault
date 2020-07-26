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
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.jcr.Property;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.nodetype.NodeType;

import org.apache.jackrabbit.JcrConstants;
import org.apache.jackrabbit.commons.cnd.ParseException;
import org.apache.jackrabbit.jcr2spi.nodetype.EffectiveNodeType;
import org.apache.jackrabbit.spi.commons.name.NameConstants;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.util.DocViewNode;
import org.apache.jackrabbit.vault.util.DocViewProperty;
import org.apache.jackrabbit.vault.validation.AnyValidationMessageMatcher;
import org.apache.jackrabbit.vault.validation.ValidationExecutorTest;
import org.apache.jackrabbit.vault.validation.spi.NodeContext;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessage;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessageSeverity;
import org.apache.jackrabbit.vault.validation.spi.util.NodeContextImpl;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class NodeTypeValidatorTest {

    private NodeTypeValidator validator;
    private DefaultWorkspaceFilter filter;

    @Before
    public void setUp() throws IOException, ConfigurationException, RepositoryException, ParseException {
        filter = new DefaultWorkspaceFilter();
        try (InputStream input = this.getClass().getResourceAsStream("/filter.xml")) {
            filter.load(input);
        }
        validator = createValidator(filter, JcrConstants.NT_FOLDER);
    }

    static NodeTypeValidator createValidator(WorkspaceFilter filter, String defaultNodeType)
            throws IOException, RepositoryException, ParseException {
        NodeTypeManagerProvider ntManagerProvider = new NodeTypeManagerProvider();
        EffectiveNodeType defaultEffectiveNodeType = ntManagerProvider.getEffectiveNodeTypeProvider()
                .getEffectiveNodeType(ntManagerProvider.getNameResolver().getQName(defaultNodeType));
        return new NodeTypeValidator(filter, ntManagerProvider, defaultEffectiveNodeType, ValidationMessageSeverity.ERROR,
                ValidationMessageSeverity.WARN);
    }

    @Test
    @Ignore
    public void testValidateComplexUnstructuredNodeTypes() throws IOException, RepositoryException, ParseException, ConfigurationException {
        NodeContext nodeContext = new NodeContextImpl("/apps/test/node4", Paths.get("node4"), Paths.get(""));

        Map<String, DocViewProperty> props = new HashMap<>();
        DocViewProperty property = new DocViewProperty("{}prop1", new String[] { "value1" }, false, PropertyType.STRING);
        props.put("{}prop1", property);
        props.put(NameConstants.JCR_PRIMARYTYPE.toString(), new DocViewProperty(NameConstants.JCR_PRIMARYTYPE.toString(),
                new String[] { "nt:unstructured" }, false, PropertyType.STRING));

        // no primary type
        DocViewNode node = new DocViewNode("jcr:root", "jcr:root", null, props, null, "sling:Folder");
        Assert.assertNull(validator.validate(node, nodeContext, false));

        props.put(NameConstants.JCR_PRIMARYTYPE.toString(),
                new DocViewProperty(NameConstants.JCR_PRIMARYTYPE.toString(), new String[] { "value1" }, false, PropertyType.STRING));
        node = new DocViewNode("test", "test", null, props, null, "nt:folder");
        ValidationExecutorTest.assertViolation(validator.validate(node, nodeContext, false),
                new ValidationMessage(ValidationMessageSeverity.ERROR,
                        String.format(NodeTypeValidator.MESSAGE_PROPERTY_NOT_ALLOWED, property, "nt:folder",
                                "No property definition found for name!")));
    }

    @Test
    public void testInvalidChildNodeTypeBelowDefault() {
        NodeContext nodeContext = new NodeContextImpl("/apps/test/node4", Paths.get("node4"), Paths.get(""));

        Map<String, DocViewProperty> props = new HashMap<>();
        props.put(NameConstants.JCR_PRIMARYTYPE.toString(), new DocViewProperty(NameConstants.JCR_PRIMARYTYPE.toString(),
                new String[] { JcrConstants.NT_UNSTRUCTURED }, false, PropertyType.STRING));
        // nt:unstructured below nt:folder is not allowed
        DocViewNode node = new DocViewNode("jcr:root", "jcr:root", null, props, null, JcrConstants.NT_UNSTRUCTURED);
        ValidationExecutorTest.assertViolation(validator.validate(node, nodeContext, false),
                new ValidationMessage(ValidationMessageSeverity.ERROR,
                        String.format(NodeTypeValidator.MESSAGE_CHILD_NODE_OF_NOT_CONTAINED_PARENT_POTENTIALLY_NOT_ALLOWED,
                                "jcr:root [nt:unstructured]", JcrConstants.NT_FOLDER,
                                "Could not find matching child node definition in parent's node type")));
    }

    @Test
    public void testMissingMandatoryChildNode() {
        NodeContext nodeContext = new NodeContextImpl("/apps/test/node4", Paths.get("node4"), Paths.get(""));

        Map<String, DocViewProperty> props = new HashMap<>();
        props.put(NameConstants.JCR_PRIMARYTYPE.toString(), new DocViewProperty(NameConstants.JCR_PRIMARYTYPE.toString(),
                new String[] { JcrConstants.NT_FILE }, false, PropertyType.STRING));
        DocViewNode node = new DocViewNode("jcr:root", "jcr:root", null, props, null, JcrConstants.NT_FILE);
        Assert.assertThat(validator.validate(node, nodeContext, false), AnyValidationMessageMatcher.noValidationInCollection());

        ValidationExecutorTest.assertViolation(validator.validateEnd(node, nodeContext, false),
                new ValidationMessage(ValidationMessageSeverity.ERROR,
                        String.format(NodeTypeValidator.MESSAGE_MANDATORY_CHILD_NODE_MISSING,
                                "jcr:content [nt:base]")));
    }

    @Test
    public void testNotAllowedProperty() {
        NodeContext nodeContext = new NodeContextImpl("/apps/test/node4", Paths.get("node4"), Paths.get(""));

        Map<String, DocViewProperty> props = new HashMap<>();
        DocViewProperty prop = new DocViewProperty("{}invalid-prop", new String[] { "some-value" }, false, PropertyType.STRING);
        props.put("{}invalid-prop", prop);
        props.put(NameConstants.JCR_PRIMARYTYPE.toString(), new DocViewProperty(NameConstants.JCR_PRIMARYTYPE.toString(),
                new String[] { JcrConstants.NT_FILE }, false, PropertyType.STRING));
        // nt:file is only supposed to have jcr:created property
        DocViewNode node = new DocViewNode("jcr:root", "jcr:root", null, props, null, JcrConstants.NT_FILE);
        ValidationExecutorTest.assertViolation(validator.validate(node, nodeContext, false),
                new ValidationMessage(ValidationMessageSeverity.ERROR,
                        String.format(NodeTypeValidator.MESSAGE_PROPERTY_NOT_ALLOWED, prop, JcrConstants.NT_FILE,
                                "No property definition found for name!")));
    }

    @Test(expected = IllegalStateException.class)
    public void testPropertyWitInconvertibleValue() {
        NodeContext nodeContext = new NodeContextImpl("/apps/test/node4", Paths.get("node4"), Paths.get(""));

        Map<String, DocViewProperty> props = new HashMap<>();
        DocViewProperty prop = new DocViewProperty(Property.JCR_CREATED, new String[] { "some-invalid-value" }, true, PropertyType.DATE);
        props.put(Property.JCR_CREATED, prop);
        props.put(NameConstants.JCR_PRIMARYTYPE.toString(), new DocViewProperty(NameConstants.JCR_PRIMARYTYPE.toString(),
                new String[] { JcrConstants.NT_FILE }, false, PropertyType.STRING));
        // nt:file is only supposed to have jcr:created property
        DocViewNode node = new DocViewNode("jcr:root", "jcr:root", null, props, null, JcrConstants.NT_FILE);
        validator.validate(node, nodeContext, false);
    }

    @Test
    public void testUnknownNamespace() {
        NodeContext nodeContext = new NodeContextImpl("/apps/test/node4", Paths.get("node4"), Paths.get(""));

        Map<String, DocViewProperty> props = new HashMap<>();
        DocViewProperty prop = new DocViewProperty("{}invalid-prop", new String[] { "some-value" }, false, PropertyType.STRING);
        props.put("{}invalid-prop", prop);
        props.put(NameConstants.JCR_PRIMARYTYPE.toString(), new DocViewProperty(NameConstants.JCR_PRIMARYTYPE.toString(),
                new String[] { "sling:Folder" }, false, PropertyType.STRING));
        // nt:file is only supposed to have jcr:created property
        DocViewNode node = new DocViewNode("jcr:root", "jcr:root", null, props, null, "sling:Folder");
        ValidationExecutorTest.assertViolation(validator.validate(node, nodeContext, false),
                new ValidationMessage(ValidationMessageSeverity.WARN,
                        String.format(NodeTypeValidator.MESSAGE_UNKNOWN_NODE_TYPE_OR_NAMESPACE,
                                "Invalid primary type sling:Folder: 'sling: is not a registered namespace prefix.'")));
    }

    @Test
    public void testExistenceOfPrimaryNodeTypes() throws IOException, ConfigurationException, RepositoryException, ParseException {
        validator = createValidator(filter, NodeType.NT_UNSTRUCTURED);
        Map<String, DocViewProperty> props = new HashMap<>();
        props.put("{}prop1", new DocViewProperty("{}prop1", new String[] { "value1" }, false, PropertyType.STRING));

        // order node only (no other property)
        DocViewNode node = new DocViewNode("jcr:root", "jcr:root", null, Collections.emptyMap(), null, null);
        Assert.assertThat(validator.validate(node, new NodeContextImpl("/apps/test", Paths.get("/some/path"), Paths.get("")), false),
                AnyValidationMessageMatcher.noValidationInCollection());

        // missing node type but not contained in filter (with properties)
        node = new DocViewNode("jcr:root", "jcr:root", null, props, null, null);
        Assert.assertThat(
                validator.validate(node, new NodeContextImpl("/apps/test2/invalid", Paths.get("/some/path"), Paths.get("")), false),
                AnyValidationMessageMatcher.noValidationInCollection());

        // missing node type and contained in filter (with properties)
        ValidationExecutorTest.assertViolation(
                validator.validate(node, new NodeContextImpl("/apps/test", Paths.get("/some/path"), Paths.get("")), false),
                new ValidationMessage(ValidationMessageSeverity.ERROR,
                        String.format(NodeTypeValidator.MESSAGE_MISSING_PRIMARY_TYPE, "/apps/test")));

        // primary node type set with additional properties
        props.put(NameConstants.JCR_PRIMARYTYPE.toString(), new DocViewProperty(NameConstants.JCR_PRIMARYTYPE.toString(),
                new String[] { "nt:unstructured" }, false, PropertyType.STRING));
        node = new DocViewNode("jcr:root", "jcr:root", null, props, null, "nt:unstructured");
        Assert.assertThat(validator.validate(node, new NodeContextImpl("/apps/test", Paths.get("/some/path"), Paths.get("")), false),
                AnyValidationMessageMatcher.noValidationInCollection());

    }
}
