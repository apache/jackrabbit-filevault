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

package org.apache.jackrabbit.vault.fs.spi;

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

import javax.jcr.NamespaceException;
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.spi.QNodeTypeDefinition;
import org.apache.jackrabbit.spi.commons.namespace.NamespaceMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 */
public class DefaultNodeTypeSet implements NodeTypeSet {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(DefaultNodeTypeSet.class);

    private String systemId;

    /**
     * the list of nodetype templates
     */
    private Map<Name, QNodeTypeDefinition> nodeTypes =
            new TreeMap<Name, QNodeTypeDefinition>();

    /**
     * the current namespace mapping
     */
    private NamespaceMapping nsMapping = new NamespaceMapping();

    /**
     * the list of removed nodetype templates
     */
    private Map<Name, QNodeTypeDefinition> removed =
            new TreeMap<Name, QNodeTypeDefinition>();


    public DefaultNodeTypeSet(String systemId) {
        this.systemId = systemId;
    }

    public DefaultNodeTypeSet(NodeTypeSet set) {
        this(set.getSystemId(), set.getNodeTypes().values(), set.getNamespaceMapping());
    }

    public DefaultNodeTypeSet(String systemId,
                              Collection<QNodeTypeDefinition> nodeTypes,
                              NamespaceMapping nsMapping) {
        this.systemId = systemId;
        for (QNodeTypeDefinition t: nodeTypes) {
            this.nodeTypes.put(t.getName(), t);
        }
        this.nsMapping = nsMapping;
    }

    public void add(NodeTypeSet set) {
        for (QNodeTypeDefinition tpl: set.getNodeTypes().values()) {
            log.debug("adding {}", tpl.getName());
            nodeTypes.put(tpl.getName(), tpl);
        }
        add(set.getNamespaceMapping());
    }

    public void add(Collection<QNodeTypeDefinition> set, NamespaceMapping nsMapping) {
        for (QNodeTypeDefinition tpl: set) {
            log.debug("adding {}", tpl.getName());
            nodeTypes.put(tpl.getName(), tpl);
        }
        add(nsMapping);
    }

    private void add(NamespaceMapping mapping) {
        for (Object o : mapping.getPrefixToURIMapping().keySet()) {
            try {
                String pfx = (String) o;
                String uri = mapping.getURI(pfx);
                nsMapping.setMapping(pfx, uri);
            } catch (NamespaceException e) {
                throw new IllegalStateException("Error while transfering mappings.", e);
            }
        }
    }

    public QNodeTypeDefinition remove(Name name)
            throws RepositoryException {
        QNodeTypeDefinition tpl = nodeTypes.remove(name);
        if (tpl != null) {
            removed.put(tpl.getName(), tpl);
            log.debug("removing registered {}", tpl.getName());
        }
        return tpl;
    }

    public Map<Name, QNodeTypeDefinition> getNodeTypes() {
        return nodeTypes;
    }

    public NamespaceMapping getNamespaceMapping() {
        return nsMapping;
    }

    public Map<Name, QNodeTypeDefinition> getRemoved() {
        return removed;
    }

    public String getSystemId() {
        return systemId;
    }

    public void setSystemId(String systemId) {
        this.systemId = systemId;
    }
}