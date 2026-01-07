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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;

import org.apache.jackrabbit.spi.commons.name.NameConstants;
import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.util.DocViewNode2;
import org.apache.jackrabbit.vault.util.DocViewProperty2;
import org.apache.jackrabbit.vault.validation.AnyValidationViolationMessageMatcher;
import org.apache.jackrabbit.vault.validation.ValidationExecutorTest;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessage;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessageSeverity;
import org.apache.jackrabbit.vault.validation.spi.util.NodeContextImpl;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class EmptyElementsValidatorTest {

    private EmptyElementsValidator validator;

    @Before
    public void setUp() throws IOException, ConfigurationException {
        try (InputStream input = this.getClass().getResourceAsStream("/filter.xml")) {
            DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
            filter.load(input);
            validator = new EmptyElementsValidator(ValidationMessageSeverity.ERROR, filter);
        }
    }

    @Test
    public void testWithEmptyElements() {
        // order node only (no other property)
        DocViewNode2 node = new DocViewNode2(NameConstants.JCR_ROOT, Collections.emptySet());
        MatcherAssert.assertThat(
                validator.validate(
                        node, new NodeContextImpl("/apps/test/node1", Paths.get("node1"), Paths.get("")), false),
                AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());

        // another order node (to be covered by another file)
        MatcherAssert.assertThat(
                validator.validate(
                        node, new NodeContextImpl("/apps/test/node2", Paths.get("node2"), Paths.get("")), false),
                AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());

        // another order node
        MatcherAssert.assertThat(
                validator.validate(
                        node, new NodeContextImpl("/apps/test/node3", Paths.get("node3"), Paths.get("")), false),
                AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());

        // no order node (due to jcr:title property)
        node = new DocViewNode2(
                NameConstants.JCR_ROOT, Collections.singleton(new DocViewProperty2(NameConstants.JCR_TITLE, "title")));
        MatcherAssert.assertThat(
                validator.validate(
                        node, new NodeContextImpl("/apps/test/node4", Paths.get("node4"), Paths.get("")), false),
                AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());

        // no order node (due to primary type)
        node = new DocViewNode2(
                NameConstants.JCR_ROOT,
                Collections.singleton(new DocViewProperty2(NameConstants.JCR_PRIMARYTYPE, "nt:unstructured")));
        MatcherAssert.assertThat(
                validator.validate(
                        node, new NodeContextImpl("/apps/test/node5", Paths.get("node5"), Paths.get("")), false),
                AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());

        // overwritten node 2 (plain file/folder)
        Assert.assertNull(validator.validate(
                new NodeContextImpl("/apps/test/node2", Paths.get("apps", "test", "node.xml"), Paths.get("base"))));

        // empty node with name rep:policy (doesn't do any harm and is included in standard packages from exporter as
        // well)
        node = new DocViewNode2(NameConstants.REP_POLICY, Collections.emptySet());
        MatcherAssert.assertThat(
                validator.validate(
                        node, new NodeContextImpl("/apps/test/node6", Paths.get("node6"), Paths.get("")), false),
                AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());

        ValidationExecutorTest.assertViolation(
                validator.done(),
                new ValidationMessage(
                        ValidationMessageSeverity.ERROR,
                        EmptyElementsValidator.MESSAGE_EMPTY_NODES,
                        "/apps/test/node1",
                        Paths.get("node1"),
                        Paths.get(""),
                        null),
                new ValidationMessage(
                        ValidationMessageSeverity.ERROR,
                        EmptyElementsValidator.MESSAGE_EMPTY_NODES,
                        "/apps/test/node3",
                        Paths.get("node3"),
                        Paths.get(""),
                        null));
    }

    @Test
    public void testOnlyNonEmptyElements() {
        // primary node type set as well
        DocViewNode2 node = new DocViewNode2(
                NameConstants.JCR_ROOT,
                Collections.singleton(new DocViewProperty2(NameConstants.JCR_PRIMARYTYPE, "nt:unstructured")));
        MatcherAssert.assertThat(
                validator.validate(
                        node, new NodeContextImpl("somepath1", Paths.get("/some/path"), Paths.get("")), false),
                AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());

        // primary node type set with additional properties
        node = new DocViewNode2(
                NameConstants.JCR_ROOT,
                Arrays.asList(
                        new DocViewProperty2(NameConstants.JCR_PRIMARYTYPE, "nt:unstructured"),
                        new DocViewProperty2(NameConstants.JCR_TITLE, "title")));
        MatcherAssert.assertThat(
                validator.validate(
                        node, new NodeContextImpl("somepath2", Paths.get("/some/path"), Paths.get("")), false),
                AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());
        MatcherAssert.assertThat(
                validator.done(), AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());
    }

    @Test
    public void testWithEmptyElementsAndFolders() {
        // order node only (no other property)
        DocViewNode2 node = new DocViewNode2(NameConstants.JCR_ROOT, Collections.emptySet());
        MatcherAssert.assertThat(
                validator.validate(
                        node, new NodeContextImpl("/apps/test/node1", Paths.get("node1"), Paths.get("")), false),
                AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());

        // folder below
        MatcherAssert.assertThat(
                validator.validate(new NodeContextImpl("/apps/test/node1", Paths.get("test"), Paths.get("base"))),
                AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());
        MatcherAssert.assertThat(
                validator.done(), AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());
        ;
    }
}
