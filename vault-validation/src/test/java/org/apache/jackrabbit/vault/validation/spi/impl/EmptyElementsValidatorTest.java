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
package org.apache.jackrabbit.vault.validation.spi.impl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.jcr.PropertyType;

import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.util.DocViewNode;
import org.apache.jackrabbit.vault.util.DocViewProperty;
import org.apache.jackrabbit.vault.validation.AnyValidationMessageMatcher;
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
        try (InputStream input = this.getClass().getResourceAsStream("/filter.xml"))  {
            DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
            filter.load(input);
            validator = new EmptyElementsValidator(ValidationMessageSeverity.ERROR, filter);
        }
    }

    @Test
    public void testWithEmptyElements() {
        Map<String, DocViewProperty> props = new HashMap<>();
        props.put("prop1", new DocViewProperty("prop1", new String[] { "value1" } , false, PropertyType.STRING));

        // order node only (no other property)
        DocViewNode node = new DocViewNode("jcr:root", "jcr:root", null, Collections.emptyMap(), null, null);
        MatcherAssert.assertThat(validator.validate(node, new NodeContextImpl("/apps/test/node1", Paths.get("node1"), Paths.get("")), false), AnyValidationMessageMatcher.noValidationInCollection());
        
        // another order node (to be covered by another file)
        node = new DocViewNode("jcr:root", "jcr:root", null, Collections.emptyMap(), null, null);
        MatcherAssert.assertThat(validator.validate(node, new NodeContextImpl("/apps/test/node2", Paths.get("node2"), Paths.get("")), false), AnyValidationMessageMatcher.noValidationInCollection());
        
        // another order node only
        node = new DocViewNode("jcr:root", "jcr:root", null, Collections.emptyMap(), null, null);
        MatcherAssert.assertThat(validator.validate(node, new NodeContextImpl("/apps/test/node3", Paths.get("node3"), Paths.get("")), false), AnyValidationMessageMatcher.noValidationInCollection());
        
        // no order node (due to props)
        node = new DocViewNode("jcr:root", "jcr:root", null, props, null, null);
        MatcherAssert.assertThat(validator.validate(node, new NodeContextImpl("/apps/test/node4", Paths.get("node4"), Paths.get("")), false), AnyValidationMessageMatcher.noValidationInCollection());
        
        // no order node (due to primary type)
        node = new DocViewNode("jcr:root", "jcr:root", null, Collections.emptyMap(), null, "nt:unstructed");
        MatcherAssert.assertThat(validator.validate(node, new NodeContextImpl("/apps/test/node5", Paths.get("node5"), Paths.get("")), false), AnyValidationMessageMatcher.noValidationInCollection());
        
        // overwritten node 2 (plain file/folder)
        Assert.assertNull(validator.validate(new NodeContextImpl("/apps/test/node2", Paths.get("apps", "test", "node.xml"), Paths.get("base"))));
        
        // empty node with name rep:policy (doesn't do any harm and is included in standard packages from exporter as well)
        node = new DocViewNode("rep:policy", "rep:polucy", null, Collections.emptyMap(), null, null);
        MatcherAssert.assertThat(validator.validate(node, new NodeContextImpl("/apps/test/node6", Paths.get("node6"), Paths.get("")), false), AnyValidationMessageMatcher.noValidationInCollection());
        
        ValidationExecutorTest.assertViolation(validator.done(), 
                new ValidationMessage(ValidationMessageSeverity.ERROR, EmptyElementsValidator.MESSAGE_EMPTY_NODES, "/apps/test/node1", Paths.get("node1"), Paths.get(""), null),
                new ValidationMessage(ValidationMessageSeverity.ERROR, EmptyElementsValidator.MESSAGE_EMPTY_NODES, "/apps/test/node3", Paths.get("node3"), Paths.get(""), null));
    }

    @Test
    public void testNoEmptyElements() {
        Map<String, DocViewProperty> props = new HashMap<>();
        props.put("prop1", new DocViewProperty("prop1", new String[] { "value1" } , false, PropertyType.STRING));

        // primary node type set as well
        DocViewNode node = new DocViewNode("jcr:root", "jcr:root", null, Collections.emptyMap(), null, "nt:unstructured");
        MatcherAssert.assertThat(validator.validate(node, new NodeContextImpl("somepath1", Paths.get("/some/path"), Paths.get("")), false), AnyValidationMessageMatcher.noValidationInCollection());
        
        // primary node type set with additional properties
        node = new DocViewNode("jcr:root", "jcr:root", null, props, null, "nt:unstructured");
        MatcherAssert.assertThat(validator.validate(node, new NodeContextImpl("somepath2", Paths.get("/some/path"), Paths.get("")), false), AnyValidationMessageMatcher.noValidationInCollection());
        MatcherAssert.assertThat(validator.done(), AnyValidationMessageMatcher.noValidationInCollection());
    }

    @Test
    public void testWithEmptyElementsAndFolders() {
        Map<String, DocViewProperty> props = new HashMap<>();
        props.put("prop1", new DocViewProperty("prop1", new String[] { "value1" } , false, PropertyType.STRING));

        // order node only (no other property)
        DocViewNode node = new DocViewNode("jcr:root", "jcr:root", null, Collections.emptyMap(), null, null);
        MatcherAssert.assertThat(validator.validate(node, new NodeContextImpl("/apps/test/node1", Paths.get("node1"), Paths.get("")), false), AnyValidationMessageMatcher.noValidationInCollection());
        
        // folder below 
        MatcherAssert.assertThat(validator.validate(new NodeContextImpl("/apps/test/node1", Paths.get("test"), Paths.get("base"))), AnyValidationMessageMatcher.noValidationInCollection());
        MatcherAssert.assertThat(validator.done(), AnyValidationMessageMatcher.noValidationInCollection());;
    }
}
