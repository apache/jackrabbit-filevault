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

package org.apache.jackrabbit.vault.fs.filter;

import javax.jcr.Item;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.nodetype.NodeDefinition;

import org.apache.jackrabbit.vault.util.JcrConstants;

/**
 * Defines an filter that matches file/folder like nodes. It matches
 * all nt:hierarchyNode nodes that have or define a jcr:content
 * child node.
 */
public class FileFolderNodeFilter extends DepthItemFilter {

    /**
     * {@inheritDoc}
     *
     * Returns <code>true</code> if the item is a node of type nt:hierarchyNode
     * that has or defines a 'jcr:content' child node.
     */
    public boolean matches(Item item) throws RepositoryException {
        if (item.isNode()) {
            Node node = (Node) item;
            if (node.isNodeType(JcrConstants.NT_HIERARCHYNODE)) {
                if (node.hasNode(JcrConstants.JCR_CONTENT)) {
                    return true;
                } else {
                    for (NodeDefinition pd: node.getPrimaryNodeType().getChildNodeDefinitions()) {
                        if (pd.getName().equals(JcrConstants.JCR_CONTENT)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

}