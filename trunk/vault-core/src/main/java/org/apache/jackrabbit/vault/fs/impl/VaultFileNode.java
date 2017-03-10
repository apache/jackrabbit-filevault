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

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import javax.jcr.RepositoryException;

import org.apache.jackrabbit.vault.fs.api.Aggregate;
import org.apache.jackrabbit.vault.fs.api.DumpContext;
import org.apache.jackrabbit.vault.fs.api.Dumpable;

/**
 * The jcr file node combines the aggregates with the Vault file. each file
 * node produces a list of jcr files, composed out of the artifacts on the
 * artifacts node. the Vault files are the inserted into the overall hierarchy
 * of the Vault filesystem.
 *
 */
public class VaultFileNode implements Dumpable {

    /**
     * the artifacts node
     */
    private final AggregateImpl aggregate;

    /**
     * the files of this node
     */
    private List<VaultFileImpl> files = new LinkedList<VaultFileImpl>();

    private Collection<VaultFileNode> children;

    private final VaultFileNode parent;

    public VaultFileNode(VaultFileNode parent, AggregateImpl node) throws RepositoryException {
        this.parent = parent;
        this.aggregate = node;
    }

    public String getName() {
        return aggregate.getRelPath();
    }

    public String getPath() {
        return aggregate.getPath();
    }

    public Collection<VaultFileNode> getChildren() throws RepositoryException {
        if (children == null) {
            children = new LinkedList<VaultFileNode>();
            List<? extends Aggregate> leaves = aggregate.getLeaves();
            if (leaves != null && !leaves.isEmpty()) {
                for (Aggregate child: leaves) {
                    children.add(new VaultFileNode(this, (AggregateImpl) child));
                }
            }
        }
        return children;
    }

    public Aggregate getAggregate() {
        return aggregate;
    }

    public void invalidate() {
        files.clear();
        aggregate.invalidate();
        children = null;
    }

    public VaultFileNode getParent() {
        return parent;
    }

    /**
     * {@inheritDoc}
     */
    public void dump(DumpContext ctx, boolean isLast) {
        ctx.println(isLast, "VaultFileNode");
        ctx.indent(isLast);
        //ctx.printf(false, "# pending: %d", pendingChildNodes == null ? -1 : pendingChildNodes.size());
        //ctx.printf(false, "# children: %d", children == null ? -1 : children.size());
        if (aggregate != null) {
            aggregate.dump(ctx, true);
        } else {
            ctx.println(true, "ArtifactsNode: (null)");
        }
        ctx.outdent();
    }


    protected List<VaultFileImpl> getFiles() {
        return files;
    }
}