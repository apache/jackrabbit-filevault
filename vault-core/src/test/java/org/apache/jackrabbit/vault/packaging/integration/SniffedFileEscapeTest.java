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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.nodetype.NodeType;

import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.config.DefaultMetaInf;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.fs.impl.aggregator.FileAggregator;
import org.apache.jackrabbit.vault.packaging.ExportOptions;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.junit.Assert;
import org.junit.Test;

/**
 * Verifies that stored files whose name/content would collide with the docview
 * serialization conventions ({@code *.xml} files that sniff as Document View XML,
 * and files literally named {@code .content.xml}) are exported in a form that
 * round-trips losslessly as an opaque file, instead of being re-imported as node
 * structure. Tests the escaping performed by {@link FileAggregator}.
 */
public class SniffedFileEscapeTest extends IntegrationTestBase {

    private static final String EVIL_DOCVIEW = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<jcr:root xmlns:jcr=\"http://www.jcp.org/jcr/1.0\" xmlns:nt=\"http://www.jcp.org/jcr/nt/1.0\"\n"
            + "    jcr:primaryType=\"nt:unstructured\"\n"
            + "    jcr:uuid=\"12345678-1234-1234-1234-123456789012\"\n"
            + "    evilMarker=\"pwned\"/>\n";

    /**
     * An inert {@code *.xml} file whose bytes happen to sniff as Document View XML must
     * not be promoted to node structure on re-import: it must round-trip as the very
     * same file, byte for byte, and must not create any additional node.
     */
    @Test
    public void sniffableXmlFileRoundTripsAsFile() throws IOException, RepositoryException, PackageException {
        byte[] data = EVIL_DOCVIEW.getBytes(StandardCharsets.UTF_8);
        Node tmp = admin.getRootNode().addNode("tmp", NodeType.NT_FOLDER);
        JcrUtils.putFile(tmp, "evil.xml", "text/xml", new ByteArrayInputStream(data));
        admin.save();

        Set<String> entries = assembleAndReinstallPackage("/tmp");

        // the raw bytes must survive unchanged...
        assertNodeExists("/tmp/evil.xml");
        assertProperty("/tmp/evil.xml/jcr:content/jcr:data", new String(data, StandardCharsets.UTF_8));
        // ...and must never be sniffed into a sibling node named after the stripped extension
        assertNodeMissing("/tmp/evil");
        assertPropertyMissing("/tmp/evil.xml/evilMarker");

        // the archive must not contain a raw "evil.xml" entry (which an old importer would sniff);
        // it must be escaped into the ".dir" aggregate shape instead.
        Assert.assertFalse(
                "archive must not contain a raw, sniffable evil.xml entry", entries.contains("jcr_root/tmp/evil.xml"));
        Assert.assertTrue(
                "archive must contain the escaped .content.xml docview",
                entries.contains("jcr_root/tmp/evil.xml.dir/.content.xml"));
    }

    /**
     * An {@code nt:file} literally named {@code .content.xml} must not be confused with
     * the containing folder's own serialization, and must round-trip as a file.
     */
    @Test
    public void dotContentXmlNamedFileRoundTripsAsFile() throws IOException, RepositoryException, PackageException {
        byte[] data = "just some file content, not a docview".getBytes(StandardCharsets.UTF_8);
        Node tmp = admin.getRootNode().addNode("tmp", NodeType.NT_FOLDER);
        Node folder = tmp.addNode("folder", NodeType.NT_FOLDER);
        JcrUtils.putFile(folder, ".content.xml", "text/plain", new ByteArrayInputStream(data));
        admin.save();

        assembleAndReinstallPackage("/tmp");

        assertNodeExists("/tmp/folder");
        assertNodeExists("/tmp/folder/.content.xml");
        assertProperty("/tmp/folder/.content.xml/jcr:content/jcr:data", new String(data, StandardCharsets.UTF_8));
    }

    /**
     * Control case: a plain, non-docview {@code *.xml} file keeps the existing raw
     * export layout (no behavioral change for the overwhelming majority of files).
     */
    @Test
    public void plainXmlFileKeepsRawLayout() throws IOException, RepositoryException, PackageException {
        byte[] data = "<config><item/></config>".getBytes(StandardCharsets.UTF_8);
        Node tmp = admin.getRootNode().addNode("tmp", NodeType.NT_FOLDER);
        JcrUtils.putFile(tmp, "plain.xml", "text/xml", new ByteArrayInputStream(data));
        admin.save();

        Set<String> entries = assembleAndReinstallPackage("/tmp");

        assertNodeExists("/tmp/plain.xml");
        assertProperty("/tmp/plain.xml/jcr:content/jcr:data", new String(data, StandardCharsets.UTF_8));
        Assert.assertTrue(
                "plain, non-docview xml files must keep the raw archive entry",
                entries.contains("jcr_root/tmp/plain.xml"));
        Assert.assertFalse(
                "plain, non-docview xml files must not be escaped into .dir",
                entries.contains("jcr_root/tmp/plain.xml.dir/.content.xml"));
    }

    private Set<String> assembleAndReinstallPackage(String root)
            throws IOException, PackageException, RepositoryException {
        File pkgFile = tempFolder.newFile();

        try {
            ExportOptions options = new ExportOptions();
            DefaultMetaInf meta = new DefaultMetaInf();
            DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
            filter.add(new PathFilterSet(root));
            meta.setFilter(filter);

            Properties props = new Properties();
            props.setProperty(VaultPackage.NAME_GROUP, "jackrabbit/test");
            props.setProperty(VaultPackage.NAME_NAME, "sniffed-file-escape-package");
            meta.setProperties(props);
            options.setMetaInf(meta);

            packMgr.assemble(admin, options, pkgFile).close();

            Set<String> entries = new HashSet<>();
            try (ZipFile zip = new ZipFile(pkgFile)) {
                Enumeration<? extends ZipEntry> en = zip.entries();
                while (en.hasMoreElements()) {
                    entries.add(en.nextElement().getName());
                }
            }

            clean(root);
            try (VaultPackage vp = packMgr.open(pkgFile)) {
                vp.extract(admin, getDefaultOptions());
            }
            return entries;
        } finally {
            Files.delete(pkgFile.toPath());
        }
    }
}
