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
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Calendar;
import java.util.HashMap;

import javax.jcr.RepositoryException;

import org.apache.commons.collections.map.HashedMap;
import org.apache.commons.io.FileUtils;
import org.apache.jackrabbit.vault.fs.api.ProgressTrackerListener;
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.packaging.Dependency;
import org.apache.jackrabbit.vault.packaging.DependencyException;
import org.apache.jackrabbit.vault.packaging.DependencyHandling;
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
import org.apache.jackrabbit.vault.packaging.registry.impl.FSInstallState;
import org.apache.jackrabbit.vault.packaging.registry.impl.FSPackageRegistry;
import org.apache.jackrabbit.vault.packaging.registry.impl.FSPackageStatus;
import org.apache.jackrabbit.vault.packaging.registry.impl.JcrPackageRegistry;
import org.junit.After;
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
 * Test dependencies of JCR Packages on packages registered in FS Package registry
 */
public class TestCrossRegistryDependencies extends IntegrationTestBase {

    private static final File DIR_REGISTRY_HOME = new File("target/registry");

    private static final Logger log = LoggerFactory.getLogger(TestCrossRegistryDependencies.class);

    private FSPackageRegistry fsregistry;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        if (DIR_REGISTRY_HOME.exists()) {
            FileUtils.cleanDirectory(DIR_REGISTRY_HOME);
        } else {
            DIR_REGISTRY_HOME.mkdir();
        }
        fsregistry = new FSPackageRegistry(DIR_REGISTRY_HOME);
        packMgr.getInternalRegistry().setFsPackageRegistry(fsregistry);

        PackageId idB = fsregistry.register(getStream(TEST_PACKAGE_B_10), false);
        PackageId idC = fsregistry.register(getStream(TEST_PACKAGE_C_10), false);
        
        ImportOptions opts = new ImportOptions();
        opts.setDependencyHandling(DependencyHandling.STRICT);

        fsregistry.installPackage(admin, fsregistry.open(idB), opts, true);
        fsregistry.installPackage(admin, fsregistry.open(idC), opts, true);


    }
    
    @Test
    public void testCrossDependency() throws Exception {
        PackageId idA = packMgr.getInternalRegistry().register(getStream(TEST_PACKAGE_A_10), false);
        ImportOptions opts = getDefaultOptions();
        opts.setDependencyHandling(DependencyHandling.STRICT);
        packMgr.open(idA).install(opts);

    }

}