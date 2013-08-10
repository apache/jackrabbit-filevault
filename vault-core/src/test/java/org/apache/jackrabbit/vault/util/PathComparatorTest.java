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

import junit.framework.TestCase;

/**
 * <code>PathComparatorTest</code>...
 *
 */
public class PathComparatorTest extends TestCase {

    public void test() {
        doTest("/a", "/a", 0);
        doTest("/a", "/b", -1);
        doTest("/a1foo", "/a/foo", -1);
        doTest("/a/b/c1foo", "/a/b/c/foo", -1);
    }
    
    private void doTest(String left, String right, int result) {
        PathComparator c = new PathComparator();
        int test = c.compare(left, right);
        assertEquals(left + " <> " + right, result, test);
    }
}