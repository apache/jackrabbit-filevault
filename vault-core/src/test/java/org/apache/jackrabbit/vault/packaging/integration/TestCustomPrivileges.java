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

import org.apache.jackrabbit.api.JackrabbitWorkspace;
import org.apache.jackrabbit.api.security.authorization.PrivilegeManager;
import org.apache.jackrabbit.vault.packaging.JcrPackage;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/**
 * Test cases for packages with custom privileges
 */
public class TestCustomPrivileges extends IntegrationTestBase {

    /**
     * Installs a package that contains a custom privilege and then checks if it was installed.
     */
    @Test
    public void installWithPrivs() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/privileges.zip"), false);
        assertNotNull(pack);
        pack.install(getDefaultOptions());

        // check if privilege was installed
        PrivilegeManager mgr = ((JackrabbitWorkspace) admin.getWorkspace()).getPrivilegeManager();
        try {
            mgr.getPrivilege("testns:testpriv");
        } catch (RepositoryException e) {
            fail("testns:testpriv privilege not registered.");
        }
    }


}