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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.jcr.Binary;
import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.ValueFactory;
import javax.jcr.ValueFormatException;
import javax.jcr.lock.LockException;
import javax.jcr.nodetype.ConstraintViolationException;
import javax.jcr.version.VersionException;

import org.apache.commons.io.IOUtils;
import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.ReferenceBinary;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.config.DefaultMetaInf;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.packaging.ExportOptions;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.PackageProperties;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class BinaryPropertiesIT extends IntegrationTestBase {

    private final static String SMALL_TEXT = "Lorem ipsum"; // less than 16KB data is not stored in the blob store
    private final static String SMALL_TEXT2 = "The quick brown fox";

    private final static String BIG_BINARY_PROPERTY = "bigbin";
    private final static String SMALL_BINARY_PROPERTY = "smallbin";
    private final static String BIG_BINARY_MV_PROPERTY = "bigbin-mv";
    private final static String SMALL_BINARY_MV_PROPERTY = "smallbin-mv";

    private String fileNodePath = "/tmp/binaryless/file";
    private String exportFileNodePath = "/tmp/binaryless/file";
    private String binaryNodePath = "/tmp/binaryless/node";
    private String exportBinaryNodePath = "/tmp/binaryless/node";

    private final static int BIG_TEXT_LENGTH = 0x1000 * 64; // 64 KB
    private final static String BIG_TEXT;
    private final static String BIG_TEXT2;
    static {
        StringBuilder buffer = new StringBuilder(BIG_TEXT_LENGTH);
        buffer.append("0123456789abcdef");
        while (buffer.length() < BIG_TEXT_LENGTH) {
            buffer.append(buffer, 0, buffer.length());
        }
        BIG_TEXT = buffer.toString();
        BIG_TEXT2 = buffer.append("2").toString();
    }

    @Rule
    public TemporaryFolder tmpFolder = new TemporaryFolder();

    private Node binaryNode;

    @Parameter
    public boolean useBinaryReferences; // if true, binaries should not be part of the package (only their references)

    public boolean testAuthorizables = Boolean.getBoolean("testAuthorizables");

    @Parameters(name = "useBinaryReferences:{0}")
    public static Object[] data() {
        return new Object[] { Boolean.TRUE, Boolean.FALSE };
    }

    @BeforeClass
    public static void initRepository() throws RepositoryException, IOException {
        initRepository(true, false); // always use BlobStore with Oak
    }

    @Before
    public void setup() throws RepositoryException, PackageException, IOException {
        // test only works for Jackrabbit 2.0 or Oak with FileDataStore (as it relies on ReferenceBinary), this is assured though by the overwritten initRepository method

        if (testAuthorizables && admin instanceof JackrabbitSession) {
            UserManager um = ((JackrabbitSession)admin).getUserManager();
            Authorizable adminAuthorizable = um.getAuthorizable(admin.getUserID());
            // assertNull(adminAuthorizable.getPath());
            exportBinaryNodePath = adminAuthorizable.getPath();
            binaryNodePath = exportBinaryNodePath + "/node";
            exportFileNodePath = adminAuthorizable.getPath();
            fileNodePath = exportFileNodePath + "/file";
        }

        binaryNode = JcrUtils.getOrCreateByPath(binaryNodePath, "nt:unstructured", admin);
        Property bigProperty = setBinaryProperty(binaryNode, BIG_BINARY_PROPERTY, BIG_TEXT);
        Property smallProperty = setBinaryProperty(binaryNode, SMALL_BINARY_PROPERTY, SMALL_TEXT);
        setBinaryMultivalueProperty(binaryNode, BIG_BINARY_MV_PROPERTY, BIG_TEXT, BIG_TEXT2);
        setBinaryMultivalueProperty(binaryNode, SMALL_BINARY_MV_PROPERTY, SMALL_TEXT, SMALL_TEXT2);

        // some basic checks to make sure that reference binaries are enabled in the repository
        String referenceBigBinary = ((ReferenceBinary) bigProperty.getBinary()).getReference();
        assertNotNull(referenceBigBinary);
        if (isOak()) {
            // every binary is ReferenceBinary (but does return null for small binaries via ReferenceBinary.getReference())
            assertTrue(smallProperty.getBinary() instanceof ReferenceBinary);
        } else {
            // only large binaries are ReferenceBinary objects in JR2
            assertFalse(smallProperty.getBinary() instanceof ReferenceBinary);
        }
        JcrUtils.putFile(binaryNode.getParent(), "file", "text/plain", IOUtils.toInputStream(BIG_TEXT, StandardCharsets.UTF_8));
        admin.save();
    }

    private Property setBinaryProperty(Node node, String name, String value) throws ValueFormatException, VersionException, LockException, ConstraintViolationException, RepositoryException {
        Binary binary = admin.getValueFactory().createBinary(IOUtils.toInputStream(value, StandardCharsets.UTF_8));
        try {
            return node.setProperty(name, binary);
        } finally {
            binary.dispose();
        }
    }

    private Property setBinaryMultivalueProperty(Node node, String name, String... values) throws ValueFormatException, VersionException, LockException, ConstraintViolationException, RepositoryException {
        ValueFactory valueFactory = admin.getValueFactory();
        List<Binary> binaries = new ArrayList<>(values.length);
        for (String value : values) {
            binaries.add(valueFactory.createBinary(IOUtils.toInputStream(value, StandardCharsets.UTF_8)));
        }
        try {
            return node.setProperty(name, binaries.stream().map(b -> valueFactory.createValue(b)).toArray(Value[]::new), PropertyType.BINARY);
        } finally {
            binaries.stream().forEach(Binary::dispose);
        }
    }

    private void assertBinaryProperty(String path, String input) throws RepositoryException, IOException {
        assertBinaryProperty(path, -1, input);
    }

    private void assertBinaryProperty(String path, int index, String expectedInput) throws RepositoryException, IOException {
        try (InputStream input = IOUtils.toInputStream(expectedInput, StandardCharsets.UTF_8)) {
            assertBinaryProperty(path, index, input);
        }
    }

    private void assertBinaryProperty(String path, int index, InputStream expectedInput) throws RepositoryException, IOException {
        Property property = admin.getProperty(path);
        final Value value;
        if (index == -1) {
            value = property.getValue();
        } else {
            Value[] values = property.getValues();
            if (values.length <= index) {
                Assert.fail("Property " + path + " does only contain " + values.length + " items");
            }
            value = values[index];
        }
        Binary binary = value.getBinary();
        try (InputStream actualInput =  binary.getStream()) {
            assertTrue("Content not equal", IOUtils.contentEquals(expectedInput, actualInput));
        }
        finally {
            binary.dispose();
        }
    }

    private VaultPackage assemblePackage(String rootNodePath) throws IOException, RepositoryException {
        ExportOptions opts = new ExportOptions();
        DefaultMetaInf inf = new DefaultMetaInf();
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        filter.add(new PathFilterSet(rootNodePath));

        inf.setFilter(filter);
        Properties props = new Properties();
        props.setProperty(VaultPackage.NAME_GROUP, "jackrabbit/test");
        props.setProperty(VaultPackage.NAME_NAME, "test-package");
        props.setProperty(PackageProperties.NAME_USE_BINARY_REFERENCES, Boolean.toString(useBinaryReferences));
        inf.setProperties(props);
        opts.setMetaInf(inf);
        File tmpFile = tmpFolder.newFile("vaulttest.zip");
        return packMgr.assemble(admin, opts, tmpFile);
    }

    @Test
    public void exportBinary() throws RepositoryException, IOException, PackageException {
        String nodePath = binaryNodePath;
        try (VaultPackage pkg = assemblePackage(exportBinaryNodePath)) {
            if (useBinaryReferences) {
                // make sure that only non-reference binaries are in dedicated artifacts
                assertNull(pkg.getArchive().getEntry("jcr_root" + binaryNodePath + "/" + BIG_BINARY_PROPERTY + ".binary"));
                assertNull(pkg.getArchive().getEntry("jcr_root" + binaryNodePath + "/" + BIG_BINARY_MV_PROPERTY + "[0].binary"));
                assertNull(pkg.getArchive().getEntry("jcr_root" + binaryNodePath + "/" + BIG_BINARY_MV_PROPERTY + "[1].binary"));
                assertNotNull(pkg.getArchive().getEntry("jcr_root"+ binaryNodePath + "/" + SMALL_BINARY_PROPERTY + ".binary"));
                assertNotNull(pkg.getArchive().getEntry("jcr_root"+ binaryNodePath + "/" + SMALL_BINARY_MV_PROPERTY + "[0].binary"));
                assertNotNull(pkg.getArchive().getEntry("jcr_root"+ binaryNodePath + "/" + SMALL_BINARY_MV_PROPERTY + "[1].binary"));
            } else {
                assertNotNull(pkg.getArchive().getEntry("jcr_root" + binaryNodePath + "/" + BIG_BINARY_PROPERTY + ".binary"));
                assertNotNull(pkg.getArchive().getEntry("jcr_root" + binaryNodePath + "/" + BIG_BINARY_MV_PROPERTY + "[0].binary"));
                assertNotNull(pkg.getArchive().getEntry("jcr_root" + binaryNodePath + "/" + BIG_BINARY_MV_PROPERTY + "[1].binary"));
                assertNotNull(pkg.getArchive().getEntry("jcr_root"+ binaryNodePath + "/" + SMALL_BINARY_PROPERTY + ".binary"));
                assertNotNull(pkg.getArchive().getEntry("jcr_root"+ binaryNodePath + "/" + SMALL_BINARY_MV_PROPERTY + "[0].binary"));
                assertNotNull(pkg.getArchive().getEntry("jcr_root"+ binaryNodePath + "/" + SMALL_BINARY_MV_PROPERTY + "[1].binary"));
            }
            clean(nodePath);
            pkg.extract(admin, getDefaultOptions());
        }
        assertNodeExists(nodePath);
        assertBinaryProperty(nodePath + "/" + BIG_BINARY_PROPERTY, BIG_TEXT);
        assertBinaryProperty(nodePath + "/" + BIG_BINARY_MV_PROPERTY, 0, BIG_TEXT);
        assertBinaryProperty(nodePath + "/" + BIG_BINARY_MV_PROPERTY, 1, BIG_TEXT2);
        assertBinaryProperty(nodePath + "/" + SMALL_BINARY_PROPERTY, SMALL_TEXT);
        assertBinaryProperty(nodePath + "/" + SMALL_BINARY_MV_PROPERTY, 0, SMALL_TEXT);
        assertBinaryProperty(nodePath + "/" + SMALL_BINARY_MV_PROPERTY, 1, SMALL_TEXT2);
    }

    @Test
    public void exportMultiValueBinaryWithPotentiallyMixedReferences() throws ValueFormatException, VersionException, LockException, ConstraintViolationException, RepositoryException, IOException, PackageException {
        setBinaryMultivalueProperty(binaryNode, "mixedMVBinary", SMALL_TEXT, BIG_TEXT);
        if (useBinaryReferences) {
            // short text is a non-reference binary but big text is a reference binary
            Assert.assertThrows(ValueFormatException.class, () -> assemblePackage(binaryNode.getPath()));
        } else {
            String nodePath = binaryNode.getPath();
            try (VaultPackage pkg = assemblePackage(nodePath)) {
                clean(binaryNode.getPath());
                pkg.extract(admin, getDefaultOptions());
            }
            assertNodeExists(nodePath);
            assertBinaryProperty(nodePath + "/mixedMVBinary", 0, SMALL_TEXT);
            assertBinaryProperty(nodePath + "/mixedMVBinary", 1, BIG_TEXT);
        }
    }

    @Test
    public void exportFile() throws RepositoryException, IOException, PackageException {
        String nodePath = fileNodePath;
        try (VaultPackage pkg = assemblePackage(exportFileNodePath)) {
            Archive.Entry en = pkg.getArchive().getEntry("jcr_root" + fileNodePath);
            assertNotNull("not found: " + "jcr_root" + fileNodePath, en);
            if (useBinaryReferences) {
                assertTrue(en.isDirectory());
            } else {
                assertFalse(en.isDirectory());
            }
            clean(nodePath);
            pkg.extract(admin, getDefaultOptions());
        }
        assertNodeExists(nodePath);
        Node node = admin.getNode(nodePath);
        try (InputStream stream = JcrUtils.readFile(node)) {
            String actualText = IOUtils.toString(stream, "UTF-8");
            assertEquals(BIG_TEXT, actualText);
        }
    }

    /**
     * Tests if the same package installed twice does not report and update. See JCRVLT-108
     */
    @Test
    public void importTwice() throws RepositoryException, IOException, PackageException {
        String nodePath = binaryNodePath;

        try (VaultPackage pkg = assemblePackage(nodePath)) {
            clean(nodePath);
            ImportOptions io = getDefaultOptions();
            TrackingListener listener = new TrackingListener(null);
            io.setListener(listener);
            // extract
            pkg.extract(admin, io);
            assertEquals("A", listener.getActions().get(binaryNodePath));
            // and extract again
            listener = new TrackingListener(null);
            io.setListener(listener);
            pkg.extract(admin, io);
            assertEquals("U", listener.getActions().get(binaryNodePath));
        }
    }
}