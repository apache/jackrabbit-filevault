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
import org.apache.jackrabbit.vault.fs.api.MultiPathMapping;
import org.apache.jackrabbit.vault.fs.api.NodeNameList;
import org.apache.jackrabbit.vault.fs.api.PathMapping;
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

    private final TreeMap<String, Info> infos = new TreeMap<String, Info>(new PathComparator());

    private MultiPathMapping mapping = null;

    /**
     * list of uuids of nodes that need to be checked-in after the import
     */
    private final Set<String> toVersion = new LinkedHashSet<String>();

    private int numModified;

    private int numErrors;

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
            infos.putAll(baseImpl.infos);
            numModified +=baseImpl.numModified;
            numErrors += baseImpl.numErrors;
            toVersion.addAll(baseImpl.toVersion);
            if (mapping == null) {
                mapping = baseImpl.mapping;
            } else {
                mapping.merge(baseImpl.mapping);
            }
            if (memberships == null) {
                memberships = baseImpl.memberships;
            } else {
                memberships.putAll(baseImpl.getMemberships());
            }
        }
        return this;
    }

    public TreeMap<String, Info> getInfos() {
        return infos;
    }

    public Info getInfo(String path) {
        return infos.get(path);
    }

    @Deprecated
    public NodeNameList getNameList() {
        return infos.isEmpty()
                ? null
                : infos.firstEntry().getValue().getNameList();
    }

    private InfoImpl getOrCreateInfo(String path) {
        InfoImpl info = (InfoImpl) infos.get(path);
        if (info == null) {
            info = new InfoImpl(path);
            infos.put(path, info);
        }
        return info;
    }

    public void addNameList(String path, NodeNameList nameList) {
        getOrCreateInfo(path).nameList = nameList;
    }

    public void onModified(String path) {
        Type prev = getOrCreateInfo(path).type;
        if (prev == null || prev != Type.CRE) {
            addMod(path, Type.MOD, null);
        }
    }

    public void onNop(String path) {
        getOrCreateInfo(path);
    }

    public void onCreated(String path) {
        addMod(path, Type.CRE, null);
    }

    public void onDeleted(String path) {
        addMod(path, Type.DEL, null);
    }

    public void onReplaced(String path) {
        addMod(path, Type.REP, null);
    }

    public void onMissing(String path) {
        addMod(path, Type.MIS, null);
    }

    public void onError(String path, Exception e) {
        addMod(path, Type.ERR, e);
        numErrors++;
    }

    /**
     * remembers that a package path was remapped during import. e.g. when the importer follows and existing
     * authorizable for MERGE and UPDATE modes.
     *
     * @param packagePath the original path as presented in the package
     * @param followedPath the followed path during the import
     */
    public void onRemapped(String followedPath, String packagePath) {
        if (!packagePath.equals(followedPath)) {
            if (mapping == null) {
                mapping = new MultiPathMapping();
            }
            mapping.link(followedPath, packagePath);
        }
    }

    public PathMapping getRemapped() {
        return mapping == null ? PathMapping.IDENTITY : mapping;
    }

    private void addMod(String path, Type mod, Exception e) {
        InfoImpl info = getOrCreateInfo(path);
        if (info.type != Type.ERR) {
            info.type = mod;
            info.error = e;
        }
        if (mod != Type.NOP) {
            numModified++;
        }
        log.debug("{} {}", mod, path);
    }

    public TreeMap<String, Type> getModifications() {
        TreeMap<String, Type> mods = new TreeMap<String, Type>();
        for (Map.Entry<String, Info> e: infos.entrySet()) {
            Type mod = e.getValue().getType();
            if (mod != null) {
                mods.put(e.getKey(), mod);
            }
        }
        return mods;
    }

    @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
    @Deprecated
    public Exception getError(String path) {
        Info info = infos.get(path);
        return info == null ? null : info.getError();
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
        return numErrors;
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

    static final class InfoImpl implements Info {

        private final String path;

        private Type type = Type.NOP;

        private NodeNameList nameList;

        private Exception error;

        InfoImpl(String path) {
            this.path = path;
        }

        public String getPath() {
            return path;
        }

        public Type getType() {
            return type;
        }

        public NodeNameList getNameList() {
            return nameList;
        }

        public Exception getError() {
            return error;
        }
    }
}