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

package org.apache.jackrabbit.vault.packaging.registry.impl;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.jackrabbit.vault.packaging.NoSuchPackageException;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.apache.jackrabbit.vault.packaging.impl.HollowVaultPackage;
import org.apache.jackrabbit.vault.packaging.impl.ZipVaultPackage;
import org.apache.jackrabbit.vault.packaging.registry.RegisteredPackage;
import org.junit.Test;
import org.mockito.Mockito;

public class FSRegisteredPackageTest {

    private static final PackageId DUMMY_ID = new PackageId("someGroup", "someName", "someVersion");

    private File getTempFile(String name) throws IOException {
        File tmpFile = File.createTempFile("vaultpack", ".zip");
        try (InputStream in = getClass().getResourceAsStream(name);
            FileOutputStream out = FileUtils.openOutputStream(tmpFile)) {
            IOUtils.copy(in, out);
        }
        return tmpFile;
    }

    private VaultPackage safeLoadVaultPackage(File packageFile) throws IOException {
        if (packageFile.exists() && packageFile.length() > 0) {
            return new ZipVaultPackage(packageFile, false, true);
        } else {
            return new HollowVaultPackage(new Properties());
        }
    }

    private FSPackageRegistry newRegistry(File packageFile) throws IOException, NoSuchPackageException {
        FSPackageRegistry registry = Mockito.mock(FSPackageRegistry.class);
        Mockito.when(registry.openPackageFile(DUMMY_ID)).thenReturn(safeLoadVaultPackage(packageFile));
        return registry;
    }

    private FSInstallState newInstallState(File packageFile) throws IOException {
        FSInstallState installState = Mockito.mock(FSInstallState.class);
        Mockito.when(installState.getFilePath()).thenReturn(packageFile.toPath());
        Mockito.when(installState.getPackageId()).thenReturn(DUMMY_ID);
        return installState;
    }

    @Test
    public void testGetPackageFromNonTruncatedFile() throws IOException, NoSuchPackageException {
        File packageFile = getTempFile("test-package.zip");
        try (RegisteredPackage regPack = new FSRegisteredPackage(newRegistry(packageFile), newInstallState(packageFile));
                VaultPackage vltPack = regPack.getPackage()) {
            assertNotNull(vltPack);
            assertNotNull(vltPack.getArchive());
        } catch (IOException e) {
            fail("should not throw any exception, but thrown: " + e.getMessage());
        } finally {
            packageFile.delete();
        }
    }

    @Test
    public void testGetPackageFromTruncatedFile() throws IOException, NoSuchPackageException {
        File packageFile = getTempFile("test-package-truncated.zip");
        try (RegisteredPackage regPack = new FSRegisteredPackage(newRegistry(packageFile), newInstallState(packageFile));
                VaultPackage vltPack = regPack.getPackage()) {
            assertNotNull(vltPack);
            assertNull(vltPack.getArchive());
        } catch (IOException e) {
            fail("should not throw any exception, but thrown: " + e.getMessage());
        } finally {
            packageFile.delete();
        }
    }
}
