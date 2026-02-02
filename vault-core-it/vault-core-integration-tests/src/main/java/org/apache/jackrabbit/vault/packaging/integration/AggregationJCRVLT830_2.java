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
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Value;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.apache.jackrabbit.commons.cnd.CndImporter;
import org.apache.jackrabbit.vault.fs.api.ArtifactType;
import org.apache.jackrabbit.vault.fs.api.IdConflictPolicy;
import org.apache.jackrabbit.vault.fs.api.ImportMode;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.api.SerializationType;
import org.apache.jackrabbit.vault.fs.api.VaultInputSource;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.fs.filter.DefaultPathFilter;
import org.apache.jackrabbit.vault.fs.impl.ArtifactSetImpl;
import org.apache.jackrabbit.vault.fs.impl.io.GenericArtifactHandler;
import org.apache.jackrabbit.vault.fs.impl.io.InputSourceArtifact;
import org.apache.jackrabbit.vault.fs.io.AccessControlHandling;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class AggregationJCRVLT830_2 extends IntegrationTestBase {

    private GenericArtifactHandler handler;
    DefaultWorkspaceFilter wspFilter;
    private Node contentNode;

    private static final String NODETYPES = "<'cq'='http://www.day.com/jcr/cq/1.0'>\n"
            + "<'sling'='http://sling.apache.org/jcr/sling/1.0'>\n"
            + "<'nt'='http://www.jcp.org/jcr/nt/1.0'>\n"
            + "<'rep'='internal'>\n"
            + "\n"
            + "[sling:OrderedFolder] > sling:Folder\n"
            + "  orderable\n"
            + "  + * (nt:base) = sling:OrderedFolder version\n"
            + "\n"
            + "[sling:Folder] > nt:folder\n"
            + "  - * (undefined) multiple\n"
            + "  - * (undefined)\n"
            + "  + * (nt:base) = sling:Folder version\n";

    @Before
    public void setup() throws Exception {
        // create the necessary nodetypes
        CndImporter.registerNodeTypes(new InputStreamReader(new ByteArrayInputStream(NODETYPES.getBytes())), admin);

        handler = new GenericArtifactHandler();
        handler.setAcHandling(AccessControlHandling.OVERWRITE);

        // create a filter accepting everything
        wspFilter = new DefaultWorkspaceFilter();
        PathFilterSet filterSet = new PathFilterSet();
        filterSet.addInclude(new DefaultPathFilter(".*"));
        wspFilter.add(filterSet);
    }

    @Test
    public void importContent() throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<jcr:root xmlns:jcr=\"http://www.jcp.org/jcr/1.0\" xmlns:rep=\"internal\" xmlns:sling=\"http://sling.apache.org/jcr/sling/1.0\"\n"
                + "    jcr:title=\"content\""
                + "    jcr:primaryType=\"sling:Folder\">\n"
                + "    <en\n"
                + "        jcr:title=\"en\""
                + "        foo=\"bar\""
                + "        jcr:primaryType=\"sling:Folder\"/>\n"
                + "</jcr:root>";

        // create basic node structure /content/mysite/en/page
        contentNode = admin.getRootNode().addNode("content", "nt:unstructured");
        Node mysite = contentNode.addNode("mysite", "sling:Folder");
        Node en = mysite.addNode("en", "sling:Folder");
        Node page = en.addNode("page", "nt:unstructured");
        admin.save();

        // import the docview
        importDocViewXml(xml, contentNode, "mysite");
        admin.save();
        dumpRepositoryStructure(contentNode);

        assertNodeExists("/content/mysite/en");
        assertNodeExists("/content/mysite/en/page");
        assertEquals(
                "bar", admin.getNode("/content/mysite/en").getProperty("foo").toString());
        assertProperty("/content/mysite/en/foo", "bar");
    }

    /**
     * Helper method to import DocView XML using GenericArtifactHandler
     */
    private void importDocViewXml(String xml, Node parentNode, String nodeName)
            throws RepositoryException, IOException {
        ArtifactSetImpl artifacts = new ArtifactSetImpl();

        // Create VaultInputSource from the XML string
        VaultInputSource vaultSource = new VaultInputSource() {
            @Override
            public InputStream getByteStream() {
                return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
            }

            @Override
            public long getContentLength() {
                return xml.length();
            }

            @Override
            public long getLastModified() {
                return System.currentTimeMillis();
            }
        };

        // Create the artifact using InputSourceArtifact
        InputSourceArtifact artifact = new InputSourceArtifact(
                null, // no parent
                nodeName, // name
                ".xml", // extension
                ArtifactType.PRIMARY, // type
                vaultSource, // input source
                SerializationType.XML_DOCVIEW // serialization type
                );

        artifacts.add(artifact);

        ImportOptions options = new ImportOptions();
        options.setStrict(true);
        options.setIdConflictPolicy(IdConflictPolicy.FAIL);
        options.setImportMode(ImportMode.REPLACE);
        options.setListener(getLoggingProgressTrackerListener());
        assertNotNull(handler.accept(options, true, wspFilter, parentNode, nodeName, artifacts));
    }

    /**
     * Dumps the repository structure below the provided Node (including the node itself).
     * Recursively prints all nodes, their properties and values with proper indentation.
     *
     * @param node the node to start dumping from
     * @throws RepositoryException if an error occurs accessing the repository
     */
    private void dumpRepositoryStructure(Node node) throws RepositoryException {
        dumpNode(node, 0);
    }

    /**
     * Recursively dumps a node and its descendants with indentation.
     *
     * @param node the node to dump
     * @param depth the current depth for indentation
     * @throws RepositoryException if an error occurs accessing the repository
     */
    private void dumpNode(Node node, int depth) throws RepositoryException {
        String indent = "  ".repeat(depth);

        // Print node path and primary type
        System.out.println(indent + "+ " + node.getName() + " ["
                + node.getPrimaryNodeType().getName() + "]");

        // Print properties
        PropertyIterator properties = node.getProperties();
        while (properties.hasNext()) {
            Property property = properties.nextProperty();
            String propertyIndent = "  ".repeat(depth + 1);

            // Skip jcr:primaryType as it's already shown
            if ("jcr:primaryType".equals(property.getName())) {
                continue;
            }

            // Handle multi-value properties
            if (property.isMultiple()) {
                Value[] values = property.getValues();
                if (values.length > 0) {
                    System.out.print(propertyIndent + "- " + property.getName() + " = [");
                    for (int i = 0; i < values.length; i++) {
                        if (i > 0) System.out.print(", ");
                        System.out.print(values[i].getString());
                    }
                    System.out.println("]");
                } else {
                    System.out.println(propertyIndent + "- " + property.getName() + " = []");
                }
            } else {
                System.out.println(propertyIndent + "- " + property.getName() + " = " + property.getString());
            }
        }

        // Recursively dump child nodes
        NodeIterator children = node.getNodes();
        while (children.hasNext()) {
            Node child = children.nextNode();
            dumpNode(child, depth + 1);
        }
    }
}
