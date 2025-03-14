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

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.Principal;
import java.util.Properties;
import java.util.zip.Deflater;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import javax.jcr.security.AccessControlEntry;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.jackrabbit.commons.jackrabbit.authorization.AccessControlUtils;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.fs.config.DefaultMetaInf;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.fs.io.AccessControlHandling;
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.fs.io.Importer;
import org.apache.jackrabbit.vault.fs.io.ZipStreamArchive;
import org.apache.jackrabbit.vault.packaging.ExportOptions;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.PackageProperties;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.junit.Test;

public class ACEsAtRootIT extends IntegrationTestBase {

    @Test
    public void export() throws RepositoryException, IOException, PackageException, ConfigurationException {

        // todo: check why this is not working as expected

        UserManager userManager = ((JackrabbitSession)admin).getUserManager();

        // Create a test user

        String userId = "user1";
        String userPwd = "pwd1";
        User user1 = userManager.createUser(userId, userPwd);
        Principal principal1 = user1.getPrincipal();
        String userPath = user1.getPath();
        AccessControlUtils.addAccessControlEntry(admin, userPath, principal1, new String[]{"jcr:all"}, true);
        String repPolicyPath = String.format("%s/rep:policy", userPath);
        String profilePath = admin.getNode(userPath).addNode("profile").getPath();
        admin.save();

        assertNodeExists(userPath);

        // Setup ACEs at the root path, for the user

        AccessControlUtils.addAccessControlEntry(admin, null, principal1, new String[]{"jcr:namespaceManagement"}, true);
        AccessControlUtils.addAccessControlEntry(admin, "/", principal1, new String[]{"jcr:read"}, true);

        // Check the number of ACEs at the root
        AccessControlEntry[] originalAces = AccessControlUtils.getAccessControlList(admin, "/").getAccessControlEntries();
        assertEquals(2, originalAces.length);
        assertEquals(1, AccessControlUtils.getAccessControlList(admin, null).getAccessControlEntries().length);

        // Export with the user session

        ExportOptions opts = new ExportOptions();
        DefaultMetaInf inf = new DefaultMetaInf();
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();

//        PathFilterSet pfilterSet = new PathFilterSet("/");
//        filter.addPropertyFilterSet(pfilterSet);

        PathFilterSet userFilterSet = new PathFilterSet(userPath);
//        userFilterSet.addInclude(new DefaultPathFilter(userPath));
//        userFilterSet.addInclude(new DefaultPathFilter(profilePath + "(/.*)?"));
//        userFilterSet.addInclude(new DefaultPathFilter(repPolicyPath + "(/.*)?"));
        filter.add(userFilterSet);

//        PathFilterSet profileSet = new PathFilterSet(profilePath);
//        profileSet.addInclude(new DefaultPathFilter(profilePath));
//        filter.add(profileSet);
//
//        PathFilterSet repPolicySet = new PathFilterSet(repPolicyPath);
//        filter.add(repPolicySet);

        inf.setFilter(filter);
        Properties props = new Properties();
        props.setProperty(VaultPackage.NAME_VERSION, "0.0.1");
        props.setProperty(VaultPackage.NAME_GROUP, "jackrabbit/test");
        props.setProperty(VaultPackage.NAME_NAME, "test-package");
        props.setProperty(PackageProperties.NAME_USE_BINARY_REFERENCES, "false");
        inf.setProperties(props);

        opts.setMetaInf(inf);
        opts.setMountPath("/");
        opts.setRootPath("/");
        opts.setCompressionLevel(Deflater.BEST_SPEED);

        File tmpFile = File.createTempFile("vaulttest", ".zip");
        OutputStream os = new FileOutputStream(tmpFile);
        Session session = repository.login(new SimpleCredentials(userId, userPwd.toCharArray()));
        packMgr.assemble(admin, opts, os);
        os.close();
        session.logout();

        // Import with admin session


        ImportOptions importOptions = new ImportOptions();
//        importOptions.setImportMode(ImportMode.UPDATE);
        importOptions.setAccessControlHandling(AccessControlHandling.OVERWRITE);
        importOptions.setPatchKeepInRepo(false);

//        importOptions.setAccessControlHandling(AccessControlHandling.MERGE);

        Importer importer = new Importer(importOptions);
        Archive archive = new ZipStreamArchive(new FileInputStream(tmpFile));
        archive.open(true);


        importer.run(archive, admin.getRootNode());
        if (admin.hasPendingChanges()) {
            admin.save();
        }

        archive.close();

        // Check the number of ACEs at the root, the count should not have changed.

        assertNodeExists(userPath);
        AccessControlEntry[] actualAces = AccessControlUtils.getAccessControlList(admin, "/").getAccessControlEntries();
        assertEquals("Expected two ACEs: " + toString(originalAces) + " at / but found: " + toString(actualAces), 2, actualAces.length);
        assertEquals(1, AccessControlUtils.getAccessControlList(admin, null).getAccessControlEntries().length);

        // removing the user's node also removes the bound ACEs at the root in later Oak versions
        clean(userPath);
        tmpFile.delete();

    }

    static String toString(AccessControlEntry[] aces) {
        StringBuilder sb = new StringBuilder();
        for (AccessControlEntry ace : aces) {
            sb.append("ACE for principal ").append(ace.getPrincipal().getName()).append(" with privileges ").append(StringUtils.join(ace.getPrivileges(), ",")).append("\n");
        }
        return sb.toString();
    }
}

