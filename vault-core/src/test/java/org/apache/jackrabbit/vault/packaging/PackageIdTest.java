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
 * <code>PackageIdTest</code>...
 */
public class PackageIdTest extends TestCase {

    public void testToString() {
        PackageId packId = new PackageId("group", "name", "version");
        assertEquals("group:name:version", packId.toString());
    }

    public void testToInstallPath() {
        PackageId packId = new PackageId("group", "name", "version");
        assertEquals("/etc/packages/group/name-version", packId.getInstallationPath());
    }

    public void testToInstallPath1() {
        PackageId packId = new PackageId("group", "name", "");
        assertEquals("/etc/packages/group/name", packId.getInstallationPath());
    }

    public void testFromPath() {
        PackageId packId = new PackageId("/etc/packages/apache/jackrabbit/hotfix/name.zip", "1.0");
        assertEquals("apache/jackrabbit/hotfix:name:1.0", packId.toString());
    }

    public void testFromPath1() {
        PackageId packId = new PackageId("/etc/packages/name-1.0.zip", "1.0");
        assertEquals(":name:1.0", packId.toString());
    }

    public void testFromPath2() {
        PackageId packId = new PackageId("apache/jackrabbit/hotfix/name", "1.0");
        assertEquals("apache/jackrabbit/hotfix:name:1.0", packId.toString());
    }

    public void testFromPath3() {
        PackageId packId = new PackageId("name.zip", "1.0");
        assertEquals(":name:1.0", packId.toString());
    }

    public void testFromPath4() {
        PackageId packId = new PackageId("name", (Version) null);
        assertEquals(":name", packId.toString());
    }

    public void testFromPath5() {
        PackageId packId = new PackageId("hotfix/name-1.0", "2.0");
        assertEquals("hotfix:name-1.0:2.0", packId.toString());
    }

    public void testFromVPath() {
        PackageId packId = new PackageId("/etc/packages/apache/jackrabbit/hotfix/name.zip");
        assertEquals("apache/jackrabbit/hotfix:name", packId.toString());
    }

    public void testFromVPath1() {
        PackageId packId = new PackageId("hotfix/name-1.0");
        assertEquals("hotfix:name:1.0", packId.toString());
    }

    public void testFromVPath2() {
        PackageId packId = new PackageId("hotfix/name-1.0-SNAPSHOT");
        assertEquals("hotfix:name:1.0-SNAPSHOT", packId.toString());
    }

    public void testFromVPath3() {
        PackageId packId = new PackageId("hotfix/cq-name-1.0-SNAPSHOT");
        assertEquals("hotfix:cq-name:1.0-SNAPSHOT", packId.toString());
    }

    public void testFromVPath4() {
        PackageId packId = new PackageId("hotfix/cq-5.3.0-hotfix-12345-1.0-SNAPSHOT");
        assertEquals("hotfix:cq-5.3.0-hotfix-12345:1.0-SNAPSHOT", packId.toString());
    }

    public void testFromVPath5() {
        PackageId packId = new PackageId("hotfix/cq-5.3.0-hotfix-12345-1.0-R1234");
        assertEquals("hotfix:cq-5.3.0-hotfix-12345:1.0-R1234", packId.toString());
    }

    public void testFromVPath6() {
        PackageId packId = new PackageId("hotfix/cq-5.3.0-RG12");
        assertEquals("hotfix:cq-5.3.0-RG12", packId.toString());
    }

    public void testEquals() {
        PackageId pack1 = new PackageId("group", "name", "version");
        PackageId pack2 = new PackageId("group", "name", "version");
        assertEquals(pack1, pack2);
    }
    public void testFromString() {
        PackageId packId = PackageId.fromString("group:name:version");
        assertEquals(packId.getGroup(), "group");
        assertEquals(packId.getName(), "name");
        assertEquals(packId.getVersion().toString(), "version");
    }

    public void testFromString2() {
        PackageId packId = PackageId.fromString("group:name");
        assertEquals(packId.getGroup(), "group");
        assertEquals(packId.getName(), "name");
        assertEquals(packId.getVersionString(), "");
    }

    public void testFromString3() {
        PackageId packId = PackageId.fromString("name");
        assertEquals(packId.getGroup(), "");
        assertEquals(packId.getName(), "name");
        assertEquals(packId.getVersionString(), "");
    }

    public void testFromString4() {
        PackageId packId = PackageId.fromString(":name:version");
        assertEquals(packId.getGroup(), "");
        assertEquals(packId.getName(), "name");
        assertEquals(packId.getVersionString(), "version");
    }

    public void testRoundtrip() {
        PackageId p1 = new PackageId("", "name", "");
        PackageId p2 = PackageId.fromString(p1.toString());
        assertEquals(p1.getName(), p2.getName());
        assertEquals(p1.getGroup(), p2.getGroup());
        assertEquals(p1.getVersion(), p2.getVersion());
    }

    public void testRoundtrip2() {
        PackageId p1 = new PackageId("", "name", "version");
        PackageId p2 = PackageId.fromString(p1.toString());
        assertEquals(p1.getName(), p2.getName());
        assertEquals(p1.getGroup(), p2.getGroup());
        assertEquals(p1.getVersion(), p2.getVersion());
    }

    public void testRoundtrip3() {
        PackageId p1 = new PackageId("group", "name", "");
        PackageId p2 = PackageId.fromString(p1.toString());
        assertEquals(p1.getName(), p2.getName());
        assertEquals(p1.getGroup(), p2.getGroup());
        assertEquals(p1.getVersion(), p2.getVersion());
    }

    public void testRoundtrip4() {
        PackageId p1 = new PackageId("group", "name", "version");
        PackageId p2 = PackageId.fromString(p1.toString());
        assertEquals(p1.getName(), p2.getName());
        assertEquals(p1.getGroup(), p2.getGroup());
        assertEquals(p1.getVersion(), p2.getVersion());
    }


}