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

import javax.jcr.Property;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;

import org.apache.jackrabbit.JcrConstants;
import org.apache.jackrabbit.commons.cnd.ParseException;
import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.spi.NameFactory;
import org.apache.jackrabbit.spi.commons.name.NameConstants;
import org.apache.jackrabbit.spi.commons.name.NameFactoryImpl;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.util.DocViewNode2;
import org.apache.jackrabbit.vault.util.DocViewProperty2;
import org.apache.jackrabbit.vault.util.StandaloneManagerProvider;
import org.apache.jackrabbit.vault.validation.AnyValidationViolationMessageMatcher;
import org.apache.jackrabbit.vault.validation.ValidationExecutorTest;
import org.apache.jackrabbit.vault.validation.spi.NodeContext;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessage;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessageSeverity;
import org.apache.jackrabbit.vault.validation.spi.util.NodeContextImpl;
import org.apache.jackrabbit.vault.validation.spi.util.classloaderurl.URLFactory;
import org.hamcrest.MatcherAssert;
import org.junit.Before;
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
        StandaloneManagerProvider ntManagerProvider = new StandaloneManagerProvider();
        for (String cndUrl : cndUrls) {
            try (Reader reader =
                    new InputStreamReader(URLFactory.createURL(cndUrl).openStream(), StandardCharsets.US_ASCII)) {
                ntManagerProvider.registerNodeTypes(reader);
            } catch (RepositoryException | IOException | ParseException e) {
                throw new IllegalArgumentException("Error loading node types from CND at " + cndUrl, e);
            }
        }
        return new NodeTypeValidator(
                false,
                filter,
                ntManagerProvider,
                defaultNodeType,
                ValidationMessageSeverity.ERROR,
                ValidationMessageSeverity.WARN,
                ValidationMessageSeverity.WARN);
    }

    @Test
    public void testValidateNotAllowedProperties()
            throws IOException, RepositoryException, ParseException, ConfigurationException {
        NodeContext nodeContext = new NodeContextImpl("/apps/test/node4", Paths.get("node4"), Paths.get(""));
        DocViewProperty2 property =
                new DocViewProperty2(NameFactoryImpl.getInstance().create("{}prop1"), "value");
        DocViewNode2 node = new DocViewNode2(NameConstants.JCR_ROOT, Collections.singleton(property));

        // no primary type
        ValidationExecutorTest.assertViolation(
                validator.validate(node, nodeContext, false),
                new ValidationMessage(
                        ValidationMessageSeverity.ERROR,
                        String.format(NodeTypeValidator.MESSAGE_MISSING_PRIMARY_TYPE, "/apps/test/node4")));

        node = new DocViewNode2(
                NameConstants.JCR_CONTENT,
                Arrays.asList(new DocViewProperty2(NameConstants.JCR_PRIMARYTYPE, JcrConstants.NT_FOLDER), property));

        ValidationExecutorTest.assertViolation(
                validator.validate(node, nodeContext, false),
                new ValidationMessage(
                        ValidationMessageSeverity.ERROR,
                        String.format(
                                JcrNodeTypeMetaDataImpl.MESSAGE_PROPERTY_NOT_ALLOWED,
                                "prop1",
                                "String",
                                "types [nt:folder]",
                                "No applicable property definition found for name and type!"),
                        nodeContext));
    }

    @Test
    public void testUncontainedRootNode() {
        NodeContext nodeContext = new NodeContextImpl("/", Paths.get("jcr_root"), Paths.get(""));

        DocViewNode2 node = new DocViewNode2(
                NameConstants.JCR_CONTENT,
                Arrays.asList(
                        new DocViewProperty2(NameConstants.JCR_PRIMARYTYPE, "rep:root"),
                        new DocViewProperty2(
                                NameConstants.JCR_MIXINTYPES,
                                Arrays.asList("rep:AccessControllable", "rep:RepoAccessControllable"))));
        MatcherAssert.assertThat(
                validator.validate(node, nodeContext, true),
                AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());
        MatcherAssert.assertThat(
                validator.validateEnd(node, nodeContext, true),
                AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());
        MatcherAssert.assertThat(
                validator.done(), AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());
    }

    @Test
    public void testInvalidChildNodeTypeBelowDefault() {
        NodeContext nodeContext = new NodeContextImpl("/apps", Paths.get("apps"), Paths.get(""));
        MatcherAssert.assertThat(
                validator.validateJcrPath(nodeContext, true, false),
                AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());
        nodeContext = new NodeContextImpl("/apps/test", Paths.get("apps", "test"), Paths.get(""));

        // nt:unstructured below nt:folder is not allowed
        DocViewNode2 node = new DocViewNode2(
                NameConstants.JCR_CONTENT,
                Arrays.asList(new DocViewProperty2(NameConstants.JCR_PRIMARYTYPE, JcrConstants.NT_UNSTRUCTURED)));
        MatcherAssert.assertThat(
                validator.validate(node, nodeContext, false),
                AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());
        ValidationExecutorTest.assertViolation(
                validator.done(),
                new ValidationMessage(
                        ValidationMessageSeverity.WARN,
                        String.format(
                                JcrNodeTypeMetaDataImpl.MESSAGE_CHILD_NODE_NOT_ALLOWED,
                                "test",
                                "nt:unstructured",
                                "potential default types [" + JcrConstants.NT_FOLDER + "]",
                                "Node type does not allow arbitrary child nodes and does not allow this specific name and node type either!"),
                        nodeContext));
    }

    // https://issues.apache.org/jira/browse/JCRVLT-527
    @Test
    public void testChildFolderBelowTypeNotAllowingNtFolder() {
        NodeContext nodeContext =
                new NodeContextImpl("/apps/test", Paths.get("apps", "test", ".content.xml"), Paths.get(""));
        DocViewNode2 node = new DocViewNode2(
                NameConstants.JCR_CONTENT,
                Collections.singleton(new DocViewProperty2(NameConstants.JCR_PRIMARYTYPE, "rep:AuthorizableFolder")));

        MatcherAssert.assertThat(
                validator.validate(node, nodeContext, true),
                AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());

        // add child as folder first
        nodeContext = new NodeContextImpl("/apps/test/child", Paths.get("apps", "test", "child"), Paths.get(""));
        MatcherAssert.assertThat(
                validator.validateJcrPath(nodeContext, true, false),
                AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());

        // now refine type via .content.xml
        nodeContext = new NodeContextImpl(
                "/apps/test/child", Paths.get("apps", "test", "child", ".content.xml"), Paths.get(""));

        node = new DocViewNode2(
                NameConstants.JCR_CONTENT,
                Arrays.asList(
                        new DocViewProperty2(NameConstants.JCR_PRIMARYTYPE, "rep:SystemUser"),
                        new DocViewProperty2(NameConstants.REP_PRINCIPAL_NAME, "mySystemUser")));

        MatcherAssert.assertThat(
                validator.validate(node, nodeContext, true),
                AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());
        MatcherAssert.assertThat(
                validator.done(), AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());
    }

    @Test
    public void testMissingMandatoryProperty() {
        // now refine type via .content.xml
        NodeContext nodeContext = new NodeContextImpl(
                "/apps/test/child", Paths.get("apps", "test", "child", ".content.xml"), Paths.get(""));
        DocViewNode2 node = new DocViewNode2(
                NameConstants.JCR_ROOT,
                Collections.singleton(new DocViewProperty2(NameConstants.JCR_PRIMARYTYPE, "rep:SystemUser")));

        MatcherAssert.assertThat(
                validator.validate(node, nodeContext, true),
                AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());

        ValidationExecutorTest.assertViolation(
                validator.done(),
                new ValidationMessage(
                        ValidationMessageSeverity.ERROR,
                        String.format(
                                JcrNodeTypeMetaDataImpl.MESSAGE_MANDATORY_PROPERTY_MISSING,
                                "rep:principalName",
                                "types [rep:SystemUser]",
                                nodeContext.getNodePath()),
                        nodeContext));
    }

    @Test
    public void testMissingMandatoryChildNode() {
        NodeContext nodeContext = new NodeContextImpl("/apps/test/node4", Paths.get("node4"), Paths.get(""));

        DocViewNode2 node = new DocViewNode2(
                NameConstants.JCR_ROOT,
                Collections.singleton(new DocViewProperty2(NameConstants.JCR_PRIMARYTYPE, JcrConstants.NT_FILE)));
        MatcherAssert.assertThat(
                validator.validate(node, nodeContext, false),
                AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());

        ValidationExecutorTest.assertViolation(
                validator.done(),
                new ValidationMessage(
                        ValidationMessageSeverity.ERROR,
                        String.format(
                                JcrNodeTypeMetaDataImpl.MESSAGE_MANDATORY_CHILD_NODE_MISSING,
                                "jcr:content [nt:base]",
                                "types [nt:file]",
                                "/apps/test/node4"),
                        nodeContext));
        MatcherAssert.assertThat(
                validator.done(), AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());
    }

    @Test
    public void testNotAllowedProperty() {
        NodeContext nodeContext = new NodeContextImpl("/apps/test/node4", Paths.get("node4"), Paths.get(""));

        DocViewNode2 node = new DocViewNode2(
                NameConstants.JCR_CONTENT,
                Arrays.asList(
                        new DocViewProperty2(NameConstants.JCR_PRIMARYTYPE, JcrConstants.NT_FILE),
                        new DocViewProperty2(NameFactoryImpl.getInstance().create("{}invalid-prop"), "some-value")));

        // nt:file is only supposed to have jcr:created property
        ValidationExecutorTest.assertViolation(
                validator.validate(node, nodeContext, false),
                new ValidationMessage(
                        ValidationMessageSeverity.ERROR,
                        String.format(
                                JcrNodeTypeMetaDataImpl.MESSAGE_PROPERTY_NOT_ALLOWED,
                                "invalid-prop",
                                "String",
                                "types [" + JcrConstants.NT_FILE + "]",
                                "No applicable property definition found for name and type!"),
                        nodeContext));
    }

    @Test
    public void testPropertyWithInconvertibleValue() {
        NodeContext nodeContext = new NodeContextImpl("/apps/test/node4", Paths.get("node4"), Paths.get(""));

        DocViewNode2 node = new DocViewNode2(
                NameConstants.JCR_CONTENT,
                Arrays.asList(
                        new DocViewProperty2(NameConstants.JCR_PRIMARYTYPE, JcrConstants.NT_FILE),
                        new DocViewProperty2(NameConstants.JCR_CREATED, "some-invalid-value", PropertyType.DATE)));
        ValidationExecutorTest.assertViolation(
                validator.validate(node, nodeContext, false),
                new ValidationMessage(
                        ValidationMessageSeverity.ERROR,
                        String.format(
                                NodeTypeValidator.MESSAGE_INVALID_PROPERTY_VALUE,
                                Property.JCR_CREATED,
                                "not a valid date format: some-invalid-value")));
    }

    @Test
    public void testUnknownNamespaceInType() {
        NodeContext nodeContext = new NodeContextImpl("/apps/test/node4", Paths.get("node4"), Paths.get(""));

        DocViewNode2 node = new DocViewNode2(
                NameConstants.JCR_ROOT,
                Collections.singleton(new DocViewProperty2(NameConstants.JCR_PRIMARYTYPE, "sling:Folder")));

        // 'sling' prefix of jcr:primaryType value is not registered
        ValidationExecutorTest.assertViolation(
                validator.validate(node, nodeContext, false),
                new ValidationMessage(
                        ValidationMessageSeverity.WARN,
                        String.format(
                                NodeTypeValidator.MESSAGE_UNKNOWN_NODE_TYPE_OR_NAMESPACE,
                                "Invalid primary type 'sling:Folder': sling: is not a registered namespace prefix."),
                        nodeContext));
        MatcherAssert.assertThat(
                validator.done(), AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());
    }

    @Test
    public void testUnknownNamespaceInName() {
        NodeContext nodeContext = new NodeContextImpl("/apps/test/cq:dialog", Paths.get("_cq_dialog"), Paths.get(""));

        DocViewNode2 node = new DocViewNode2(
                NameConstants.JCR_ROOT,
                Collections.singleton(new DocViewProperty2(NameConstants.JCR_PRIMARYTYPE, JcrConstants.NT_FOLDER)));
        ValidationExecutorTest.assertViolation(
                validator.validate(node, nodeContext, false),
                new ValidationMessage(
                        ValidationMessageSeverity.WARN,
                        String.format(
                                NodeTypeValidator.MESSAGE_UNKNOWN_NODE_TYPE_OR_NAMESPACE,
                                "Invalid node name 'cq:dialog': cq: is not a registered namespace prefix."),
                        nodeContext));
        MatcherAssert.assertThat(
                validator.done(), AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());
    }

    @Test
    public void testExistenceOfPrimaryNodeTypes()
            throws IOException, ConfigurationException, RepositoryException, ParseException {
        validator = createValidator(filter, NameConstants.NT_UNSTRUCTURED);

        // order node only (no other property)
        DocViewNode2 node = new DocViewNode2(NameConstants.JCR_ROOT, Collections.emptyList());
        MatcherAssert.assertThat(
                validator.validate(
                        node, new NodeContextImpl("/apps/test", Paths.get("/some/path"), Paths.get("")), false),
                AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());

        // missing node type but not contained in filter (with properties)
        node = new DocViewNode2(
                NameConstants.JCR_ROOT,
                Collections.singleton(new DocViewProperty2(NameConstants.JCR_TITLE, "mytitle")));
        MatcherAssert.assertThat(
                validator.validate(
                        node,
                        new NodeContextImpl("/apps/test2/invalid", Paths.get("/some/path"), Paths.get("")),
                        false),
                AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());

        // missing node type and contained in filter (with properties)
        ValidationExecutorTest.assertViolation(
                validator.validate(
                        node, new NodeContextImpl("/apps/test", Paths.get("/some/path"), Paths.get("")), false),
                new ValidationMessage(
                        ValidationMessageSeverity.ERROR,
                        String.format(NodeTypeValidator.MESSAGE_MISSING_PRIMARY_TYPE, "/apps/test")));

        // primary node type set with additional properties
        node = new DocViewNode2(
                NameConstants.JCR_ROOT,
                Arrays.asList(
                        new DocViewProperty2(NameConstants.JCR_PRIMARYTYPE, JcrConstants.NT_UNSTRUCTURED),
                        new DocViewProperty2(NameConstants.JCR_TITLE, "mytitle")));

        MatcherAssert.assertThat(
                validator.validate(
                        node, new NodeContextImpl("/apps/test", Paths.get("/some/path"), Paths.get("")), false),
                AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());
        MatcherAssert.assertThat(
                validator.done(), AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());
    }

    /**
     * The mixin mix:lastModified defines an autocreated unprotected property jcr:lastModifiedBy.
     * Setting it to any value should not lead to a validation error (JCRVLT-479).
     */
    @Test
    public void testAutoCreatedUnprotectedProperty() {
        NodeContext nodeContext = new NodeContextImpl("/apps/test/node4", Paths.get("node4"), Paths.get(""));

        DocViewNode2 node = new DocViewNode2(
                NameConstants.JCR_CONTENT,
                Arrays.asList(
                        new DocViewProperty2(NameConstants.JCR_PRIMARYTYPE, JcrConstants.NT_UNSTRUCTURED),
                        new DocViewProperty2(NameConstants.JCR_LASTMODIFIEDBY, "some-value")));
        MatcherAssert.assertThat(
                validator.validate(node, nodeContext, false),
                AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());
        MatcherAssert.assertThat(
                validator.done(), AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());
    }

    @Test
    public void testMultiValuePropertyWithEmptyValueArray() {
        NodeContext nodeContext = new NodeContextImpl("/apps/test/node4", Paths.get("node4"), Paths.get(""));

        DocViewNode2 node = new DocViewNode2(
                NameConstants.JCR_CONTENT,
                Arrays.asList(
                        new DocViewProperty2(NameConstants.JCR_PRIMARYTYPE, JcrConstants.NT_UNSTRUCTURED),
                        new DocViewProperty2(
                                NameFactoryImpl.getInstance().create("{}mvProperty"),
                                Collections.emptyList(),
                                PropertyType.STRING)));
        MatcherAssert.assertThat(
                validator.validate(node, nodeContext, false),
                AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());
        MatcherAssert.assertThat(
                validator.done(), AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());
    }

    @Test
    public void testVersioningProperties() throws IOException, RepositoryException, ParseException {
        validator = createValidator(filter, NameConstants.NT_UNSTRUCTURED, "tccl:test-nodetypes.cnd");
        NodeContext nodeContext = new NodeContextImpl("/apps/test/node4", Paths.get("node4"), Paths.get(""));

        DocViewNode2 node = new DocViewNode2(
                NameConstants.JCR_CONTENT,
                Arrays.asList(
                        new DocViewProperty2(NameConstants.JCR_PRIMARYTYPE, "WorkflowModel"),
                        new DocViewProperty2(
                                NameConstants.JCR_MIXINTYPES,
                                Collections.singletonList(NameConstants.MIX_VERSIONABLE.toString())),
                        new DocViewProperty2(NameConstants.JCR_UUID, "41699399-95fd-444d-ab8c-b9f8e614607e"),
                        new DocViewProperty2(NameConstants.JCR_ISCHECKEDOUT, "true", PropertyType.BOOLEAN)));
        MatcherAssert.assertThat(
                validator.validate(node, nodeContext, false),
                AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());
        MatcherAssert.assertThat(
                validator.done(), AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());
    }

    @Test
    public void testAuthorizableNodes() {
        // the folder is detected first
        NodeContext nodeContext =
                new NodeContextImpl("/home/users/system/systemuser", Paths.get("node4"), Paths.get(""));
        MatcherAssert.assertThat(
                validator.validateJcrPath(nodeContext, true, false),
                AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());

        // then the doctype with a different primary type
        DocViewNode2 node = new DocViewNode2(
                NameConstants.JCR_ROOT,
                Arrays.asList(
                        new DocViewProperty2(NameConstants.JCR_PRIMARYTYPE, "rep:SystemUser"),
                        new DocViewProperty2(NameConstants.JCR_UUID, "41699399-95fd-444d-ab8c-b9f8e614607e"),
                        new DocViewProperty2(REP_AUTHORIZABLE_ID, "systemuser"),
                        new DocViewProperty2(REP_PRINCIPAL_NAME, "systemuser")));

        MatcherAssert.assertThat(
                validator.validate(node, nodeContext, true),
                AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());
        MatcherAssert.assertThat(
                validator.done(), AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());
    }

    // JCRVLT-539
    @Test
    public void testFolderAfterDocviewNotOverwritingPrimaryType() {
        NodeContext nodeContext =
                new NodeContextImpl("/apps/test", Paths.get("apps", "test", ".content.xml"), Paths.get(""));
        // first process docview xml with three nodes

        NameFactory nameFactory = NameFactoryImpl.getInstance();
        // 1. "test" (root)
        MatcherAssert.assertThat(
                validator.validateJcrPath(nodeContext, false, true),
                AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());

        DocViewNode2 node = new DocViewNode2(
                NameConstants.JCR_ROOT,
                Collections.singleton(
                        new DocViewProperty2(NameConstants.JCR_PRIMARYTYPE, JcrConstants.NT_UNSTRUCTURED)));
        MatcherAssert.assertThat(
                validator.validate(node, nodeContext, true),
                AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());

        // and 2. "test/child"
        nodeContext = new NodeContextImpl("/apps/test/child", Paths.get("apps", "test", ".content.xml"), Paths.get(""));
        node = new DocViewNode2(
                nameFactory.create("{}child"),
                Collections.singleton(
                        new DocViewProperty2(NameConstants.JCR_PRIMARYTYPE, JcrConstants.NT_UNSTRUCTURED)));
        MatcherAssert.assertThat(
                validator.validateJcrPath(nodeContext, false, true),
                AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());
        MatcherAssert.assertThat(
                validator.validate(node, nodeContext, false),
                AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());

        // and 3. "test/child/grandchild"
        nodeContext = new NodeContextImpl(
                "/apps/test/child/grandchild", Paths.get("apps", "test", ".content.xml"), Paths.get(""));
        node = new DocViewNode2(
                nameFactory.create("{}grandchild"),
                Collections.singleton(
                        new DocViewProperty2(NameConstants.JCR_PRIMARYTYPE, JcrConstants.NT_UNSTRUCTURED)));
        MatcherAssert.assertThat(
                validator.validateJcrPath(nodeContext, false, true),
                AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());
        MatcherAssert.assertThat(
                validator.validate(node, nodeContext, false),
                AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());

        // then process folder "test/child"
        nodeContext = new NodeContextImpl("/apps/test/child", Paths.get("apps", "test", "child"), Paths.get(""));
        MatcherAssert.assertThat(
                validator.validateJcrPath(nodeContext, true, false),
                AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());

        // make sure that grandchild is valid (as parent is nt:unstructured and not nt:folder)
        MatcherAssert.assertThat(
                validator.done(), AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());
    }
}
