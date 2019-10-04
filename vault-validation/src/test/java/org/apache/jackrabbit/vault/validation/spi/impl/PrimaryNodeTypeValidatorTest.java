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
import org.apache.jackrabbit.vault.validation.spi.impl.PrimaryNodeTypeValidator;
import org.junit.Assert;
import org.junit.Test;


public class PrimaryNodeTypeValidatorTest {

    private PrimaryNodeTypeValidator validator;

    @Test
    public void testNodeTypes() throws IOException, ConfigurationException {
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        try (InputStream input = this.getClass().getResourceAsStream("/filter.xml")) {
            filter.load(input);
        }
        validator = new PrimaryNodeTypeValidator(ValidationMessageSeverity.ERROR, filter);
        Map<String, DocViewProperty> props = new HashMap<>();
        props.put("prop1", new DocViewProperty("prop1", new String[] { "value1" } , false, PropertyType.STRING));

        // order node only (no other property)
        DocViewNode node = new DocViewNode("jcr:root", "jcr:root", null, Collections.emptyMap(), null, null);
        Assert.assertThat(validator.validate(node, "/apps/test", null, false), AnyValidationMessageMatcher.noValidationInCollection());

        // primary node type set with additional properties
        node = new DocViewNode("jcr:root", "jcr:root", null, props, null, "nt:unstructured");
        Assert.assertThat(validator.validate(node, "/apps/test", null, false), AnyValidationMessageMatcher.noValidationInCollection());

        // missing node type but not contained in filter (with properties)
        node = new DocViewNode("jcr:root", "jcr:root", null, props, null, null);
        Assert.assertThat(validator.validate(node, "/apps/test2/invalid", null, false), AnyValidationMessageMatcher.noValidationInCollection());

        // missing node type and contained in filter (with properties)
        ValidationExecutorTest.assertViolation(
                        validator.validate(node, "/apps/test", null, false),
                        new ValidationMessage(ValidationMessageSeverity.ERROR, String.format(PrimaryNodeTypeValidator.MESSAGE_MISSING_PRIMARY_TYPE, "/apps/test")));
    }
}
