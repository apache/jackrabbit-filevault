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

package org.apache.jackrabbit.vault.packaging.integration;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.jackrabbit.vault.packaging.Dependency;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.SubPackageHandling;
import org.apache.jackrabbit.vault.packaging.registry.impl.FSInstallState;
import org.apache.jackrabbit.vault.packaging.registry.impl.FSPackageStatus;
import org.junit.Test;
import org.xmlunit.matchers.CompareMatcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

/**
 * Test the Package registry interface
 */
public class TestFSInstallState {

    private static final PackageId TMP_PACKAGE_ID = new PackageId("my_packages", "tmp", "");

    private static final String TEST_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<registryMetadata packageid=\"my_packages:tmp\" size=\"1234\"" +
            " installtime=\"1234\" filepath=\"test.zip\" external=\"true\" packagestatus=\"extracted\">\n" +
            "    <dependency packageid=\"my_packages:tmp\"/>\n" +
            "    <subpackage packageid=\"my_packages:tmp\" sphoption=\"ADD\"/>\n" +
            "</registryMetadata>\n";

    @Test
    public void testWriteInstallState() throws IOException {
        File testFile = new File("test.zip");
        Set<Dependency> deps = new HashSet<>();
        deps.add(new Dependency(TMP_PACKAGE_ID));
        Map<PackageId, SubPackageHandling.Option> subs = new HashMap<>();
        subs.put(TMP_PACKAGE_ID, SubPackageHandling.Option.ADD);

        FSInstallState state = new FSInstallState(TMP_PACKAGE_ID, FSPackageStatus.EXTRACTED)
                .withFilePath(testFile.toPath())
                .withExternal(true)
                .withDependencies(deps)
                .withSubPackages(subs)
                .withSize(1234L)
                .withInstallTime(1234L);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        state.save(out);
        out.close();

        assertThat(out.toString("utf-8"), CompareMatcher.isIdenticalTo(TEST_XML));
        assertEquals(TEST_XML, out.toString("utf-8"));
    }

    @Test
    public void testReadInstallStateNonExistent() throws IOException {
        FSInstallState state = FSInstallState.fromFile(new File("nonexist.xml"));
        assertEquals(null, state);
    }
    @Test
    public void testReadInstallState() throws IOException {
        Set<Dependency> deps = new HashSet<>();
        deps.add(new Dependency(TMP_PACKAGE_ID));
        Map<PackageId, SubPackageHandling.Option> subs = new HashMap<>();
        subs.put(TMP_PACKAGE_ID, SubPackageHandling.Option.ADD);

        FSInstallState state = FSInstallState.fromStream(new ByteArrayInputStream(TEST_XML.getBytes("utf-8")), "test.xml");
        assertEquals(Paths.get("test.zip"), state.getFilePath());
        assertEquals(FSPackageStatus.EXTRACTED, state.getStatus());
        assertEquals(true, state.isExternal());
        assertEquals(deps, state.getDependencies());
        assertEquals(subs, state.getSubPackages());
        assertEquals(subs, state.getSubPackages());
        assertEquals(1234L, state.getSize());
        assertEquals((Long) 1234L, state.getInstallationTime());

    }
}