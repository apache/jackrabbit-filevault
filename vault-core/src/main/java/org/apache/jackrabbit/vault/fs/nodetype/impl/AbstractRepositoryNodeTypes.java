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
package org.apache.jackrabbit.vault.fs.nodetype.impl;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.vault.fs.nodetype.RepositoryNodeTypes;
import org.apache.jackrabbit.vault.packaging.UncoveredAncestorHandling;
import org.apache.jackrabbit.vault.util.PathUtil;

public abstract class AbstractRepositoryNodeTypes implements RepositoryNodeTypes {

    public AbstractRepositoryNodeTypes() {
        super();
    }

    @Override
    public void enforce(UncoveredAncestorHandling enforcementType, String path, Session session) {
        switch (enforcementType) {
        case IGNORE:
            return;
        case CREATEORUPDATE:
            
        case CREATE:
            break;
        case VALIDATE: 
            break;
        default:
            break;
        }
    }

    void validateNodeWithTypes(Node node) throws RepositoryException {
        final String primaryNodeType = getPrimaryNodeType(node.getPath());
        if (primaryNodeType.isEmpty()) {
            // this is fine, as no expectation on node type
            return;
        }
        if (node.getPrimaryNodeType().isNodeType(primaryNodeType)) {
            throw new RepositoryException("Invalid node type at path " + node.getPath() + ": " + node.getPrimaryNodeType());
        }
        for (String mixinType : getMixinNodeTypes(node.getPath())) {
            if (!node.isNodeType(mixinType)) {
                throw new RepositoryException("Node at " + node.getPath() + " does not have mixin " + mixinType);
            }
        }
    }

    Node createNodeWithTypes(Node parent, String name) throws RepositoryException {
        // what happens if this is empty?
        final Node node;
        String path = PathUtil.getPath(parent, name);
        final String primaryNodeType = getPrimaryNodeType(path);
        if (primaryNodeType.isEmpty()) {
            node = parent.addNode(name);
        } else {
            node = parent.addNode(name, primaryNodeType);
            for (String mixinType : getMixinNodeTypes(path)) {
                node.addMixin(mixinType);
            }
        }
        return node;
    }

    void updateNodeWithTypes(Node node) throws RepositoryException {
        final String primaryNodeType = getPrimaryNodeType(node.getPath());
        if (!primaryNodeType.isEmpty()) {
            node.setPrimaryType(primaryNodeType);
            for (String mixinType : getMixinNodeTypes(node.getPath())) {
                node.addMixin(mixinType);
            }
        }
    }

}