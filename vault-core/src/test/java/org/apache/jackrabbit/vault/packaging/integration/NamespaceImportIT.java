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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import javax.jcr.NamespaceException;
import javax.jcr.NamespaceRegistry;
import javax.jcr.Node;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;

import org.apache.jackrabbit.api.JackrabbitRepository;
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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests namespace aware node/property imports
 */
public class NamespaceImportIT extends IntegrationTestBase {

    private final static String PREFIX = "prefix";

    private final static String URI1 = "http://one.namespace.io";

    private final static String URI2 = "http://two.namespace.io";

    private Instance sourceOakRepository;

    @Before
    public void setUp() throws Exception {
        sourceOakRepository = new Instance(); // source instance

        // Register namespaces with same prefix but different URIs
        // on different instances
        sourceOakRepository.registerNamespace(PREFIX, URI1);
        super.setUp();
        NamespaceRegistry nsRegistry = admin.getWorkspace().getNamespaceRegistry();
        try {
            if (URI1.equals(nsRegistry.getURI(PREFIX))) {
                throw new IllegalStateException("prefix already registered for a different uri");
            }
        } catch (NamespaceException e) {
            nsRegistry.registerNamespace(PREFIX, URI2);
        }
    }

    @After
    public void tearDown() throws Exception {
        sourceOakRepository.admin.logout();
        if (sourceOakRepository.repository instanceof JackrabbitRepository) {
            JackrabbitRepository.class.cast(sourceOakRepository.repository).shutdown();
        }
        super.tearDown();
    }

    @Test
    public void importClashingNamespace() throws RepositoryException, IOException, PackageException {

        // Set a property with the namespace prefix on instance i1
        sourceOakRepository.getRootNode().addNode("tmp").setProperty("{" + URI1 + "}prop1", "value1");
        sourceOakRepository.admin.save();

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
        try (VaultPackage pkg = sourceOakRepository.packMgr.assemble(sourceOakRepository.admin, opts, tmpFile)) {
            Archive archive = pkg.getArchive();

            // Import the archive in the target repo, with strict mode enabled
            ImportOptions io = new ImportOptions();
            io.setStrict(true);
            packMgr.extract(archive, io, true);

            assertEquals(admin.getRootNode().getNode("tmp").getProperty("{" + URI1 + "}prop1").getString(), "value1");
        } finally {
            tmpFile.delete();
        }
    }

    @Test
    public void importClashingNamespaceOnPath() throws RepositoryException, IOException, PackageException {

        // Set a property with the namespace prefix on instance i1
        sourceOakRepository.getRootNode().addNode("tmp").addNode("{" + URI1 + "}node1").setProperty("test", "value1");
        sourceOakRepository.admin.save();

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
        try (VaultPackage pkg = sourceOakRepository.packMgr.assemble(sourceOakRepository.admin, opts, tmpFile)) {
            Archive archive = pkg.getArchive();

            // Import the archive in another instance with strict mode enabled
            ImportOptions io = new ImportOptions();
            io.setStrict(true);
            packMgr.extract(archive, io, true);

            assertEquals(admin.getRootNode().getProperty("tmp/{" + URI1 + "}node1/test").getString(), "value1");

            Session admin2 = repository.login(new SimpleCredentials("admin", "admin".toCharArray()));
            assertNotEquals(PREFIX, admin2.getNamespacePrefix(URI1));
            admin2.logout();
        } finally {
            tmpFile.delete();
        }
    }

    @Test
    public void importUndeclaredNamespaceOnDocViewPath() throws IOException, PackageException, RepositoryException {
        // namespace declared in package but not in affected docview file
        try (VaultPackage pkg = extractVaultPackageStrict("/test-packages/namespace.zip")) {
            assertNotNull(pkg);
        }
    }


    @Test
    public void testBadNamespaceNames() throws RepositoryException, IOException, PackageException {
        extractVaultPackageStrict("/test-packages/badnamespacenames.zip");

        assertNodeExists("/tmp/badnamespacenames");

        String prefixBar = admin.getNamespacePrefix("bar");
        assertNodeExists("/tmp/badnamespacenames/" + prefixBar + ":child");
        String prefixQux = admin.getNamespacePrefix("qux");
        assertNodeExists("/tmp/badnamespacenames/" + prefixBar + ":child/" + prefixQux + ":child");
        // Still fails: why?
        // String prefixFoo = admin.getNamespacePrefix("foo");
        // assertProperty("/tmp/badnamespacenames/" + prefixBar + ":child/" + prefixQux + ":child/" + prefixFoo + ":someproperty", "xyz");
    }

    /** Simple Oak repository wrapper */
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

    }

}
