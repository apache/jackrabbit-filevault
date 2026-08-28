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

import org.apache.jackrabbit.api.JackrabbitRepository;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.config.DefaultMetaInf;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.integration.support.RepositoryProvider.RepositoryWithMetadata;
import org.apache.jackrabbit.vault.packaging.ExportOptions;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.apache.jackrabbit.vault.packaging.impl.JcrPackageManagerImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.jcr.NamespaceException;
import javax.jcr.NamespaceRegistry;
import javax.jcr.Node;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;

/**
 * Tests namespace aware node/property imports when namespace prefix mapping has changed in the target environment but
 * not in the source environment, but the old prefix is still registered in the target environment using a throwaway URI
 * for maintaining referential consistency with old content.
 */
public class NamespaceRemappingIT extends IntegrationTestBase {

    private final static String ORIG_PREFIX = "ns2";

    private final static String DESIRED_PREFIX = "exiffy";

    private final static String DESIRED_URI = "http://one.namespace.io";

    private final static String THROWAWAY_URI = "http://one.namespace.io/DELETEME";

    private Instance sourceOakRepository = null;

    @Before
    public void setUp() throws Exception {
        assumeTrue(isOak());
        sourceOakRepository = new Instance(); // source instance

        // Register namespaces with same prefix but different URIs
        // on different instances
        sourceOakRepository.registerNamespace(ORIG_PREFIX, DESIRED_URI);
        super.setUp();
        NamespaceRegistry nsRegistry = admin.getWorkspace().getNamespaceRegistry();
        try {
            if (DESIRED_URI.equals(nsRegistry.getURI(ORIG_PREFIX))) {
                throw new IllegalStateException("prefix already registered for a different uri");
            }
        } catch (NamespaceException e) {
            nsRegistry.registerNamespace(DESIRED_PREFIX, DESIRED_URI);
            nsRegistry.registerNamespace(ORIG_PREFIX, THROWAWAY_URI);
        }
    }

    @After
    public void tearDown() throws Exception {
        if (sourceOakRepository != null) {
            sourceOakRepository.admin.logout();
            if (sourceOakRepository.repository instanceof JackrabbitRepository) {
                JackrabbitRepository.class.cast(sourceOakRepository.repository).shutdown();
            }
        }
        super.tearDown();
    }

    @Test
    public void importClashingNamespace() throws RepositoryException, IOException, PackageException {

        // Set a property with the namespace prefix on instance i1 and on the target instance
        Node srcTmpNode = sourceOakRepository.getRootNode().addNode("tmp");
        srcTmpNode.setProperty("{" + DESIRED_URI + "}prop1", "value1");
        srcTmpNode.setProperty("prop2", "value2");
        sourceOakRepository.admin.save();

        Node dstTmpNode = admin.getRootNode().addNode("tmp");
        dstTmpNode.setProperty("{" + THROWAWAY_URI + "}prop1", "value1");
        dstTmpNode.addNode("tmp").setProperty("prop2", "value1");
        admin.save();

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
            assertEquals(admin.getRootNode().getNode("tmp").getProperty("prop2").getString(), "value2");
            assertEquals(admin.getRootNode().getNode("tmp").getProperty("{" + DESIRED_URI + "}prop1").getString(), "value1");
        } finally {
            tmpFile.delete();
        }
    }

    /** Simple Oak repository wrapper */
    private static final class Instance implements Closeable {

        private final RepositoryWithMetadata repositoryWithMetadata;

        final Repository repository;

        Session admin;

        final JcrPackageManagerImpl packMgr;

        private Instance()
                throws RepositoryException, IOException {
            repositoryWithMetadata = repositoryProvider.createRepository(false, false);
            repository = repositoryWithMetadata.getRepository();
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

        @Override
        public void close() throws IOException {
            admin.logout();
            try {
                repositoryProvider.closeRepository(repositoryWithMetadata);
            } catch (RepositoryException e) {
                throw new IOException(e);
            }
        }

    }

}
