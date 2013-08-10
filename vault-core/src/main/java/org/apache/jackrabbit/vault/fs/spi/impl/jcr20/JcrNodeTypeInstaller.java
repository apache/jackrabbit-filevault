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

package org.apache.jackrabbit.vault.fs.spi.impl.jcr20;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.nodetype.NodeType;
import javax.jcr.nodetype.NodeTypeDefinition;
import javax.jcr.nodetype.NodeTypeIterator;
import javax.jcr.nodetype.NodeTypeManager;

import org.apache.jackrabbit.spi.QNodeTypeDefinition;
import org.apache.jackrabbit.spi.commons.conversion.DefaultNamePathResolver;
import org.apache.jackrabbit.spi.commons.nodetype.NodeTypeDefinitionFactory;
import org.apache.jackrabbit.vault.fs.api.ProgressTrackerListener;
import org.apache.jackrabbit.vault.fs.spi.DefaultNodeTypeSet;
import org.apache.jackrabbit.vault.fs.spi.NodeTypeInstaller;
import org.apache.jackrabbit.vault.fs.spi.NodeTypeSet;
import org.apache.jackrabbit.vault.fs.spi.ProgressTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The <code>JcrNodeTypeInstaller</code> is used to install nodetypes using the
 * JCR 2.0 node type install features
 */
public class JcrNodeTypeInstaller implements NodeTypeInstaller {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(JcrNodeTypeInstaller.class);

    private final Session session;

    public JcrNodeTypeInstaller(Session session) {
        this.session = session;
    }

    /**
     * {@inheritDoc}
     */
    public Collection<NodeType> install(ProgressTracker tracker, NodeTypeSet types)
            throws IOException, RepositoryException {

        // register node types
        NodeTypeManager ntMgr = session.getWorkspace().getNodeTypeManager();

        // filter out registered
        DefaultNodeTypeSet set;
        if (types instanceof DefaultNodeTypeSet) {
            set = (DefaultNodeTypeSet) types;
        } else {
            set = new DefaultNodeTypeSet(types);
        }
        log.debug("Removing registered nodetypes");
        DefaultNamePathResolver npResolver = new DefaultNamePathResolver(session);
        NodeTypeIterator iter = ntMgr.getAllNodeTypes();
        while (iter.hasNext()) {
            NodeType nt = iter.nextNodeType();
            set.remove(npResolver.getQName(nt.getName()));
        }

        ProgressTrackerListener.Mode mode = null;
        if (tracker != null) {
            mode = tracker.setMode(ProgressTrackerListener.Mode.TEXT);
        }

        // register namespaces
        Map<String, String> pfxToURI = set.getNamespaceMapping().getPrefixToURIMapping();
        if (!pfxToURI.isEmpty()) {
            for (Object o : pfxToURI.keySet()) {
                String prefix = (String) o;
                String uri = (String) pfxToURI.get(prefix);
                try {
                    session.getNamespacePrefix(uri);
                    track(tracker, "-", prefix + " -> " + uri);
                } catch (RepositoryException e) {
                    session.getWorkspace().getNamespaceRegistry().registerNamespace(prefix, uri);
                    track(tracker, "A", prefix + " -> " + uri);
                }
            }
        }

        // register node types
        NodeTypeDefinitionFactory fac = new NodeTypeDefinitionFactory(session);
        List<NodeTypeDefinition> nodeTypes = fac.create(set.getNodeTypes().values());
        if (nodeTypes.size() > 0) {
            try {
                ntMgr.registerNodeTypes(nodeTypes.toArray(new NodeTypeDefinition[nodeTypes.size()]), true);
            } catch (UnsupportedOperationException e) {
                log.error("Unable to install node types.");
                throw e;
            }
        }

        // add some tracking info
        for (QNodeTypeDefinition t: set.getRemoved().values()) {
            String name = npResolver.getJCRName(t.getName());
            track(tracker, "-", name);
        }
        List<NodeType> nts = new LinkedList<NodeType>();
        for (QNodeTypeDefinition t: set.getNodeTypes().values()) {
            String name = npResolver.getJCRName(t.getName());
            track(tracker, "A", name);
            nts.add(session.getWorkspace().getNodeTypeManager().getNodeType(name));
        }
        if (tracker != null) {
            tracker.setMode(mode);
        }
        return nts;
    }

    private void track(ProgressTracker tracker, String action, String path) {
        log.debug("{} {}", action, path);
        if (tracker != null) {
            tracker.track(action, path);
        }
    }
}