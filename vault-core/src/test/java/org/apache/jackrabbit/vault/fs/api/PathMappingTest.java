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
package org.apache.jackrabbit.vault.fs.api;

import org.junit.Test;

import static junit.framework.Assert.assertEquals;

/**
 * <code>PathMappingTest</code>...
 */
public class PathMappingTest {

    @Test
    public void testSimple() {
        PathMapping map = new SimplePathMapping("/tmp", "/content");
        assertEquals("/content", map.map("/tmp"));
        assertEquals("/content/products", map.map("/tmp/products"));
        assertEquals("/foo", map.map("/foo"));
    }

    @Test
    public void testSimpleReverse() {
        PathMapping map = new SimplePathMapping("/tmp", "/content");
        assertEquals("/tmp", map.map("/content", true));
        assertEquals("/tmp/products", map.map("/content/products", true));
        assertEquals("/foo", map.map("/foo", true));
    }

    @Test
    public void testMulti() {
        MultiPathMapping map = new MultiPathMapping();
        map.link("/source/tree/a", "/dest/1");
        map.link("/source/tree/b", "/dest/2");
        map.link("/source/foo/a", "/dest/foo/1");
        map.link("/dest/foo/1/top", "/test");

        assertEquals("", map.map(""));
        assertEquals("/", map.map("/"));
        assertEquals("/content", map.map("/content"));
        assertEquals("/dest/1", map.map("/source/tree/a"));
        assertEquals("/dest/1/test", map.map("/source/tree/a/test"));
        assertEquals("/dest/2/test", map.map("/source/tree/b/test"));
        assertEquals("/dest/foo/1/test", map.map("/source/foo/a/test"));
        assertEquals("/test/flop", map.map("/source/foo/a/top/flop"));
   }

    @Test
    public void testMultiReverse() {
        MultiPathMapping map = new MultiPathMapping();
        map.link("/source/tree/a", "/dest/1");
        map.link("/source/tree/b", "/dest/2");
        map.link("/source/foo/a", "/dest/foo/1");
        map.link("/dest/foo/1/top", "/test");

        assertEquals("", map.map(""));
        assertEquals("/", map.map("/"));
        assertEquals("/content", map.map("/content"));
        assertEquals("/source/tree/a", map.map("/dest/1", true));
        assertEquals("/source/tree/b/test", map.map("/dest/2/test", true));
        assertEquals("/source/tree/a/a/b/c/d", map.map("/dest/1/a/b/c/d", true));
        assertEquals("/source/foo/a/test", map.map("/dest/foo/1/test", true));
        assertEquals("/dest/foo/1/top/flop", map.map("/test/flop", true));

    }
}