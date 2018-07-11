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

import java.io.File;
import java.io.IOException;
import javax.jcr.RepositoryException;

import org.apache.commons.io.FileUtils;
import org.apache.jackrabbit.vault.fs.api.ProgressTrackerListener;
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.packaging.Dependency;
import org.apache.jackrabbit.vault.packaging.DependencyException;
import org.apache.jackrabbit.vault.packaging.NoSuchPackageException;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.PackageExistsException;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.registry.DependencyReport;
import org.apache.jackrabbit.vault.packaging.registry.ExecutionPlan;
import org.apache.jackrabbit.vault.packaging.registry.ExecutionPlanBuilder;
import org.apache.jackrabbit.vault.packaging.registry.PackageTask;
import org.apache.jackrabbit.vault.packaging.registry.RegisteredPackage;
import org.apache.jackrabbit.vault.packaging.registry.PackageTask.Type;
import org.apache.jackrabbit.vault.packaging.registry.impl.FSPackageRegistry;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Test the Package registry interface
 */
public class TestFSPackageRegistry extends IntegrationTestBase {

    private static final File DIR_REGISTRY_HOME = new File("target/registry");

    private static final Logger log = LoggerFactory.getLogger(TestFSPackageRegistry.class);

    private FSPackageRegistry registry;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        if (DIR_REGISTRY_HOME.exists()) {
            FileUtils.cleanDirectory(DIR_REGISTRY_HOME);
        } else {
            DIR_REGISTRY_HOME.mkdir();
        }
        registry = new FSPackageRegistry(DIR_REGISTRY_HOME);
    }

    /**
     * checks is a non existent package is really not there
     */
    @Test
    public void testOpenNonExistentPackage() throws IOException, PackageException {
        assertFalse("package must not exist", registry.contains(TMP_PACKAGE_ID));
        assertNull("package must not exist", registry.open(TMP_PACKAGE_ID));
    }

    /**
     * registers a package via stream
     */
    @Test
    public void testRegisterStream() throws IOException, PackageException {
        PackageId id = registry.register(getStream("testpackages/tmp.zip"), false);
        assertEquals("package id", TMP_PACKAGE_ID, id);

        try (RegisteredPackage pkg = registry.open(id)) {
            assertEquals("package id of registered is correct", TMP_PACKAGE_ID, pkg.getId());
            assertFalse("Package is not installed", pkg.isInstalled());
        }
    }

    /**
     * registers a package twice via stream (replace = false)
     */
    @Test
    public void testRegisterStreamTwiceFails() throws IOException, PackageException {
        PackageId id = registry.register(getStream("testpackages/tmp.zip"), false);
        assertEquals("package id", TMP_PACKAGE_ID, id);

        try {
            registry.register(getStream("testpackages/tmp.zip"), false);
            fail("registering the package twice should fail");
        } catch (PackageExistsException e) {
            // expected
            assertEquals("colliding pid must be correct", id, e.getId());
        }
    }

    /**
     * registers a package twice via stream (replace = true)
     */
    @Test
    public void testRegisterStreamTwiceSucceeds() throws IOException, PackageException {
        PackageId id = registry.register(getStream("testpackages/tmp.zip"), false);
        assertEquals("package id", TMP_PACKAGE_ID, id);
        registry.register(getStream("testpackages/tmp.zip"), true);
    }

    /**
     * registers a package twice via a file
     */
    @Test
    public void testRegisterFileTwiceFails() throws IOException, PackageException {
        File file = getTempFile("testpackages/tmp.zip");
        PackageId id = registry.register(file, false);
        assertEquals("package id", TMP_PACKAGE_ID, id);
        assertTrue("file should still exist", file.exists());

        try (RegisteredPackage pkg = registry.open(id)) {
            assertEquals("package id of registered is correct", TMP_PACKAGE_ID, pkg.getId());
            assertFalse("Package is not installed", pkg.isInstalled());
        }

        try {
            registry.register(file, false);
            fail("registering the package twice should fail");
        } catch (PackageExistsException e) {
            // expected
            assertEquals("colliding pid must be correct", id, e.getId());
        } finally {
            file.delete();
        }
    }

    /**
     * registers a package twice via a temp file file
     */
    @Test
    public void testRegisterTempFileTwiceFails() throws IOException, PackageException {
        File file = getTempFile("testpackages/tmp.zip");
        PackageId id = registry.register(file, false);
        assertEquals("package id", TMP_PACKAGE_ID, id);

        try (RegisteredPackage pkg = registry.open(id)) {
            assertEquals("package id of registered is correct", TMP_PACKAGE_ID, pkg.getId());
            assertFalse("Package is not installed", pkg.isInstalled());
        }

        file = getTempFile("testpackages/tmp.zip");
        try {
            registry.register(file, false);
            fail("registering the package twice should fail");
        } catch (PackageExistsException e) {
            // expected
            assertEquals("colliding pid must be correct", id, e.getId());
        }
    }

    /**
     * registers a package twice via file (replace = true)
     */
    @Test
    public void testRegisterFileTwiceSucceeds() throws IOException, PackageException {
        File file = getTempFile("testpackages/tmp.zip");
        PackageId id = registry.register(file, false);
        assertEquals("package id", TMP_PACKAGE_ID, id);
        assertTrue("file should still exist", file.exists());
        registry.register(file, true);
        file.delete();
    }
    
    /**
     * registers a file as external package twice (replace = true)
     */
    @Test
    public void testRegisterExternalFileTwiceFails() throws IOException, PackageException {
        File file = getTempFile("testpackages/tmp.zip");
        PackageId id = registry.registerExternal(file, false);
        assertEquals("package id", TMP_PACKAGE_ID, id);

        try (RegisteredPackage pkg = registry.open(id)) {
            assertEquals("package id of registered is correct", TMP_PACKAGE_ID, pkg.getId());
            assertFalse("Package is not installed", pkg.isInstalled());
        }

        file = getTempFile("testpackages/tmp.zip");
        try {
            registry.registerExternal(file, false);
            fail("registering the package twice should fail");
        } catch (PackageExistsException e) {
            // expected
            assertEquals("colliding pid must be correct", id, e.getId());
        }
    }

    /**
     * registers a file as external package twice (replace = true)
     */
    @Test
    public void testRegisterExternalFileTwiceSucceeds() throws IOException, PackageException {
        File file = getTempFile("testpackages/tmp.zip");
        PackageId id = registry.registerExternal(file, false);
        assertEquals("package id", TMP_PACKAGE_ID, id);
        assertTrue("file should still exist", file.exists());
        registry.registerExternal(file, true);
        file.delete();
    }
    
    /**
     * registers a file as external package with subpackages (replace = true)
     */
    @Test
    public void testRegisterExternalWithSubPackages() throws IOException, PackageException {
        File file = getTempFile("testpackages/subtest.zip");
        registry.registerExternal(file, false);

        assertTrue(registry.contains(PACKAGE_ID_SUB_A));
        assertTrue(registry.contains(PACKAGE_ID_SUB_B));
    }
    
    /**
     * installs a file as external package with subpackages
     */
    @Test
    public void testInstallExternalWithSubPackages() throws IOException, PackageException {
        File file = getTempFile("testpackages/subtest.zip");
        PackageId parentPkg = registry.registerExternal(file, false);
        
        ExecutionPlanBuilder builder = registry.createExecutionPlan();
        builder.with(new ProgressTrackerListener() {
            public void onMessage(Mode mode, String action, String path) {
                log.info("{} {}", action, path);
            }

            public void onError(Mode mode, String path, Exception e) {
                log.info("E {} {}", path, e.toString());
            }
        });
        builder.addTask().with(parentPkg).with(Type.EXTRACT);
        ExecutionPlan plan  = builder.with(admin).execute();
        assertFalse(plan.hasErrors());
        
        assertTrue(registry.open(PACKAGE_ID_SUB_A).isInstalled());
        assertTrue(registry.open(PACKAGE_ID_SUB_B).isInstalled());
    }
    
    /**
     * registers a file as external package twice (replace = true)
     */
    
    
    /**
     * test if package removal works
     */
    @Test
    public void testRemovePackage() throws IOException, PackageException {
        PackageId id = registry.register(getStream("testpackages/tmp.zip"), false);
        assertEquals("package id", TMP_PACKAGE_ID, id);

        registry.remove(id);
        assertFalse("package must not exist", registry.contains(TMP_PACKAGE_ID));
        assertNull("package must not exist", registry.open(TMP_PACKAGE_ID));
    }

    /**
     * test packages set
     */
    @Test
    public void testPackages() throws IOException, PackageException {
        assertTrue("initially the packages set is empty", registry.packages().isEmpty());
        registry.register(getStream("testpackages/tmp.zip"), false);
        assertEquals("packages contains 1 element", 1, registry.packages().size());
        assertTrue("contains new package", registry.packages().contains(TMP_PACKAGE_ID));
    }

    /**
     * test if remove non existing should fail
     */
    @Test
    public void testRemoveNonExistingPackage() throws IOException, PackageException {
        try {
            registry.remove(TMP_PACKAGE_ID);
            fail("remove non existing should fail");
        } catch (NoSuchPackageException e) {
            assertEquals("exception should contain correct package id", TMP_PACKAGE_ID, e.getId());
        }
    }

    /**
     * test if analyze dependencies fails for non existing package
     */
    @Test
    public void testAnalyzeDependenciesFailsForNonExisting() throws IOException {
        try {
            registry.analyzeDependencies(TMP_PACKAGE_ID, false);
            fail("usage report should fail for non existing package.");
        } catch (NoSuchPackageException e) {
            assertEquals("exception should contain correct package id", TMP_PACKAGE_ID, e.getId());
        }
    }

    /**
     * test if analyze dependencies is correct for non installed packages
     */
    @Test
    public void testAnalyzeDependencies() throws IOException, PackageException {
        // a depends on b and c
        PackageId idA = registry.register(getStream(TEST_PACKAGE_A_10), false);

        DependencyReport report = registry.analyzeDependencies(idA, false);
        assertEquals("resolved dependencies", "", PackageId.toString(report.getResolvedDependencies()));
        assertEquals("unresolved dependencies", "my_packages:test_b,my_packages:test_c:[1.0,2.0)",
                Dependency.toString(report.getUnresolvedDependencies()));

        // b depends on c
        registry.register(getStream(TEST_PACKAGE_B_10), false);
        report = registry.analyzeDependencies(idA, false);
        assertEquals("resolved dependencies", "my_packages:test_b:1.0",
                PackageId.toString(report.getResolvedDependencies()));
        assertEquals("unresolved dependencies", "my_packages:test_c:[1.0,2.0)",
                Dependency.toString(report.getUnresolvedDependencies()));

        registry.register(getStream(TEST_PACKAGE_C_10), false);
        report = registry.analyzeDependencies(idA, false);
        assertEquals("resolved dependencies", "my_packages:test_b:1.0,my_packages:test_c:1.0",
                PackageId.toString(report.getResolvedDependencies()));
        assertEquals("unresolved dependencies", "", Dependency.toString(report.getUnresolvedDependencies()));

    }

    @Test
    public void testUnsupportedInstallTasks() throws IOException, PackageException, RepositoryException {
        // a depends on b and c
        PackageId idC = registry.register(getStream(TEST_PACKAGE_C_10), false);

        ExecutionPlanBuilder builder = registry.createExecutionPlan();
        builder.with(new ProgressTrackerListener() {
            public void onMessage(Mode mode, String action, String path) {
                log.info("{} {}", action, path);
            }

            public void onError(Mode mode, String path, Exception e) {
                log.info("E {} {}", path, e.toString());
            }
        });

        builder.addTask().with(idC).with(PackageTask.Type.INSTALL);
        ExecutionPlan plan  = builder.with(admin).execute();
        assertTrue(plan.hasErrors());
        assertFalse(registry.open(idC).isInstalled());
    }

    @Test
    public void testExecutionPlanInstallation() throws IOException, PackageException, RepositoryException {
        // a depends on b and c
        PackageId idA = registry.register(getStream(TEST_PACKAGE_A_10), false);

        DependencyReport report = registry.analyzeDependencies(idA, false);
        assertEquals("resolved dependencies", "", PackageId.toString(report.getResolvedDependencies()));
        assertEquals("unresolved dependencies", "my_packages:test_b,my_packages:test_c:[1.0,2.0)",
                Dependency.toString(report.getUnresolvedDependencies()));

        // b depends on c
        PackageId idB = registry.register(getStream(TEST_PACKAGE_B_10), false);
        report = registry.analyzeDependencies(idB, false);
        assertEquals("resolved dependencies", "", PackageId.toString(report.getResolvedDependencies()));
        assertEquals("unresolved dependencies", "my_packages:test_c",
                Dependency.toString(report.getUnresolvedDependencies()));

        ExecutionPlanBuilder builder = registry.createExecutionPlan();
        builder.with(new ProgressTrackerListener() {
            public void onMessage(Mode mode, String action, String path) {
                log.info("{} {}", action, path);
            }

            public void onError(Mode mode, String path, Exception e) {
                log.info("E {} {}", path, e.toString());
            }
        });

        builder.addTask().with(idA).with(PackageTask.Type.EXTRACT);
        builder.addTask().with(idB).with(PackageTask.Type.EXTRACT);
        try {
            builder.with(admin).execute();
            fail("registering the package with missing dependencies should fail");
        } catch (DependencyException ex) {
            // expected
        }
        PackageId idC = registry.register(getStream(TEST_PACKAGE_C_10), false);
        report = registry.analyzeDependencies(idB, false);
        assertEquals("resolved dependencies", "my_packages:test_c:1.0",
                PackageId.toString(report.getResolvedDependencies()));
        assertEquals("unresolved dependencies", "", Dependency.toString(report.getUnresolvedDependencies()));

        builder.addTask().with(idC).with(PackageTask.Type.EXTRACT);
        ExecutionPlan plan  = builder.with(admin).execute();
        assertFalse(plan.hasErrors());

        assertTrue(registry.open(idA).isInstalled());
        assertTrue(registry.open(idB).isInstalled());
        assertTrue(registry.open(idC).isInstalled());
    }
    
    @Test
    public void testExtractSubPackage() throws IOException, PackageException, RepositoryException {
        registry.register(getStream("testpackages/subtest.zip"), false);

        assertTrue(registry.contains(PACKAGE_ID_SUB_A));
        assertTrue(registry.contains(PACKAGE_ID_SUB_B));
    }

    @Test
    public void testUsages() throws Exception {
        PackageId idB = registry.register(getStream(TEST_PACKAGE_B_10), false);
        PackageId idC = registry.register(getStream(TEST_PACKAGE_C_10), false);

        assertEquals("usage", "", PackageId.toString(registry.usage(idC)));

        ExecutionPlanBuilder builder = registry.createExecutionPlan();
        builder.with(new ProgressTrackerListener() {
            public void onMessage(Mode mode, String action, String path) {
                log.info("{} {}", action, path);
            }

            public void onError(Mode mode, String path, Exception e) {
                log.info("E {} {}", path, e.toString());
            }
        });

        builder.addTask().with(idB).with(PackageTask.Type.EXTRACT);
        builder.addTask().with(idC).with(PackageTask.Type.EXTRACT);
        ExecutionPlan plan  = builder.with(admin).execute();
        assertFalse(plan.hasErrors());

        assertEquals("usage", "my_packages:test_b:1.0", PackageId.toString(registry.usage(idC)));
    }

}