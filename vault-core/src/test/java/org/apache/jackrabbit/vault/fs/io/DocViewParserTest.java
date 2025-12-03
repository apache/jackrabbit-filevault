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
package org.apache.jackrabbit.vault.fs.io;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Assert;
import org.junit.Test;

public class DocViewParserTest {

    @Test
    public void testGetDocumentViewXmlRootPathFromContentXml() throws IOException {
        Path filePath = Paths.get("test", "parent", ".content.xml");
        Assert.assertEquals("/test/parent", DocViewParser.getDocumentViewXmlRootNodePath(null, filePath));
    }

    @Test
    public void testGetDocumentViewXmlRootPathFromContentXmlBelowDotDir() throws IOException {
        // http://jackrabbit.apache.org/filevault/vaultfs.html#Extended_File_aggregates
        Path filePath = Paths.get("test", "parent.dir", ".content.xml");
        Assert.assertEquals("/test/parent", DocViewParser.getDocumentViewXmlRootNodePath(null, filePath));
    }

    @Test
    public void testGetDocumentViewXmlRootPathFromEscapedFilename() throws IOException {
        // http://jackrabbit.apache.org/filevault/vaultfs.html#Extended_File_aggregates
        Path filePath = Paths.get("test", "parent", "_cq_test%3aimage.xml");
        try (InputStream inputStream = getClass().getResourceAsStream("docview.xml")) {
            Assert.assertEquals(
                    "/test/parent/cq:test:image", DocViewParser.getDocumentViewXmlRootNodePath(inputStream, filePath));
        }
    }

    @Test
    public void testGetDocumentViewXmlRootPathFromNonXmlFile() throws IOException {
        // http://jackrabbit.apache.org/filevault/vaultfs.html#Extended_File_aggregates
        Path filePath = Paths.get("test", "parent", "test.jpg");
        Assert.assertNull(DocViewParser.getDocumentViewXmlRootNodePath(null, filePath));
    }

    @Test
    public void testGetDocumentViewXmlRootPathFromNonDocviewXmlFile() throws IOException {
        // http://jackrabbit.apache.org/filevault/vaultfs.html#Extended_File_aggregates
        Path filePath = Paths.get("test", "parent", "test.xml");
        try (InputStream inputStream = getClass().getResourceAsStream("non-docview.xml")) {
            Assert.assertNull(DocViewParser.getDocumentViewXmlRootNodePath(inputStream, filePath));
        }
    }
}
