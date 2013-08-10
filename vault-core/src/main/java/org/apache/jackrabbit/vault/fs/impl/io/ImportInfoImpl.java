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

package org.apache.jackrabbit.vault.fs.impl.io;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.version.Version;

import org.apache.jackrabbit.vault.fs.api.ImportInfo;
import org.apache.jackrabbit.vault.fs.api.NodeNameList;
import org.apache.jackrabbit.vault.util.JcrConstants;
import org.apache.jackrabbit.vault.util.PathComparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <code>ImportInfo</code>...
 *
 */
public class ImportInfoImpl implements ImportInfo {

    /**
     * the default logger
     */
    static final Logger log = LoggerFactory.getLogger(ImportInfoImpl.class);

    private final TreeMap<String, Type> mods = new TreeMap<String, Type>(new PathComparator());

    private final Map<String, Exception> errorMap =  new HashMap<String, Exception>();

    private Map<String, String> remapped;

    /**
     * list of uuids of nodes that need to be checked-in after the import
     */
    private final Set<String> toVersion = new LinkedHashSet<String>();

    private NodeNameList nameList;

    private Node node;

    private int numModified;

    private Map<String, String[]> memberships;

    public static ImportInfo create(ImportInfo base) {
        if (base == null) {
            return new ImportInfoImpl();
        } else {
            return base;
        }
    }

    public ImportInfoImpl() {
    }

    public ImportInfoImpl merge(ImportInfo base) {
        if (base instanceof ImportInfoImpl) {
            ImportInfoImpl baseImpl = (ImportInfoImpl) base;
            mods.putAll(baseImpl.mods);
            errorMap.putAll(baseImpl.errorMap);
            numModified +=baseImpl.numModified;
            toVersion.addAll(baseImpl.toVersion);
            // also merge node and name list if not set yet
            if (node == null) {
                node = baseImpl.node;
                nameList = baseImpl.nameList;
            }
            if (remapped == null) {
                remapped = baseImpl.remapped;
            } else {
                remapped.putAll(baseImpl.getRemapped());
            }
            if (memberships == null) {
                memberships = baseImpl.memberships;
            } else {
                memberships.putAll(baseImpl.getMemberships());
            }
        }
        return this;
    }

    public NodeNameList getNameList() {
        return nameList;
    }

    public void setNameList(NodeNameList nameList) {
        this.nameList = nameList;
    }

    /**
     * Returns the root node of this import.
     * @return root node if this import or <code>null</code>
     */
    public Node getNode() {
        return node;
    }

    public void setNode(Node node) {
        this.node = node;
    }

    public void onModified(String path) {
        Type prev = mods.get(path);
        if (prev == null || prev != Type.CRE) {
            addMod(path, Type.MOD);
        }
    }

    public void onNop(String path) {
        if (!mods.containsKey(path)) {
            addMod(path, Type.NOP);
        }
    }

    public void onCreated(String path) {
        addMod(path, Type.CRE);
    }

    public void onDeleted(String path) {
        addMod(path, Type.DEL);
    }

    public void onReplaced(String path) {
        addMod(path, Type.REP);
    }

    public void onMissing(String path) {
        addMod(path, Type.MIS);
    }

    public void onError(String path, Exception e) {
        addMod(path, Type.ERR);
        errorMap.put(path, e);
    }

    public void onRemapped(String oldPath, String newPath) {
        if (remapped == null) {
            remapped = new HashMap<String, String>();
        }
        remapped.put(oldPath, newPath);
    }

    public Map<String, String> getRemapped() {
        return remapped == null ? Collections.<String, String>emptyMap() : remapped;
    }

    private void addMod(String path, Type mod) {
        // don't overwrite errors
        if (mods.get(path) != Type.ERR) {
            mods.put(path, mod);
            if (mod != Type.NOP) {
                numModified++;
            }
        }
        log.debug("{} {}", mod, path);
    }

    public TreeMap<String, Type> getModifications() {
        return mods;
    }

    public Exception getError(String path) {
        return errorMap.get(path);
    }

    public Collection<String> getToVersion() {
        return toVersion;
    }
    
    public void registerToVersion(String path) {
        toVersion.add(path);
    }

    public void checkinNodes(Session session) {
        Iterator<String> iter = toVersion.iterator();
        while (iter.hasNext()) {
            String path = iter.next();
            iter.remove();
            try {
                Node node = session.getNode(path);
                // check if node is really versionable. SPI might not have known it at the time
                // this node was registered for versioning
                if (node.isNodeType(JcrConstants.MIX_VERSIONABLE)) {
                    try {
                        Version v = node.checkin();
                        log.info("Checked in versionable node {} (v={})", path, v.getName());
                    } catch (RepositoryException e) {
                        log.error("Error while checkin node {}: {}", path, e.toString());
                    }
                }
            } catch (RepositoryException e) {
                log.error("Error while retrieving node to be versioned at {}.", path, e);
            }
        }
    }

    public int numModified() {
        return numModified;
    }

    public int numErrors() {
        return errorMap.size();
    }

    public void registerMemberships(String id, String[] members) {
        if (memberships == null) {
            memberships = new HashMap<String, String[]>();
        }
        memberships.put(id, members);
    }

    public Map<String, String[]> getMemberships() {
        return memberships == null ? Collections.<String, String[]>emptyMap() : memberships;
    }
}