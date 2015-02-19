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

import junit.framework.TestCase;

/**
 * <code>DefaultPathFilterTest</code>...
 */
public class DefaultPathFilterTest extends TestCase {

    public void testExact() {
        test("/foo\\.bar", "/foo.bar", true);
        test("/foo\\.bar", "/foo_bar", false);
    }

    public void testFiles() {
        test("/foo/bar\\.[^/]*$", "/foo/bar.txt", true);
        test("/foo/bar\\.[^/]*$", "/foo/bar.zip", true);
        test("/foo/bar\\.[^/]*$", "/foo/bar1.txt", false);
        test("/foo/bar.[^/]*$", "/foo/bar.dir/readme", false);
        test("^.*/bar\\.[^/]*$", "/foo/bar.txt", true);
        test("^.*/bar\\.[^/]*$", "/foo/bar1.txt", false);
        test("^.*/bar\\.[^/]*$", "/foo/bar.dir/readme", false);
        test("^.*/bar\\.[^/]*$", "foobar.txt", false);
    }

    public void testDirectChildren() {
        test("/foo/[^/]*$", "/foo/bar", true);
        test("/foo/[^/]*$", "/foo/bar/readme", false);
    }

    public void testDeepChildren() {
        test("/foo/.*", "/foo/bar", true);
        test("/foo/.*", "/foo/bar/readme.txt", true);
        test("/foo/.*", "/bar/bar/readme.txt", false);
    }
    
    public void testSelfAndDeepChildren() {
        test("/foo(/.*)?", "/foo", true);
        test("/foo(/.*)?", "/foo/bar/readme.txt", true);
        test("/foo(/.*)?", "/foobar", false);
        test("/foo(/.*)?", "/foobar/foo", false);
    }

    private void test(String pattern, String path, boolean result) {
        DefaultPathFilter f = new DefaultPathFilter(pattern);
        assertEquals("Pattern '" + pattern + "' matches '" + path + "'", result, f.matches(path));
    }

}