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

import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.junit.Test;

/**
 * {@code DefaultPathFilterTest}...
 */
public class DefaultPathFilterTest {

    @Test
    public void testExact() throws ConfigurationException {
        test("/foo\\.bar", "/foo.bar", true);
        test("/foo\\.bar", "/foo_bar", false);
    }

    @Test
    public void testFiles() throws ConfigurationException {
        test("/foo/bar\\.[^/]*$", "/foo/bar.txt", true);
        test("/foo/bar\\.[^/]*$", "/foo/bar.zip", true);
        test("/foo/bar\\.[^/]*$", "/foo/bar1.txt", false);
        test("/foo/bar.[^/]*$", "/foo/bar.dir/readme", false);
        test("^.*/bar\\.[^/]*$", "/foo/bar.txt", true);
        test("^.*/bar\\.[^/]*$", "/foo/bar1.txt", false);
        test("^.*/bar\\.[^/]*$", "/foo/bar.dir/readme", false);
        test("^.*/bar\\.[^/]*$", "foobar.txt", false);
    }

    @Test
    public void testDirectChildren() throws ConfigurationException {
        test("/foo/[^/]*$", "/foo/bar", true);
        test("/foo/[^/]*$", "/foo/bar/readme", false);
    }

    @Test
    public void testDeepChildren() throws ConfigurationException {
        test("/foo/.*", "/foo/bar", true);
        test("/foo/.*", "/foo/bar/readme.txt", true);
        test("/foo/.*", "/bar/bar/readme.txt", false);
    }

    @Test
    public void testSelfAndDeepChildren() throws ConfigurationException {
        test("/foo(/.*)?", "/foo", true);
        test("/foo(/.*)?", "/foo/bar/readme.txt", true);
        test("/foo(/.*)?", "/foobar", false);
        test("/foo(/.*)?", "/foobar/foo", false);
    }

    @Test(expected = ConfigurationException.class)
    public void testInvalidPattern() throws ConfigurationException {
        new DefaultPathFilter("[");
    }
    
    private void test(String pattern, String path, boolean result) throws ConfigurationException {
        DefaultPathFilter f = new DefaultPathFilter(pattern);
        assertEquals("Pattern '" + pattern + "' matches '" + path + "'", result, f.matches(path));
    }

}