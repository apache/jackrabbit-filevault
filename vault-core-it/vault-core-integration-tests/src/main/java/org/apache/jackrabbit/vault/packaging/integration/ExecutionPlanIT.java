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
import java.io.IOException;

import org.apache.jackrabbit.vault.packaging.CyclicDependencyException;
import org.apache.jackrabbit.vault.packaging.DependencyException;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.registry.ExecutionPlan;
import org.apache.jackrabbit.vault.packaging.registry.ExecutionPlanBuilder;
import org.apache.jackrabbit.vault.packaging.registry.PackageRegistry;
import org.apache.jackrabbit.vault.packaging.registry.PackageTask;
import org.apache.jackrabbit.vault.packaging.registry.impl.JcrPackageRegistry;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Test the Package registry interface
 */
public class ExecutionPlanIT extends IntegrationTestBase {

    private static final PackageId TMP_PACKAGE_ID = new PackageId("my_packages", "tmp", "");

    /**
     * Test package A-1.0. Depends on B and C-1.X
     */
    private static String TEST_PACKAGE_A_10 = "/test-packages/test_a-1.0.zip";
    private static PackageId TEST_PACKAGE_ID_A_10 = PackageId.fromString("my_packages:test_a:1.0");

    /**
     * Test package B-1.0. Depends on C
     */
    private static String TEST_PACKAGE_B_10 = "/test-packages/test_b-1.0.zip";
    private static PackageId TEST_PACKAGE_ID_B_10 = PackageId.fromString("my_packages:test_b:1.0");

    /**
     * Test package C-1.0
     */
    private static String TEST_PACKAGE_C_10 = "/test-packages/test_c-1.0.zip";
    private static PackageId TEST_PACKAGE_ID_C_10 = PackageId.fromString("my_packages:test_c:1.0");

    /**
     * Test package D-1.0. Depends on B and E
     */
    private static String TEST_PACKAGE_D_10 = "/test-packages/test_d-1.0.zip";

    /**
     * Test package E-1.0. Depends on D
     */
    private static String TEST_PACKAGE_E_10 = "/test-packages/test_e-1.0.zip";


    private PackageRegistry registry;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        registry = new JcrPackageRegistry(admin);
    }

    /**
     * test execution plan serialization
     */
    @Test
    public void testSerialization() throws IOException, PackageException {
        PackageId idC = registry.register(getStream(TEST_PACKAGE_C_10), false);
        PackageId idB = registry.register(getStream(TEST_PACKAGE_B_10), false);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ExecutionPlanBuilder builder = registry.createExecutionPlan();
        builder
                .addTask().with(idB).with(PackageTask.Type.EXTRACT)
                .addTask().with(idC).with(PackageTask.Type.INSTALL)
                .addTask().with(idC).with(PackageTask.Type.REMOVE)
                .addTask().with(idC).with(PackageTask.Type.UNINSTALL)
                .save(out);

        String expected =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<executionPlan version=\"1.0\">\n" +
                        "    <task cmd=\"uninstall\" packageId=\"my_packages:test_c:1.0\"/>\n" +
                        "    <task cmd=\"remove\" packageId=\"my_packages:test_c:1.0\"/>\n" +
                        "    <task cmd=\"install\" packageId=\"my_packages:test_c:1.0\"/>\n" +
                        "    <task cmd=\"extract\" packageId=\"my_packages:test_b:1.0\"/>\n" +
                        "</executionPlan>\n";

        String result = out.toString("utf-8");
        assertEquals(expected, result);

        out = new ByteArrayOutputStream();
        registry.createExecutionPlan().load(new ByteArrayInputStream(expected.getBytes("utf-8"))).save(out);
        result = out.toString("utf-8");
        assertEquals(expected, result);
    }

    /**
     * Tests if validation fails of the task don't have the required properties
     */
    @Test
    public void testValidationFailsForNoIdOrType() throws IOException, PackageException {
        try {
            ExecutionPlanBuilder builder = registry.createExecutionPlan();
            builder.addTask().with(PackageTask.Type.UNINSTALL);
            builder.validate();
            fail("task with no id must not validate");
        } catch (PackageException e) {
            // expected
        }

        try {
            ExecutionPlanBuilder builder = registry.createExecutionPlan();
            builder.addTask().with(TMP_PACKAGE_ID);
            builder.validate();
            fail("task with no type must not validate");
        } catch (PackageException e) {
            // expected
        }
    }

    @Test
    public void testInstallTask() throws IOException, PackageException {
        PackageId idA = registry.register(getStream(TEST_PACKAGE_A_10), false);
        PackageId idB = registry.register(getStream(TEST_PACKAGE_B_10), false);
        PackageId idC = registry.register(getStream(TEST_PACKAGE_C_10), false);
        assertFalse("package A is not installed", registry.open(idA).isInstalled());
        assertFalse("package B is not installed", registry.open(idB).isInstalled());
        assertFalse("package C is not installed", registry.open(idC).isInstalled());

        ExecutionPlan plan = registry.createExecutionPlan()
                .addTask().with(idA).with(PackageTask.Type.INSTALL)
                .with(admin)
                .with(getDefaultOptions().getListener())
                .execute();
        assertTrue("plan is finished", plan.isExecuted());
        assertFalse("plan has no errors", plan.hasErrors());

        assertTrue("package A is installed", registry.open(idA).isInstalled());
        assertTrue("package B is installed", registry.open(idB).isInstalled());
        assertTrue("package C is installed", registry.open(idC).isInstalled());
    }

    @Test
    public void testInstallTask2() throws IOException, PackageException {
        PackageId idA = registry.register(getStream(TEST_PACKAGE_A_10), false);
        PackageId idB = registry.register(getStream(TEST_PACKAGE_B_10), false);
        PackageId idC = registry.register(getStream(TEST_PACKAGE_C_10), false);
        assertFalse("package A is not installed", registry.open(idA).isInstalled());
        assertFalse("package B is not installed", registry.open(idB).isInstalled());
        assertFalse("package C is not installed", registry.open(idC).isInstalled());

        ExecutionPlan plan = registry.createExecutionPlan()
                .addTask().with(idC).with(PackageTask.Type.INSTALL)
                .addTask().with(idA).with(PackageTask.Type.INSTALL)
                .with(admin)
                .with(getDefaultOptions().getListener())
                .execute();
        assertTrue("plan is finished", plan.isExecuted());
        assertFalse("plan has no errors", plan.hasErrors());

        assertTrue("package A is installed", registry.open(idA).isInstalled());
        assertTrue("package B is installed", registry.open(idB).isInstalled());
        assertTrue("package C is installed", registry.open(idC).isInstalled());
    }

    @Test
    public void testValidateCyclicTaskFails() throws IOException, PackageException {
        PackageId idB = registry.register(getStream(TEST_PACKAGE_B_10), false);
        PackageId idC = registry.register(getStream(TEST_PACKAGE_C_10), false);
        PackageId idD = registry.register(getStream(TEST_PACKAGE_D_10), false);
        PackageId idE = registry.register(getStream(TEST_PACKAGE_E_10), false);
        assertFalse("package B is not installed", registry.open(idB).isInstalled());
        assertFalse("package C is not installed", registry.open(idD).isInstalled());
        assertFalse("package D is not installed", registry.open(idC).isInstalled());
        assertFalse("package E is not installed", registry.open(idE).isInstalled());

        try {
            registry.createExecutionPlan()
                    .addTask().with(idD).with(PackageTask.Type.INSTALL)
                    .with(admin)
                    .with(getDefaultOptions().getListener())
                    .validate();
            fail("plan should fail with CyclicDependencyException");
        } catch (CyclicDependencyException e) {
            // expected
        }
    }

    @Test
    public void testMissingDependencyFails() throws IOException, PackageException {
        PackageId idB = registry.register(getStream(TEST_PACKAGE_B_10), false);
        assertFalse("package B is not installed", registry.open(idB).isInstalled());

        try {
            registry.createExecutionPlan()
                    .addTask().with(idB).with(PackageTask.Type.INSTALL)
                    .with(admin)
                    .with(getDefaultOptions().getListener())
                    .validate();
            fail("plan should fail with DependencyException");
        } catch (DependencyException e) {
            // expected
        }
    }

    @Test
    public void testValidateMissingFails() throws IOException {
        for (PackageTask.Type type: PackageTask.Type.values()) {
            try {
                registry.createExecutionPlan().addTask().with(TEST_PACKAGE_ID_A_10).with(type).validate();
                fail(type + " task with missing package must not validate");
            } catch (PackageException e) {
                // ok
            }
        }
    }

    @Test
    public void testUninstallTask() throws IOException, PackageException {
        testInstallTask();

        ExecutionPlan plan = registry.createExecutionPlan()
                .addTask().with(TEST_PACKAGE_ID_A_10).with(PackageTask.Type.UNINSTALL)
                .with(admin)
                .with(getDefaultOptions().getListener())
                .execute();
        assertTrue("plan is finished", plan.isExecuted());
        assertFalse("plan has no errors", plan.hasErrors());

        assertFalse("package A is not installed", registry.open(TEST_PACKAGE_ID_A_10).isInstalled());
    }

    @Test
    public void testUninstallAutomaticDependenciesTask() throws IOException, PackageException {
        testInstallTask();

        ExecutionPlan plan = registry.createExecutionPlan()
                .addTask().with(TEST_PACKAGE_ID_C_10).with(PackageTask.Type.UNINSTALL)
                .with(admin)
                .with(getDefaultOptions().getListener())
                .execute();
        assertTrue("plan is finished", plan.isExecuted());
        assertFalse("plan has no errors", plan.hasErrors());

        assertFalse("package A is not installed", registry.open(TEST_PACKAGE_ID_A_10).isInstalled());
        assertFalse("package B is not installed", registry.open(TEST_PACKAGE_ID_B_10).isInstalled());
        assertFalse("package C is not installed", registry.open(TEST_PACKAGE_ID_C_10).isInstalled());
    }

    @Test
    public void testRemoveTask() throws IOException, PackageException {
        PackageId idA = registry.register(getStream(TEST_PACKAGE_A_10), false);
        assertTrue("package A is registered", registry.contains(idA));

        ExecutionPlan plan = registry.createExecutionPlan()
                .addTask().with(idA).with(PackageTask.Type.REMOVE)
                .with(admin)
                .with(getDefaultOptions().getListener())
                .execute();
        assertTrue("plan is finished", plan.isExecuted());
        assertFalse("plan has no errors", plan.hasErrors());
        assertFalse("package A is removed", registry.contains(idA));
    }

    @Test
    public void testRemoveInstalledTask() throws IOException, PackageException {
        testInstallTask();

        ExecutionPlan plan = registry.createExecutionPlan()
                .addTask().with(TEST_PACKAGE_ID_C_10).with(PackageTask.Type.REMOVE)
                .with(admin)
                .with(getDefaultOptions().getListener())
                .execute();
        assertTrue("plan is finished", plan.isExecuted());
        assertTrue("plan has errors", plan.hasErrors());
        assertTrue("package A is not removed", registry.contains(TEST_PACKAGE_ID_C_10));
    }

    @Test
    public void testExtractTask() throws IOException, PackageException {
        PackageId idA = registry.register(getStream(TEST_PACKAGE_A_10), false);
        PackageId idB = registry.register(getStream(TEST_PACKAGE_B_10), false);
        PackageId idC = registry.register(getStream(TEST_PACKAGE_C_10), false);
        assertFalse("package A is not installed", registry.open(idA).isInstalled());
        assertFalse("package B is not installed", registry.open(idB).isInstalled());
        assertFalse("package C is not installed", registry.open(idC).isInstalled());

        ExecutionPlan plan = registry.createExecutionPlan()
                .addTask().with(idA).with(PackageTask.Type.EXTRACT)
                .with(admin)
                .with(getDefaultOptions().getListener())
                .execute();
        assertTrue("plan is finished", plan.isExecuted());
        assertFalse("plan has no errors", plan.hasErrors());

        assertTrue("package A is installed", registry.open(idA).isInstalled());
        assertTrue("package B is installed", registry.open(idB).isInstalled());
        assertTrue("package C is installed", registry.open(idC).isInstalled());
    }

    @Test
    public void testStackedExecutionPlans() throws IOException, PackageException {
        PackageId idA = registry.register(getStream(TEST_PACKAGE_A_10), false);
        PackageId idB = registry.register(getStream(TEST_PACKAGE_B_10), false);
        PackageId idC = registry.register(getStream(TEST_PACKAGE_C_10), false);
        
        assertFalse("package A is not installed", registry.open(idA).isInstalled());
        assertFalse("package B is not installed", registry.open(idB).isInstalled());
        assertFalse("package C is not installed", registry.open(idC).isInstalled());

        ExecutionPlanBuilder builder1 = registry.createExecutionPlan()
                .addTask().with(idB).with(PackageTask.Type.EXTRACT)
                .addTask().with(idC).with(PackageTask.Type.EXTRACT)
                .with(admin)
                .with(getDefaultOptions().getListener());
        
        
        // create new executionPlanBuilder for package A
        ExecutionPlanBuilder builder2 = registry.createExecutionPlan()
                .addTask().with(idA).with(PackageTask.Type.EXTRACT)
                .with(admin)
                .with(getDefaultOptions().getListener());
        assertEquals("builder2 contains all packageTasks", 3, builder2.preview().size());
        
        // If calculatedIds of builder1 are declared external idB & idC should be removed
        builder2.with(builder1.preview());
        assertEquals("builder2 only contains 1 PackageTask", 1, builder2.preview().size());
        assertEquals("builder2 handles only idA", builder2.preview().iterator().next(), idA);
        
        ExecutionPlan plan = builder2.execute();
        assertTrue("builder2 should fail before builder1 is executed.",plan.hasErrors());
        
        builder1.execute();
        
        // revalidate builder2 to reset error state (calculate implicitly validates)
        assertEquals("builder2 only contains 1 PackageTask", 1, builder2.preview().size());
        
        ExecutionPlan plan2 = builder2.execute();
        assertFalse("builder2 should succeed after builder1 has been executed.", plan2.hasErrors());
        
    }
}