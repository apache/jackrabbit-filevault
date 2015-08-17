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

import org.apache.commons.io.IOUtils;
import org.apache.jackrabbit.api.ReferenceBinary;
import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.config.DefaultMetaInf;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.packaging.ExportOptions;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.PackageProperties;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.junit.Before;
import org.junit.Test;

import javax.jcr.Binary;
import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * <code>TestEmptyPackage</code>...
 */
public class TestBinarylessExport extends IntegrationTestBase {

    private final String BIG_TEXT = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Suspendisse vel dui in elit venenatis dictum sed nec arcu. Phasellus aliquam imperdiet tincidunt. Vestibulum lacinia mollis mi. Cras non metus.";
    private final String SMALL_TEXT = "Lorem ipsum";
    private final String BINARY_NODE_PATH = "/tmp/binaryless/node";
    private final String BIG_BINARY_PROPERTY = "bigbin";
    private final String SMALL_BINARY_PROPERTY = "smallbin";

    private final String FILE_NODE_PATH = "/tmp/binaryless/file";




    @Before
    public void setup() throws RepositoryException, PackageException, IOException {

        Node binaryNode = JcrUtils.getOrCreateByPath(BINARY_NODE_PATH, "nt:unstructured", admin);

        Binary bigBin = admin.getValueFactory().createBinary(IOUtils.toInputStream(BIG_TEXT, "UTF-8"));
        Property bigProperty = binaryNode.setProperty(BIG_BINARY_PROPERTY, bigBin);
        String referenceBigBinary = ((ReferenceBinary) bigProperty.getBinary()).getReference();
        assertNotNull(referenceBigBinary);

        Binary smallBin = admin.getValueFactory().createBinary(IOUtils.toInputStream(SMALL_TEXT, "UTF-8"));
        Property smallProperty = binaryNode.setProperty(SMALL_BINARY_PROPERTY, smallBin);
        assertFalse(smallProperty.getBinary() instanceof ReferenceBinary);


        JcrUtils.putFile(binaryNode.getParent(), "file", "text/plain", IOUtils.toInputStream(BIG_TEXT, "UTF-8"));
        admin.save();
    }


    @Test
    public void exportBinary() throws RepositoryException, IOException, PackageException {

        String nodePath = BINARY_NODE_PATH;
        String property = BIG_BINARY_PROPERTY;

        ExportOptions opts = new ExportOptions();
        DefaultMetaInf inf = new DefaultMetaInf();
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        filter.add(new PathFilterSet(nodePath));

        inf.setFilter(filter);
        Properties props = new Properties();
        props.setProperty(VaultPackage.NAME_GROUP, "jackrabbit/test");
        props.setProperty(VaultPackage.NAME_NAME, "test-package");
        props.setProperty(PackageProperties.NAME_USE_BINARY_REFERENCES, "true");
        inf.setProperties(props);

        opts.setMetaInf(inf);

        File tmpFile = File.createTempFile("vaulttest", "zip");
        VaultPackage pkg = packMgr.assemble(admin, opts, tmpFile);

        assertNull(pkg.getArchive().getEntry("jcr_root" + BINARY_NODE_PATH + "/" + BIG_BINARY_PROPERTY + ".binary"));
        assertNotNull(pkg.getArchive().getEntry("jcr_root"+ BINARY_NODE_PATH + "/" + SMALL_BINARY_PROPERTY + ".binary"));


        admin.getNode(nodePath).remove();

        pkg.extract(admin, getDefaultOptions());

        assertNodeExists(nodePath);

        long actualBinarySize = ((Property) admin.getItem(nodePath + "/" + property)).getBinary().getSize();

        assertEquals(BIG_TEXT.getBytes("UTF-8").length, actualBinarySize);

        pkg.close();
        tmpFile.delete();
    }


    @Test
    public void exportFile() throws RepositoryException, IOException, PackageException {

        String nodePath = FILE_NODE_PATH;

        ExportOptions opts = new ExportOptions();
        DefaultMetaInf inf = new DefaultMetaInf();
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        filter.add(new PathFilterSet(nodePath));

        inf.setFilter(filter);
        Properties props = new Properties();
        props.setProperty(VaultPackage.NAME_GROUP, "jackrabbit/test");
        props.setProperty(VaultPackage.NAME_NAME, "test-package");
        props.setProperty(PackageProperties.NAME_USE_BINARY_REFERENCES, "true");
        inf.setProperties(props);

        opts.setMetaInf(inf);

        File tmpFile = File.createTempFile("vaulttest", "zip");
        VaultPackage pkg = packMgr.assemble(admin, opts, tmpFile);

        assertTrue(pkg.getArchive().getEntry("jcr_root" + FILE_NODE_PATH).isDirectory());

        admin.getNode(nodePath).remove();

        pkg.extract(admin, getDefaultOptions());

        assertNodeExists(nodePath);

        Node node = admin.getNode(nodePath);

        InputStream stream = JcrUtils.readFile(node);

        String actualText = IOUtils.toString(stream, "UTF-8");
        assertEquals(BIG_TEXT, actualText);

        pkg.close();
        tmpFile.delete();
    }
}