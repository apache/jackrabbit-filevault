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
package org.apache.jackrabbit.vault.sync.impl;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.Calendar;
import java.util.Collections;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import javax.jcr.nodetype.NodeType;

import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.jackrabbit.vault.packaging.integration.IntegrationTestBase;
import org.apache.jackrabbit.vault.util.MimeTypes;
import org.awaitility.Awaitility;
import org.hamcrest.Matchers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class VaultSyncServiceImplIT extends IntegrationTestBase {

    @Rule
    public TemporaryFolder tmpFolder = new TemporaryFolder();

    @Test
    public void testAddRemoveFileFromNonVltCheckoutFolder() throws RepositoryException, IOException, InterruptedException {
        Path syncRootDirectory1 = tmpFolder.newFolder().toPath();
        Session newAdminSession = repository.login(new SimpleCredentials("admin", "admin".toCharArray()));
        // setup .vlt-sync-filter.xml in advance, otherwise changes to it are detected potentially later than the ones to the actual file
        Path filterFile = syncRootDirectory1.resolve(".vlt-sync-filter.xml");
        // modify filter
        try (InputStream input = this.getClass().getResourceAsStream("filter.xml")) {
            Files.copy(input, filterFile, StandardCopyOption.REPLACE_EXISTING);
        }
        // check for file not yet being there in repo
        assertNodeMissing("/testroot/testfile.txt");
       
        // create new session
        VaultSyncServiceImpl service = new VaultSyncServiceImpl(newAdminSession, true, 50, Collections.singleton(syncRootDirectory1.toFile()));
        try {
            Path syncDirectory = syncRootDirectory1.resolve("testroot");
            try (InputStream input = this.getClass().getResourceAsStream("testfile1.txt")) {
                Files.createDirectories(syncDirectory);
                Files.copy(input, syncDirectory.resolve("testfile.txt"));
            }
            // await node being added to repo
            Awaitility.await().until(() -> {
                admin.refresh(false);
                return admin.nodeExists("/testroot/testfile.txt");
            });
            Node fileNode = admin.getNode("/testroot/testfile.txt");
            Calendar lastModified1 = JcrUtils.getLastModified(fileNode);

            // wait until the change on the JCR has been processed and ignored by VaultSyncService
            Thread.sleep(200); // must be longer than poll intervall

            // now change file
            try (InputStream input = this.getClass().getResourceAsStream("testfile2.txt")) {
                Files.copy(input, syncDirectory.resolve("testfile.txt"), StandardCopyOption.REPLACE_EXISTING);
            }
            // await last modification of node being changed
            Awaitility.await().until(() -> {
                admin.refresh(false);
                return JcrUtils.getLastModified(fileNode);
            }, Matchers.greaterThan(lastModified1));
        } finally {
            Awaitility.reset();
            service.deactivate();
        }
    }

    @Test
    public void testSyncOnceFromRepository() throws RepositoryException, IOException, InterruptedException {
        Node testRootNode = admin.getNode("/").addNode("testroot", NodeType.NT_FOLDER);
        try (InputStream input = this.getClass().getResourceAsStream("testfile1.txt")) {
            JcrUtils.putFile(testRootNode, "testfile", MimeTypes.APPLICATION_OCTET_STREAM, input);
        }
        admin.save();
        Path syncRootDirectory1 = tmpFolder.newFolder().toPath();
        Path filterFile = syncRootDirectory1.resolve(".vlt-sync-filter.xml");
        try (InputStream input = this.getClass().getResourceAsStream("filter.xml")) {
            Files.copy(input, filterFile, StandardCopyOption.REPLACE_EXISTING);
        }
        Path propertiesFile = syncRootDirectory1.resolve(".vlt-sync-config.properties");
        try (InputStream input = this.getClass().getResourceAsStream(".vlt-sync-config-jcr2fs-once.properties")) {
            Files.copy(input, propertiesFile, StandardCopyOption.REPLACE_EXISTING);
        }
        Session newAdminSession = repository.login(new SimpleCredentials("admin", "admin".toCharArray()));
        // create new session
        VaultSyncServiceImpl service = new VaultSyncServiceImpl(newAdminSession, true, 250, Collections.singleton(syncRootDirectory1.toFile()));
        try {
            // wait a bit
            Thread.sleep(5000);
            Path syncFile = syncRootDirectory1.resolve(Paths.get("testroot", "testfile"));
            Awaitility.await().until(() -> Files.exists(syncFile));
            FileTime lastModified1 = Files.getLastModifiedTime(syncFile);
            // now modify node
            try (InputStream input = this.getClass().getResourceAsStream("testfile2.txt")) {
                JcrUtils.putFile(testRootNode, "testfile", MimeTypes.APPLICATION_OCTET_STREAM, input);
            }
            admin.save();
            Awaitility.await().until(() -> Files.getLastModifiedTime(syncFile), Matchers.greaterThan(lastModified1));
        } finally {
            service.deactivate();
        }
    }
    
}
