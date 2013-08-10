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

package org.apache.jackrabbit.vault.fs.impl;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.vault.fs.api.Aggregator;
import org.apache.jackrabbit.vault.fs.api.DumpContext;

/**
 * List of configured aggregators that selects one given a repository node.
 */
public class AggregatorProvider {

    /**
     * list of aggregators
     */
    private final List<Aggregator> aggregators;

    /**
     * Constructs a new aggregator provider with a given aggregator list.
     * @param aggregators the list of aggregators.
     */
    public AggregatorProvider(List<Aggregator> aggregators) {
        this.aggregators = Collections.unmodifiableList(aggregators);
    }

    /**
     * Returns the list of aggregators
     * @return the list of aggregators
     */
    public List<Aggregator> getAggregators() {
        return aggregators;
    }

    /**
     * Selects an aggregator that can handle the given node. If no aggregator can
     * be found, <code>null</code> is returned. Although this is a very rare case
     * because there should always be a default, catch-all aggregator.
     *
     * @param node the node to match
     * @return an aggregator that handles the node or <code>null</code> if not found.
     * @throws RepositoryException if a repository error occurs
     */
    public Aggregator getAggregator(Node node, String path) throws RepositoryException {
        for (Aggregator a: aggregators) {
            if (a.matches(node, path)) {
                return a;
            }
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    public void dump(DumpContext ctx, boolean isLast) {
        ctx.println(isLast, "aggregators");
        ctx.indent(isLast);
        for (Iterator<Aggregator> iter = aggregators.iterator(); iter.hasNext();) {
            Aggregator a = iter.next();
            a.dump(ctx, !iter.hasNext());
        }
        ctx.outdent();
    }
}