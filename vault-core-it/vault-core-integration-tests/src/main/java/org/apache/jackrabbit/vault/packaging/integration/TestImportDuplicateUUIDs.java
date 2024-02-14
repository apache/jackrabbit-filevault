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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.Calendar;
import java.util.Properties;

import javax.jcr.Binary;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.UnsupportedRepositoryOperationException;
import javax.jcr.ValueFactory;
import javax.jcr.nodetype.NodeDefinitionTemplate;
import javax.jcr.nodetype.NodeType;
import javax.jcr.nodetype.NodeTypeManager;
import javax.jcr.nodetype.NodeTypeTemplate;

import org.apache.commons.io.FileUtils;
import org.apache.jackrabbit.commons.cnd.CndImporter;
import org.apache.jackrabbit.commons.cnd.ParseException;
import org.apache.jackrabbit.vault.fs.api.FilterSet;
import org.apache.jackrabbit.vault.fs.api.IdConflictPolicy;
import org.apache.jackrabbit.vault.fs.api.ImportMode;
import org.apache.jackrabbit.vault.fs.api.PathFilter;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.fs.config.DefaultMetaInf;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.fs.filter.DefaultPathFilter;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.fs.io.Importer;
import org.apache.jackrabbit.vault.fs.io.ZipArchive;
import org.apache.jackrabbit.vault.packaging.ExportOptions;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static javax.jcr.nodetype.NodeType.MIX_REFERENCEABLE;
import static javax.jcr.nodetype.NodeType.NT_UNSTRUCTURED;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.apache.jackrabbit.commons.JcrUtils.getOrCreateByPath;
import static org.apache.jackrabbit.util.Text.getRelativeParent;
import static org.apache.jackrabbit.vault.packaging.PackageProperties.NAME_GROUP;
import static org.apache.jackrabbit.vault.packaging.PackageProperties.NAME_NAME;
import static org.apache.jackrabbit.vault.util.PathUtil.append;

public class TestImportDuplicateUUIDs extends IntegrationTestBase {

    public static final String TEST_ROOT = "testroot";
    
    private static final Logger log = LoggerFactory.getLogger(TestImportDuplicateUUIDs.class);

    private Node testRoot;
    
    private static final String NODE_TYPES = "[dam:Asset] > nt:hierarchyNode\n"
    		+ "  primaryitem jcr:content\n"
    		+ "  + jcr:content (dam:AssetContent) = dam:AssetContent\n"
    		+ "  + * (nt:base) = nt:base version\n"
    		+ "\n"
    		+ "[dam:AssetContent] > nt:unstructured\n"
    		+ "  + metadata (nt:unstructured)\n"
    		+ "  + related (nt:unstructured)\n"
    		+ "  + renditions (nt:folder)";
    

    @Before
    public void before() throws RepositoryException, Exception {
        testRoot = admin.getRootNode().addNode(TEST_ROOT);
        admin.getWorkspace().getNamespaceRegistry().registerNamespace("dam", "http://www.day.com/day/dam");
        
        NodeTypeManager ntm = admin.getWorkspace().getNodeTypeManager();
        
        CndImporter.registerNodeTypes(new StringReader(NODE_TYPES), admin);
        
    }

    
    
    
    @Test
    public void testInstallPackage_LEGACY() throws Exception {
        String srcName = randomAlphanumeric(10) + ".png";
        String srcPath = append(testRoot.getPath(), srcName);
        String dstPath = srcPath + "-renamed";
        
        Node asset = getOrCreateByPath(srcPath, NT_UNSTRUCTURED, "dam:Asset", admin, true);
        Node assetContent = asset.addNode("jcr:content","dam:AssetContent");
        assetContent.setProperty("someprop", "somevalue");
        
        Node binaryNode = addFileNode(asset,"binary.png");

        assertNodeExists(assetContent.getPath());
        asset.addMixin(MIX_REFERENCEABLE);
        File pkgFile = exportContentPackage(srcPath);
        admin.save();

        admin.move(srcPath, dstPath);
        assertNodeMissing(srcPath);
        
        installContentPackage(pkgFile, IdConflictPolicy.LEGACY);
        
        assertNodeExists(srcPath);
        assertNodeExists(srcPath + "/jcr:content");
        assertProperty(assetContent.getPath() + "/someprop", "somevalue");
    }
    
    
    // Identical to the one above, but with different IdConflictPolicy
    @Test
    public void testInstallPackage_CREATE_NEW_ID() throws Exception {
        String srcName = randomAlphanumeric(10) + ".png";
        String srcPath = append(testRoot.getPath(), srcName);
        String dstPath = srcPath + "-renamed";
        
        Node asset = getOrCreateByPath(srcPath, NT_UNSTRUCTURED, "dam:Asset", admin, true);
        Node assetContent = asset.addNode("jcr:content","dam:AssetContent");
        assetContent.setProperty("someprop", "somevalue");
        
        Node binaryNode = addFileNode(asset,"binary.png");

        assertNodeExists(assetContent.getPath());
        asset.addMixin(MIX_REFERENCEABLE);
        File pkgFile = exportContentPackage(srcPath);
        admin.save();

        admin.move(srcPath, dstPath);
        assertNodeMissing(srcPath);
        
        installContentPackage(pkgFile, IdConflictPolicy.CREATE_NEW_ID);
        
        assertNodeExists(srcPath);
        assertNodeExists(srcPath + "/jcr:content");
        assertProperty(assetContent.getPath() + "/someprop", "somevalue");
    }
    
    
    
    

    
    private Node addFileNode (Node parent, String name) throws Exception {
    	
      ValueFactory valueFactory = parent.getSession().getValueFactory();           
      Binary contentValue = valueFactory.createBinary(new ByteArrayInputStream("fake binary".getBytes()) );        
      Node fileNode = parent.addNode(name, "nt:file");
      Node resNode = fileNode.addNode("jcr:content", "nt:resource");
      resNode.setProperty("jcr:mimeType", "image/png");
      resNode.setProperty("jcr:data", contentValue);

      Calendar lastModified = Calendar.getInstance();
      lastModified.setTimeInMillis(lastModified.getTimeInMillis());
      resNode.setProperty("jcr:lastModified", lastModified);
      
      return fileNode;
    }
    
    private File exportContentPackage(String path) throws Exception {
        ExportOptions opts = new ExportOptions();
        DefaultMetaInf inf = new DefaultMetaInf();
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        
        
        PathFilterSet pfs = new PathFilterSet(path);
        pfs.addInclude(new DefaultPathFilter(path + "/.*"));
        filter.add(pfs);
        
        inf.setFilter(filter);
        Properties props = new Properties();
        props.setProperty(NAME_GROUP, "jackrabbit/test");
        props.setProperty(NAME_NAME, "test-package");
        inf.setProperties(props);
        
        opts.setMetaInf(inf);
        File pkgFile = File.createTempFile("testImportMovedResource", ".zip");
        try(VaultPackage pkg = packMgr.assemble(admin, opts, pkgFile)) {
            return pkg.getFile();
        }
    }

    private void installContentPackage(File pkgFile, IdConflictPolicy policy) throws RepositoryException, IOException, ConfigurationException {
    	
        try (ZipArchive archive = new ZipArchive(pkgFile);) {
	        archive.open(true);
	        ImportOptions opts = getDefaultOptions();
	        opts.setIdConflictPolicy(policy);
	        opts.setFilter(archive.getMetaInf().getFilter());
	        opts.setImportMode(ImportMode.UPDATE_PROPERTIES);
	        
	        opts.setStrict(true);
	        Importer importer = new Importer(opts);
	        
	        log.info("importing");
	        importer.run(archive, admin.getRootNode());
        }
    }
}
