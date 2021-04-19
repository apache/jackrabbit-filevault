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
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.jcr.PropertyType;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.PackageInfo;
import org.apache.jackrabbit.vault.packaging.PackageProperties;
import org.apache.jackrabbit.vault.packaging.PackageType;
import org.apache.jackrabbit.vault.packaging.impl.DefaultPackageInfo;
import org.apache.jackrabbit.vault.util.DocViewNode;
import org.apache.jackrabbit.vault.util.DocViewProperty;
import org.apache.jackrabbit.vault.validation.AnyValidationMessageMatcher;
import org.apache.jackrabbit.vault.validation.AnyValidationViolationMatcher;
import org.apache.jackrabbit.vault.validation.ValidationExecutorTest;
import org.apache.jackrabbit.vault.validation.ValidationViolation;
import org.apache.jackrabbit.vault.validation.spi.FilterValidator;
import org.apache.jackrabbit.vault.validation.spi.NodeContext;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessage;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessageSeverity;
import org.apache.jackrabbit.vault.validation.spi.util.NodeContextImpl;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.xml.sax.SAXException;

@RunWith(MockitoJUnitRunner.class)
public class AdvancedFilterValidatorTest {

    private AdvancedFilterValidator validator;

    @Mock
    private FilterValidator filterValidator1;

    @Mock
    private FilterValidator filterValidator2;

    @Mock
    private PackageProperties properties;

    @Mock
    private WorkspaceFilter filter;

    private Collection<PackageInfo> dependenciesMetaInfo;

    private Collection<String> validRoots;

    private DocumentBuilderFactory factory;

    @Before
    public void setUp() throws IOException {
        dependenciesMetaInfo = new LinkedList<>();
        validRoots = new LinkedList<>();
        validRoots.addAll(AdvancedFilterValidatorFactory.DEFAULT_VALID_ROOTS);
        factory = AdvancedFilterValidatorFactory.createFilterXsdAwareDocumentBuilder(Locale.ROOT);
    }

    private NodeContext getStandardNodeContext(String nodePath) {
        return new NodeContextImpl(nodePath, Paths.get("somefile"), Paths.get(""));
    }

    @Test
    public void testValidFilter()
            throws URISyntaxException, IOException, SAXException, ParserConfigurationException, ConfigurationException {
        Mockito.when(filterValidator2.validate(Mockito.any()))
                .thenReturn(Collections.singleton(new ValidationMessage(ValidationMessageSeverity.ERROR, "error1")));
        Mockito.when(filterValidator1.validate(Mockito.any())).thenReturn(null);
        try (InputStream input = this.getClass()
                .getResourceAsStream("/simple-package/META-INF/vault/filter.xml");
                InputStream input2 = this.getClass()
                        .getResourceAsStream("/simple-package/META-INF/vault/filter.xml")) {

            DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
            filter.load(input);

            validator = new AdvancedFilterValidator(
                    factory,
                    ValidationMessageSeverity.WARN,
                    AdvancedFilterValidatorFactory.DEFAULT_SEVERITY_FOR_UNCOVERED_ANCESTOR_NODES,
                    ValidationMessageSeverity.ERROR,
                    ValidationMessageSeverity.ERROR,
                    false,
                    dependenciesMetaInfo,
                    filter, // this is per test
                    validRoots);
            Map<String, FilterValidator> validatorsById = new HashMap<>();
            validatorsById.put("id1", filterValidator1);
            validatorsById.put("id2", filterValidator2);
            validatorsById.put("myself", validator);
            validator.setFilterValidators(validatorsById);

            Collection<ValidationMessage> messages = validator.validateMetaInfData(input2, Paths.get("vault/filter.xml"), Paths.get(""));
            ValidationExecutorTest.assertViolation(messages,
                    new ValidationViolation("id2", ValidationMessageSeverity.ERROR, "error1"));
            // all contained FilterSets are sealed (due to the call of AdvancedFilterValidator.validate(...) -> FilterSet.getEntries())
            sealFilterSet(filter.getFilterSets());
            sealFilterSet(filter.getPropertyFilterSets());
            Mockito.verify(filterValidator1).validate(filter);
            Mockito.verify(filterValidator2).validate(filter);

            // as this is a cleanup filter no orphaned entries should be there
            MatcherAssert.assertThat(validator.done(), AnyValidationMessageMatcher.noValidationInCollection());
        }
    }

    @Test
    public void testAllNodesContained() throws IOException, ConfigurationException {
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        try (InputStream input = this.getClass().getResourceAsStream("/filter.xml")) {
            filter.load(input);
        }
        validator = new AdvancedFilterValidator(
                factory,
                ValidationMessageSeverity.WARN,
                AdvancedFilterValidatorFactory.DEFAULT_SEVERITY_FOR_UNCOVERED_ANCESTOR_NODES,
                ValidationMessageSeverity.ERROR,
                ValidationMessageSeverity.ERROR,
                false,
                dependenciesMetaInfo,
                filter, // this is per test
                validRoots);
        MatcherAssert.assertThat(validator.validateJcrPath(getStandardNodeContext("/apps/test/huhu"), false, false),
                AnyValidationViolationMatcher.noValidationInCollection());
        MatcherAssert.assertThat(validator.validateJcrPath(getStandardNodeContext("/apps/test"), false, false),
                AnyValidationViolationMatcher.noValidationInCollection());
        MatcherAssert.assertThat(validator.validateJcrPath(getStandardNodeContext("/apps/test2/valid"), false, false),
                AnyValidationViolationMatcher.noValidationInCollection());
        MatcherAssert.assertThat(validator.validateJcrPath(getStandardNodeContext("/apps/test3/valid"), false, false),
                AnyValidationViolationMatcher.noValidationInCollection());
        MatcherAssert.assertThat(validator.validateJcrPath(getStandardNodeContext("/apps/test4/test/valid"), false, false),
                AnyValidationViolationMatcher.noValidationInCollection());
    }

    @Test
    public void testUncontainedNodes() throws IOException, ConfigurationException {
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        try (InputStream input = this.getClass().getResourceAsStream("/filter.xml")) {
            filter.load(input);
        }
        validator = new AdvancedFilterValidator(
                factory,
                ValidationMessageSeverity.WARN,
                AdvancedFilterValidatorFactory.DEFAULT_SEVERITY_FOR_UNCOVERED_ANCESTOR_NODES,
                ValidationMessageSeverity.ERROR,
                ValidationMessageSeverity.ERROR,
                false,
                dependenciesMetaInfo,
                filter, // this is per test
                validRoots);

        MatcherAssert.assertThat(validator.validateJcrPath(getStandardNodeContext("/apps"), true, false),
                AnyValidationViolationMatcher.noValidationInCollection());
        ValidationExecutorTest.assertViolation(validator.validateJcrPath(getStandardNodeContext("/apps/notcontained"), false, false),
                new ValidationMessage(ValidationMessageSeverity.WARN,
                        String.format(AdvancedFilterValidator.MESSAGE_NODE_NOT_CONTAINED, "/apps/notcontained")));
        ValidationExecutorTest.assertViolation(validator.validateJcrPath(getStandardNodeContext("/apps/test3/invalid"), false, false),
                new ValidationMessage(ValidationMessageSeverity.WARN,
                        String.format(AdvancedFilterValidator.MESSAGE_NODE_NOT_CONTAINED, "/apps/test3/invalid")));

        // docview nodes root node should be skipped
        Map<String, DocViewProperty> props = new HashMap<>();
        props.put("prop1", new DocViewProperty("prop1", new String[] { "value1" }, false, PropertyType.STRING));

        DocViewNode node = new DocViewNode("jcr:root", "jcr:root", null, props, null, "nt:unstructured");
        MatcherAssert.assertThat(validator.validate(node, getStandardNodeContext("/apps/notcontained"), true),
                AnyValidationViolationMatcher.noValidationInCollection());
        // order nodes should be skipped
        node = new DocViewNode("ordernode", "ordernode", null, Collections.emptyMap(), null, null);
        MatcherAssert.assertThat(validator.validate(node, getStandardNodeContext("/apps/notcontained/ordernode"), false),
                AnyValidationViolationMatcher.noValidationInCollection());
        // regular nodes should not be skipped
        node = new DocViewNode("regularnode", "regularnode", null, props, null, null);

        ValidationExecutorTest.assertViolation(validator.validate(node, getStandardNodeContext("/apps/notcontained/regularnode"), false),
                new ValidationMessage(ValidationMessageSeverity.WARN,
                        String.format(AdvancedFilterValidator.MESSAGE_NODE_NOT_CONTAINED, "/apps/notcontained/regularnode")));
    }

    @Test
    public void testUncoveredAncestorNodesFailure() throws IOException, ConfigurationException {
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        try (InputStream input = this.getClass().getResourceAsStream("/filter.xml")) {
            filter.load(input);
        }
        validator = new AdvancedFilterValidator(
                factory,
                ValidationMessageSeverity.ERROR,
                AdvancedFilterValidatorFactory.DEFAULT_SEVERITY_FOR_UNCOVERED_ANCESTOR_NODES,
                ValidationMessageSeverity.ERROR,
                ValidationMessageSeverity.ERROR,
                false,
                dependenciesMetaInfo,
                filter, // this is per test
                validRoots);

        // default severity INFO
        ValidationExecutorTest.assertViolation(validator.validateJcrPath(getStandardNodeContext("/apps"), false, false),
                ValidationMessageSeverity.INFO,
                new ValidationMessage(ValidationMessageSeverity.INFO,
                        String.format(AdvancedFilterValidator.MESSAGE_ANCESTOR_NODE_NOT_COVERED_BUT_VALID_ROOT, "/apps")));
        ValidationExecutorTest.assertViolation(validator.validateJcrPath(getStandardNodeContext("/apps/test4"), false, false),
                ValidationMessageSeverity.INFO,
                new ValidationMessage(ValidationMessageSeverity.INFO,
                        String.format(AdvancedFilterValidator.MESSAGE_ANCESTOR_NODE_NOT_COVERED, "/apps/test4")));

        // default severity ERROR
        validator = new AdvancedFilterValidator(
                factory,
                ValidationMessageSeverity.ERROR,
                ValidationMessageSeverity.ERROR,
                ValidationMessageSeverity.ERROR,
                ValidationMessageSeverity.ERROR,
                false,
                dependenciesMetaInfo,
                filter, // this is per test
                validRoots);

        ValidationExecutorTest.assertViolation(validator.validateJcrPath(getStandardNodeContext("/apps/test4"), false, false),
                ValidationMessageSeverity.INFO,
                new ValidationMessage(ValidationMessageSeverity.ERROR,
                        String.format(AdvancedFilterValidator.MESSAGE_ANCESTOR_NODE_NOT_COVERED, "/apps/test4")));

        // set valid roots
        validRoots.add("/someroot");
        // default severity ERROR
        validator = new AdvancedFilterValidator(
                factory,
                ValidationMessageSeverity.ERROR,
                ValidationMessageSeverity.WARN,
                ValidationMessageSeverity.ERROR,
                ValidationMessageSeverity.ERROR,
                false,
                dependenciesMetaInfo,
                filter, // this is per test
                Collections.emptyList());
        ValidationExecutorTest.assertViolation(validator.validateJcrPath(getStandardNodeContext("/apps"), false, false),
                ValidationMessageSeverity.WARN,
                new ValidationMessage(ValidationMessageSeverity.WARN,
                        String.format(AdvancedFilterValidator.MESSAGE_ANCESTOR_NODE_NOT_COVERED, "/apps")));
    }

    @Test
    public void testRootNodesInFilterPartlyContainedInDependencies() throws IOException, ConfigurationException {
        DefaultWorkspaceFilter dependencyFilter = new DefaultWorkspaceFilter();
        try (InputStream input = this.getClass().getResourceAsStream("/dependency1-filter.xml")) {
            dependencyFilter.load(input);
        }

        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        try (InputStream input = this.getClass().getResourceAsStream("/filter-with-uncovered-roots.xml")) {
            filter.load(input);
        }
        validRoots.add("/customroot");
        dependenciesMetaInfo
                .add(new DefaultPackageInfo(PackageId.fromString("group:dependency1"), dependencyFilter, PackageType.APPLICATION));
        validator = new AdvancedFilterValidator(
                factory,
                ValidationMessageSeverity.ERROR,
                ValidationMessageSeverity.ERROR,
                ValidationMessageSeverity.ERROR,
                ValidationMessageSeverity.ERROR,
                false,
                dependenciesMetaInfo,
                filter, // this is per test
                validRoots);
        Collection<ValidationMessage> messages = validator.validate(filter);
        ValidationExecutorTest.assertViolation(messages, ValidationMessageSeverity.INFO,
                new ValidationMessage(ValidationMessageSeverity.ERROR,
                        String.format(AdvancedFilterValidator.MESSAGE_FILTER_ROOT_ANCESTOR_UNCOVERED, "/apps/uncovered")),
                new ValidationMessage(ValidationMessageSeverity.ERROR,
                        String.format(AdvancedFilterValidator.MESSAGE_FILTER_ROOT_ANCESTOR_COVERED_BUT_EXCLUDED, "/apps/covered2/excluded",
                                "group:dependency1")),
                new ValidationMessage(ValidationMessageSeverity.ERROR,
                        String.format(AdvancedFilterValidator.MESSAGE_FILTER_ROOT_ANCESTOR_UNCOVERED, "/invalidroot")));
    }

    @Test
    public void testOrphanedFilterEntries() throws IOException, ConfigurationException {
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        try (InputStream input = this.getClass().getResourceAsStream("/filter.xml")) {
            filter.load(input);
        }
        validator = new AdvancedFilterValidator(
                factory,
                ValidationMessageSeverity.INFO,
                AdvancedFilterValidatorFactory.DEFAULT_SEVERITY_FOR_UNCOVERED_ANCESTOR_NODES,
                ValidationMessageSeverity.ERROR,
                ValidationMessageSeverity.ERROR,
                false,
                dependenciesMetaInfo,
                filter, // this is per test
                validRoots);
        Collection<ValidationMessage> messages = validator.validate(filter);

        messages = validator.validateJcrPath(getStandardNodeContext("/apps/test3"), false, false);
        ValidationExecutorTest.assertViolation(messages, ValidationMessageSeverity.INFO,
                new ValidationMessage(ValidationMessageSeverity.INFO,
                        String.format(AdvancedFilterValidator.MESSAGE_NODE_BELOW_CLEANUP_FILTER, "/apps/test3")));
        MatcherAssert.assertThat(validator.validateJcrPath(getStandardNodeContext("/apps/test2/something/anothervalid"), false, false),
                AnyValidationMessageMatcher.noValidationInCollection());
        messages = validator.done();
        ValidationExecutorTest.assertViolation(messages, ValidationMessageSeverity.INFO,
                new ValidationMessage(ValidationMessageSeverity.ERROR,
                        String.format(AdvancedFilterValidator.MESSAGE_ORPHANED_FILTER_ENTRIES,
                                "entry with root '/apps/test', includes [regex: .*/valid] below root '/apps/test2', entry with root '/apps/test4/test'")));
    }

    @Test
    public void testUncoveredRootNodesInFilter() throws IOException, ConfigurationException {
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        try (InputStream input = this.getClass().getResourceAsStream("/filter.xml")) {
            filter.load(input);
        }
        validator = new AdvancedFilterValidator(
                factory,
                ValidationMessageSeverity.INFO,
                AdvancedFilterValidatorFactory.DEFAULT_SEVERITY_FOR_UNCOVERED_ANCESTOR_NODES,
                ValidationMessageSeverity.INFO,
                ValidationMessageSeverity.ERROR,
                false,
                dependenciesMetaInfo,
                filter, // this is per test
                validRoots);
        Collection<ValidationMessage> messages = validator.validate(filter);
        ValidationExecutorTest.assertViolation(messages, ValidationMessageSeverity.INFO,
                new ValidationMessage(ValidationMessageSeverity.INFO,
                        String.format(AdvancedFilterValidator.MESSAGE_FILTER_ROOT_ANCESTOR_UNCOVERED, "/apps/test4")));
    }

    @Test
    public void testFilterWithInvalidElements()
            throws URISyntaxException, IOException, SAXException, ParserConfigurationException, ConfigurationException {
        try (InputStream input = this.getClass()
                .getResourceAsStream("/invalid-package/META-INF/vault/filter.xml")) {
            validator = new AdvancedFilterValidator(
                    factory,
                    ValidationMessageSeverity.WARN,
                    AdvancedFilterValidatorFactory.DEFAULT_SEVERITY_FOR_UNCOVERED_ANCESTOR_NODES,
                    ValidationMessageSeverity.ERROR,
                    ValidationMessageSeverity.ERROR,
                    false,
                    dependenciesMetaInfo,
                    filter,
                    validRoots);
            Collection<ValidationMessage> messages = validator.validateMetaInfData(input, Paths.get("vault/filter.xml"), Paths.get(""));
            ValidationExecutorTest.assertViolation(messages,
                    new ValidationMessage(ValidationMessageSeverity.WARN,
                            "cvc-enumeration-valid: Value 'invalid' is not facet-valid with respect to enumeration '[replace, merge, update]'. It must be a value from the enumeration.", 19, 49, null),
                    new ValidationMessage(ValidationMessageSeverity.WARN,
                        "cvc-attribute.3: The value 'invalid' of attribute 'mode' on element 'filter' is not valid with respect to its type, 'mode'.", 19, 49, null), // unclear why type is 'null'
                    new ValidationMessage(ValidationMessageSeverity.WARN,
                            "cvc-complex-type.3.2.2: Attribute 'mode' is not allowed to appear in element 'exclude'.", 20, 51, null),
                    new ValidationMessage(ValidationMessageSeverity.WARN,
                            "cvc-complex-type.2.4.a: Invalid content was found starting with element 'invalidelement'. One of '{exclude, include}' is expected.",
                            22, 27, null),
                    new ValidationMessage(ValidationMessageSeverity.WARN,
                            "cvc-complex-type.4: Attribute 'root' must appear on element 'filter'.",
                            24, 13, null),
                    new ValidationMessage(ValidationMessageSeverity.WARN,
                            "cvc-complex-type.3.2.2: Attribute 'root' is not allowed to appear in element 'include'.",
                            25, 27, null),
                    new ValidationMessage(ValidationMessageSeverity.WARN,
                            "cvc-complex-type.4: Attribute 'pattern' must appear on element 'include'.",
                            25, 27, null),
                    new ValidationMessage(ValidationMessageSeverity.WARN, AdvancedFilterValidator.MESSAGE_INVALID_FILTER_XML)); // because
                                                                                                                                // of
                                                                                                                                // invalid
                                                                                                                                // mode value
        }
    }

    @Test
    public void testFilterWithNonMatchingRegex() throws URISyntaxException, IOException, SAXException,
            ParserConfigurationException, ConfigurationException {
        validator = new AdvancedFilterValidator(
                factory,
                ValidationMessageSeverity.WARN,
                AdvancedFilterValidatorFactory.DEFAULT_SEVERITY_FOR_UNCOVERED_ANCESTOR_NODES,
                ValidationMessageSeverity.ERROR,
                ValidationMessageSeverity.ERROR,
                false,
                dependenciesMetaInfo,
                filter, // this is per test
                validRoots);
        validator.setFilterValidators(Collections.singletonMap("filterid", validator));
        try (InputStream input = this.getClass()
                .getResourceAsStream("/invalid-package/META-INF/vault/filter-non-matching-regex.xml")) {
            Collection<ValidationMessage> messages = validator.validateMetaInfData(input, Paths.get("vault/filter-non-matching-regex.xml"),
                    Paths.get(""));
            ValidationExecutorTest.assertViolation(messages,
                    new ValidationViolation("filterid", ValidationMessageSeverity.WARN,
                            String.format(AdvancedFilterValidator.MESSAGE_INVALID_PATTERN,
                                    "/some/other", "/etc/project1")),
                    new ValidationViolation("filterid", ValidationMessageSeverity.WARN,
                            String.format(AdvancedFilterValidator.MESSAGE_ROOT_PATH_NOT_ABSOLUTE, "invalidroot")));
        }
    }

    @Test
    public void testIsRegexValidForRootPath() {
        Assert.assertTrue(AdvancedFilterValidator.isRegexValidForRootPath("/apps/test", "/apps"));
        Assert.assertTrue(AdvancedFilterValidator.isRegexValidForRootPath(".*/somepath", "/apps"));
        Assert.assertTrue(AdvancedFilterValidator.isRegexValidForRootPath("^.*/somepath$", "/apps"));
        Assert.assertFalse(AdvancedFilterValidator.isRegexValidForRootPath("/[aps]{4}", "/apps/test"));

        Assert.assertFalse(AdvancedFilterValidator.isRegexValidForRootPath("/apps", "/apps/test"));
        Assert.assertFalse(AdvancedFilterValidator.isRegexValidForRootPath("/apps/test", "/apps2/test"));
        Assert.assertFalse(AdvancedFilterValidator.isRegexValidForRootPath("/[aps]{3}/somepath", "/apps"));
        Assert.assertTrue(AdvancedFilterValidator.isRegexValidForRootPath("/apps/test", "/apps/test"));
    }

    private static void sealFilterSet(List<PathFilterSet> pathFilterSets) {
        for (PathFilterSet pathFilterSet : pathFilterSets) {
            pathFilterSet.seal();
        }
    }

    @Test
    public void testHasDanglingAncestors() throws IOException, ConfigurationException {
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        try (InputStream input = this.getClass().getResourceAsStream("/test-filter.xml")) {
            filter.load(input);
        }
        validator = new AdvancedFilterValidator(
                factory,
                ValidationMessageSeverity.WARN,
                AdvancedFilterValidatorFactory.DEFAULT_SEVERITY_FOR_UNCOVERED_ANCESTOR_NODES,
                ValidationMessageSeverity.ERROR,
                ValidationMessageSeverity.ERROR,
                false,
                dependenciesMetaInfo,
                filter, // this is per test
                validRoots);
        Assert.assertNull(validator.getDanglingAncestorNodePath("/var/acs-commons/on-deploy-scripts-status/README.txt", filter));
        Assert.assertEquals("/var/acs-commons/mcp", validator.getDanglingAncestorNodePath("/var/acs-commons/mcp/rep:policy", filter));
        // https://issues.apache.org/jira/browse/JCRVLT-378
        Assert.assertNull(validator.getDanglingAncestorNodePath("/var/acs-commons/on-deploy-scripts-status/rep:policy", filter));
        // make sure it is returned only once
        Assert.assertNull(validator.getDanglingAncestorNodePath("/var/acs-commons/mcp/rep:policy/allow", filter));
    }
}
