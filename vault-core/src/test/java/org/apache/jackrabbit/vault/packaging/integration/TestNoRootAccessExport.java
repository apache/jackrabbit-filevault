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

import javax.jcr.GuestCredentials;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.security.AccessControlManager;
import javax.jcr.security.Privilege;

import org.apache.jackrabbit.api.security.JackrabbitAccessControlList;
import org.apache.jackrabbit.commons.jackrabbit.authorization.AccessControlUtils;
import org.apache.jackrabbit.vault.packaging.JcrPackage;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.impl.JcrPackageManagerImpl;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;

/**
 * Test export w/o read access to the root node
 */
public class TestNoRootAccessExport extends IntegrationTestBase {

    @Test
    @Ignore("JCRVLT-100")
    public void exportNoRootAccess() throws RepositoryException, IOException, PackageException {
        // setup access control
        Node packageRoot = new JcrPackageManagerImpl(admin).getPackageRoot();
        AccessControlManager acMgr = admin.getAccessControlManager();
        JackrabbitAccessControlList acl = AccessControlUtils.getAccessControlList(acMgr, "/");
        acMgr.removePolicy(acl.getPath(), acl);

        AccessControlUtils.getAccessControlList(acMgr, packageRoot.getPath());
        AccessControlUtils.allow(packageRoot, org.apache.jackrabbit.oak.spi.security.principal.EveryonePrincipal.NAME, Privilege.JCR_ALL);

        Node tmpNode = new JcrPackageManagerImpl(admin).getPackageRoot();
        AccessControlUtils.getAccessControlList(acMgr, tmpNode.getPath());
        AccessControlUtils.allow(tmpNode, org.apache.jackrabbit.oak.spi.security.principal.EveryonePrincipal.NAME, Privilege.JCR_ALL);

        admin.save();

        // import existing package
        JcrPackage pack = packMgr.upload(getStream("testpackages/tmp_foo_bar_test.zip"), false);
        PackageId id = pack.getDefinition().getId();
        assertNotNull(pack);
        pack.extract(getDefaultOptions());
        assertNodeExists("/tmp/foo/bar/test.txt");

        // login as guest an
        Session anonymous = repository.login(new GuestCredentials());
        JcrPackageManagerImpl jcrPackageManager = new JcrPackageManagerImpl(anonymous);
        pack = jcrPackageManager.open(id);
        jcrPackageManager.assemble(pack, null);
    }

}