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

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import javax.jcr.RepositoryException;

import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.api.ProgressTrackerListener;
import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.util.Text;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static junit.framework.Assert.assertEquals;

/**
 * <code>ImportTests</code>...
 */
public class DumpCoverageTests extends IntegrationTestBase {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(DumpCoverageTests.class);

    public static final String TEST_ROOT = "/testroot";

    public static final String[] ALL_PAGES = {
            TEST_ROOT + "/content",
            TEST_ROOT + "/content/en",
            TEST_ROOT + "/content/en/foo",
            TEST_ROOT + "/content/en/bar",
            TEST_ROOT + "/content/fr",
            TEST_ROOT + "/content/fr/foo"
    };
    public static final String[] LANGUAGE_PAGES = {
            TEST_ROOT + "/content/en",
            TEST_ROOT + "/content/en/foo",
            TEST_ROOT + "/content/en/bar",
            TEST_ROOT + "/content/fr",
            TEST_ROOT + "/content/fr/foo"
    };
    public static String[] ALL_PATHS;
    static {
        ALL_PATHS = new String[ALL_PAGES.length*2];
        for (int i=0; i<ALL_PAGES.length;i++) {
            ALL_PATHS[i*2] = ALL_PAGES[i];
            ALL_PATHS[i*2+1] = ALL_PAGES[i] + "/jcr:content";
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


    private static class Collector implements ProgressTrackerListener {
        private final List<String> paths = new LinkedList<String>();

        public void onMessage(Mode mode, String action, String path) {
            paths.add(path);
        }

        public void onError(Mode mode, String path, Exception e) {
        }
    }

    public static void checkResults(String msg, String[] expected, List<String> result) {
        Arrays.sort(expected);
        Collections.sort(result);
        String left = Text.implode(expected, "\n");
        String right = Text.implode(result.toArray(new String[result.size()]), "\n");
        assertEquals(msg, left, right);
    }
}