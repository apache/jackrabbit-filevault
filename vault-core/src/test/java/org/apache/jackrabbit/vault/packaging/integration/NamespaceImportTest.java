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
import java.util.Properties;

import javax.jcr.Node;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;

import org.apache.jackrabbit.oak.jcr.Jcr;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.config.DefaultMetaInf;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.packaging.ExportOptions;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.apache.jackrabbit.vault.packaging.impl.JcrPackageManagerImpl;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * Is rather an IT but not relying on {@link IntegrationTestBase}
 */
public class NamespaceImportTest {

    private final static String PREFIX = "prefix";

    private final static String URI1 = "http://one.namespace.io";

    private final static String URI2 = "http://two.namespace.io";

    private Instance i1;

    private Instance i2;

    @Before
    public void setup() throws RepositoryException {
        i1 = new Instance();
        i2 = new Instance();

        // Register namespaces with same prefix but different URIs
        // on different instances, i1, i2
        i1.registerNamespace(PREFIX, URI1);
        i2.registerNamespace(PREFIX, URI2);
    }

    @Test
    public void importClashingNamespace() throws RepositoryException, IOException, PackageException {

        // Set a property with the namespace prefix on instance i1
        i1.getRootNode().addNode("tmp").setProperty("{" + URI1 + "}prop1", "value1");
        i1.admin.save();

        // Export the property from instance i1 in a content package archive

        ExportOptions opts = new ExportOptions();
        DefaultMetaInf inf = new DefaultMetaInf();
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        filter.add(new PathFilterSet("/tmp"));
        inf.setFilter(filter);
        Properties props = new Properties();
        props.setProperty(VaultPackage.NAME_GROUP, "jackrabbit/test");
        props.setProperty(VaultPackage.NAME_NAME, "test-package");
        inf.setProperties(props);
        opts.setMetaInf(inf);

        File tmpFile = File.createTempFile("vaulttest", "zip");
        try (VaultPackage pkg = i1.packMgr.assemble(i1.admin, opts, tmpFile)) {
            Archive archive = pkg.getArchive();

            // Import the archive in the instance i2, with strict mode enabled

            ImportOptions io = new ImportOptions();
            io.setStrict(true);
            i2.packMgr.extract(archive, io, true);

            assertEquals(i2.getRootNode().getNode("tmp").getProperty("{" + URI1 + "}prop1").getString(), "value1");
        } finally {
            tmpFile.delete();
        }
    }

    @Test
    public void importClashingNamespaceOnPath() throws RepositoryException, IOException, PackageException {

        // Set a property with the namespace prefix on instance i1
        i1.getRootNode().addNode("tmp").addNode("{" + URI1 + "}node1").setProperty("test", "value1");
        i1.admin.save();

        // Export the property from instance i1 in a content package archive

        ExportOptions opts = new ExportOptions();
        DefaultMetaInf inf = new DefaultMetaInf();
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        filter.add(new PathFilterSet("/tmp"));
        inf.setFilter(filter);
        Properties props = new Properties();
        props.setProperty(VaultPackage.NAME_GROUP, "jackrabbit/test");
        props.setProperty(VaultPackage.NAME_NAME, "test-package");
        inf.setProperties(props);
        opts.setMetaInf(inf);

        File tmpFile = File.createTempFile("vaulttest", "zip");
        try (VaultPackage pkg = i1.packMgr.assemble(i1.admin, opts, tmpFile)) {
            Archive archive = pkg.getArchive();

            // Import the archive in the instance i2, with strict mode enabled

            ImportOptions io = new ImportOptions();
            io.setStrict(true);
            i2.packMgr.extract(archive, io, true);

            assertEquals(i2.getRootNode().getProperty("tmp/{" + URI1 + "}node1/test").getString(), "value1");

            i2.relogin();
            assertNotEquals(PREFIX, i2.admin.getNamespacePrefix(URI1));
        } finally {
            tmpFile.delete();
        }
    }

    private static final class Instance {

        final Repository repository;

        Session admin;

        final JcrPackageManagerImpl packMgr;

        private Instance()
                throws RepositoryException {
            repository = new Jcr().createRepository();
            admin = repository.login(new SimpleCredentials("admin", "admin".toCharArray()));
            packMgr = new JcrPackageManagerImpl(admin, new String[0]);
        }

        Node getRootNode()
                throws RepositoryException {
            return admin.getRootNode();
        }

        void registerNamespace(String prefix, String uri)
                throws RepositoryException {
            admin.getWorkspace().getNamespaceRegistry().registerNamespace(prefix, uri);
        }

        void relogin() throws RepositoryException {
            admin.logout();
            admin = repository.login(new SimpleCredentials("admin", "admin".toCharArray()));

        }
    }

}
