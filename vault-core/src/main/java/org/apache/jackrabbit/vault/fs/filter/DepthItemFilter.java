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
import org.apache.jackrabbit.vault.fs.api.ItemFilter;

/**
 * Implements a filter that filters item according to their (passed) depth.
 *
 */
public class DepthItemFilter implements ItemFilter {

    /**
     * The minimal depth
     */
    private int minDepth = 0;

    /**
     * The maximal depth
     */
    private int maxDepth = Integer.MAX_VALUE;

    /**
     * Default constructor.
     */
    public DepthItemFilter() {
    }

    /**
     * Creates a new depth filter for the given depths.
     * @param minDepth the minimal depth
     * @param maxDepth the maximal depth
     */
    public DepthItemFilter(int minDepth, int maxDepth) {
        this.minDepth = minDepth;
        this.maxDepth = maxDepth;
    }

    /**
     * Sets the minimal depth
     * @param minDepth the minimal depth
     */
    public void setMinDepth(String minDepth) {
        this.minDepth = Integer.decode(minDepth);
    }

    /**
     * Sets the maximal depth
     * @param maxDepth the maximal depth
     */
    public void setMaxDepth(String maxDepth) {
        this.maxDepth = Integer.decode(maxDepth);
    }

    /**
     * {@inheritDoc}
     *
     * Matches if the given depth is greater or equal the minimum depth and
     * less or equal the maximum depth and if the call to {@link #matches(Item)}
     * returns <code>true</code>.
     */
    public boolean matches(Item item, int depth) throws RepositoryException {
        return depth >= minDepth && depth <= maxDepth && matches(item);
    }

    /**
     * Returns <code>true</code>. Subclasses can override to implement something
     * useful that is dependant of the depth.
     * 
     * @param item the item to match
     * @return <code>true</code> if the item matches; <code>false</code> otherwise.
     * @throws RepositoryException if an error occurs.
     */
    public boolean matches(Item item) throws RepositoryException {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    public void dump(DumpContext ctx, boolean isLast) {
        ctx.printf(isLast, "%s:", getClass().getSimpleName());
        ctx.indent(isLast);
        ctx.printf(false, "minDepth: %d", minDepth);
        ctx.printf(true, "maxDepth: %d", maxDepth);
        ctx.outdent();
    }
}