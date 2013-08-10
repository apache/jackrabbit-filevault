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

import org.apache.jackrabbit.vault.fs.api.DumpContext;

/**
 * Filters on the node type of a node.
 *
 */
public class NodeTypeItemFilter extends DepthItemFilter {

    /**
     * the node type to filter on
     */
    private String nodeType;

    /**
     * indicates if super types should be respected
     */
    private boolean respectSupertype;

    /**
     * Default constructor
     */
    public NodeTypeItemFilter() {
    }

    /**
     * Creates a new node type filter.
     * @param nodeType the node type to filter on
     * @param respectSupertype indicates if super type should be respected
     * @param minDepth the minimal depth
     * @param maxDepth the maximal depth
     */
    public NodeTypeItemFilter(String nodeType, boolean respectSupertype,
                              int minDepth, int maxDepth) {
        super(minDepth, maxDepth);
        this.nodeType = nodeType;
        this.respectSupertype = respectSupertype;
    }

    /**
     * Creates a new node type filter.
     * @param nodeType the node type to filter on
     * @param respectSupertype indicates if super type should be respected
     */
    public NodeTypeItemFilter(String nodeType, boolean respectSupertype) {
        this(nodeType, respectSupertype, 0, Integer.MAX_VALUE);
    }

    /**
     * Sets the node type to filter on
     * @param nodeType the node type
     */
    public void setNodeType(String nodeType) {
        this.nodeType = nodeType;
    }

    /**
     * Sets the flag that indicates if super type should be respected.
     * @param respectSupertype if <code>true</code>, super types are respected.
     */
    public void setRespectSupertype(String respectSupertype) {
        this.respectSupertype = Boolean.valueOf(respectSupertype);
    }

    /**
     * {@inheritDoc}
     *
     * Returns <code>true</code> if the item is a node and if the configured
     * node type is equal to the primary type of the node. if super types are
     * respected it also returns <code>true</code> if the items node type
     * extends from the configured node type (Node.isNodeType() check).
     */
    public boolean matches(Item item) throws RepositoryException {
        if (item.isNode()) {
            if (respectSupertype) {
                try {
                    return ((Node) item).isNodeType(nodeType);
                } catch (RepositoryException e) {
                    // ignore
                    return false;
                }
            } else {
                return ((Node) item).getPrimaryNodeType().getName().equals(nodeType);
            }
        }
        return false;
        
    }

    /**
     * {@inheritDoc}
     */
    public void dump(DumpContext ctx, boolean isLast) {
        super.dump(ctx, isLast);
        ctx.indent(isLast);
        ctx.printf(false, "nodeType: %s", nodeType);
        ctx.printf(true, "respectSupertype: %b", respectSupertype);
        ctx.outdent();
    }
    
}