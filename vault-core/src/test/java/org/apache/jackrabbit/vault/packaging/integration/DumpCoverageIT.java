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

package org.apache.jackrabbit.vault.packaging.integration;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import javax.jcr.RepositoryException;

import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.jackrabbit.util.Text;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.api.ProgressTrackerListener;
import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.junit.Before;
import org.junit.Test;


/**
 * Tests of coverage dump functionality.
 */
public class DumpCoverageIT extends IntegrationTestBase {

    public static final String TEST_ROOT = "/testroot";

    public static final List<String> ENGLISH_PAGES = Arrays
            .asList(new String[] { TEST_ROOT + "/content/en", TEST_ROOT + "/content/en/foo", TEST_ROOT + "/content/en/bar" });

    public static final List<String> FRENCH_PAGES = Arrays
            .asList(new String[] { TEST_ROOT + "/content/fr", TEST_ROOT + "/content/fr/foo" });

    public static final List<String> LANGUAGE_PAGES;
    static {
        LANGUAGE_PAGES = new ArrayList<>();
        LANGUAGE_PAGES.addAll(ENGLISH_PAGES);
        LANGUAGE_PAGES.addAll(FRENCH_PAGES);
    }

    public static final List<String> ALL_PAGES;
    static {
        ALL_PAGES = new ArrayList<>();
        ALL_PAGES.add(TEST_ROOT + "/content");
        ALL_PAGES.addAll(LANGUAGE_PAGES);
    }

    public static List<String> ALL_PATHS;
    static {
        ALL_PATHS = new ArrayList<>();
        for (String page : ALL_PAGES) {
            ALL_PATHS.add(page);
            ALL_PATHS.add(page + "/jcr:content");
        }
    }

    @Before
    public void init() throws RepositoryException {
        clean(TEST_ROOT);

        JcrUtils.getOrCreateByPath(TEST_ROOT, "nt:folder", admin);
        for (String path: ALL_PAGES) {
            JcrUtils.getOrCreateByPath(path, "nt:folder", admin);
            JcrUtils.getOrCreateByPath(path + "/jcr:content", "nt:folder", admin);
        }
    }

    @Test
    public void testFullCoverage() throws IOException, RepositoryException, ConfigurationException {
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        PathFilterSet set1 = new PathFilterSet(TEST_ROOT + "/content");
        filter.add(set1);
        Collector listener = new Collector();
        filter.dumpCoverage(admin, listener, false);
        checkResults("Full coverage needs to include all paths", ALL_PATHS, listener.paths);
    }

    @Test
    public void testNoJcrContentCoverage() throws IOException, RepositoryException, ConfigurationException {
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        PathFilterSet set1 = new PathFilterSet(TEST_ROOT + "/content");
        filter.add(set1);
        Collector listener = new Collector();
        filter.dumpCoverage(admin, listener, true);
        checkResults("Partial coverage needs to include all pages", ALL_PAGES, listener.paths);
    }


    @Test
    public void testSplitRootsCoverage() throws IOException, RepositoryException, ConfigurationException {
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        PathFilterSet set1 = new PathFilterSet(TEST_ROOT + "/content/en");
        PathFilterSet set2 = new PathFilterSet(TEST_ROOT + "/content/fr");
        filter.add(set1);
        filter.add(set2);
        Collector listener = new Collector();
        filter.dumpCoverage(admin, listener, true);
        checkResults("Split roots", LANGUAGE_PAGES, listener.paths);
    }

    @Test
    public void testNestedRootsCoverage() throws IOException, RepositoryException, ConfigurationException {
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        PathFilterSet set1 = new PathFilterSet(TEST_ROOT + "/content/en/foo");
        PathFilterSet set2 = new PathFilterSet(TEST_ROOT + "/content/fr");
        PathFilterSet set3 = new PathFilterSet(TEST_ROOT + "/content/en");
        filter.add(set1);
        filter.add(set2);
        filter.add(set3);
        Collector listener = new Collector();
        filter.dumpCoverage(admin, listener, true);
        checkResults("nested roots", LANGUAGE_PAGES, listener.paths);
    }

    @Test
    public void testMissingRootsCoverage() throws IOException, RepositoryException, ConfigurationException {
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        PathFilterSet set1 = new PathFilterSet(TEST_ROOT + "/content/f");
        PathFilterSet set2 = new PathFilterSet(TEST_ROOT + "/content/en");
        filter.add(set1);
        filter.add(set2);
        Collector listener = new Collector();
        filter.dumpCoverage(admin, listener, true);
        checkResults("missing roots", ENGLISH_PAGES, listener.paths);
    }

    private static class Collector implements ProgressTrackerListener {
        private final List<String> paths = new LinkedList<String>();

        public void onMessage(Mode mode, String action, String path) {
            paths.add(path);
        }

        public void onError(Mode mode, String path, Exception e) {
        }
    }

    public static void checkResults(String msg, List<String> expected, List<String> result) {
        Collections.sort(expected);
        Collections.sort(result);
        String left = Text.implode(expected.toArray(new String[0]), "\n");
        String right = Text.implode(result.toArray(new String[0]), "\n");
        assertEquals(msg, left, right);
    }
}