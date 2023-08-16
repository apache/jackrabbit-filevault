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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.fs.filter.DefaultPathFilter;
import org.apache.jackrabbit.vault.validation.AnyValidationViolationMessageMatcher;
import org.apache.jackrabbit.vault.validation.ValidationExecutorTest;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessage;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessageSeverity;
import org.apache.jackrabbit.vault.validation.spi.Validator;
import org.hamcrest.MatcherAssert;
import org.junit.Test;

public class OverlappingFilterValidatorTest {

    @Test
    public void testIsExcluded() throws ConfigurationException {
        PathFilterSet pathFilterSet = new PathFilterSet();
        pathFilterSet.setRoot("/apps/test");
        pathFilterSet.addInclude(new DefaultPathFilter("/apps/test/install/.*"));
        assertFalse(OverlappingFilterValidator.isExcluded("/apps/test/install/.*", pathFilterSet));

        pathFilterSet = new PathFilterSet();
        pathFilterSet.setRoot("/apps/test/install");
        assertFalse(OverlappingFilterValidator.isExcluded("/apps/test/install/.*", pathFilterSet));

        pathFilterSet = new PathFilterSet();
        pathFilterSet.setRoot("/apps/test");
        pathFilterSet.addExclude(new DefaultPathFilter("/apps/test/install/.*"));
        assertTrue(OverlappingFilterValidator.isExcluded("/apps/test/install/.*", pathFilterSet));

        pathFilterSet = new PathFilterSet();
        pathFilterSet.setRoot("/apps/test");
        pathFilterSet.addExclude(new DefaultPathFilter("/apps/test/install"));
        assertTrue(OverlappingFilterValidator.isExcluded("/apps/test/install", pathFilterSet));

        pathFilterSet = new PathFilterSet();
        pathFilterSet.setRoot("/apps/test");
        pathFilterSet.addInclude(new DefaultPathFilter("/apps/test/install"));
        assertFalse(OverlappingFilterValidator.isExcluded("/apps/test/install", pathFilterSet));

        pathFilterSet = new PathFilterSet();
        pathFilterSet.setRoot("/conf/myapp/settings");
        pathFilterSet.addInclude(new DefaultPathFilter("/conf/myapp/settings/wcm"));
        assertTrue(OverlappingFilterValidator.isExcluded("/conf/myapp/settings/cloudconfigs.*", pathFilterSet));
        
        // more complex regex, which is matched as is against the original unescaped pattern
        pathFilterSet = new PathFilterSet();
        pathFilterSet.setRoot("/apps/myapp/config");
        pathFilterSet.addExclude(new DefaultPathFilter("/apps/myapp/config(\\..*)?(/.*)?"));
        assertTrue(OverlappingFilterValidator.isExcluded("/apps/myapp/config.publish/.*", pathFilterSet));

        // more complex regex, which is matched as is against the original unescaped pattern
        pathFilterSet = new PathFilterSet();
        pathFilterSet.setRoot("/apps/myapp/settings/child");
        assertTrue(OverlappingFilterValidator.isExcluded("/apps/myapp/settings/.*", pathFilterSet));
    }

    @Test
    public void testSingleNodeOverlappingFilters() throws IOException, ConfigurationException {
        String rules1 = "<workspaceFilter version=\"1.0\">\n"
                + "    <filter root=\"/apps/test1\">\n"
                + "        <include pattern=\"/apps/test1/child\" />\n"
                + "    </filter>\n"
                + "</workspaceFilter>";
        String rules2 = "<workspaceFilter version=\"1.0\">\n"
                + "    <filter root=\"/apps/test1\">\n"
                + "        <include pattern=\"/apps/test1/child\" />\n"
                + "    </filter>\n"
                + "</workspaceFilter>";
        assertFiltersAreNotOverlapping(rules1, rules2, false); // with default settings the overlapping single nodes are ignored
    }

    @Test
    public void testNonOverlappingPackagesWithoutIncludesOrExcludes() throws IOException, ConfigurationException {
        String rules1 = "<workspaceFilter version=\"1.0\">\n"
                + "    <filter root=\"/apps/test1\">\n"
                + "    </filter>\n"
                + "</workspaceFilter>";
        String rules2 = "<workspaceFilter version=\"1.0\">\n"
                + "    <filter root=\"/apps/test2\">\n"
                + "    </filter>\n"
                + "</workspaceFilter>";
        assertFiltersAreNotOverlapping(rules1, rules2, false);
    }

    @Test
    public void testNonOverlappingPackagesWithIncludes() throws IOException, ConfigurationException {
        String rules1 = "<workspaceFilter version=\"1.0\">\n"
                + "    <filter root=\"/apps/test2\">\n"
                + "        <include pattern=\".*/test1\" />\n"
                + "    </filter>\n"
                + "</workspaceFilter>";
        String rules2 = "<workspaceFilter version=\"1.0\">\n"
                + "    <filter root=\"/apps/test2\">\n"
                + "        <include pattern=\".*/test2\" />\n"
                + "    </filter>\n"
                + "</workspaceFilter>";
        assertFiltersAreNotOverlapping(rules1, rules2, false);
    }

    @Test
    public void testOverlappingPackagesWithSameRootsWithoutIncludesOrExcludes() throws IOException, ConfigurationException {
        String rules1 = "<workspaceFilter version=\"1.0\">\n"
                + "    <filter root=\"/apps/test\">\n"
                + "    </filter>\n"
                + "</workspaceFilter>";
        String rules2 = "<workspaceFilter version=\"1.0\">\n"
                + "    <filter root=\"/apps/test\">\n"
                + "    </filter>\n"
                + "</workspaceFilter>";
        assertFiltersAreOverlapping(rules1, rules2, "/apps/test", "(implicit) ALL", 0, false);
    }

    @Test
    public void testOverlappingPackagesWithDifferentRootsWithoutIncludesOrExcludes2() throws IOException, ConfigurationException {
        String rules1 = "<workspaceFilter version=\"1.0\">\n"
                + "    <filter root=\"/apps/test/install\">\n"
                + "    </filter>\n"
                + "</workspaceFilter>";
        String rules2 = "<workspaceFilter version=\"1.0\">\n"
                + "    <filter root=\"/apps/test\">\n"
                + "    </filter>\n"
                + "</workspaceFilter>";
        assertFiltersAreOverlapping(rules1, rules2, "/apps/test/install", "(implicit) ALL", 0, false);
    }

    @Test
    public void testOverlappingPackagesWithSameRootsWithIncludes() throws IOException, ConfigurationException {
        String rules1 = "<workspaceFilter version=\"1.0\">\n"
                + "    <filter root=\"/apps/test\"/>\n"
                + "    <filter root=\"/apps/test2\">\n"
                + "        <include pattern=\".*/valid/.*\" />\n"
                + "        <include pattern=\".*/anothervalid\" />\n"
                + "    </filter>\n"
                + "</workspaceFilter>";
        String rules2 = "<workspaceFilter version=\"1.0\">\n"
                + "    <filter root=\"/apps/test3\"/>\n"
                + "    <filter root=\"/apps/test2\">\n"
                + "        <include pattern=\".*/valid/.*\" />\n"
                + "        <include pattern=\".*/anothervalid\" />\n"
                + "    </filter>\n"
                + "</workspaceFilter>";
        assertFiltersAreOverlapping(rules1, rules2, "/apps/test2", "'.*/valid/.*'", 1, false);
    }

    @Test
    public void testOverlappingSingleNodes() throws IOException, ConfigurationException {
        String rules1 = "<workspaceFilter version=\"1.0\">\n"
                + "    <filter root=\"/apps\">\n"
                + "        <include pattern=\"/apps/test\" />\n"
                + "        <include pattern=\"/apps/test/child\" />\n"
                + "    </filter>\n"
                + "</workspaceFilter>";
        String rules2 = "<workspaceFilter version=\"1.0\">\n"
                + "    <filter root=\"/apps\">\n"
                + "        <include pattern=\"/apps/test\" />\n"
                + "        <include pattern=\"/apps/test/child2\" />\n"
                + "    </filter>\n"
                + "</workspaceFilter>";
        assertFiltersAreNotOverlapping(rules1, rules2, false);
        assertFiltersAreOverlapping(rules1, rules2, "/apps", "'/apps/test'", 0, true);
    }

    private static void assertFiltersAreOverlapping(String rulesPackage1, String rulesPackage2, String rulesPackage1OverlappingRoot, String rulesPackage1OverlappingPattern, int indexOverlappingRule2, boolean prohibitOverlappingSingleNodePatterns) throws IOException, ConfigurationException {
        Collection<ValidationMessage> messages = validate(rulesPackage1, rulesPackage2, prohibitOverlappingSingleNodePatterns);
        assertOverlappingFilterViolations(messages, rulesPackage1OverlappingRoot, rulesPackage1OverlappingPattern, "package1", rulesPackage2, indexOverlappingRule2, "package2");
    }

    private static void assertFiltersAreNotOverlapping(String rulesPackage1, String rulesPackage2, boolean prohibitOverlappingSingleNodePatterns) throws IOException, ConfigurationException {
        Collection<ValidationMessage> messages = validate(rulesPackage1, rulesPackage2, prohibitOverlappingSingleNodePatterns);
        MatcherAssert.assertThat(messages, AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());
        messages = validate(rulesPackage2, rulesPackage1, false);
        MatcherAssert.assertThat(messages, AnyValidationViolationMessageMatcher.noValidationViolationMessageInCollection());
    }

    private static Collection<ValidationMessage> validate(String rulesPackage1, String rulesPackage2, boolean prohibitOverlappingSingleNodePatterns) throws IOException, ConfigurationException {
        OverlappingFilterValidator validator = new OverlappingFilterValidator(ValidationMessageSeverity.ERROR, prohibitOverlappingSingleNodePatterns ? ValidationMessageSeverity.ERROR : ValidationMessageSeverity.INFO);
        WorkspaceFilter filter1 = createWorkspaceFilter(rulesPackage1);
        WorkspaceFilter filter2 = createWorkspaceFilter(rulesPackage2);
        validator.addFilter(filter1, "package1");
        validator.addFilter(filter2, "package2");
        assertNull(validator.validate(filter1));
        Collection<ValidationMessage> messages = validator.done();
        return messages;
    }

    private static void assertOverlappingFilterViolations(Collection<ValidationMessage> messages, String root, String includePattern, String package1, String ruleOverlappingFilter, int indexOverlappingRule, String package2) throws IOException, ConfigurationException {
        WorkspaceFilter filter = createWorkspaceFilter(ruleOverlappingFilter);
        // which rules overlaps which one (order is important)?
        ValidationExecutorTest.assertViolation(messages,
                new ValidationMessage(ValidationMessageSeverity.ERROR,
                        String.format(OverlappingFilterValidator.MESSAGE_OVERLAPPING_FILTERS, 
                                root, includePattern, package1, 
                                filter.getFilterSets().get(indexOverlappingRule), package2)));
    }

    static DefaultWorkspaceFilter createWorkspaceFilter(String filterRules) throws IOException, ConfigurationException {
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        try ( ByteArrayInputStream input = new ByteArrayInputStream(filterRules.getBytes(StandardCharsets.US_ASCII))) {
            filter.load(input);
        }
        return filter;
    }
}
