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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * {@code DependencyTest}...
 */
public class DependencyTest {

    @Test
    public void testFromString() {
        Dependency d = Dependency.fromString("group:name:[1.0,2.0]");
        assertEquals("group", d.getGroup());
        assertEquals("name", d.getName());
        assertEquals("[1.0,2.0]", d.getRange().toString());
    }

    @Test
    public void testFromString2() {
        Dependency d = Dependency.fromString("name");
        assertEquals("", d.getGroup());
        assertEquals("name", d.getName());
        assertEquals(VersionRange.INFINITE, d.getRange());
    }

    @Test
    public void testFromString3() {
        Dependency d = Dependency.fromString("group:name");
        assertEquals("group", d.getGroup());
        assertEquals("name", d.getName());
        assertEquals(VersionRange.INFINITE, d.getRange());
    }

    @Test
    public void testFromString4() {
        Dependency d = Dependency.fromString("foo/bar/group/name");
        assertEquals("foo/bar/group", d.getGroup());
        assertEquals("name", d.getName());
        assertEquals(VersionRange.INFINITE, d.getRange());
    }

    @Test
    public void testFromString41() {
        Dependency d = Dependency.fromString("foo/bar/group:name");
        assertEquals("foo/bar/group", d.getGroup());
        assertEquals("name", d.getName());
        assertEquals(VersionRange.INFINITE, d.getRange());
    }

    @Test
    public void testFromString5() {
        Dependency d = Dependency.fromString("foo/bar/group/name:[1.0,2.0]");
        assertEquals("foo/bar/group", d.getGroup());
        assertEquals("name", d.getName());
        assertEquals("[1.0,2.0]", d.getRange().toString());
    }

    @Test
    public void testToString() {
        Dependency d = new Dependency("group", "name", VersionRange.fromString("[1.0, 2.0]"));
        assertEquals("group:name:[1.0,2.0]", d.toString());
    }

    @Test
    public void testToString2() {
        Dependency d = new Dependency("", "name", VersionRange.fromString("[1.0, 2.0]"));
        assertEquals(":name:[1.0,2.0]", d.toString());
    }

    @Test
    public void testToString3() {
        Dependency d = new Dependency("", "name", null);
        assertEquals("name", d.toString());
    }

    @Test
    public void testToString4() {
        Dependency d = new Dependency("group", "name", null);
        assertEquals("group:name", d.toString());
    }

    @Test
    public void testToString5() {
        PackageId id = new PackageId("group", "name", Version.EMPTY);
        Dependency d = new Dependency(id);
        assertEquals("group:name", d.toString());
    }

    @Test
    public void testParse() {
        Dependency[] d = Dependency.parse("name1,group2:name2,group3:name3:1.0,group4:name4:[1.0,2.0],:name5:[1.0,2.0]");
        assertEquals(5,d.length);
        assertEquals("name1", d[0].toString());
        assertEquals("group2:name2", d[1].toString());
        assertEquals("group3:name3:1.0", d[2].toString());
        assertEquals("group4:name4:[1.0,2.0]", d[3].toString());
        assertEquals(":name5:[1.0,2.0]", d[4].toString());
    }

    @Test
    public void testMatches() {
        PackageId id = PackageId.fromString("apache/jackrabbit/product:jcr-content:5.5.0-SNAPSHOT.20111116");
        Dependency d = Dependency.fromString("apache/jackrabbit/product:jcr-content:[5.5.0-SNAPSHOT,)");
        assertTrue(d.matches(id));
    }

    @Test
    public void testAddDependency() {
        Dependency[] d = Dependency.parse("n1,g2:n2,g3:n3:1,g4:n4:[1,2]");
        String expected = "n1,g2:n2,g3:n3:1,g4:n4:[1,2],n5:g5:[1,2]";
        assertEquals(expected, Dependency.toString(DependencyUtil.add(d, Dependency.fromString("n5:g5:[1,2]"))));
    }

    @Test
    public void testAddDependencyExisting() {
        Dependency[] d = Dependency.parse("n1,g2:n2,g3:n3:1,g4:n4:[1,2]");
        String expected = "n1,g2:n2,g3:n3:1,g4:n4:[1,2]";
        assertEquals(expected, Dependency.toString(DependencyUtil.add(d, Dependency.fromString("g3:n3"))));
    }

    @Test
    public void testAddDependencyNullName() {
        Dependency[] d = Dependency.parse("n1,g2:n2,g3:n3:1,g4:n4:[1,2]");
        String expected = "n1,g2:n2,g3:n3:1,g4:n4:[1,2],g3";
        assertEquals(expected, Dependency.toString(DependencyUtil.add(d, Dependency.fromString("g3"))));
    }


}
