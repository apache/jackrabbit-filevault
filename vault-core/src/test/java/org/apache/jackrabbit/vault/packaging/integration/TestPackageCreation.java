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

import org.apache.jackrabbit.vault.packaging.PackageException;
import org.junit.Test;

import static org.junit.Assert.fail;

/**
 * <code>TestPackageInstall</code>...
 */
public class TestPackageCreation extends IntegrationTestBase {

    public static String[] GROUP_NAMES = {"foo", "foo-zoo", "foo.zoo", "jcr:foo", "foo/zoo"};
    public static String[] ILLEGAL_GROUP_NAMES = {"foo ", " foo", ":foo"};

    public static String[] PACKAGE_NAMES = {"bar", "bar.zar", "bar-zar", "jcr:bar"};
    public static String[] ILLEGAL_PACKAGE_NAMES = {"bar ", " bar", ":bar", "jcr/bar"};

    public static String[] ILLEGAL_VERSION_NAMES = {"1.0 ", " 1.0", ":1.0", "jcr:1.0", "1/0"};

    @Test
    public void testCreateGroup() throws RepositoryException, IOException, PackageException {
        for (String name: GROUP_NAMES) {
            packMgr.create(name, "bar");
            assertNodeExists("/etc/packages/" + name + "/bar.zip");
        }
    }

    @Test
    public void testCreate() throws RepositoryException, IOException, PackageException {
        for (String name: PACKAGE_NAMES) {
            packMgr.create("foo", name);
            assertNodeExists("/etc/packages/foo/" + name + ".zip");
        }
    }

    @Test
    public void testCreateWithVersion() throws RepositoryException, IOException, PackageException {
        packMgr.create("foo", "bar", "3.1.2");
        assertNodeExists("/etc/packages/foo/bar-3.1.2.zip");
    }

    @Test
    public void testCreateIllegalGroup() throws RepositoryException, IOException, PackageException {
        for (String name: ILLEGAL_GROUP_NAMES) {
            try {
                packMgr.create(name, "bar", "3.1.2");
                fail("Illegal group name must fail: " + name);
            } catch (RepositoryException e) {
                // ok
            }
        }
    }

    @Test
    public void testCreateIllegalName() throws RepositoryException, IOException, PackageException {
        for (String name: ILLEGAL_PACKAGE_NAMES) {
            try {
                packMgr.create("foo", name, "3.1.2");
                fail("Illegal package name must fail: " + name);
            } catch (RepositoryException e) {
                // ok
            }
        }
    }

    @Test
    public void testCreateIllegalVersions() throws RepositoryException, IOException, PackageException {
        for (String name: ILLEGAL_VERSION_NAMES) {
            try {
                packMgr.create("foo", "bar", name);
                fail("Illegal version must fail: " + name);
            } catch (RepositoryException e) {
                // ok
            }
        }
    }

    @Test
    public void testUploadIllegal() throws RepositoryException, IOException, PackageException {
        try {
            packMgr.upload(getStream("testpackages/tmp_illegal.zip"), false);
            fail("Uploading a package with an illegal name must fail.");
        } catch (RepositoryException e) {
            // ok
        }
    }

}