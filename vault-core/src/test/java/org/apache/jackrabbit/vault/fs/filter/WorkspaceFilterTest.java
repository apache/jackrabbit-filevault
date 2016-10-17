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
package org.apache.jackrabbit.vault.fs.filter;

import java.io.IOException;
import java.util.List;

import org.apache.jackrabbit.vault.fs.api.FilterSet;
import org.apache.jackrabbit.vault.fs.api.PathFilter;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.api.PathMapping;
import org.apache.jackrabbit.vault.fs.api.SimplePathMapping;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * {@code WorkspaceFilterTest}...
 */
public class WorkspaceFilterTest {

    @Test
    public void testMatching() {
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        PathFilterSet set1 = new PathFilterSet("/foo");
        filter.add(set1);
        PathFilterSet set2 = new PathFilterSet("/tmp");
        set2.addInclude(new DefaultPathFilter("/tmp(/.*)?"));
        set2.addExclude(new DefaultPathFilter("/tmp/foo(/.*)?"));
        filter.add(set2);
        assertTrue(filter.contains("/foo"));
        assertTrue(filter.contains("/foo/bar"));
        assertTrue(filter.contains("/tmp"));
        assertTrue(filter.contains("/tmp/bar"));
        assertFalse(filter.contains("/tmp/foo"));
        assertFalse(filter.contains("/tmp/foo/bar"));
        assertFalse(filter.contains("/"));
        assertFalse(filter.contains("/bar"));

        assertTrue(filter.covers("/foo"));
        assertTrue(filter.covers("/tmp"));
        assertTrue(filter.covers("/tmp/foo"));
    }

    @Test
    public void testMapping1() {
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        PathFilterSet set1 = new PathFilterSet("/tmp/stage/products");
        filter.add(set1);
        PathMapping map = new SimplePathMapping("/tmp/stage", "/content/geometrixx/en");
        WorkspaceFilter mapped = filter.translate(map);
        assertFalse(mapped.contains("/content/geometrixx/en"));
        assertTrue(mapped.contains("/content/geometrixx/en/products"));
    }

    @Test
    public void testMapping2() {
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        PathFilterSet set1 = new PathFilterSet("/tmp/stage");
        set1.addInclude(new DefaultPathFilter("/tmp/stage/products(/.*)?"));
        set1.addExclude(new DefaultPathFilter("/tmp/stage/products/triangle(/.*)?"));
        set1.addExclude(new DefaultPathFilter(".*/foo"));
        filter.add(set1);
        PathMapping map = new SimplePathMapping("/tmp/stage", "/content/geometrixx/en");
        WorkspaceFilter mapped = filter.translate(map);
        assertFalse(mapped.contains("/content/geometrixx/en"));
        assertTrue(mapped.contains("/content/geometrixx/en/products"));
        assertFalse(mapped.contains("/content/geometrixx/en/products/triangle"));
        assertFalse(mapped.contains("/content/geometrixx/en/products/foo"));
    }

    @Test
    public void testRelativePatterns() {
        PathFilterSet set1 = new PathFilterSet("/foo");
        set1.addInclude(new DefaultPathFilter("/foo/.*"));
        set1.addInclude(new DefaultPathFilter("/bar/.*"));
        set1.seal();
        assertFalse(set1.hasOnlyRelativePatterns());

        PathFilterSet set2 = new PathFilterSet("/foo");
        set2.addInclude(new DefaultPathFilter(".*/foo/.*"));
        set2.addInclude(new DefaultPathFilter(".*/bar/.*"));
        set2.seal();
        assertTrue(set2.hasOnlyRelativePatterns());

        PathFilterSet set3 = new PathFilterSet("/foo");
        set3.addInclude(new DefaultPathFilter(".*/foo/.*"));
        set3.addInclude(new DefaultPathFilter("/.*/bar/.*"));
        set3.seal();
        assertFalse(set3.hasOnlyRelativePatterns());
    }

    @Test
    public void testLoadingWorkspaceFilter()
            throws IOException, ConfigurationException {
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        filter.load(getClass().getResourceAsStream("workspacefilters/items.xml"));

        List<PathFilterSet> nodeFilterSets = filter.getFilterSets();
        assertNotNull(nodeFilterSets);
        assertEquals(1, nodeFilterSets.size());
        PathFilterSet nodeFilterSet = nodeFilterSets.get(0);
        assertEquals("/var/foo/bar", nodeFilterSet.getRoot());
        List<FilterSet.Entry<PathFilter>> nodeFilters = nodeFilterSet.getEntries();
        assertEquals(1, nodeFilters.size());
        FilterSet.Entry<PathFilter> nodeFilter = nodeFilters.get(0);
        assertFalse(nodeFilter.isInclude());

        List<PathFilterSet> propertyFilterSets = filter.getPropertyFilterSets();
        assertNotNull(propertyFilterSets);
        assertEquals(1, propertyFilterSets.size());
        PathFilterSet propertyFilterSet = propertyFilterSets.get(0);
        assertEquals("/var/foo/bar", propertyFilterSet.getRoot());
        List<FilterSet.Entry<PathFilter>> propertyFilters = propertyFilterSet.getEntries();
        assertEquals(1, propertyFilters.size());
        FilterSet.Entry<PathFilter> propertyFilter = propertyFilters.get(0);
        assertFalse(propertyFilter.isInclude());

    }
}