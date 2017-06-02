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

import java.io.IOException;
import java.io.InputStream;

import org.apache.jackrabbit.vault.fs.impl.SubPackageFilterArchive;
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.MemoryArchive;
import org.apache.jackrabbit.vault.packaging.integration.IntegrationTestBase;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

/**
 * {@code SubPackageFilterArchiveTest}...
 */
public class SubPackageFilterArchiveTest {


    private MemoryArchive memoryArchive;

    private SubPackageFilterArchive archive;

    @Before
    public void setup() throws Exception {
        memoryArchive = new MemoryArchive(false);
        InputStream in = IntegrationTestBase.class.getResourceAsStream("testpackages/subtest.zip");
        memoryArchive.run(in);
        in.close();
        archive = new SubPackageFilterArchive(memoryArchive);
    }

    @Test
    public void testGetEntry() throws IOException {
        assertNotNull("/etc/packages must not be null in memory archive", memoryArchive.getEntry("/jcr_root/etc/packages"));
        assertNotNull("/etc/packages must not be null in memory archive", memoryArchive.getEntry("/jcr_root/etc/packages/my_packages/sub_a.zip"));
        assertNull("/etc/packages must be null", archive.getEntry("/jcr_root/etc/packages"));
        assertNull("/etc/packages must be null", archive.getEntry("/jcr_root/etc/packages/my_packages/sub_a.zip"));
    }

    @Test
    public void testListChildren() throws IOException {
        for (Archive.Entry e: archive.getEntry("/jcr_root/etc").getChildren()) {
            if ("packages".equals(e.getName())) {
                fail("getChildren() must not contain 'packages");
            }
        }
    }
}