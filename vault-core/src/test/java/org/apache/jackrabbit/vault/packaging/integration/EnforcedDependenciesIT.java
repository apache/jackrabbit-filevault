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

import javax.jcr.RepositoryException;

import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.packaging.CyclicDependencyException;
import org.apache.jackrabbit.vault.packaging.Dependency;
import org.apache.jackrabbit.vault.packaging.DependencyException;
import org.apache.jackrabbit.vault.packaging.DependencyHandling;
import org.apache.jackrabbit.vault.packaging.JcrPackage;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/**
 * Tests that cover installation and uninstallation with enforced dependency checks.
 */
public class EnforcedDependenciesIT extends IntegrationTestBase {

    /**
     * Test package A-1.0. Depends on B and C-1.X
     */
    private static String TEST_PACKAGE_A_10 = "/test-packages/test_a-1.0.zip";

    /**
     * Test package A-2.0. Depends on B and C-2.X
     */
    private static String TEST_PACKAGE_A_20 = "/test-packages/test_a-2.0.zip";

    /**
     * Test package B-1.0. Depends on C
     */
    private static String TEST_PACKAGE_B_10 = "/test-packages/test_b-1.0.zip";

    /**
     * Test package C-1.0
     */
    private static String TEST_PACKAGE_C_10 = "/test-packages/test_c-1.0.zip";

    /**
     * Test package C-1.1
     */
    private static String TEST_PACKAGE_C_11 = "/test-packages/test_c-1.1.zip";

    /**
     * Test package C-2.0
     */
    private static String TEST_PACKAGE_C_20 = "/test-packages/test_c-2.0.zip";

    /**
     * Test package D-1.0. Depends on B and E
     */
    private static String TEST_PACKAGE_D_10 = "/test-packages/test_d-1.0.zip";

    /**
     * Test package E-1.0. Depends on D
     */
    private static String TEST_PACKAGE_E_10 = "/test-packages/test_e-1.0.zip";

    /**
     * Tests if dependencies are ignored by default
     */
    @Test
    public void testIgnoredDependencies() throws RepositoryException, IOException, PackageException {
        JcrPackage a1 = installPackage(TEST_PACKAGE_A_10);
        assertProperty("/tmp/a/version", "1.0");
        JcrPackage a2= installPackage(TEST_PACKAGE_A_20);
        assertProperty("/tmp/a/version", "2.0");
        JcrPackage b1 = installPackage(TEST_PACKAGE_B_10);
        assertProperty("/tmp/b/version", "1.0");
        JcrPackage c1 = installPackage(TEST_PACKAGE_C_10);
        assertProperty("/tmp/c/version", "1.0");

        c1.uninstall(getDefaultOptions());
        assertNodeMissing("/tmp/c");

        b1.uninstall(getDefaultOptions());
        assertNodeMissing("/tmp/b");

        a2.uninstall(getDefaultOptions());
        assertProperty("/tmp/a/version", "1.0");

        a1.uninstall(getDefaultOptions());
        assertNodeMissing("/tmp/a");
    }

    /**
     * Test unresolved calculation
     */
    @Test
    public void testUnresolved() throws IOException, RepositoryException {
        JcrPackage a1 = uploadPackage(TEST_PACKAGE_A_10);
        Dependency[] deps = a1.getUnresolvedDependencies();
        assertEquals("package must report unresolved dependencies", "my_packages:test_b,my_packages:test_c:[1.0,2.0)", Dependency.toString(deps));
    }

    /**
     * Test if installing B fails if C is missing.
     */
    @Test
    public void testInstallDepMissing() throws IOException, RepositoryException, PackageException {
        JcrPackage b1 = uploadPackage(TEST_PACKAGE_B_10);
        ImportOptions opts = getDefaultOptions();
        opts.setDependencyHandling(DependencyHandling.STRICT);
        try {
            b1.install(opts);
            fail("Installing with missing dependency must fail.");
        } catch (DependencyException e) {
            // expected
        }
    }

    /**
     * Test if installing A fails if C is missing.
     */
    @Test
    public void testInstallDepMissingRequired() throws IOException, RepositoryException, PackageException {
        uploadPackage(TEST_PACKAGE_B_10);
        JcrPackage a1 = uploadPackage(TEST_PACKAGE_A_10);

        ImportOptions opts = getDefaultOptions();
        opts.setDependencyHandling(DependencyHandling.REQUIRED);
        try {
            a1.install(opts);
            fail("Installing with missing dependency must fail.");
        } catch (DependencyException e) {
            // expected
        }
    }

    /**
     * Test if installing B fails if C is loaded but not installed
     */
    @Test
    public void testInstallDepUninstalled() throws IOException, RepositoryException, PackageException {
        uploadPackage(TEST_PACKAGE_C_10);
        assertNodeMissing("/tmp/c/");
        JcrPackage b1 = uploadPackage(TEST_PACKAGE_B_10);

        ImportOptions opts = getDefaultOptions();
        opts.setDependencyHandling(DependencyHandling.STRICT);

        try {
            b1.install(opts);
            fail("Installing with uninstalled dependency must fail.");
        } catch (DependencyException e) {
            // expected
        }
    }

    /**
     * Test if installing B automatically installs C
     */
    @Test
    public void testInstallDepInstallsRequired() throws IOException, RepositoryException, PackageException {
        uploadPackage(TEST_PACKAGE_C_10);
        assertNodeMissing("/tmp/c/");
        JcrPackage b1 = uploadPackage(TEST_PACKAGE_B_10);
        assertNotNull(b1);

        ImportOptions opts = getDefaultOptions();
        opts.setDependencyHandling(DependencyHandling.REQUIRED);

        b1.install(opts);
        assertProperty("/tmp/b/version", "1.0");
        assertProperty("/tmp/c/version", "1.0");
    }

    /**
     * Test if installing A-2.0 automatically installs B and C-2.0
     */
    @Test
    public void testInstallDepInstallsAll() throws IOException, RepositoryException, PackageException {
        uploadPackage(TEST_PACKAGE_C_10);
        uploadPackage(TEST_PACKAGE_C_11);
        uploadPackage(TEST_PACKAGE_C_20);
        uploadPackage(TEST_PACKAGE_B_10);
        assertNodeMissing("/tmp/b/");
        assertNodeMissing("/tmp/c/");
        JcrPackage a2 = uploadPackage(TEST_PACKAGE_A_20);
        assertNotNull(a2);

        ImportOptions opts = getDefaultOptions();
        opts.setDependencyHandling(DependencyHandling.REQUIRED);

        a2.install(opts);
        assertProperty("/tmp/a/version", "2.0");
        assertProperty("/tmp/b/version", "1.0");
        assertProperty("/tmp/c/version", "2.0");
    }

    /**
     * Test if installing D automatically installs B and C
     */
    @Test
    public void testInstallDeep() throws IOException, RepositoryException, PackageException {
        uploadPackage(TEST_PACKAGE_C_10);
        uploadPackage(TEST_PACKAGE_B_10);
        assertNodeMissing("/tmp/b/");
        assertNodeMissing("/tmp/c/");
        JcrPackage d1 = uploadPackage(TEST_PACKAGE_D_10);

        ImportOptions opts = getDefaultOptions();
        opts.setDependencyHandling(DependencyHandling.BEST_EFFORT);

        d1.install(opts);
        assertProperty("/tmp/d/version", "1.0");
        assertProperty("/tmp/b/version", "1.0");
        assertProperty("/tmp/c/version", "1.0");
    }

    /**
     * Test if installing D with E causes cyclic error for REQUIRED
     */
    @Test
    public void testInstallCyclicFails() throws IOException, RepositoryException, PackageException {
        uploadPackage(TEST_PACKAGE_B_10);
        uploadPackage(TEST_PACKAGE_C_10);
        uploadPackage(TEST_PACKAGE_E_10);
        JcrPackage d1 = uploadPackage(TEST_PACKAGE_D_10);

        ImportOptions opts = getDefaultOptions();
        opts.setDependencyHandling(DependencyHandling.REQUIRED);

        try {
            d1.install(opts);
            fail("installing D -> E -> D should cause cyclic dependency exception.");
        } catch (CyclicDependencyException e) {
            // ok
        }
    }

    /**
     * Test if installing D with E does not cause cyclic error for BEST_EFFORT
     */
    @Test
    public void testInstallCyclicSucceeds() throws IOException, RepositoryException, PackageException {
        uploadPackage(TEST_PACKAGE_B_10);
        uploadPackage(TEST_PACKAGE_C_10);
        uploadPackage(TEST_PACKAGE_E_10);
        JcrPackage d1 = uploadPackage(TEST_PACKAGE_D_10);

        ImportOptions opts = getDefaultOptions();
        opts.setDependencyHandling(DependencyHandling.BEST_EFFORT);

        d1.install(opts);
        assertProperty("/tmp/b/version", "1.0");
        assertProperty("/tmp/c/version", "1.0");
        assertProperty("/tmp/d/version", "1.0");
        assertProperty("/tmp/e/version", "1.0");
    }

    /**
     * Test if installing A-1.0 automatically installs B and C-1.1
     */
    @Test
    public void testInstallDepInstallsCorrectVersion() throws IOException, RepositoryException, PackageException {
        uploadPackage(TEST_PACKAGE_C_10);
        uploadPackage(TEST_PACKAGE_C_11);
        uploadPackage(TEST_PACKAGE_C_20);
        uploadPackage(TEST_PACKAGE_B_10);
        assertNodeMissing("/tmp/b/");
        assertNodeMissing("/tmp/c/");
        JcrPackage a1 = uploadPackage(TEST_PACKAGE_A_10);

        ImportOptions opts = getDefaultOptions();
        opts.setDependencyHandling(DependencyHandling.REQUIRED);

        a1.install(opts);
        assertProperty("/tmp/a/version", "1.0");
        assertProperty("/tmp/b/version", "1.0");
        assertProperty("/tmp/c/version", "1.1");
    }

    /**
     * Test if installing besteffort works
     */
    @Test
    public void testInstallDepInstallsBestEffort() throws IOException, RepositoryException, PackageException {
        uploadPackage(TEST_PACKAGE_C_10);
        assertNodeMissing("/tmp/c/");
        JcrPackage a1 = uploadPackage(TEST_PACKAGE_A_10);
        assertNotNull(a1);

        ImportOptions opts = getDefaultOptions();
        opts.setDependencyHandling(DependencyHandling.BEST_EFFORT);

        a1.install(opts);
        assertProperty("/tmp/a/version", "1.0");
        assertProperty("/tmp/c/version", "1.0");
    }

    /**
     * Test if installing B succeeds if C is installed
     */
    @Test
    public void testInstallDepInstalled() throws IOException, RepositoryException, PackageException {
        installPackage(TEST_PACKAGE_C_10);
        assertProperty("/tmp/c/version", "1.0");
        JcrPackage b1 = uploadPackage(TEST_PACKAGE_B_10);
        assertNotNull(b1);

        ImportOptions opts = getDefaultOptions();
        opts.setDependencyHandling(DependencyHandling.STRICT);
        b1.install(opts);
        assertProperty("/tmp/b/version", "1.0");
    }

    /**
     * Test if installing A fails if C has the wrong version
     */
    @Test
    public void testInstallWrongVersion() throws IOException, RepositoryException, PackageException {
        installPackage(TEST_PACKAGE_C_10);
        assertProperty("/tmp/c/version", "1.0");

        JcrPackage a2 = uploadPackage(TEST_PACKAGE_A_20);

        ImportOptions opts = getDefaultOptions();
        opts.setDependencyHandling(DependencyHandling.STRICT);

        try {
            a2.install(opts);
            fail("Installing with wrong installed version dependency must fail.");
        } catch (DependencyException e) {
            // expected
        }
    }

    /**
     * Test if installing A succeeds if C has correct version
     */
    @Test
    public void testInstallCorrectVersion() throws IOException, RepositoryException, PackageException {
        installPackage(TEST_PACKAGE_B_10);
        assertProperty("/tmp/b/version", "1.0");
        installPackage(TEST_PACKAGE_C_11);
        assertProperty("/tmp/c/version", "1.1");

        JcrPackage a1 = uploadPackage(TEST_PACKAGE_A_10);

        ImportOptions opts = getDefaultOptions();
        opts.setDependencyHandling(DependencyHandling.STRICT);

        a1.install(opts);
        assertProperty("/tmp/a/version", "1.0");
    }

    /**
     * Test if un-installing C fails if B is still installed
     */
    @Test
    public void testUninstallStrict() throws IOException, RepositoryException, PackageException {
        JcrPackage c1 = installPackage(TEST_PACKAGE_C_10);
        assertProperty("/tmp/c/version", "1.0");
        installPackage(TEST_PACKAGE_B_10);
        assertProperty("/tmp/b/version", "1.0");

        ImportOptions opts = getDefaultOptions();
        opts.setDependencyHandling(DependencyHandling.STRICT);

        try {
            c1.uninstall(opts);
            fail("Uninstalling must fail if another package still requires it.");
        } catch (DependencyException e) {
            // expected
        }
    }

    /**
     * Test if un-installing C auto-uninstalls A and B for BEST_EFFORT
     */
    @Test
    public void testUninstallBestEffort() throws IOException, RepositoryException, PackageException {
        installPackage(TEST_PACKAGE_A_10);
        installPackage(TEST_PACKAGE_B_10);
        JcrPackage c1 = installPackage(TEST_PACKAGE_C_10);

        ImportOptions opts = getDefaultOptions();
        opts.setDependencyHandling(DependencyHandling.BEST_EFFORT);

        c1.uninstall(opts);
        assertNodeMissing("/tmp/a");
        assertNodeMissing("/tmp/b");
        assertNodeMissing("/tmp/c");
    }

    /**
     * Test if un-installing C auto-uninstalls A and B for REQUIRED
     */
    @Test
    public void testUninstallRequired() throws IOException, RepositoryException, PackageException {
        installPackage(TEST_PACKAGE_A_10);
        installPackage(TEST_PACKAGE_B_10);
        JcrPackage c1 = installPackage(TEST_PACKAGE_C_10);

        ImportOptions opts = getDefaultOptions();
        opts.setDependencyHandling(DependencyHandling.REQUIRED);

        c1.uninstall(opts);
        assertNodeMissing("/tmp/a");
        assertNodeMissing("/tmp/b");
        assertNodeMissing("/tmp/c");
    }

    /**
     * Test if uninstalling D with E does not causes cyclic error
     */
    @Test
    public void testUnInstallCyclicSucceeds() throws IOException, RepositoryException, PackageException {
        installPackage(TEST_PACKAGE_D_10);
        JcrPackage e1 = installPackage(TEST_PACKAGE_E_10);

        ImportOptions opts = getDefaultOptions();
        opts.setDependencyHandling(DependencyHandling.REQUIRED);

        e1.uninstall(opts);
        assertNodeMissing("/tmp/e");
        assertNodeMissing("/tmp/d");
    }


    /**
     * Tests package manager usage method
     */
    @Test
    public void testUsage() throws RepositoryException, IOException, PackageException {
        installPackage(TEST_PACKAGE_A_20);
        installPackage(TEST_PACKAGE_B_10);
        JcrPackage c1 = installPackage(TEST_PACKAGE_C_20);

        PackageId[] usage = packMgr.usage(c1.getDefinition().getId());
        assertEquals("correct usage", "my_packages:test_a:2.0,my_packages:test_b:1.0", PackageId.toString(usage));
    }


}