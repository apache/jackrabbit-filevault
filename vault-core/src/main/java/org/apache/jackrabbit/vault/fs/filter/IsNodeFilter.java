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
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.vault.fs.api.DumpContext;

/**
 * Item filter that checks if an item is a node.
 *
 */
public class IsNodeFilter extends DepthItemFilter {

    /**
     * Polarity of this filter
     */
    private boolean isNode = true;

    /**
     * Default constructor.
     */
    public IsNodeFilter() {
        super(1, Integer.MAX_VALUE);
    }

    /**
     * Creates a new node item filter.
     *
     * @param polarity the polarity of this filter. if <code>true</code> it matches
     * nodes, if <code>false</code> it matches properties.
     * @param minDepth the minimum depth
     * @param maxDepth the maximum depth
     *
     * @see DepthItemFilter
     */
    public IsNodeFilter(boolean polarity, int minDepth, int maxDepth) {
        super(minDepth, maxDepth);
        isNode = polarity;
    }

    /**
     * Creates a new node item filter
     * @param polarity the polarity of this filter. if <code>true</code> it matches
     * nodes, if <code>false</code> it matches properties.
     */
    public IsNodeFilter(boolean polarity) {
        this(polarity, 1, Integer.MAX_VALUE);
    }

    /**
     * Sets the polarity of this filter. If set to <code>true</code> this filter
     * matches nodes otherwise properties.
     *
     * @param polarity the polarity
     */
    public void setPolarity(String polarity) {
        setIsNode(polarity);
    }

    /**
     * Sets the polarity of this filter. If set to <code>true</code> this filter
     * matches nodes otherwise properties.
     *
     * @param polarity the polarity
     */
    public void setIsNode(String polarity) {
        isNode = Boolean.valueOf(polarity);
    }

    /**
     * {@inheritDoc}
     *
     * Returns <code>true</code> if the item is a node and the polarity is
     * positive (true).
     */
    public boolean matches(Item item) throws RepositoryException {
        return item.isNode() == isNode;
    }

    /**
     * {@inheritDoc}
     */
    public void dump(DumpContext ctx, boolean isLast) {
        super.dump(ctx, isLast);
        ctx.indent(isLast);
        ctx.printf(true, "isNode: %b", isNode);
        ctx.outdent();
    }

}