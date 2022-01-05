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
import java.util.Collection;
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
import org.junit.Before;
import org.junit.Test;

public class MergeLimitationsValidatorTest {

    private MergeLimitationsValidator validator;

    @Before
    public void setUp() throws IOException, ConfigurationException {
        try (InputStream input = this.getClass().getResourceAsStream("/filter-with-merge.xml"))  {
            DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
            filter.load(input);
            validator = new MergeLimitationsValidator(ValidationMessageSeverity.ERROR, filter);
        }
    }

    @Test
    public void testWithAggregateAtWrongLevel() {
        Map<String, DocViewProperty> props = new HashMap<>();
        props.put("prop1", new DocViewProperty("prop1", new String[] { "value1" } , false, PropertyType.STRING));

        DocViewNode node = new DocViewNode("somename", "somename", null, props, null, "nt:unstructured");
        Collection<ValidationMessage> messages = validator.validate(node, new NodeContextImpl("/apps/test/deep", Paths.get(".content.xml"), Paths.get("")), false);
        ValidationExecutorTest.assertViolation(messages,
                new ValidationMessage(ValidationMessageSeverity.ERROR, String.format(MergeLimitationsValidator.PACKAGE_NON_ROOT_NODE_MERGED, "/apps/test/deep")));
    }

    @Test
    public void testWithAggregateAtCorrectLevel() {
        Map<String, DocViewProperty> props = new HashMap<>();
        props.put("prop1", new DocViewProperty("prop1", new String[] { "value1" } , false, PropertyType.STRING));

        DocViewNode node = new DocViewNode("somename", "somename", null, props, null, "nt:unstructured");
        Collection<ValidationMessage> messages = validator.validate(node, new NodeContextImpl("/apps/test/deep", Paths.get(".content.xml"), Paths.get("")), true);
        MatcherAssert.assertThat(messages, AnyValidationMessageMatcher.noValidationInCollection());
    }

}
