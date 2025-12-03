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

import javax.jcr.PropertyType;
import javax.xml.parsers.ParserConfigurationException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;

import org.apache.jackrabbit.spi.NameFactory;
import org.apache.jackrabbit.spi.commons.name.NameConstants;
import org.apache.jackrabbit.spi.commons.name.NameFactoryImpl;
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
            ValidationExecutorTest.assertViolation(
                    messages,
                    new ValidationMessage(
                            ValidationMessageSeverity.ERROR,
                            String.format(
                                    OakIndexDefinitionValidator.MESSAGE_POTENTIAL_INDEX_IN_FILTER,
                                    rootPackagePath,
                                    "/oak:index/ccProfile")),
                    new ValidationMessage(
                            ValidationMessageSeverity.ERROR,
                            String.format(
                                    OakIndexDefinitionValidator.MESSAGE_POTENTIAL_INDEX_IN_FILTER,
                                    rootPackagePath,
                                    "/apps/project/oak:index/indexDef")),
                    new ValidationMessage(
                            ValidationMessageSeverity.ERROR,
                            String.format(
                                    OakIndexDefinitionValidator.MESSAGE_POTENTIAL_INDEX_IN_FILTER,
                                    rootPackagePath,
                                    "/apps/anotherproject/oak:index")));
        }
    }

    @Test
    public void test_index_at_root() throws Exception {
        NameFactory nameFactory = NameFactoryImpl.getInstance();
        DocViewNode2 node = new DocViewNode2(
                nameFactory.create("{}testindex"),
                Arrays.asList(
                        new DocViewProperty2(NameConstants.JCR_PRIMARYTYPE, "oak:QueryIndexDefinition"),
                        new DocViewProperty2(
                                nameFactory.create("{}includedPaths"), Arrays.asList("/home]"), PropertyType.STRING)));
        Collection<ValidationMessage> messages = validator.validate(
                node,
                new NodeContextImpl(
                        "/oak:index/testindex", Paths.get("_oak_index", "testindex", ".content.xml"), Paths.get("")),
                true);
        ValidationExecutorTest.assertViolation(
                messages,
                new ValidationMessage(
                        ValidationMessageSeverity.ERROR,
                        String.format(
                                OakIndexDefinitionValidator.MESSAGE_INDEX_AT_NODE,
                                rootPackagePath,
                                "/oak:index/testindex")));
    }

    @Test
    public void test_index_at_deep_path() throws Exception {
        NameFactory nameFactory = NameFactoryImpl.getInstance();
        DocViewNode2 node = new DocViewNode2(
                nameFactory.create("{}testindex"),
                Arrays.asList(
                        new DocViewProperty2(NameConstants.JCR_PRIMARYTYPE, "oak:QueryIndexDefinition"),
                        new DocViewProperty2(
                                nameFactory.create("{}includedPaths"), Arrays.asList("/home]"), PropertyType.STRING)));

        Collection<ValidationMessage> messages = validator.validate(
                node,
                new NodeContextImpl(
                        "/apps/project/oak:index/indexDef",
                        Paths.get("apps", "project", "_oak_index", "content.xml"),
                        Paths.get("")),
                false);
        ValidationExecutorTest.assertViolation(
                messages,
                new ValidationMessage(
                        ValidationMessageSeverity.ERROR,
                        String.format(
                                OakIndexDefinitionValidator.MESSAGE_INDEX_AT_NODE,
                                rootPackagePath,
                                "/apps/project/oak:index/indexDef")));
    }

    @Test
    public void test_index_acl() throws IOException, ConfigurationException {
        try (InputStream input = this.getClass().getResourceAsStream("/oak-index/filter-with-acl.xml")) {
            DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
            filter.load(input);
            MatcherAssert.assertThat(
                    validator.validate(filter),
                    AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());
        }
        NameFactory nameFactory = NameFactoryImpl.getInstance();
        DocViewNode2 node = new DocViewNode2(
                nameFactory.create("{}testindex"),
                Arrays.asList(
                        new DocViewProperty2(NameConstants.JCR_PRIMARYTYPE, "rep:ACL"),
                        new DocViewProperty2(NameConstants.REP_POLICY, Arrays.asList("/home]"), PropertyType.STRING)));

        Collection<ValidationMessage> messages = validator.validate(
                node,
                new NodeContextImpl("/oak:index/rep:policy", Paths.get("_oak_index", "_rep_policy.xml"), Paths.get("")),
                true);
        MatcherAssert.assertThat(
                messages, AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());

        node = new DocViewNode2(
                nameFactory.create("{}allow"),
                Arrays.asList(
                        new DocViewProperty2(NameConstants.JCR_PRIMARYTYPE, "rep:GrantACE"),
                        new DocViewProperty2(NameConstants.REP_POLICY, Arrays.asList("/home]"), PropertyType.STRING)));
        messages = validator.validate(
                node,
                new NodeContextImpl(
                        "/oak:index/rep:policy/allow", Paths.get("_oak_index", "_rep_policy.xml"), Paths.get("")),
                false);
        MatcherAssert.assertThat(
                messages, AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());
    }
}
