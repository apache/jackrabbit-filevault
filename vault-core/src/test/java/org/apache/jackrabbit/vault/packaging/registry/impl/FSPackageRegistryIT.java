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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.jcr.RepositoryException;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.jackrabbit.vault.fs.api.ProgressTrackerListener;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.packaging.Dependency;
import org.apache.jackrabbit.vault.packaging.DependencyException;
import org.apache.jackrabbit.vault.packaging.NoSuchPackageException;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.PackageExistsException;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.apache.jackrabbit.vault.packaging.integration.IntegrationTestBase;
import org.apache.jackrabbit.vault.packaging.registry.DependencyReport;
import org.apache.jackrabbit.vault.packaging.registry.ExecutionPlan;
import org.apache.jackrabbit.vault.packaging.registry.ExecutionPlanBuilder;
import org.apache.jackrabbit.vault.packaging.registry.PackageTask;
import org.apache.jackrabbit.vault.packaging.registry.PackageTask.Type;
import org.apache.jackrabbit.vault.packaging.registry.RegisteredPackage;
import org.apache.jackrabbit.vault.packaging.registry.impl.FSPackageRegistry.Config;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;
import org.osgi.framework.BundleContext;
import org.osgi.util.converter.Converter;
import org.osgi.util.converter.Converters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test the Package registry interface
 */
public class FSPackageRegistryIT extends IntegrationTestBase {

    private static final File DIR_REGISTRY_HOME = new File("target/registry");

    private static final Logger log = LoggerFactory.getLogger(FSPackageRegistryIT.class);

    public static final String[] APPLICATION_PATHS = {
            "/libs",
            "/libs/foo"
    };
    
    public static final String[] CONTENT_PATHS = {
            "/tmp",
            "/tmp/foo"
    };
    
    private static final PackageId TEST_PACKAGE_ID = new PackageId("test", "test-package-with-etc", "1.0");

    @Rule
    public TemporaryFolder tmpFolder = new TemporaryFolder();

    private FSPackageRegistry registry;
    private File registryHome;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        if (DIR_REGISTRY_HOME.exists()) {
            FileUtils.cleanDirectory(DIR_REGISTRY_HOME);
        } else {
            DIR_REGISTRY_HOME.mkdir();
        }
        
        getFreshRegistry();
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
        PackageId id = registry.register(getStream("/test-packages/tmp.zip"), false);
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
        PackageId id = registry.register(getStream("/test-packages/tmp.zip"), false);
        assertEquals("package id", TMP_PACKAGE_ID, id);

        try {
            registry.register(getStream("/test-packages/tmp.zip"), false);
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
        PackageId id = registry.register(getStream("/test-packages/tmp.zip"), false);
        assertEquals("package id", TMP_PACKAGE_ID, id);
        registry.register(getStream("/test-packages/tmp.zip"), true);
    }

    /**
     * registers a package twice via a file
     */
    @Test
    public void testRegisterFileTwiceFails() throws IOException, PackageException {
        File file = getTempFile("/test-packages/tmp.zip");
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
        File file = getTempFile("/test-packages/tmp.zip");
        PackageId id = registry.register(file, false);
        assertEquals("package id", TMP_PACKAGE_ID, id);

        try (RegisteredPackage pkg = registry.open(id)) {
            assertEquals("package id of registered is correct", TMP_PACKAGE_ID, pkg.getId());
            assertFalse("Package is not installed", pkg.isInstalled());
        }

        file = getTempFile("/test-packages/tmp.zip");
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
        File file = getTempFile("/test-packages/tmp.zip");
        PackageId id = registry.register(file, false);
        assertEquals("package id", TMP_PACKAGE_ID, id);
        assertTrue("file should still exist", file.exists());
        registry.register(file, true);
        file.delete();
        // make sure package is still accessible after original has been deleted
        try (RegisteredPackage registeredPackage = registry.open(id)) {
            try (VaultPackage pack = registeredPackage.getPackage()) {
                assertNotEquals(file, pack.getFile());
            }
        }
    }
    
    /**
     * registers a file as external package twice (replace = false)
     */
    @Test
    public void testRegisterExternalFileTwiceFails() throws IOException, PackageException {
        File file = getTempFile("/test-packages/tmp.zip");
        PackageId id = registry.registerExternal(file, false);
        assertEquals("package id", TMP_PACKAGE_ID, id);

        try (RegisteredPackage pkg = registry.open(id)) {
            assertEquals("package id of registered is correct", TMP_PACKAGE_ID, pkg.getId());
            assertFalse("Package is not installed", pkg.isInstalled());
        }

        file = getTempFile("/test-packages/tmp.zip");
        try {
            registry.registerExternal(file, false);
            fail("registering the package twice should fail");
        } catch (PackageExistsException e) {
            // expected
            assertEquals("colliding pid must be correct", id, e.getId());
        }
    }
    
    /**
     * registers a file as external package twice with 
     */
    @SuppressWarnings("deprecation")
    @Test
    public void testRegisterExternalFileTwiceFailsLoadedRegistry() throws IOException, PackageException {
        File file = getTempFile("/test-packages/tmp.zip");
        PackageId id = registry.registerExternal(file, false);
        assertEquals("package id", TMP_PACKAGE_ID, id);

        try (RegisteredPackage pkg = registry.open(id)) {
            assertEquals("package id of registered is correct", TMP_PACKAGE_ID, pkg.getId());
            assertFalse("Package is not installed", pkg.isInstalled());
        }
        
        // loading registry again to force loading of metadata from files
        registry = new FSPackageRegistry(registryHome);
        
        try {
            registry.registerExternal(file, false);
            fail("registering the package twice should fail");
        } catch (PackageExistsException e) {
            // expected
            assertEquals("colliding pid must be correct", id, e.getId());
        }
    }


    /**
     * registers a file as external package twice (replace = false)
     */
    @Test
    public void testRegisterExternalFileTwiceSucceeds() throws IOException, PackageException {
        File file = getTempFile("/test-packages/tmp.zip");
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
        File file = getTempFile("/test-packages/subtest.zip");
        registry.registerExternal(file, false);

        assertTrue(registry.contains(PACKAGE_ID_SUB_A));
        assertTrue(registry.contains(PACKAGE_ID_SUB_B));
    }
    
    /**
     * installs a file as external package with subpackages 
     * Subpackages are only installed if either explicitly added to executionPlan or another package depends on them.
     */
    @Test
    public void testInstallExternalWithSubPackages() throws IOException, PackageException {
        File file = getTempFile("/test-packages/subtest.zip");
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
        
        assertFalse(registry.open(PACKAGE_ID_SUB_A).isInstalled());
        assertFalse(registry.open(PACKAGE_ID_SUB_B).isInstalled());
    }

    @Test
    public void testInstallExternalUnScoped() throws IOException, PackageException, RepositoryException, org.apache.jackrabbit.oak.segment.file.InvalidFileStoreVersionException {
        File file = getTempFile("/test-packages/mixed_package.zip");
        
        cleanPaths(APPLICATION_PATHS);
        cleanPaths(CONTENT_PATHS);
        getFreshRegistry();

        PackageId pkg = registry.registerExternal(file, false);
        
        ExecutionPlanBuilder builder = registry.createExecutionPlan();
        Collector listener = new Collector();
        builder.with(listener);
        builder.addTask().with(pkg).with(Type.EXTRACT);
        ExecutionPlan plan  = builder.with(admin).execute();
        assertFalse(plan.hasErrors());
        checkFiltered(APPLICATION_PATHS, new String[] {}, listener.paths);
        checkFiltered(CONTENT_PATHS, new String[] {}, listener.paths);
    }

    @Test
    public void testInstallExternalContentScoped() throws IOException, PackageException, RepositoryException, org.apache.jackrabbit.oak.segment.file.InvalidFileStoreVersionException {
        File file = getTempFile("/test-packages/mixed_package.zip");

        cleanPaths(APPLICATION_PATHS);
        cleanPaths(CONTENT_PATHS);
        getFreshRegistry(InstallationScope.CONTENT_SCOPED);
        
        PackageId pkg = registry.registerExternal(file, false);
        
        ExecutionPlanBuilder builder = registry.createExecutionPlan();
        Collector listener = new Collector();
        builder.with(listener);
        builder.addTask().with(pkg).with(Type.EXTRACT);
        ExecutionPlan plan  = builder.with(admin).execute();
        assertFalse(plan.hasErrors());
        checkFiltered(CONTENT_PATHS, APPLICATION_PATHS, listener.paths);
    }

    private void cleanPaths(String[] paths) throws IOException, RepositoryException, org.apache.jackrabbit.oak.segment.file.InvalidFileStoreVersionException  {
        for (String path : paths) {
            clean(path);
        }
    }

    @Test
    public void testInstallExternalApplicationScoped() throws IOException, PackageException, RepositoryException, org.apache.jackrabbit.oak.segment.file.InvalidFileStoreVersionException {
        File file = getTempFile("/test-packages/mixed_package.zip");
        
        cleanPaths(APPLICATION_PATHS);
        cleanPaths(CONTENT_PATHS);
        getFreshRegistry(InstallationScope.APPLICATION_SCOPED);
        
        PackageId pkg = registry.registerExternal(file, false);
        
        ExecutionPlanBuilder builder = registry.createExecutionPlan();
        Collector listener = new Collector();
        builder.with(listener);
        builder.addTask().with(pkg).with(Type.EXTRACT);
        ExecutionPlan plan  = builder.with(admin).execute();
        assertFalse(plan.hasErrors());
        checkFiltered(APPLICATION_PATHS, CONTENT_PATHS, listener.paths);
        
    }
    

    /**
     * test if package removal works
     */
    @Test
    public void testRemovePackage() throws IOException, PackageException {
        PackageId id = registry.register(getStream("/test-packages/tmp.zip"), false);
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
        registry.register(getStream("/test-packages/tmp.zip"), false);
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
        registry.register(getStream("/test-packages/subtest.zip"), false);

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
    
    @Test
    public void testInstalledDependencies() throws Exception {
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

        builder.addTask().with(idC).with(PackageTask.Type.EXTRACT);
        ExecutionPlan plan  = builder.with(admin).execute();
        assertFalse(plan.hasErrors());

        Dependency depB = new Dependency(idB);
        Dependency depC = new Dependency(idC);
        assertNull("Dependency B should not resolve", registry.resolve(depB, true));
        assertEquals("Dependency C should resovle to package C", idC, registry.resolve(depC, true));
    }
    
    
    @Test
    public void testInstallTime() throws Exception {

        PackageId idC = registry.register(getStream(TEST_PACKAGE_C_10), false);

        assertNull(registry.open(idC).getInstallationTime());
        
        ExecutionPlanBuilder builder = registry.createExecutionPlan();
        builder.with(new ProgressTrackerListener() {
            public void onMessage(Mode mode, String action, String path) {
                log.info("{} {}", action, path);
            }

            public void onError(Mode mode, String path, Exception e) {
                log.info("E {} {}", path, e.toString());
            }
        });

        builder.addTask().with(idC).with(PackageTask.Type.EXTRACT);
        Calendar before = Calendar.getInstance();
        ExecutionPlan plan = builder.with(admin).execute();
        Calendar after = Calendar.getInstance();
        assertFalse(plan.hasErrors());

        assertTrue("Installation time for idC too late", registry.open(idC).getInstallationTime().compareTo(after) <= 0);
        assertTrue("Installation time for idC too early", registry.open(idC).getInstallationTime().compareTo(before) >= 0);

    }

    @Test
    public void testUnsupportedUninstall() throws Exception {
        PackageId idC = registry.register(getStream(TEST_PACKAGE_C_10), false);

        assertNull(registry.open(idC).getInstallationTime());
        
        ExecutionPlanBuilder builder = registry.createExecutionPlan();
        builder.with(new ProgressTrackerListener() {
            public void onMessage(Mode mode, String action, String path) {
                log.info("{} {}", action, path);
            }

            public void onError(Mode mode, String path, Exception e) {
                log.info("E {} {}", path, e.toString());
            }
        });

        builder.addTask().with(idC).with(PackageTask.Type.EXTRACT);
        ExecutionPlan plan = builder.with(admin).execute();
        assertFalse(plan.hasErrors());

        try{
            registry.uninstallPackage(admin, registry.open(idC), new ImportOptions());
            fail("uninstall attempt should fail.");
        } catch (PackageException ex) {
            //expected
        }
    }

    @Test
    public void testInvalidMetaXmlFile() throws Exception {
        getFreshRegistryWithDefaultConstructor("test-package.zip", "invalid-metadata.xml");
        assertNull(registry.getInstallState(TEST_PACKAGE_ID));
    }

    @Test
    public void testCacheInitializedAfterOSGiActivate() throws IOException {
         new FSPackageRegistry();
        getFreshRegistryWithDefaultConstructor("test-package.zip", "test-package.xml");
        assertTrue(registry.contains(TEST_PACKAGE_ID));
        assertEquals(Collections.singleton(TEST_PACKAGE_ID), registry.packages());
    }

    private void getFreshRegistryWithDefaultConstructor(String packageName, String packageMetadataName) throws IOException {
        if (this.registryHome != null && this.registryHome.exists()) {
            this.registryHome.delete();
        }
        this.registryHome = new File(DIR_REGISTRY_HOME, UUID.randomUUID().toString());
        FileUtils.copyInputStreamToFile(getClass().getResourceAsStream(packageName), new File(this.registryHome, "package1.zip"));
        FileUtils.copyInputStreamToFile(getClass().getResourceAsStream(packageMetadataName), new File(this.registryHome, "package1.zip.xml"));
        
        BundleContext context = Mockito.mock(BundleContext.class);
        Mockito.when(context.getProperty(FSPackageRegistry.REPOSITORY_HOME)).thenReturn(DIR_REGISTRY_HOME.toString());
        Converter converter = Converters.standardConverter();
        Map<String, Object> map = new HashMap<>();
        map.put("homePath", registryHome.getName());
        map.put("authIdsForHookExecution", new String[0]);
        map.put("authIdsForRootInstallation", new String[0]);
        Config config = converter.convert(map).to(Config.class);
        registry.activate(context, config);
    }

    @SuppressWarnings("deprecation")
    private void getFreshRegistry(InstallationScope... scope) throws IOException {
        if (this.registryHome != null && this.registryHome.exists()) {
            this.registryHome.delete();
        }
        this.registryHome = new File(DIR_REGISTRY_HOME, UUID.randomUUID().toString());
        this.registryHome.mkdir();
        if (scope.length > 0) {
            this.registry = new FSPackageRegistry(registryHome, scope[0]);
        } else {
            this.registry = new FSPackageRegistry(registryHome);
        }
    }
    
    private static class Collector implements ProgressTrackerListener {
        private final List<String> paths = new LinkedList<String>();

        public void onMessage(Mode mode, String action, String path) {
            paths.add(path);
        }

        public void onError(Mode mode, String path, Exception e) {
        }
    }
    
    public static void checkFiltered(String[] containing, String[] filtered, List<String> result) {
        assertEquals("Results don't contain expected values", Collections.EMPTY_LIST, CollectionUtils.subtract(Arrays.asList(containing), result));
        assertEquals("Results contain unexpected values", Collections.EMPTY_LIST , CollectionUtils.intersection(result, Arrays.asList(filtered)));
    }

}