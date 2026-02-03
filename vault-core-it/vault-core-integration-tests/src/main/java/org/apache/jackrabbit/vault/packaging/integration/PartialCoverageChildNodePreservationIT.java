/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.jackrabbit.vault.packaging.integration;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.apache.commons.io.IOUtils;
import org.apache.jackrabbit.commons.cnd.CndImporter;
import org.apache.jackrabbit.vault.fs.api.ImportMode;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.fs.config.DefaultMetaInf;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.fs.filter.DefaultPathFilter;
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.fs.io.ZipArchive;
import org.apache.jackrabbit.vault.packaging.ExportOptions;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Integration tests to demonstrate the problem of JCRVLT-830
 */
@FixMethodOrder
public class PartialCoverageChildNodePreservationIT extends IntegrationTestBase {

    private static final List<String[]> NODES = Arrays.asList(
            new String[] {"/content", "sling:Folder"},
            new String[] {"/content/dam", "sling:Folder"},
            new String[] {"/content/dam/qcom", "sling:Folder"},
            new String[] {"/content/dam/qcom/content-fragments", "sling:Folder"},
            new String[] {"/content/dam/qcom/content-fragments/en", "sling:Folder"},
            new String[] {"/content/dam/qcom/content-fragments/en/test1", "sling:OrderedFolder"},
            new String[] {"/content/dam/qcom/content-fragments/en/test1/jcr:content", "nt:unstructured"});

    private static final String ADDITIONAL_PATH = "/content/dam/qcom/content-fragments/en/test1";

    private static final String NODETYPES = "<'cq'='http://www.day.com/jcr/cq/1.0'>\n"
            + "<'sling'='http://sling.apache.org/jcr/sling/1.0'>\n"
            + "<'nt'='http://www.jcp.org/jcr/nt/1.0'>\n"
            + "<'rep'='internal'>\n"
            + "\n" + "[sling:OrderedFolder] > sling:Folder\n"
            + "  orderable\n"
            + "  + * (nt:base) = sling:OrderedFolder version\n" + "\n"
            + "[sling:Folder] > nt:folder\n"
            + "  - * (undefined) multiple\n"
            + "  - * (undefined)\n"
            + "  + * (nt:base) = sling:Folder version\n" + "\n"
            + "[rep:RepoAccessControllable]\n"
            + "  mixin\n" + "  + rep:repoPolicy (rep:Policy) protected ignore";

    @Before
    public void setup() throws Exception {
        // create the necessary nodetypes (only if not already registered)
        if (!admin.getWorkspace().getNodeTypeManager().hasNodeType("sling:Folder")) {
            CndImporter.registerNodeTypes(new InputStreamReader(new ByteArrayInputStream(NODETYPES.getBytes())), admin);
        }

        // create the necessary repository structure (only if not already exists)
        for (String[] elem : NODES) {
            createNode(elem[0], elem[1]);
        }

        // Add abc node for the first test
        createNode("/content/dam/qcom/content-fragments/en/test1/abc", "sling:Folder");
    }

    @Test
    public void installPackage() throws Exception {
        Archive archive = getFileArchive("/test-packages/AggregationJCRVLT830IT.zip");
        ImportOptions opts = getDefaultOptions();
        opts.setImportMode(ImportMode.REPLACE);
        packMgr.extract(archive, opts, true);

        assertNodeExists(ADDITIONAL_PATH);
        assertNodeExists(ADDITIONAL_PATH + "/abc");
    }

    /**
     * Tests the complete export→import cycle demonstrating the coverage filter
     * problem.
     *
     * Without the fix, step 4 fails because the coverage filter is lost during
     * import, causing the importer to incorrectly delete "abc" as an "uncovered"
     * child.
     */
    @Test
    public void testExportImportPreservesAddedChildNodes() throws Exception {
        // Step 1: Remove "abc" if it exists (to simulate state before it was added)
        Node test1 = admin.getNode(ADDITIONAL_PATH);
        if (test1.hasNode("abc")) {
            test1.getNode("abc").remove();
            admin.save();
        }

        // Step 2: Export the repository structure WITHOUT "abc"
        File exportedFile = exportTestStructure();

        try {
            // Step 2a: Verify the exported package doesn't include "abc"
            try (Archive exportedArchive = new ZipArchive(exportedFile); ) {
                exportedArchive.open(true);
                String test1Xml = readArchiveEntry(
                        exportedArchive, "jcr_root/content/dam/qcom/content-fragments/en/test1/.content.xml");

                // Verify "abc" is NOT in the export
                assertTrue(
                        "Exported package should not contain 'abc' child node (it didn't exist yet)",
                        !test1Xml.contains("<abc"));
            }

            // Step 2b: Compare exported package with reference AggregationJCRVLT830IT.zip
            try (Archive referenceArchive = getFileArchive("/test-packages/AggregationJCRVLT830IT.zip"); ) {
                referenceArchive.open(true);
                compareArchives(exportedFile, referenceArchive);
            }

            // Step 3: Now ADD the "abc" child node (simulating another user/process adding
            // content)
            test1.addNode("abc", "nt:unstructured").setProperty("addedAfterExport", true);
            admin.save();

            assertNodeExists(ADDITIONAL_PATH + "/abc");

            // Step 4: Re-import the original package (which doesn't have "abc")
            try (Archive reimportArchive = new ZipArchive(exportedFile); ) {
                reimportArchive.open(true);
                ImportOptions opts = getDefaultOptions();
                opts.setImportMode(ImportMode.REPLACE);
                packMgr.extract(reimportArchive, opts, true);
            }

            // Step 5: Critical assertion - "abc" should still exist after re-import
            // because sling:OrderedFolder uses partial coverage (exclude isNode=true)
            // Without the fix, this assertion would fail because "abc" would be incorrectly
            // deleted
            assertNodeExists(ADDITIONAL_PATH + "/abc");
            assertProperty(ADDITIONAL_PATH + "/abc/addedAfterExport", true);

        } finally {
            exportedFile.delete();
        }
    }

    private Node createNode(String path, String nodeType) throws RepositoryException {
        String parentPath = path.substring(0, path.lastIndexOf("/"));
        if (parentPath.isBlank()) {
            parentPath = "/";
        }
        String nodeName = path.substring(path.lastIndexOf("/") + 1, path.length());
        Node parentNode = admin.getNode(parentPath);
        Node child = null;
        if (parentNode.hasNode(nodeName)) {
            child = parentNode.getNode(nodeName);
        } else {
            child = parentNode.addNode(nodeName, nodeType);
            admin.save();
        }
        return child;
    }

    /**
     * Helper method to export the test structure to a package file
     */
    private File exportTestStructure()
            throws RepositoryException, IOException, PackageException, ConfigurationException {
        File tempFile = File.createTempFile("test-export", ".zip");

        ExportOptions opts = new ExportOptions();
        DefaultMetaInf metaInf = new DefaultMetaInf();
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();

        // Create the same filter as in the pre-built package
        PathFilterSet filterSet = new PathFilterSet("/content/dam/qcom/content-fragments/en/test1");
        filterSet.addInclude(new DefaultPathFilter("/content/dam/qcom/content-fragments/en/test1"));
        filterSet.addInclude(new DefaultPathFilter("/content/dam/qcom/content-fragments/en/test1/jcr:content"));
        filterSet.addInclude(new DefaultPathFilter("/content/dam/qcom/content-fragments/en/test1/jcr:content/.*"));
        filter.add(filterSet);
        metaInf.setFilter(filter);

        Properties props = new Properties();
        props.setProperty(VaultPackage.NAME_GROUP, "filevault/test");
        props.setProperty(VaultPackage.NAME_NAME, "JCRVLT-830");
        props.setProperty(VaultPackage.NAME_VERSION, "0.0.1");
        metaInf.setProperties(props);

        opts.setMetaInf(metaInf);

        try (VaultPackage pkg = packMgr.assemble(admin, opts, tempFile)) {
            // Package exported
        }

        return tempFile;
    }

    /**
     * Helper method to read the content of an archive entry as a string
     */
    private String readArchiveEntry(Archive archive, String path) throws IOException, ConfigurationException {
        Archive.Entry entry = archive.getEntry(path);
        if (entry == null) {
            throw new IOException("Entry not found in archive: " + path);
        }

        try (java.io.InputStream is = archive.openInputStream(entry)) {
            return IOUtils.toString(is, "UTF-8");
        }
    }

    /**
     * Compares the exported package with the reference package to ensure they have
     * identical structure. This validates that the test creates the exact same
     * content as the pre-built reference package.
     */
    private void compareArchives(File exportedFile, Archive referenceArchive)
            throws IOException, ConfigurationException {
        Archive exportedArchive = new ZipArchive(exportedFile);
        exportedArchive.open(true);

        try {
            // Compare the actual content structure - this is what matters most
            // Note: We don't compare filter.xml because it may have different formatting
            // (escaped patterns, default excludes, etc.) but represents the same logical
            // filter
            String[] contentPaths = {"jcr_root/content/dam/qcom/content-fragments/en/test1/.content.xml"};

            for (String path : contentPaths) {
                String exportedContent = readArchiveEntry(exportedArchive, path);
                String referenceContent = readArchiveEntry(referenceArchive, path);

                // Normalize whitespace for comparison (different serialization might produce
                // slight formatting differences)
                String normalizedExported = normalizeXml(exportedContent);
                String normalizedReference = normalizeXml(referenceContent);

                assertEquals(
                        "Content mismatch for " + path + ".\nExported:\n" + normalizedExported + "\n\nReference:\n"
                                + normalizedReference,
                        normalizedReference,
                        normalizedExported);
            }

            // Verify the filter covers the same root path (logical equivalence, not string
            // match)
            verifyFilterRootEquivalent(exportedArchive, referenceArchive);

        } finally {
            exportedArchive.close();
        }
    }

    /**
     * Verifies that both archives have filters covering the same root path. This is
     * a logical comparison, not a string match, since filter formatting can vary.
     */
    private void verifyFilterRootEquivalent(Archive exportedArchive, Archive referenceArchive)
            throws IOException, ConfigurationException {
        String exportedFilter = readArchiveEntry(exportedArchive, "META-INF/vault/filter.xml");
        String referenceFilter = readArchiveEntry(referenceArchive, "META-INF/vault/filter.xml");

        // Extract the filter root attribute (simple string match is sufficient)
        String exportedRoot = extractFilterRoot(exportedFilter);
        String referenceRoot = extractFilterRoot(referenceFilter);

        assertEquals("Filter root path mismatch", referenceRoot, exportedRoot);

        // Verify exported filter contains the essential include patterns for
        // jcr:content
        assertTrue("Exported filter must include jcr:content path", exportedFilter.contains("/test1/jcr:content"));
    }

    /**
     * Extracts the root attribute from a filter.xml file
     */
    private String extractFilterRoot(String filterXml) {
        // Simple regex to extract: <filter root="/some/path">
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("root=\"([^\"]+)\"");
        java.util.regex.Matcher matcher = pattern.matcher(filterXml);
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new IllegalArgumentException("Could not find filter root in: " + filterXml);
    }

    /**
     * Normalizes XML content for comparison by removing cosmetic differences that
     * don't affect functionality. This includes: - Extra whitespace and line
     * endings - Namespace declarations that are unused in the reference (like
     * xmlns:nt) - Explicit primaryType attributes on child nodes when they match
     * the default type
     */
    private String normalizeXml(String xml) {
        // Remove leading/trailing whitespace from each line and normalize line endings
        String normalized = xml.replaceAll("(?m)^\\s+", "")
                .replaceAll("(?m)\\s+$", "")
                .replaceAll("\\r\\n", "\n")
                .trim();

        // Remove xmlns:nt namespace declaration if present (cosmetic difference)
        normalized = normalized.replaceAll("\\s+xmlns:nt=\"[^\"]+\"", "");

        // Normalize jcr:content element - remove explicit primaryType if it's
        // nt:unstructured (default)
        // Replace <jcr:content jcr:primaryType="nt:unstructured"/> with <jcr:content/>
        normalized =
                normalized.replaceAll("<jcr:content\\s+jcr:primaryType=\"nt:unstructured\"\\s*/>", "<jcr:content/>");

        return normalized;
    }
}
