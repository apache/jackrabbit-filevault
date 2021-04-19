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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.jcr.PropertyType;
import javax.xml.parsers.ParserConfigurationException;

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
import org.xml.sax.SAXException;

public class OakIndexDefinitionValidatorTest {

    private OakIndexDefinitionValidator validator;
    private Path rootPackagePath = Paths.get("rootpackage");

    @Before
    public void setUp() throws ParserConfigurationException, SAXException {
        validator = new OakIndexDefinitionValidator(rootPackagePath, ValidationMessageSeverity.ERROR);
    }

    @Test
    public void test_filter() throws Exception {
        try (InputStream input = this.getClass().getResourceAsStream("/oak-index/filter.xml")) {
            DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
            filter.load(input);
            Collection<ValidationMessage> messages = validator.validate(filter);
            ValidationExecutorTest.assertViolation(messages,
                    new ValidationMessage(ValidationMessageSeverity.ERROR,
                            String.format(OakIndexDefinitionValidator.MESSAGE_POTENTIAL_INDEX_IN_FILTER, rootPackagePath,"/oak:index/ccProfile")),
                    new ValidationMessage(ValidationMessageSeverity.ERROR, String
                            .format(OakIndexDefinitionValidator.MESSAGE_POTENTIAL_INDEX_IN_FILTER, rootPackagePath, "/apps/project/oak:index/indexDef")),
                    new ValidationMessage(ValidationMessageSeverity.ERROR, String
                            .format(OakIndexDefinitionValidator.MESSAGE_POTENTIAL_INDEX_IN_FILTER, rootPackagePath, "/apps/anotherproject/oak:index")));
        }
    }

    @Test
    public void test_index_at_root() throws Exception {
        Map<String, DocViewProperty> props = new HashMap<>();
        props.put("includedPaths", new DocViewProperty("includedPaths", new String[] { "/home]" }, true, PropertyType.STRING));
        DocViewNode node = new DocViewNode("testindex", "testindex", null, props, null, "oak:QueryIndexDefinition");

        Collection<ValidationMessage> messages = validator.validate(node, new NodeContextImpl("/oak:index/testindex",
                Paths.get("_oak_index", "testindex", ".content.xml"), Paths.get("")), true);
        ValidationExecutorTest.assertViolation(messages,
                new ValidationMessage(ValidationMessageSeverity.ERROR,
                        String.format(OakIndexDefinitionValidator.MESSAGE_INDEX_AT_NODE, rootPackagePath, "/oak:index/testindex")));
    }

    @Test
    public void test_index_at_deep_path() throws Exception {
        Map<String, DocViewProperty> props = new HashMap<>();
        props.put("includedPaths", new DocViewProperty("includedPaths", new String[] { "/home]" }, true, PropertyType.STRING));
        DocViewNode node = new DocViewNode("indexDef", "indexDef", null, props, null, "oak:QueryIndexDefinition");

        Collection<ValidationMessage> messages = validator.validate(node, new NodeContextImpl("/apps/project/oak:index/indexDef",
                Paths.get("apps", "project", "_oak_index", "content.xml"), Paths.get("")), false);
        ValidationExecutorTest.assertViolation(messages,
                new ValidationMessage(ValidationMessageSeverity.ERROR,
                        String.format(OakIndexDefinitionValidator.MESSAGE_INDEX_AT_NODE, rootPackagePath, "/apps/project/oak:index/indexDef")));

    }
    
    @Test
    public void test_index_acl() throws IOException, ConfigurationException {
        try (InputStream input = this.getClass().getResourceAsStream("/oak-index/filter-with-acl.xml")) {
            DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
            filter.load(input);
            MatcherAssert.assertThat(validator.validate(filter), AnyValidationMessageMatcher.noValidationInCollection());
        }
        Map<String, DocViewProperty> props = new HashMap<>();
        props.put("rep:policy", new DocViewProperty("rep:policy", new String[] { "/home]" }, true, PropertyType.STRING));
        DocViewNode node = new DocViewNode("rep:policy", "rep:policy", null, props, null, "rep:ACL");

        Collection<ValidationMessage> messages = validator.validate(node, new NodeContextImpl("/oak:index/rep:policy",
                Paths.get("_oak_index", "_rep_policy.xml"), Paths.get("")), true);
        MatcherAssert.assertThat(messages, AnyValidationMessageMatcher.noValidationInCollection());
        node = new DocViewNode("allow", "allow", null, props, null, "rep:GrantACE");
        messages = validator.validate(node, new NodeContextImpl("/oak:index/rep:policy/allow",
                Paths.get("_oak_index", "_rep_policy.xml"), Paths.get("")), false);
        MatcherAssert.assertThat(messages, AnyValidationMessageMatcher.noValidationInCollection());
    }
}
