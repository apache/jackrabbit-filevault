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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.jcr.Property;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.JcrConstants;
import org.apache.jackrabbit.commons.cnd.ParseException;
import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.spi.commons.name.NameConstants;
import org.apache.jackrabbit.spi.commons.name.NameFactoryImpl;
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
import org.apache.jackrabbit.vault.validation.spi.util.classloaderurl.URLFactory;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class NodeTypeValidatorTest {

    private NodeTypeValidator validator;
    private DefaultWorkspaceFilter filter;

    static final Name REP_AUTHORIZABLE_ID = NameFactoryImpl.getInstance().create(Name.NS_REP_URI, "authorizableId");
    static final Name REP_PRINCIPAL_NAME = NameFactoryImpl.getInstance().create(Name.NS_REP_URI, "principalName");
    
    @Before
    public void setUp() throws IOException, ConfigurationException, RepositoryException, ParseException {
        filter = new DefaultWorkspaceFilter();
        try (InputStream input = this.getClass().getResourceAsStream("/filter.xml")) {
            filter.load(input);
        }
        validator = createValidator(filter, NameConstants.NT_FOLDER);
    }

    static NodeTypeValidator createValidator(WorkspaceFilter filter, Name defaultNodeType, String... cndUrls)
            throws IOException, RepositoryException, ParseException {
        NodeTypeManagerProvider ntManagerProvider = new NodeTypeManagerProvider();
        for (String cndUrl : cndUrls) {
            try (Reader reader = new InputStreamReader(URLFactory.createURL(cndUrl).openStream(), StandardCharsets.US_ASCII)) {
                ntManagerProvider.registerNodeTypes(reader);
            } catch (RepositoryException | IOException | ParseException e) {
                throw new IllegalArgumentException("Error loading node types from CND at " + cndUrl, e);
            }
        }
        return new NodeTypeValidator(filter, ntManagerProvider, defaultNodeType, ValidationMessageSeverity.ERROR,
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
                        String.format(JcrNodeTypeMetaDataImpl.MESSAGE_PROPERTY_NOT_ALLOWED, property, "nt:folder",
                                "No property definition found for name!")));
    }

    @Test
    public void testUncontainedRootNode() {
        NodeContext nodeContext = new NodeContextImpl("/", Paths.get("jcr_root"), Paths.get(""));

        Map<String, DocViewProperty> props = new HashMap<>();
        props.put(NameConstants.JCR_PRIMARYTYPE.toString(), 
                new DocViewProperty(NameConstants.JCR_PRIMARYTYPE.toString(),
                new String[] { "rep:root" }, false, PropertyType.STRING));
        props.put(NameConstants.JCR_MIXINTYPES.toString(),
                new DocViewProperty(NameConstants.JCR_MIXINTYPES.toString(),
                new String[] { "rep:AccessControllable", "rep:RepoAccessControllable" }, true, PropertyType.STRING));
        DocViewNode node = new DocViewNode("jcr:root", "jcr:root", null, props, null, "rep:root");
        
        MatcherAssert.assertThat(validator.validate(node, nodeContext, true), AnyValidationMessageMatcher.noValidationInCollection());
        MatcherAssert.assertThat(validator.validateEnd(node, nodeContext, true), AnyValidationMessageMatcher.noValidationInCollection());
        MatcherAssert.assertThat(validator.done(), AnyValidationMessageMatcher.noValidationInCollection());
    }

    @Test
    public void testInvalidChildNodeTypeBelowDefault() {
        NodeContext nodeContext = new NodeContextImpl("/apps", Paths.get("apps"), Paths.get(""));
        MatcherAssert.assertThat(validator.validateJcrPath(nodeContext, true, false), AnyValidationMessageMatcher.noValidationInCollection());
        nodeContext = new NodeContextImpl("/apps/test", Paths.get("apps", "test"), Paths.get(""));

        Map<String, DocViewProperty> props = new HashMap<>();
        props.put(NameConstants.JCR_PRIMARYTYPE.toString(), new DocViewProperty(NameConstants.JCR_PRIMARYTYPE.toString(),
                new String[] { JcrConstants.NT_UNSTRUCTURED }, false, PropertyType.STRING));
        
        // nt:unstructured below nt:folder is not allowed
        DocViewNode node = new DocViewNode("jcr:root", "jcr:root", null, props, null, JcrConstants.NT_UNSTRUCTURED);
        ValidationExecutorTest.assertViolation(validator.validate(node, nodeContext, false),
                new ValidationMessage(ValidationMessageSeverity.ERROR,
                        String.format(JcrNodeTypeMetaDataImpl.MESSAGE_CHILD_NODE_OF_NOT_CONTAINED_PARENT_POTENTIALLY_NOT_ALLOWED,
                                "test", "nt:unstructured", JcrConstants.NT_FOLDER,
                                "Node type does not allow arbitrary child nodes and does not allow this specific name and node type either!"), nodeContext));
        MatcherAssert.assertThat(validator.done(), AnyValidationMessageMatcher.noValidationInCollection());
    }

    @Test
    public void testMissingMandatoryChildNode() {
        NodeContext nodeContext = new NodeContextImpl("/apps/test/node4", Paths.get("node4"), Paths.get(""));

        Map<String, DocViewProperty> props = new HashMap<>();
        props.put(NameConstants.JCR_PRIMARYTYPE.toString(), new DocViewProperty(NameConstants.JCR_PRIMARYTYPE.toString(),
                new String[] { JcrConstants.NT_FILE }, false, PropertyType.STRING));
        DocViewNode node = new DocViewNode("jcr:root", "jcr:root", null, props, null, JcrConstants.NT_FILE);
        MatcherAssert.assertThat(validator.validate(node, nodeContext, false), AnyValidationMessageMatcher.noValidationInCollection());

        ValidationExecutorTest.assertViolation(validator.done(),
                new ValidationMessage(ValidationMessageSeverity.ERROR,
                        String.format(JcrNodeTypeMetaDataImpl.MESSAGE_MANDATORY_CHILD_NODE_MISSING,
                                "jcr:content [nt:base]", "nt:file", "/apps/test/node4")));
        MatcherAssert.assertThat(validator.done(), AnyValidationMessageMatcher.noValidationInCollection());
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
                        String.format(JcrNodeTypeMetaDataImpl.MESSAGE_PROPERTY_NOT_ALLOWED, "invalid-prop", "String", JcrConstants.NT_FILE,
                                "No applicable property definition found for name and type!"), nodeContext));
    }

    @Test
    public void testPropertyWitInconvertibleValue() {
        NodeContext nodeContext = new NodeContextImpl("/apps/test/node4", Paths.get("node4"), Paths.get(""));

        Map<String, DocViewProperty> props = new HashMap<>();
        DocViewProperty prop = new DocViewProperty(Property.JCR_CREATED, new String[] { "some-invalid-value" }, true, PropertyType.DATE);
        props.put(Property.JCR_CREATED, prop);
        props.put(NameConstants.JCR_PRIMARYTYPE.toString(), new DocViewProperty(NameConstants.JCR_PRIMARYTYPE.toString(),
                new String[] { JcrConstants.NT_FILE }, false, PropertyType.STRING));
        // nt:file is only supposed to have jcr:created property
        DocViewNode node = new DocViewNode("jcr:root", "jcr:root", null, props, null, JcrConstants.NT_FILE);
        ValidationExecutorTest.assertViolation(validator.validate(node, nodeContext, false),
                new ValidationMessage(ValidationMessageSeverity.ERROR,
                        String.format(NodeTypeValidator.MESSAGE_INVALID_PROPERTY_VALUE, Property.JCR_CREATED,
                                "not a valid date format: some-invalid-value")));
    }

    @Test
    public void testUnknownNamespaceInType() {
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
                                "Invalid primary type 'sling:Folder': sling: is not a registered namespace prefix."), nodeContext));
        MatcherAssert.assertThat(validator.done(), AnyValidationMessageMatcher.noValidationInCollection());
    }

    @Test
    public void testUnknownNamespaceInName() {
        NodeContext nodeContext = new NodeContextImpl("/apps/test/cq:dialog", Paths.get("_cq_dialog"), Paths.get(""));
        Map<String, DocViewProperty> props = new HashMap<>();
        props.put(NameConstants.JCR_PRIMARYTYPE.toString(), new DocViewProperty(NameConstants.JCR_PRIMARYTYPE.toString(),
                new String[] { "nt:Folder" }, false, PropertyType.STRING));
        DocViewNode node = new DocViewNode("cq:dialog", "cq:dialog", null, props, null, "nt:folder");
        ValidationExecutorTest.assertViolation(validator.validate(node, nodeContext, false),
                new ValidationMessage(ValidationMessageSeverity.WARN,
                        String.format(NodeTypeValidator.MESSAGE_UNKNOWN_NODE_TYPE_OR_NAMESPACE,
                                "Invalid node name 'cq:dialog': cq: is not a registered namespace prefix."), nodeContext));
        MatcherAssert.assertThat(validator.done(), AnyValidationMessageMatcher.noValidationInCollection());
    }

    
    @Test
    public void testExistenceOfPrimaryNodeTypes() throws IOException, ConfigurationException, RepositoryException, ParseException {
        validator = createValidator(filter, NameConstants.NT_UNSTRUCTURED);
        Map<String, DocViewProperty> props = new HashMap<>();
        props.put("{}prop1", new DocViewProperty("{}prop1", new String[] { "value1" }, false, PropertyType.STRING));

        // order node only (no other property)
        DocViewNode node = new DocViewNode("jcr:root", "jcr:root", null, Collections.emptyMap(), null, null);
        MatcherAssert.assertThat(validator.validate(node, new NodeContextImpl("/apps/test", Paths.get("/some/path"), Paths.get("")), false),
                AnyValidationMessageMatcher.noValidationInCollection());

        // missing node type but not contained in filter (with properties)
        node = new DocViewNode("jcr:root", "jcr:root", null, props, null, null);
        MatcherAssert.assertThat(
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
        MatcherAssert.assertThat(validator.validate(node, new NodeContextImpl("/apps/test", Paths.get("/some/path"), Paths.get("")), false),
                AnyValidationMessageMatcher.noValidationInCollection());
        MatcherAssert.assertThat(validator.done(), AnyValidationMessageMatcher.noValidationInCollection());

    }

    /**
     * The mixin mix:lastModified defines an autocreated unprotected property jcr:lastModifiedBy.
     * Setting it to any value should not lead to a validation error (JCRVLT-479).
     */
    @Test
    public void testAutoCreatedUnprotectedProperty() {
        NodeContext nodeContext = new NodeContextImpl("/apps/test/node4", Paths.get("node4"), Paths.get(""));

        Map<String, DocViewProperty> props = new HashMap<>();
        DocViewProperty prop = new DocViewProperty("jcr:lastModifiedBy", new String[] { "some-value" }, false, PropertyType.STRING);
        props.put("jcr:lastModifiedBy", prop);
        props.put(NameConstants.JCR_PRIMARYTYPE.toString(), new DocViewProperty(NameConstants.JCR_PRIMARYTYPE.toString(),
                new String[] { JcrConstants.NT_UNSTRUCTURED }, false, PropertyType.STRING));
        DocViewNode node = new DocViewNode("jcr:root", "jcr:root", null, props, new String[] { NameConstants.MIX_LASTMODIFIED.toString() }, JcrConstants.NT_UNSTRUCTURED);
        MatcherAssert.assertThat(validator.validate(node, nodeContext, false),
                AnyValidationMessageMatcher.noValidationInCollection());
        MatcherAssert.assertThat(validator.done(), AnyValidationMessageMatcher.noValidationInCollection());
    }

    @Test
    public void testMultiValuePropertyWithEmptyValueArray() {
        NodeContext nodeContext = new NodeContextImpl("/apps/test/node4", Paths.get("node4"), Paths.get(""));

        Map<String, DocViewProperty> props = new HashMap<>();
        DocViewProperty prop = new DocViewProperty("mvProperty", new String[0], true, PropertyType.STRING);
        props.put("mvProperty", prop);
        props.put(NameConstants.JCR_PRIMARYTYPE.toString(), new DocViewProperty(NameConstants.JCR_PRIMARYTYPE.toString(),
                new String[] { JcrConstants.NT_UNSTRUCTURED }, false, PropertyType.STRING));
        DocViewNode node = new DocViewNode("jcr:root", "jcr:root", null, props, null, JcrConstants.NT_UNSTRUCTURED);
        MatcherAssert.assertThat(validator.validate(node, nodeContext, false),
                AnyValidationMessageMatcher.noValidationInCollection());
        MatcherAssert.assertThat(validator.done(), AnyValidationMessageMatcher.noValidationInCollection());
    }

    @Test
    public void testVersioningProperties() throws IOException, RepositoryException, ParseException {
        validator = createValidator(filter, NameConstants.NT_UNSTRUCTURED, "tccl:test-nodetypes.cnd");
        NodeContext nodeContext = new NodeContextImpl("/apps/test/node4", Paths.get("node4"), Paths.get(""));

        Map<String, DocViewProperty> props = new HashMap<>();
        props.put(NameConstants.JCR_PRIMARYTYPE.toString(), new DocViewProperty(NameConstants.JCR_PRIMARYTYPE.toString(),
                new String[] { "WorkflowModel" }, false, PropertyType.STRING));
        props.put(NameConstants.JCR_UUID.toString(), new DocViewProperty(NameConstants.JCR_UUID.toString(),
                new String[] { "41699399-95fd-444d-ab8c-b9f8e614607e" }, false, PropertyType.STRING));
        props.put(NameConstants.JCR_ISCHECKEDOUT.toString(), new DocViewProperty(NameConstants.JCR_ISCHECKEDOUT.toString(),
                new String[] { "true" }, false, PropertyType.BOOLEAN));
        DocViewNode node = new DocViewNode("jcr:root", "jcr:root", null, props, new String[] { NameConstants.MIX_VERSIONABLE.toString() }, "WorkflowModel");
        MatcherAssert.assertThat(validator.validate(node, nodeContext, false),
                AnyValidationMessageMatcher.noValidationInCollection());
        MatcherAssert.assertThat(validator.done(), AnyValidationMessageMatcher.noValidationInCollection());
    }

    @Test
    public void testAuthorizableNodes() {
        // the folder is detected first
        NodeContext nodeContext = new NodeContextImpl("/home/users/system/systemuser", Paths.get("node4"), Paths.get(""));
        MatcherAssert.assertThat(validator.validateJcrPath(nodeContext, true, false),
                AnyValidationMessageMatcher.noValidationInCollection());
        Map<String, DocViewProperty> props = new HashMap<>();
        props.put(NameConstants.JCR_PRIMARYTYPE.toString(), new DocViewProperty(NameConstants.JCR_PRIMARYTYPE.toString(),
                new String[] { "rep:SystemUser" }, false, PropertyType.STRING));
        props.put(NameConstants.JCR_UUID.toString(), new DocViewProperty(NameConstants.JCR_UUID.toString(),
                new String[] { "41699399-95fd-444d-ab8c-b9f8e614607e" }, false, PropertyType.STRING));
        props.put(REP_AUTHORIZABLE_ID.toString(), new DocViewProperty(REP_AUTHORIZABLE_ID.toString(),
                new String[] { "systemuser" }, false, PropertyType.STRING));
        props.put(REP_PRINCIPAL_NAME.toString(), new DocViewProperty(REP_PRINCIPAL_NAME.toString(),
                new String[] { "systemuser" }, false, PropertyType.STRING));
        // then the actual doctype with the user
        DocViewNode node = new DocViewNode("jcr:root", "jcr:root", null, props, new String[] { }, "rep:SystemUser");
        MatcherAssert.assertThat(validator.validate(node, nodeContext, true),
                AnyValidationMessageMatcher.noValidationInCollection());
        MatcherAssert.assertThat(validator.done(), AnyValidationMessageMatcher.noValidationInCollection());
    }
}
