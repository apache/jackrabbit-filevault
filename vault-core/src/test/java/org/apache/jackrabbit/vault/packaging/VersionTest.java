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

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * {@code VersionTest}...
 */
public class VersionTest  {

    @Test
    public void testFromSegments() {
        String[] segs = {"1", "2", "3-SNAPSHOT"};
        Version v = Version.create(segs);
        assertEquals("1.2.3-SNAPSHOT", v.toString());
    }

    @Test
    public void testParse() {
        assertEquals("1", Version.create("1").toString());
        assertEquals("1.0", Version.create("1.0").toString());
        assertEquals("1.0.0", Version.create("1.0.0").toString());
        assertEquals("0.1", Version.create("0.1").toString());
        assertEquals("0.1.0", Version.create("0.1.0").toString());
        assertEquals("0.0.1", Version.create("0.0.1").toString());
        assertEquals("1.2.3.4", Version.create("1.2.3.4").toString());
        assertEquals("1.2.3.4.5", Version.create("1.2.3.4.5").toString());
        assertEquals("6-FP1", Version.create("6-FP1").toString());
        assertEquals("6.3-FP1", Version.create("6.3-FP1").toString());
        assertEquals("6.3.1-FP1", Version.create("6.3.1-FP1").toString());
    }

    @Test
    public void testNormalizedSegments() {
        assertArrayEquals(new String[]{"1"}, Version.create("1").getNormalizedSegments());
        assertArrayEquals(new String[]{"4"}, Version.create("-.4").getNormalizedSegments());
        assertArrayEquals(new String[]{"6","3","FP1"}, Version.create("6.3-FP1").getNormalizedSegments());
    }

    @Test
    public void testCompare() {
        compare("1.0.0", "1.0.0", 0);
        compare("1.0.1", "1.0.0", 1);
        compare("1.1", "1.0.0", 1);
        compare("1.11", "1.9", 1);
        compare("1.1-SNAPSHOT", "1.0.0", 1);
        compare("2.0", "2.0-beta-8", -1);
        compare("2.0", "2.0-SNAPSHOT", -1);
        compare("1.11", "1.9-SNAPSHOT", 1);
        compare("1.11-SNAPSHOT", "1.9-SNAPSHOT", 1);
        compare("1.11-SNAPSHOT", "1.9", 1);
        compare("1.1", "1.1-SNAPSHOT", -1);
        compare("1.1-SNAPSHOT", "1.1-R12345", 1);
        compare("2.1.492-NPR-12954-R012", "2.1.476", 1);
        compare("6.1.58", "6.1.58-FP3", -1);
        compare("6.1.58", "6.1.58.FP3", -1);
        compare("6.1.59", "6.1.58.FP3", 1);
        compare("6.1.58-FP3", "6.1.58-FP2", 1);
        compare("6.1.58-FP3", "6.1.58.FP3", 0);
        compare("6.1.58-FP3", "6.1.58.FP4", -1);
        compare("6.1.58.FP3", "6.1.58-FP4", -1);
        compare("6.1.58.FP3", "6.1.58.FP4", -1);
        compare("6.1.0", "6.1-FP3", -1);
        compare("6.1", "6.1-FP3", -1);
    }

    private void compare(String v1, String v2, int comp) {
        Version vv1 = Version.create(v1);
        Version vv2 = Version.create(v2);
        int ret = vv1.compareTo(vv2);
        if (ret == comp) {
            return;
        }
        if (ret < 0 && comp < 0) {
            return;
        }
        if (ret > 0 && comp > 0) {
            return;
        }
        fail(v1 + " compare to " + v2 + " must return " + comp);
    }

}