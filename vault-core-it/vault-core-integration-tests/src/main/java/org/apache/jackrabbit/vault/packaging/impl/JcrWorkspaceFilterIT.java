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
package org.apache.jackrabbit.vault.packaging.impl;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.jackrabbit.vault.fs.api.ImportMode;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.fs.filter.DefaultPathFilter;
import org.apache.jackrabbit.vault.packaging.integration.IntegrationTestBase;
import org.junit.Assert;
import org.junit.Test;


public class JcrWorkspaceFilterIT extends IntegrationTestBase {

    @Test
    public void testComplexFilterRoundtrip() throws RepositoryException, ConfigurationException {
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        PathFilterSet pathFilterSet = new PathFilterSet("/my/root");
        pathFilterSet.setImportMode(ImportMode.MERGE);
        pathFilterSet.setType("cleanup");
        pathFilterSet.addInclude(new DefaultPathFilter("/my/root/include.*"));
        pathFilterSet.addExclude(new DefaultPathFilter("/my/root/exclude.*"));
        
        PathFilterSet propertyFilterSet = new PathFilterSet("/my/root");
        propertyFilterSet.setImportMode(ImportMode.MERGE);
        propertyFilterSet.setType("cleanup");
        propertyFilterSet.addInclude(new DefaultPathFilter("/my/root/myprops.*"));
        propertyFilterSet.addExclude(new DefaultPathFilter("/my/root/notmyprops.*"));
        filter.add(pathFilterSet, propertyFilterSet);
        Node defNode = JcrUtils.getOrCreateByPath("/etc/packages/mypackage/jcr:content/vlt:definition", "vlt:PackageDefinition", admin);
        JcrWorkspaceFilter.saveFilter(filter, defNode, false);
        Assert.assertEquals(filter, JcrWorkspaceFilter.loadFilter(defNode));
    }
    
}
