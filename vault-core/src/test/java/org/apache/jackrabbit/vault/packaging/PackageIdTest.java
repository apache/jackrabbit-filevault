/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.jackrabbit.vault.packaging;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@code PackageIdTest}...
 */
public class PackageIdTest {

    @Test
    public void testToString() {
        PackageId packId = new PackageId("group", "name", "version");
        assertEquals("group:name:version", packId.toString());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testToInstallPath() {
        PackageId packId = new PackageId("group", "name", "version");
        assertEquals("/etc/packages/group/name-version", packId.getInstallationPath());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testToInstallPath1() {
        PackageId packId = new PackageId("group", "name", "");
        assertEquals("/etc/packages/group/name", packId.getInstallationPath());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testFromPath() {
        PackageId packId = new PackageId("/etc/packages/apache/jackrabbit/hotfix/name.zip", "1.0");
        assertEquals("apache/jackrabbit/hotfix:name:1.0", packId.toString());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testFromPath1() {
        PackageId packId = new PackageId("/etc/packages/name-1.0.zip", "1.0");
        assertEquals(":name:1.0", packId.toString());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testFromPath2() {
        PackageId packId = new PackageId("apache/jackrabbit/hotfix/name", "1.0");
        assertEquals("apache/jackrabbit/hotfix:name:1.0", packId.toString());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testFromPath3() {
        PackageId packId = new PackageId("name.zip", "1.0");
        assertEquals(":name:1.0", packId.toString());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testFromPath4() {
        PackageId packId = new PackageId("name", (Version) null);
        assertEquals(":name", packId.toString());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testFromPath5() {
        PackageId packId = new PackageId("hotfix/name-1.0", "2.0");
        assertEquals("hotfix:name-1.0:2.0", packId.toString());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testFromVPath() {
        PackageId packId = new PackageId("/etc/packages/apache/jackrabbit/hotfix/name.zip");
        assertEquals("apache/jackrabbit/hotfix:name", packId.toString());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testFromVPath1() {
        PackageId packId = new PackageId("hotfix/name-1.0");
        assertEquals("hotfix:name:1.0", packId.toString());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testFromVPath2() {
        PackageId packId = new PackageId("hotfix/name-1.0-SNAPSHOT");
        assertEquals("hotfix:name:1.0-SNAPSHOT", packId.toString());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testFromVPath3() {
        PackageId packId = new PackageId("hotfix/cq-name-1.0-SNAPSHOT");
        assertEquals("hotfix:cq-name:1.0-SNAPSHOT", packId.toString());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testFromVPath4() {
        PackageId packId = new PackageId("hotfix/cq-5.3.0-hotfix-12345-1.0-SNAPSHOT");
        assertEquals("hotfix:cq-5.3.0-hotfix-12345:1.0-SNAPSHOT", packId.toString());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testFromVPath5() {
        PackageId packId = new PackageId("hotfix/cq-5.3.0-hotfix-12345-1.0-R1234");
        assertEquals("hotfix:cq-5.3.0-hotfix-12345:1.0-R1234", packId.toString());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testFromVPath6() {
        PackageId packId = new PackageId("hotfix/cq-5.3.0-RG12");
        assertEquals("hotfix:cq-5.3.0-RG12", packId.toString());
    }

    @Test
    public void testEquals() {
        PackageId pack1 = new PackageId("group", "name", "version");
        PackageId pack2 = new PackageId("group", "name", "version");
        assertEquals(pack1, pack2);
    }

    @Test
    public void testFromString() {
        PackageId packId = PackageId.fromString("group:name:version");
        assertEquals(packId.getGroup(), "group");
        assertEquals(packId.getName(), "name");
        assertEquals(packId.getVersion().toString(), "version");
    }

    @Test
    public void testFromString2() {
        PackageId packId = PackageId.fromString("group:name");
        assertEquals(packId.getGroup(), "group");
        assertEquals(packId.getName(), "name");
        assertEquals(packId.getVersionString(), "");
    }

    @Test
    public void testFromString3() {
        PackageId packId = PackageId.fromString("name");
        assertEquals(packId.getGroup(), "");
        assertEquals(packId.getName(), "name");
        assertEquals(packId.getVersionString(), "");
    }

    @Test
    public void testFromString4() {
        PackageId packId = PackageId.fromString(":name:version");
        assertEquals(packId.getGroup(), "");
        assertEquals(packId.getName(), "name");
        assertEquals(packId.getVersionString(), "version");
    }

    @Test
    public void testRoundtrip() {
        PackageId p1 = new PackageId("", "name", "");
        PackageId p2 = PackageId.fromString(p1.toString());
        assertEquals(p1.getName(), p2.getName());
        assertEquals(p1.getGroup(), p2.getGroup());
        assertEquals(p1.getVersion(), p2.getVersion());
    }

    @Test
    public void testRoundtrip2() {
        PackageId p1 = new PackageId("", "name", "version");
        PackageId p2 = PackageId.fromString(p1.toString());
        assertEquals(p1.getName(), p2.getName());
        assertEquals(p1.getGroup(), p2.getGroup());
        assertEquals(p1.getVersion(), p2.getVersion());
    }

    @Test
    public void testRoundtrip3() {
        PackageId p1 = new PackageId("group", "name", "");
        PackageId p2 = PackageId.fromString(p1.toString());
        assertEquals(p1.getName(), p2.getName());
        assertEquals(p1.getGroup(), p2.getGroup());
        assertEquals(p1.getVersion(), p2.getVersion());
    }

    @Test
    public void testRoundtrip4() {
        PackageId p1 = new PackageId("group", "name", "version");
        PackageId p2 = PackageId.fromString(p1.toString());
        assertEquals(p1.getName(), p2.getName());
        assertEquals(p1.getGroup(), p2.getGroup());
        assertEquals(p1.getVersion(), p2.getVersion());
    }

    @Test
    public void testIsValidAcceptsRegularNames() {
        // positive control: ordinary names/groups/versions must keep validating
        // after the backslash/control-character fix is applied.
        assertTrue(PackageId.isValid("my_company/sub-group", "my-package.name", "1.0.0-SNAPSHOT"));
        assertTrue(PackageId.isValid("", "name", ""));
        assertTrue(PackageId.isValid("group", "name", null));
    }

    @Test
    public void testIsValidRejectsBackslashInName() {
        // '..\..\evil' shaped ids must not validate: on Windows, Path.resolve
        // treats '\' as a separator, so an id containing it can escape the
        // FS registry's home directory.
        assertFalse(PackageId.isValid("group", "..\\..\\evil", "1.0"));
        assertFalse(PackageId.isValid("group", "evil\\name", "1.0"));
    }

    @Test
    public void testIsValidRejectsBackslashInGroupSegment() {
        // group segments are split on '/' and each segment re-validated, so a
        // backslash hidden inside one segment must be rejected too.
        assertFalse(PackageId.isValid("evil\\group", "name", "1.0"));
        assertFalse(PackageId.isValid("outer/evil\\group", "name", "1.0"));
    }

    @Test
    public void testIsValidRejectsBackslashInVersion() {
        assertFalse(PackageId.isValid("group", "name", "..\\..\\evil"));
    }

    @Test
    public void testIsValidRejectsControlCharacters() {
        // control characters have no legitimate use in package ids and enable
        // log/name spoofing; every id part must reject them.
        assertFalse(PackageId.isValid("group", "name\u0000evil", "1.0"));
        assertFalse(PackageId.isValid("group", "name\u001Fevil", "1.0"));
        assertFalse(PackageId.isValid("group", "name\u007Fevil", "1.0"));
        assertFalse(PackageId.isValid("evil\u0000group", "name", "1.0"));
        assertFalse(PackageId.isValid("group", "name", "1.0\u0000evil"));
    }
}
