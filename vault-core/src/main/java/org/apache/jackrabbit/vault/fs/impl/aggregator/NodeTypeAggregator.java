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

package org.apache.jackrabbit.vault.fs.impl.aggregator;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.vault.fs.api.ArtifactType;
import org.apache.jackrabbit.vault.fs.api.DumpContext;
import org.apache.jackrabbit.vault.fs.api.ImportInfo;
import org.apache.jackrabbit.vault.fs.filter.NodeTypeItemFilter;
import org.apache.jackrabbit.vault.fs.impl.AggregateImpl;
import org.apache.jackrabbit.vault.fs.impl.ArtifactSetImpl;
import org.apache.jackrabbit.vault.fs.impl.io.CNDSerializer;
import org.apache.jackrabbit.vault.fs.impl.io.ImportInfoImpl;
import org.apache.jackrabbit.vault.fs.io.Serializer;
import org.apache.jackrabbit.vault.util.JcrConstants;

/**
 * Implements an aggregator that serializes nt:nodeType nodes into a .cnd files.
 */
public class NodeTypeAggregator extends GenericAggregator {

    /**
     * {@inheritDoc}
     *
     * @return <code>true</code> always.
     */
    public boolean hasFullCoverage() {
        return true;
    }

    /**
     * {@inheritDoc}
     *
     * If no match filter is defined, add the nt:nodeType as node type filter.
     */
    public boolean matches(Node node, String path) throws RepositoryException {
        if (getMatchFilter().isEmpty()) {
            getMatchFilter().addInclude(
                    new NodeTypeItemFilter(JcrConstants.NT_NODETYPE, true)
            );
        }
        return super.matches(node, path);
    }


    /**
     * {@inheritDoc}
     * @param aggregate
     */
    public ArtifactSetImpl createArtifacts(AggregateImpl aggregate) throws RepositoryException {
        ArtifactSetImpl artifacts = new ArtifactSetImpl();
        Serializer ser = new CNDSerializer(aggregate);
        artifacts.add(null, aggregate.getRelPath(), ".xcnd", ArtifactType.PRIMARY, ser, 0);
        return artifacts;
    }

    /**
     * {@inheritDoc}
     */
    public ImportInfo remove(Node node, boolean recursive, boolean trySave) throws RepositoryException {
        ImportInfo info = new ImportInfoImpl();
        info.onDeleted(node.getPath());
        Node parent = node.getParent();
        node.remove();
        if (trySave) {
            parent.save();
        }
        return info;
    }

    /**
     * {@inheritDoc}
     */
    public void dump(DumpContext ctx, boolean isLast) {
        ctx.println(isLast, getClass().getSimpleName());
    }
    
}