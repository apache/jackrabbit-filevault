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

package org.apache.jackrabbit.vault.packaging;

import java.io.IOException;
import java.util.List;

import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ScopedFilterTest {

    @Test
    public void testApplicationScoped() throws IOException, ConfigurationException {
        DefaultWorkspaceFilter base = new DefaultWorkspaceFilter();
        base.load(getClass().getResourceAsStream("../fs/filter/workspacefilters/mixed.xml"));

        ScopedWorkspaceFilter filter = ScopedWorkspaceFilter.createApplicationScoped(base);

        List<PathFilterSet> sets = filter.getFilterSets();
        assertEquals("/apps/myproject/foo", sets.get(0).getRoot());
        assertEquals("/libs/jackrabbit", sets.get(1).getRoot());
        assertEquals("/", sets.get(2).getRoot());

        assertTrue(filter.contains("/libs/jackrabbit/bar/test"));
        assertFalse(filter.contains("/bar"));
    }

    @Test
    public void testContentScoped() throws IOException, ConfigurationException {
        DefaultWorkspaceFilter base = new DefaultWorkspaceFilter();
        base.load(getClass().getResourceAsStream("../fs/filter/workspacefilters/mixed.xml"));

        ScopedWorkspaceFilter filter = ScopedWorkspaceFilter.createContentScoped(base);

        List<PathFilterSet> sets = filter.getFilterSets();
        assertEquals("/bar", sets.get(0).getRoot());
        assertEquals("/", sets.get(1).getRoot());

        assertFalse(filter.contains("/libs/jackrabbit/bar/test"));
        assertTrue(filter.contains("/bar"));
    }
}
