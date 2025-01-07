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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.jcr.Session;

import org.apache.jackrabbit.vault.fs.api.ImportMode;
import org.apache.jackrabbit.vault.fs.io.AccessControlHandling;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.registry.ExecutionPlan;
import org.apache.jackrabbit.vault.packaging.registry.PackageTask;
import org.apache.jackrabbit.vault.packaging.registry.PackageTask.State;
import org.apache.jackrabbit.vault.packaging.registry.PackageTask.Type;
import org.apache.jackrabbit.vault.packaging.registry.PackageTaskOptions;
import org.apache.jackrabbit.vault.packaging.registry.taskoption.ImportOptionsPackageTaskOption;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

@RunWith(org.mockito.junit.MockitoJUnitRunner.class)
public class ExecutionPlanBuilderImplTest {

    private ExecutionPlanBuilderImpl builder;

    @Mock
    private Session session;
    @Before
    public void setUp() {
        builder = new ExecutionPlanBuilderImpl(new MockPackageRegistry(MockPackageRegistry.NEW_PACKAGE_ID));
    }

    @Test
    public void testSaveAndLoad() throws IOException, PackageException {
        ImportOptions importOptions = new ImportOptions();
        importOptions.setStrict(true);
        importOptions.setAccessControlHandling(AccessControlHandling.MERGE_PRESERVE);
        importOptions.setAutoSaveThreshold(123);
        importOptions.setCugHandling(AccessControlHandling.CLEAR);
        importOptions.setImportMode(ImportMode.UPDATE);
        importOptions.setDryRun(true);
        importOptions.setNonRecursive(true);
        importOptions.setOverwritePrimaryTypesOfFolders(true);
        PackageTaskOptions options = new ImportOptionsPackageTaskOption(importOptions);
        builder.addTask().with(MockPackageRegistry.NEW_PACKAGE_ID).withOptions(options).with(PackageTask.Type.INSTALL);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        builder.save(out);
        builder = new ExecutionPlanBuilderImpl(new MockPackageRegistry(MockPackageRegistry.NEW_PACKAGE_ID));
        builder.load(new ByteArrayInputStream(out.toByteArray()));
        builder.with(session);
        ExecutionPlan plan = builder.execute();
        PackageTaskImpl expectedTask = new PackageTaskImpl(MockPackageRegistry.NEW_PACKAGE_ID, Type.INSTALL, options);
        expectedTask.state = State.COMPLETED;
        MatcherAssert.assertThat(plan.getTasks(), Matchers.contains(expectedTask));
    }
}
