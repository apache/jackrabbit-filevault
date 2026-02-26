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
package org.apache.jackrabbit.vault.packaging.integration;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.JcrConstants;
import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.jackrabbit.vault.fs.api.ImportMode;
import org.apache.jackrabbit.vault.fs.api.PathFilter;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.fs.filter.DefaultPathFilter;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Integration tests for WorkspaceFilter#isSubtreeFullyOverwritten()
 */
public class IsSubtreeFullyOverwrittenIT extends IntegrationTestBase {

    private static final String TEST_ROOT = "/tmp/isSubtreeFullyOverwritten";

    @Before
    public void setUp() throws Exception {
        super.setUp();
        clean(TEST_ROOT);
    }

    /**
     * Path is outside all filter roots: no covering filter set.
     * Expects false (early exit, no repository traversal).
     */
    @Test
    public void returnsFalseWhenPathNotCoveredByAnyFilter() throws RepositoryException, ConfigurationException {
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        PathFilterSet set = new PathFilterSet("/other/root");
        set.addInclude(new DefaultPathFilter("/other/root(/.*)?"));
        filter.add(set);

        Node root = JcrUtils.getOrCreateByPath(TEST_ROOT + "/content", JcrConstants.NT_UNSTRUCTURED, admin);
        admin.save();

        assertFalse(filter.isSubtreeFullyOverwritten(admin, root.getPath()));
    }

    /**
     * Path is covered by filter but node does not exist in repository.
     * Expects false (nodeExists check).
     */
    @Test
    public void returnsFalseWhenNodeDoesNotExist() throws RepositoryException, ConfigurationException {
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        PathFilterSet set = new PathFilterSet(TEST_ROOT);
        set.addInclude(new DefaultPathFilter(TEST_ROOT + "(/.*)?"));
        filter.add(set);

        assertFalse(filter.isSubtreeFullyOverwritten(admin, TEST_ROOT + "/nonexistent"));
    }

    /**
     * Filter has MERGE_PROPERTIES (not REPLACE). Subtree must not be considered fully overwritten.
     * Expects false (import mode check).
     */
    @Test
    public void returnsFalseWhenImportModeIsNotReplace() throws RepositoryException, ConfigurationException {
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        PathFilterSet set = new PathFilterSet(TEST_ROOT);
        set.addInclude(new DefaultPathFilter(TEST_ROOT + "(/.*)?"));
        set.setImportMode(ImportMode.MERGE_PROPERTIES);
        filter.add(set);

        JcrUtils.getOrCreateByPath(TEST_ROOT + "/node", JcrConstants.NT_UNSTRUCTURED, admin);
        admin.save();

        assertFalse(filter.isSubtreeFullyOverwritten(admin, TEST_ROOT + "/node"));
    }

    /**
     * Path matches global-ignored filter. Must not traverse or consider overwritten.
     * Expects false (global ignored check).
     */
    @Test
    public void returnsFalseWhenPathIsGloballyIgnored() throws RepositoryException, ConfigurationException {
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        PathFilterSet set = new PathFilterSet(TEST_ROOT);
        set.addInclude(new DefaultPathFilter(TEST_ROOT + "(/.*)?"));
        filter.add(set);
        filter.setGlobalIgnored(PathFilter.ALL);

        JcrUtils.getOrCreateByPath(TEST_ROOT + "/node", JcrConstants.NT_UNSTRUCTURED, admin);
        admin.save();

        assertFalse(filter.isSubtreeFullyOverwritten(admin, TEST_ROOT + "/node"));
    }

    /**
     * Parent is included, but a child is excluded by filter. Recursive check finds child not contained.
     * Expects false (contains() fails for excluded descendant).
     */
    @Test
    public void returnsFalseWhenChildNodeIsExcludedByFilter() throws RepositoryException, ConfigurationException {
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        PathFilterSet set = new PathFilterSet(TEST_ROOT);
        set.addInclude(new DefaultPathFilter(TEST_ROOT + "(/.*)?"));
        set.addExclude(new DefaultPathFilter(TEST_ROOT + "/parent/excluded(/.*)?"));
        filter.add(set);

        JcrUtils.getOrCreateByPath(TEST_ROOT + "/parent", JcrConstants.NT_UNSTRUCTURED, admin);
        JcrUtils.getOrCreateByPath(TEST_ROOT + "/parent/excluded", JcrConstants.NT_UNSTRUCTURED, admin);
        admin.save();

        assertFalse(filter.isSubtreeFullyOverwritten(admin, TEST_ROOT + "/parent"));
    }

    /**
     * Subtree exists, REPLACE mode, all nodes and properties included. Recursive traversal succeeds.
     * Expects true (full overwrite allowed).
     */
    @Test
    public void returnsTrueWhenSubtreeExistsAndFullyIncluded() throws RepositoryException, ConfigurationException {
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        PathFilterSet set = new PathFilterSet(TEST_ROOT);
        set.addInclude(new DefaultPathFilter(TEST_ROOT + "(/.*)?"));
        filter.add(set);

        JcrUtils.getOrCreateByPath(TEST_ROOT + "/parent", JcrConstants.NT_UNSTRUCTURED, admin);
        JcrUtils.getOrCreateByPath(TEST_ROOT + "/parent/child", JcrConstants.NT_UNSTRUCTURED, admin);
        admin.save();

        assertTrue(filter.isSubtreeFullyOverwritten(admin, TEST_ROOT + "/parent"));
    }

    /**
     * Single node, no children. All properties (e.g. jcr:primaryType) included. Edge case for recursion.
     * Expects true.
     */
    @Test
    public void returnsTrueWhenLeafNodeHasNoChildren() throws RepositoryException, ConfigurationException {
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        PathFilterSet set = new PathFilterSet(TEST_ROOT);
        set.addInclude(new DefaultPathFilter(TEST_ROOT + "(/.*)?"));
        filter.add(set);

        JcrUtils.getOrCreateByPath(TEST_ROOT + "/leaf", JcrConstants.NT_UNSTRUCTURED, admin);
        admin.save();

        assertTrue(filter.isSubtreeFullyOverwritten(admin, TEST_ROOT + "/leaf"));
    }

    /**
     * Global-ignored filter matches a different path; test path is not ignored. Check proceeds normally.
     * Expects true (global ignored does not apply).
     */
    @Test
    public void returnsTrueWhenGlobalIgnoredDoesNotMatchPath() throws RepositoryException, ConfigurationException {
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        PathFilterSet set = new PathFilterSet(TEST_ROOT);
        set.addInclude(new DefaultPathFilter(TEST_ROOT + "(/.*)?"));
        filter.add(set);
        filter.setGlobalIgnored(new DefaultPathFilter("/other/ignored(/.*)?"));

        JcrUtils.getOrCreateByPath(TEST_ROOT + "/node", JcrConstants.NT_UNSTRUCTURED, admin);
        admin.save();

        assertTrue(filter.isSubtreeFullyOverwritten(admin, TEST_ROOT + "/node"));
    }

    /**
     * Property filter excludes a property on the node. includesProperty() fails during traversal.
     * Expects false (property exclusion prevents full overwrite).
     */
    @Test
    public void returnsFalseWhenPropertyIsExcludedByFilter() throws RepositoryException, ConfigurationException {
        PathFilterSet nodeSet = new PathFilterSet(TEST_ROOT);
        nodeSet.addInclude(new DefaultPathFilter(TEST_ROOT + "(/.*)?"));
        PathFilterSet propSet = new PathFilterSet(TEST_ROOT);
        propSet.addInclude(new DefaultPathFilter(TEST_ROOT + "(/.*)?"));
        propSet.addExclude(new DefaultPathFilter(".*/customProp"));
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        filter.add(nodeSet, propSet);

        Node node = JcrUtils.getOrCreateByPath(TEST_ROOT + "/withProp", JcrConstants.NT_UNSTRUCTURED, admin);
        node.setProperty("customProp", "value");
        admin.save();

        assertFalse(filter.isSubtreeFullyOverwritten(admin, TEST_ROOT + "/withProp"));
    }

    /**
     * JCRVLT-830: Repo has a parent (e.g. content/mysite/en) and a child (page) that is excluded by the filter.
     * When importing a package that does not contain that child, the importer may only remove it if the subtree
     * is fully overwritten. Here the child is excluded, so the subtree is not fully overwritten.
     * Expects false so the importer keeps the existing child instead of removing it.
     */
    @Test
    public void jcrvlt830ReturnsFalseWhenExistingChildInRepoIsExcludedByFilter()
            throws RepositoryException, ConfigurationException {
        String contentRoot = TEST_ROOT + "/content/mysite";
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        PathFilterSet set = new PathFilterSet(contentRoot);
        set.addInclude(new DefaultPathFilter(contentRoot + "(/.*)?"));
        set.addExclude(new DefaultPathFilter(contentRoot + "/en/page(/.*)?"));
        filter.add(set);

        JcrUtils.getOrCreateByPath(contentRoot + "/en", JcrConstants.NT_UNSTRUCTURED, admin);
        JcrUtils.getOrCreateByPath(contentRoot + "/en/page", JcrConstants.NT_UNSTRUCTURED, admin);
        admin.save();

        assertFalse(filter.isSubtreeFullyOverwritten(admin, contentRoot + "/en"));
    }
}
