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
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;

import javax.jcr.Node;
import javax.jcr.nodetype.NodeTypeManager;
import org.apache.jackrabbit.commons.cnd.CndImporter;
import org.apache.jackrabbit.vault.fs.api.ImportMode;
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.junit.Before;
import org.junit.Test;

public class AggregationJCRVLT830IT extends IntegrationTestBase {
    
    
    private static final List<String[]> PATH = Arrays.asList(
            new String[] {"content","sling:Folder"},
            new String[] {"dam","sling:Folder"},
            new String[] {"qcom","sling:Folder"},
            new String[] {"content-fragments","sling:Folder"},
            new String[] {"en","sling:Folder"},
            new String[] {"test1","sling:OrderedFolder"},
            new String[] {"jcr:content","nt:unstructured"}
            );
    
    private static final String ADDITIONAL_PATH = "/content/dam/qcom/content-fragments/en/test1";
    
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
            + "  + * (nt:base) = sling:Folder version\n"
            + "\n"
            + "[rep:RepoAccessControllable]\n"
            + "  mixin\n"
            + "  + rep:repoPolicy (rep:Policy) protected ignore";

    @Before
    public void setup() throws Exception {
        // create the necessary nodetypes
        CndImporter.registerNodeTypes(new InputStreamReader(new ByteArrayInputStream(NODETYPES.getBytes())), admin);
        
        // create the necessary repository structure
        Node node = admin.getRootNode();
        for (int i=0; i < PATH.size();i++) {
            String[] elem = PATH.get(i);
            node = node.addNode(elem[0],elem[1]);
        }
        Node additional = admin.getNode(ADDITIONAL_PATH);
        additional.addNode("abc");
        additional.getSession().save();
    }
    
    
    @Test
    public void installPackage() throws Exception {
        Archive archive  = getFileArchive("/test-packages/AggregationJCRVLT830IT.zip");
        ImportOptions opts = getDefaultOptions();
        opts.setImportMode(ImportMode.REPLACE);
        packMgr.extract(archive, opts, true);
        
        assertNodeExists(ADDITIONAL_PATH);
        assertNodeExists(ADDITIONAL_PATH + "/abc");
        
    }

}
