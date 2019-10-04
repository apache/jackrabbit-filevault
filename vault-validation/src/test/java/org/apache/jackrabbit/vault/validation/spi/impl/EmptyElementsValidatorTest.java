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
import org.apache.jackrabbit.vault.validation.spi.impl.EmptyElementsValidator;
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
        Assert.assertThat(validator.validate(node, "/apps/test/node1", Paths.get("node1"), false), AnyValidationMessageMatcher.noValidationInCollection());
        
        // another order node (to be covered by another file)
        node = new DocViewNode("jcr:root", "jcr:root", null, Collections.emptyMap(), null, null);
        Assert.assertThat(validator.validate(node, "/apps/test/node2", Paths.get("node2"), false), AnyValidationMessageMatcher.noValidationInCollection());
        
        // another order node only
        node = new DocViewNode("jcr:root", "jcr:root", null, Collections.emptyMap(), null, null);
        Assert.assertThat(validator.validate(node, "/apps/test/node3", Paths.get("node3"), false), AnyValidationMessageMatcher.noValidationInCollection());
        
        // no order node (due to props)
        node = new DocViewNode("jcr:root", "jcr:root", null, props, null, null);
        Assert.assertThat(validator.validate(node, "/apps/test/node4", Paths.get("node4"), false), AnyValidationMessageMatcher.noValidationInCollection());
        
        // no order node (due to primary type)
        node = new DocViewNode("jcr:root", "jcr:root", null, Collections.emptyMap(), null, "nt:unstructed");
        Assert.assertThat(validator.validate(node, "/apps/test/node5", Paths.get("node45"), false), AnyValidationMessageMatcher.noValidationInCollection());
        //
        Assert.assertFalse(validator.shouldValidateJcrData(Paths.get("apps", "test", "node2")));
        ValidationExecutorTest.assertViolation(validator.done(), new ValidationMessage(ValidationMessageSeverity.ERROR, String.format(EmptyElementsValidator.MESSAGE_EMPTY_NODES, "'/apps/test/node1' (in 'node1'), '/apps/test/node3' (in 'node3')")));
    }

    @Test
    public void testNoEmptyElements() {
        Map<String, DocViewProperty> props = new HashMap<>();
        props.put("prop1", new DocViewProperty("prop1", new String[] { "value1" } , false, PropertyType.STRING));

        // order node only (no other property)
        DocViewNode node = new DocViewNode("jcr:root", "jcr:root", null, Collections.emptyMap(), null, "nt:unstructured");
        Assert.assertThat(validator.validate(node, "somepath1", null, false), AnyValidationMessageMatcher.noValidationInCollection());
        
        // primary node type set with additional properties
        node = new DocViewNode("jcr:root", "jcr:root", null, props, null, "nt:unstructured");
        Assert.assertThat(validator.validate(node, "somepath2", null, false), AnyValidationMessageMatcher.noValidationInCollection());
        Assert.assertNull(validator.done());
    }
}
