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

package org.apache.jackrabbit.vault.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Before;
import org.junit.Test;

public class TreeTest {

    private Tree<String> tree;

    private String[] paths = {
            "/test/a",
            "/test/b",
            "/test/c",
            "/test/d/dd",
    };
    
    @Before
    public void setUp() throws Exception {
        tree = new Tree<String>();
        for (String path: paths) {
            tree.put(path, path);
        }
    }

    @Test
    public void testCommonRootPath() {
        assertEquals("Root Path", "/test", tree.getRootPath());
    }

    @Test
    public void testIteration() {
        int i = 0;
        for (String path: tree.map().keySet()) {
            assertEquals("Entry", paths[i++], path);    
        }
        assertEquals("Too many entries", paths.length, i);
    }

    @Test
    public void testGetNop() {
        assertNull("/test/e should not exist", tree.getNode("/test/e"));
        testTreeOk();
    }

    @Test
    public void testTreeOk() {
        assertEquals("Tree Size", paths.length, tree.map().keySet().size());
        int i=0;
        for (String path: tree.map().keySet()) {
            assertEquals("Entry", paths[i++], path);
        }
    }

    @Test
    public void testSimple() {
        Tree<String> t = new Tree<String>();
        t.put("/content/en/foo", "foo");
        assertEquals("/content/en/foo", t.getRootPath());
    }

}