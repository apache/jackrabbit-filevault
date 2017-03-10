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
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.config.DefaultMetaInf;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.fs.filter.DefaultPathFilter;
import org.apache.jackrabbit.vault.packaging.ExportOptions;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.junit.Before;
import org.junit.Test;

/**
 * {@code TestFilteredExport} cover testing the filtering of properties
 */
public class TestFilteredPropertyExport extends IntegrationTestBase {

    @Before
    public void setUp() throws Exception {
        super.setUp();
        setupTmpFooBarWithProperties(admin);
        admin.save();
    }

    @Test
    public void noPropertyFiltered() throws IOException, RepositoryException, PackageException {
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        filter.add(new PathFilterSet("/tmp"));
        // export and extract
        File pkgFile = assemblePackage(filter);
        clean("/tmp");
        packMgr.open(pkgFile).extract(admin, getDefaultOptions());
        // validate the extracted content
        assertPropertiesExist("/tmp", "p1", "p2", "p3");
        assertPropertiesExist("/tmp/foo", "p1", "p2", "p3");
        assertPropertiesExist("/tmp/foo/bar", "p1", "p2", "p3");
    }

    @Test
    public void filterPropertyP1OnFoo() throws IOException, RepositoryException, PackageException {
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        filter.add(new PathFilterSet("/tmp"));

        PathFilterSet properties = new PathFilterSet("/tmp");
        properties.addExclude(new DefaultPathFilter("/tmp/foo/p1"));
        filter.addPropertyFilterSet(properties);
        // export and extract
        File pkgFile = assemblePackage(filter);
        clean("/tmp");
        packMgr.open(pkgFile).extract(admin, getDefaultOptions());
        // validate the extracted content
        assertPropertiesExist("/tmp", "p1", "p2", "p3");
        assertPropertiesExist("/tmp/foo", "p2", "p3");
        assertPropertiesMissg("/tmp/foo", "p1");
        assertPropertiesExist("/tmp/foo/bar", "p1", "p2", "p3");
    }

    @Test
    public void filterPropertyPxOnFoo() throws IOException, RepositoryException, PackageException {
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        filter.add(new PathFilterSet("/tmp"));

        PathFilterSet properties = new PathFilterSet("/tmp");
        properties.addExclude(new DefaultPathFilter("/tmp/foo/p.*"));
        filter.addPropertyFilterSet(properties);
        // export and extract
        File pkgFile = assemblePackage(filter);
        clean("/tmp");
        packMgr.open(pkgFile).extract(admin, getDefaultOptions());
        // validate the extracted content
        assertPropertiesExist("/tmp", "p1", "p2", "p3");
        assertPropertiesMissg("/tmp/foo", "p1", "p2", "p3");
        assertPropertiesExist("/tmp/foo/bar", "p1", "p2", "p3");
    }

    @Test
    public void filterRelativeProperties() throws IOException, RepositoryException, PackageException {
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        filter.add(new PathFilterSet("/tmp"));

        PathFilterSet properties = new PathFilterSet("/tmp");
        properties.addExclude(new DefaultPathFilter(".*/p1"));
        filter.addPropertyFilterSet(properties);
        // export and extract
        File pkgFile = assemblePackage(filter);
        clean("/tmp");
        packMgr.open(pkgFile).extract(admin, getDefaultOptions());
        // validate the extracted content
        assertPropertiesExist("/tmp", "p2", "p3");
        assertPropertiesMissg("/tmp", "p1");
        assertPropertiesExist("/tmp/foo", "p2", "p3");
        assertPropertiesMissg("/tmp/foo", "p1");
        assertPropertiesExist("/tmp/foo/bar", "p2", "p3");
        assertPropertiesMissg("/tmp/foo/bar", "p1");
    }


    /**
     * Setup the path /tmp/foo/bar with properties set at each level
     */
    private void setupTmpFooBarWithProperties(Session session)
            throws RepositoryException {
        Node root = session.getRootNode();
        Node tmp = setupProperties(root.addNode("tmp"));
        Node foo = setupProperties(tmp.addNode("foo"));
        Node bar = setupProperties(foo.addNode("bar"));
    }

    private Node setupProperties(Node node)
            throws RepositoryException {
        node.setProperty("p1", "v1");
        node.setProperty("p2", "v2");
        node.setProperty("p3", "v3");
        return node;
    }

    private File assemblePackage(WorkspaceFilter filter)
            throws IOException, RepositoryException {

        File tmpFile = File.createTempFile("vaulttest", "zip");

        ExportOptions options = new ExportOptions();
        DefaultMetaInf meta = new DefaultMetaInf();
        meta.setFilter(filter);

        Properties props = new Properties();
        props.setProperty(VaultPackage.NAME_GROUP, "jackrabbit/test");
        props.setProperty(VaultPackage.NAME_NAME, "filtered-export-package");
        meta.setProperties(props);

        options.setMetaInf(meta);

        packMgr.assemble(admin, options, tmpFile).close();
        return tmpFile;
    }

    private void assertPropertiesExist(String rootPath, String... propNames)
            throws RepositoryException {
        for (String propName : propNames) {
            String propPath = String.format("%s/%s", rootPath, propName);
            assertPropertyExists(propPath);
        }
    }

    private void assertPropertiesMissg(String rootPath, String... propNames)
            throws RepositoryException {
        for (String propName : propNames) {
            String propPath = String.format("%s/%s", rootPath, propName);
            assertPropertyMissing(propPath);
        }
    }

}