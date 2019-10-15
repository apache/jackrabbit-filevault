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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.apache.jackrabbit.vault.fs.api.FilterSet;
import org.apache.jackrabbit.vault.fs.api.PathFilter;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.api.PathMapping;
import org.apache.jackrabbit.vault.fs.api.SimplePathMapping;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.tika.io.IOUtils;
import org.junit.Test;

/**
 * {@code WorkspaceFilterTest}...
 */
public class WorkspaceFilterTest {

    @Test
    public void testMatching() throws ConfigurationException {
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
    public void testMapping2() throws ConfigurationException {
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
    public void testRelativePatterns() throws ConfigurationException {
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
        try (InputStream input = getClass().getResourceAsStream("workspacefilters/items.xml")) {
            filter.load(input);
        }
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
        
        // make sure serialization format is kept (including comments)
        try (InputStream input = getClass().getResourceAsStream("workspacefilters/items.xml");
             InputStream actualInput = filter.getSource()) {
            assertEquals(IOUtils.toString(input), IOUtils.toString(actualInput));
        }
    }

    @Test
    public void testToSource() throws IOException, ConfigurationException {

        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        filter.load(getClass().getResourceAsStream("workspacefilters/complex.xml"));
        filter.resetSource();

        String expected = IOUtils.toString(getClass().getResourceAsStream("workspacefilters/complex-expected.xml"));

        assertEquals("Filter source", expected, filter.getSourceAsString());
    }

    @Test
    public void testGeneratedSourceFromCode() throws ConfigurationException  {
        String expected = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<workspaceFilter version=\"1.0\">\n" +
                "    <filter root=\"/tmp\">\n" +
                "        <include pattern=\"/tmp\"/>\n" +
                "    </filter>\n" +
                "</workspaceFilter>\n";

        PathFilterSet props = new PathFilterSet("/tmp");
        PathFilterSet nodes = new PathFilterSet("/tmp");

        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        nodes.addInclude(new DefaultPathFilter("/tmp"));

        filter.add(nodes, props);

        assertEquals(expected, filter.getSourceAsString());

    }

    @Test
    public void testGeneratedSourceFromCodeWithProps() throws ConfigurationException  {
        String expected = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<workspaceFilter version=\"1.0\">\n" +
                "    <filter root=\"/foo\"/>\n" +
                "    <filter root=\"/tmp\">\n" +
                "        <exclude pattern=\"/tmp/foo/p.*\" matchProperties=\"true\"/>\n" +
                "    </filter>\n" +
                "</workspaceFilter>\n";

        PathFilterSet properties = new PathFilterSet("/tmp");
        properties.addExclude(new DefaultPathFilter("/tmp/foo/p.*"));

        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        filter.add(new PathFilterSet("/foo"));
        filter.add(new PathFilterSet("/tmp"), properties);

        assertEquals(expected, filter.getSourceAsString());

    }

    @Test
    public void testEquals() throws IOException, ConfigurationException {
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        try (InputStream input = getClass().getResourceAsStream("workspacefilters/complex.xml")) {
            filter.load(input);
        }
        DefaultWorkspaceFilter filter2 = new DefaultWorkspaceFilter();
        try (InputStream input = getClass().getResourceAsStream("workspacefilters/complex.xml")) {
            filter2.load(input);
        }
        assertEquals(filter, filter2);
        DefaultWorkspaceFilter filter3 = new DefaultWorkspaceFilter();
        try (InputStream input = getClass().getResourceAsStream("workspacefilters/mixed.xml")) {
            filter3.load(input);
        }
        assertNotEquals(filter, filter3);
        // modify filter2 slightly
        filter2.setGlobalIgnored(PathFilter.NONE);
        assertNotEquals(filter, filter2);
    }

    @Test
    public void testModificationLeadsToDifferentSerialization() throws IOException, ConfigurationException {
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        try (InputStream input = getClass().getResourceAsStream("workspacefilters/items.xml")) {
            filter.load(input);
        }
        // now modify the filter
        filter.add(new PathFilterSet("/newroot"));
        String previousSerialization;
        try (InputStream input = getClass().getResourceAsStream("workspacefilters/items.xml")) {
            previousSerialization = IOUtils.toString(input);
        }
        // and check the serialization again
        try (InputStream actualInput = filter.getSource()) {
            String actual = IOUtils.toString(actualInput);
            assertNotEquals(previousSerialization, actual);
            previousSerialization = actual;
        }
        filter.add(new PathFilterSet("/someotherroot"), new PathFilterSet("/someotherroot"));
        // and check the serialization again
        try (InputStream actualInput = filter.getSource()) {
            String actual = IOUtils.toString(actualInput);
            assertNotEquals(previousSerialization, actual);
            previousSerialization = actual;
        }
    }

    @Test(expected=ConfigurationException.class)
    public void testInvalidPattern() throws IOException, ConfigurationException {
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        try (InputStream input = getClass().getResourceAsStream("workspacefilters/invalid-pattern.xml")) {
            filter.load(input);
        }
    }
}