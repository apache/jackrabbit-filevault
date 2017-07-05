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

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import javax.jcr.ItemExistsException;
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.fs.io.ZipStreamArchive;
import org.apache.jackrabbit.vault.packaging.JcrPackage;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Test cases for shallow package installation
 */
@RunWith(Parameterized.class)
public class TestArchiveExtraction extends IntegrationTestBase {

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{{0, false}, {1000, false}, {1024*1024, true}});
    }

    private final int streamBufferSize;

    private final boolean isBuffered;

    public TestArchiveExtraction(int streamBufferSize, boolean isBuffered) {
        this.streamBufferSize = streamBufferSize;
        this.isBuffered = isBuffered;
    }

    @Override
    public Archive getFileArchive(String name) {
        if (streamBufferSize > 0) {
            try {
                return super.getStreamArchive(name, streamBufferSize);
            } catch (IOException e) {
                throw new IllegalArgumentException(e);
            }
        }
        return super.getFileArchive(name);
    }

    private void validateArchive(Archive a) {
        if (a instanceof ZipStreamArchive) {
            assertEquals("isBuffered", isBuffered, ((ZipStreamArchive) a).isBuffered());
        }
    }

    @Test
    public void testDefaultArchiveInstall() throws RepositoryException, IOException, PackageException {
        Archive a = getFileArchive("testpackages/tmp.zip");
        ImportOptions opts = getDefaultOptions();
        PackageId[] ids = packMgr.extract(a, opts, true);
        validateArchive(a);
        assertEquals(1, ids.length);
        assertEquals(new PackageId("my_packages", "tmp", ""), ids[0]);
        assertNodeExists("/tmp/foo/bar/tobi");
        assertNodeExists("/etc/packages/my_packages/tmp.zip");
        // check if size is 0
        long size = admin.getProperty("/etc/packages/my_packages/tmp.zip/jcr:content/jcr:data").getLength();
        assertEquals("package binary size", 0, size);

        JcrPackage pack = packMgr.open(ids[0]);
        assertTrue("Package should be marked as installed", pack.isInstalled());
        assertTrue("Package should be marked as empty", pack.isEmpty());
        assertNull("Package should not have a snapshot", pack.getSnapshot());
        assertNotNull("Package should have a definition", pack.getDefinition());
        assertNotNull("Package should have a definition creation date", pack.getDefinition().getCreated());
        assertNotNull("Package should have properties", pack.getPackage().getProperties());
        assertNotNull("Package should have a properties creation date", pack.getPackage().getCreated());

        try {
            pack.install(getDefaultOptions());
            fail("re-install of a hollow package should fail.");
        } catch (IllegalStateException e) {
            // ok
        }
    }

    @Test
    public void testDefaultArchiveInstallFailsWithoutReplace() throws RepositoryException, IOException, PackageException {
        uploadPackage("testpackages/tmp.zip");
        Archive a = getFileArchive("testpackages/tmp.zip");
        ImportOptions opts = getDefaultOptions();
        try {
            packMgr.extract(a, opts, false);
            fail("extract w/o replace should fail.");
        } catch (ItemExistsException e) {
            // expected
        }
    }

    @Test
    public void testDefaultArchiveInstallCanReplace() throws RepositoryException, IOException, PackageException {
        uploadPackage("testpackages/tmp.zip");
        Archive a = getFileArchive("testpackages/tmp.zip");
        ImportOptions opts = getDefaultOptions();
        PackageId[] ids = packMgr.extract(a, opts, true);
        assertEquals(1, ids.length);
        assertEquals(new PackageId("my_packages", "tmp", ""), ids[0]);
    }

    @Test
    public void testNonRecursive() throws RepositoryException, IOException, PackageException {
        Archive a = getFileArchive("testpackages/subtest.zip");

        // install
        ImportOptions opts = getDefaultOptions();
        opts.setNonRecursive(true);
        PackageId[] ids = packMgr.extract(a, opts, false);
        assertEquals(1, ids.length);
        assertEquals(new PackageId("my_packages", "subtest", ""), ids[0]);

        // check for sub packages
        assertNodeExists("/etc/packages/my_packages/sub_a.zip");
        long size = admin.getProperty("/etc/packages/my_packages/sub_a.zip/jcr:content/jcr:data").getLength();
        assertTrue("sub package must have data", size > 0);
        assertNodeMissing("/tmp/a");

        assertNodeExists("/etc/packages/my_packages/sub_b.zip");
        size = admin.getProperty("/etc/packages/my_packages/sub_b.zip/jcr:content/jcr:data").getLength();
        assertTrue("sub package must have data", size > 0);
        assertNodeMissing("/tmp/b");
    }

    @Test
    public void testRecursive() throws RepositoryException, IOException, PackageException {
        Archive a = getFileArchive("testpackages/subtest.zip");

        // install
        ImportOptions opts = getDefaultOptions();
        PackageId[] ids = packMgr.extract(a, opts, false);
        assertEquals(3, ids.length);
        Set<PackageId> testSet = new HashSet<>(Arrays.asList(ids));
        assertTrue(testSet.contains(new PackageId("my_packages", "subtest", "")));
        assertTrue(testSet.contains(new PackageId("my_packages", "sub_a", "")));
        assertTrue(testSet.contains(new PackageId("my_packages", "sub_b", "")));

        // check for sub packages
        assertNodeExists("/etc/packages/my_packages/sub_a.zip");
        long size = admin.getProperty("/etc/packages/my_packages/sub_a.zip/jcr:content/jcr:data").getLength();
        assertEquals("sub package must no data", 0, size);
        assertNodeExists("/tmp/a");

        assertNodeExists("/etc/packages/my_packages/sub_b.zip");
        size = admin.getProperty("/etc/packages/my_packages/sub_b.zip/jcr:content/jcr:data").getLength();
        assertEquals("sub package must no data", 0, size);
        assertNodeExists("/tmp/b");
    }
}