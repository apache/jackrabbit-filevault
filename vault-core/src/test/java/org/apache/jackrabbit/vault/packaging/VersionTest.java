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

import junit.framework.TestCase;

/**
 * <code>VersionTest</code>...
 */
public class VersionTest extends TestCase {

    public void testFromSegments() {
        String[] segs = {"1", "2", "3-SNAPSHOT"};
        Version v = Version.create(segs);
        assertEquals("1.2.3-SNAPSHOT", v.toString());
    }

    public void testCompare() {
        compare("1.0.0", "1.0.0", 0);
        compare("1.0.1", "1.0.0", 1);
        compare("1.1", "1.0.0", 1);
        compare("1.11", "1.9", 1);
        compare("1.1-SNAPSHOT", "1.0.0", 1);
        compare("2.0", "2.0-beta-8", 1);
        compare("2.0", "2.0-SNAPSHOT", 1);
        compare("1.11", "1.9-SNAPSHOT", 1);
        compare("1.11-SNAPSHOT", "1.9-SNAPSHOT", 1);
        compare("1.11-SNAPSHOT", "1.9", 1);
        compare("1.1", "1.1-SNAPSHOT", 1);
        compare("1.1-SNAPSHOT", "1.1-R12345", 1);
    }

    public void testOsgiCompare() {
        osgiCompare("1.0.0", "1.0.0", 0);
        osgiCompare("1.0.1", "1.0.0", 1);
        osgiCompare("1.1", "1.0.0", 1);
        osgiCompare("1.11", "1.9", 1);
        osgiCompare("1.1-SNAPSHOT", "1.0.0", 1);
        osgiCompare("2.0", "2.0-beta-8", -1);
        osgiCompare("2.0", "2.0-SNAPSHOT", -1);
        osgiCompare("1.11", "1.9-SNAPSHOT", 1);
        osgiCompare("1.11-SNAPSHOT", "1.9-SNAPSHOT", 1);
        osgiCompare("1.11-SNAPSHOT", "1.9", 1);
        osgiCompare("1.1", "1.1-SNAPSHOT", -1);
        osgiCompare("1.1-SNAPSHOT", "1.1-R12345", 1);
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
    private void osgiCompare(String v1, String v2, int comp) {
        Version vv1 = Version.create(v1);
        Version vv2 = Version.create(v2);
        int ret = vv1.osgiCompareTo(vv2);
        if (ret == comp) {
            return;
        }
        if (ret < 0 && comp < 0) {
            return;
        }
        if (ret > 0 && comp > 0) {
            return;
        }
        fail(v1 + " osgi compare to " + v2 + " must return " + comp);
    }
}