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
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Properties;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.apache.commons.io.IOUtils;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.config.DefaultMetaInf;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.packaging.ExportOptions;
import org.apache.jackrabbit.vault.packaging.JcrPackage;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.junit.Test;
import org.xml.sax.InputSource;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * <code>TestEmptyPackage</code>...
 */
public class TestSpecialDoubleProperties extends IntegrationTestBase {

    @Test
    public void exportDoubles() throws RepositoryException, IOException, PackageException {
        Node tmp = admin.getRootNode().addNode("tmp", "nt:unstructured");
        Node content = tmp.addNode("jcr:content", "nt:unstructured");
        content.setProperty("double_nan", Double.NaN);
        content.setProperty("double_pos_inf", Double.POSITIVE_INFINITY);
        content.setProperty("double_neg_inf", Double.NEGATIVE_INFINITY);
        admin.save();

        ExportOptions opts = new ExportOptions();
        DefaultMetaInf inf = new DefaultMetaInf();
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        PathFilterSet set1 = new PathFilterSet("/tmp");
        filter.add(set1);
        inf.setFilter(filter);
        Properties props = new Properties();
        props.setProperty(VaultPackage.NAME_GROUP, "apache/test");
        props.setProperty(VaultPackage.NAME_NAME, "test-package");
        inf.setProperties(props);

        opts.setMetaInf(inf);
        File tmpFile = File.createTempFile("vaulttest", ".zip");
        VaultPackage pkg = packMgr.assemble(admin, opts, tmpFile);

        Archive.Entry e = pkg.getArchive().getEntry("jcr_root/tmp/.content.xml");
        InputSource is = pkg.getArchive().getInputSource(e);
        Reader r = new InputStreamReader(is.getByteStream(), "utf-8");
        String contentXml = IOUtils.toString(r);

        assertEquals("Serialized content",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<jcr:root xmlns:jcr=\"http://www.jcp.org/jcr/1.0\" xmlns:nt=\"http://www.jcp.org/jcr/nt/1.0\"\n" +
                        "    jcr:primaryType=\"nt:unstructured\">\n" +
                        "    <jcr:content\n" +
                        "        jcr:primaryType=\"nt:unstructured\"\n" +
                        "        double_nan=\"{Double}NaN\"\n" +
                        "        double_neg_inf=\"{Double}-Infinity\"\n" +
                        "        double_pos_inf=\"{Double}Infinity\"/>\n" +
                        "</jcr:root>\n",
                contentXml);
        pkg.close();
        tmpFile.delete();
    }

    @Test
    public void importDoubles() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/double_properties.zip"), false);
        assertNotNull(pack);
        pack.install(getDefaultOptions());

        Node tmp = admin.getNode("/tmp/jcr:content");
        assertEquals(Double.NaN, tmp.getProperty("double_nan").getDouble(), 0.0);
        assertEquals(Double.POSITIVE_INFINITY, tmp.getProperty("double_pos_inf").getDouble(), 0.0);
        assertEquals(Double.NEGATIVE_INFINITY, tmp.getProperty("double_neg_inf").getDouble(), 0.0);
    }

}