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
import javax.jcr.Property;
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.vault.fs.api.DumpContext;

/**
 * Filter that checks the declared type of an item
 *
 */
public class DeclaringTypeItemFilter extends DepthItemFilter {

    /**
     * The node type to check
     */
    private String nodeType;

    /**
     * indicates if only props should be checked
     */
    private boolean propsOnly;

    /**
     * Default constructor.
     */
    public DeclaringTypeItemFilter() {
    }

    /**
     * Creates a new filter for the given node type and flags.
     * @param nodeType the node type name to check
     * @param propsOnly if <code>true</code> only properties are checked
     * @param minDepth the minimal depth
     * @param maxDepth the maximal depth
     */
    public DeclaringTypeItemFilter(String nodeType, boolean propsOnly,
                                   int minDepth, int maxDepth) {
        super(minDepth, maxDepth);
        this.nodeType = nodeType;
        this.propsOnly = propsOnly;
    }

    /**
     * Creates a new filter for the given node type and flags
     * @param nodeType the node type name to check
     * @param propsOnly if <code>true</code> only properties are checked
     */
    public DeclaringTypeItemFilter(String nodeType, boolean propsOnly) {
        this(nodeType, propsOnly, 0, Integer.MAX_VALUE);
    }

    /**
     * Sets the node type to match the declaring one of the item
     * @param nodeType the node type
     */
    public void setNodeType(String nodeType) {
        this.nodeType = nodeType;
    }

    /**
     * Sets the flag that indicates if only properties are to be checked.
     * @param propsOnly if <code>true</code> only properties are checked.
     */
    public void setPropsOnly(String propsOnly) {
        this.propsOnly = Boolean.valueOf(propsOnly);
    }

    /**
     * {@inheritDoc}
     *
     * Matches if the declaring node type of the item is equal to the one
     * specified in this filter. If the item is a node and <code>propsOnly</code>
     * flag is <code>true</code> it returns <code>false</code>.
     */
    public boolean matches(Item item) throws RepositoryException {
        if (item.isNode()) {
            return !propsOnly && ((Node) item).getDefinition().getDeclaringNodeType().getName().equals(nodeType);
        } else {
            return ((Property) item).getDefinition().getDeclaringNodeType().getName().equals(nodeType);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void dump(DumpContext ctx, boolean isLast) {
        super.dump(ctx, isLast);
        ctx.indent(isLast);
        ctx.printf(false, "nodeType: %s", nodeType);
        ctx.printf(true, "propsOnly: %b", propsOnly);
        ctx.outdent();
    }
    
}