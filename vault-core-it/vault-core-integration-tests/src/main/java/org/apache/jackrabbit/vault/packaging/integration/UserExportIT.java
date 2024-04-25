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
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import java.io.InputStream;
import java.util.Properties;
import java.util.zip.Deflater;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.vault.fs.api.ImportMode;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.fs.config.DefaultMetaInf;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.fs.filter.DefaultPathFilter;
import org.apache.jackrabbit.vault.fs.io.AccessControlHandling;
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.fs.io.Importer;
import org.apache.jackrabbit.vault.fs.io.ZipStreamArchive;
import org.apache.jackrabbit.vault.packaging.ExportOptions;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.PackageProperties;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.UUID.randomUUID;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class UserExportIT extends IntegrationTestBase {
    private static final Logger log = LoggerFactory.getLogger(UserExportIT.class);
    private static final String TEST_USER_INTERMEDIATE_PATH = "/home/users/_";
    private static final String TEST_USER_REP_USER_NAME = "_6k_test";
    private static final String TEST_USER_ID = "user1";
    private static final String TEST_USER_PATH = String.format("%s/%s", TEST_USER_INTERMEDIATE_PATH, TEST_USER_REP_USER_NAME);
    @Before
    public void setUp() throws Exception {
        super.setUp();
        clean(TEST_USER_PATH);
    }

    @Test
    public void testFileNameEscaping() throws RepositoryException, ConfigurationException, IOException, PackageException {
        assertNull(getTestUser());
        log.info("Test user does not exist");
        User test = ((JackrabbitSession) admin).getUserManager().createUser(TEST_USER_ID, randomUUID().toString(), new PrincipalImpl(TEST_USER_ID), TEST_USER_INTERMEDIATE_PATH);
        admin.move(test.getPath(), TEST_USER_PATH);
        admin.save();
        assertNotNull(getTestUser());
        log.info("Test user created at path {}", test.getPath());
        byte[] serialised = export(admin, TEST_USER_PATH);
        File tmpFile = File.createTempFile("test", ".zip");
        try (FileOutputStream fso = new FileOutputStream(tmpFile)) {
            fso.write(serialised);
        }
        log.info("Test user exported at path {}", tmpFile.getAbsolutePath());
        clean(TEST_USER_PATH);
        assertNull(getTestUser());
        log.info("Test user removed from the repository");
        importPackage(admin, new ByteArrayInputStream(serialised));
        assertNotNull(getTestUser());
        log.info("Test user imported");
    }

    private User getTestUser() throws RepositoryException {
        return (User) ((JackrabbitSession) admin).getUserManager().getAuthorizable(TEST_USER_ID);
    }

    private void importPackage(Session session, InputStream is) throws IOException, ConfigurationException, RepositoryException, PackageException {
        ImportOptions opts = new ImportOptions();
        opts.setAccessControlHandling(AccessControlHandling.OVERWRITE);
        opts.setCugHandling(AccessControlHandling.OVERWRITE);
        opts.setImportMode(ImportMode.REPLACE);
        opts.setPatchKeepInRepo(false);
        opts.setAutoSaveThreshold(-1);
        opts.setStrict(true);
        Importer importer = new Importer(opts);
        try (Archive archive = new ZipStreamArchive(is)) {
            archive.open(false);
            importer.run(archive, session, "/");
            if (importer.hasErrors() && opts.isStrict(true)) {
                throw new RuntimeException("Failed to import");
            }
        }
    }


    private byte[] export(Session session, String authorizablePath) throws IOException, RepositoryException, ConfigurationException {

        PathFilterSet nodeFilters = new PathFilterSet(authorizablePath);
        PathFilterSet propertyFilters = new PathFilterSet(authorizablePath);

        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        filter.add(nodeFilters, propertyFilters);

        DefaultMetaInf inf = new DefaultMetaInf();
        inf.setFilter(filter);

        Properties props = new Properties();
        props.setProperty(VaultPackage.NAME_GROUP, "jackrabbit/sync");
        props.setProperty(VaultPackage.NAME_NAME, randomUUID().toString());
        props.setProperty(VaultPackage.NAME_VERSION, "0.0.1");
        props.setProperty(PackageProperties.NAME_USE_BINARY_REFERENCES, String.valueOf(true));
        inf.setProperties(props);

        ExportOptions opts = new ExportOptions();
        opts.setMetaInf(inf);

        opts.setRootPath("/");
        opts.setMountPath("/");

        opts.setCompressionLevel(Deflater.BEST_SPEED);

        try (ByteArrayOutputStream export = new ByteArrayOutputStream()) {
            packMgr.assemble(session, opts, export);
            export.flush();
            return export.toByteArray();
        }
    }
}
