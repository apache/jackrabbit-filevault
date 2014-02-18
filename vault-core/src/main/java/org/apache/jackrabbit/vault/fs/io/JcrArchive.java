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

package org.apache.jackrabbit.vault.fs.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.api.VaultInputSource;
import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.fs.config.DefaultMetaInf;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.fs.config.MetaInf;
import org.apache.jackrabbit.vault.fs.config.VaultSettings;
import org.apache.jackrabbit.vault.fs.spi.CNDReader;
import org.apache.jackrabbit.vault.fs.spi.ServiceProviderFactory;
import org.apache.jackrabbit.vault.util.Constants;
import org.apache.jackrabbit.vault.util.JcrConstants;
import org.apache.jackrabbit.vault.util.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements an archive that is based on nt:file/nt:folder nodes in a JCR
 * repository.
 */
public class JcrArchive extends AbstractArchive {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(JcrArchive.class);

    private Node archiveRoot;

    private final String rootPath;

    private DefaultMetaInf inf;

    private Entry jcrRoot;

    private String chRoot;

    public JcrArchive(Node archiveRoot, String rootPath) {
        this.archiveRoot = archiveRoot;
        this.rootPath = rootPath;
    }

    public void open(boolean strict) throws IOException {
        if (jcrRoot != null) {
            return;
        }
        try {
            if (archiveRoot.hasNode(Constants.ROOT_DIR)) {
                jcrRoot = new JcrEntry(archiveRoot.getNode(Constants.ROOT_DIR), Constants.ROOT_DIR, true);
            } else {
                jcrRoot = new JcrEntry(archiveRoot, archiveRoot.getName(), true);
            }
            if (archiveRoot.hasNode(Constants.META_DIR)) {
                inf = loadMetaInf(
                        new JcrEntry(archiveRoot.getNode(Constants.META_DIR), Constants.META_DIR, true),
                        strict);
            } else {
                inf = new DefaultMetaInf();
                inf.setSettings(VaultSettings.createDefault());
                DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
                PathFilterSet filterSet = new PathFilterSet(rootPath);
                filter.add(filterSet);
                inf.setFilter(filter);

                // if archive is rooted, create intermediate entries
                if (chRoot != null && chRoot.length() > 0) {
                    String[] roots = Text.explode(rootPath, '/');
                    if (roots.length > 0) {
                        VirtualEntry newRoot = new VirtualEntry(jcrRoot.getName());
                        VirtualEntry entry = newRoot;
                        for (String name: roots) {
                            VirtualEntry newEntry = new VirtualEntry(name);
                            entry.children.put(name, newEntry);
                            entry = newEntry;
                        }
                        for (Entry e: jcrRoot.getChildren()) {
                            entry.children.put(e.getName(), e);
                        }
                        jcrRoot = newRoot;
                    }
                }
            }
        } catch (RepositoryException e) {
            IOException ie = new IOException("Error while opening JCR archive.");
            ie.initCause(e);
            throw ie;
        } catch (ConfigurationException e) {
            IOException ie = new IOException("Error while opening JCR archive.");
            ie.initCause(e);
            throw ie;
        }
    }

    private DefaultMetaInf loadMetaInf(Entry dir, boolean strict)
            throws IOException, ConfigurationException {
        DefaultMetaInf inf = new DefaultMetaInf();
        // filter
        for (Entry entry: dir.getChildren()) {
            String name = entry.getName();
            VaultInputSource src = getInputSource(entry);
            if (name.equals(Constants.FILTER_XML)) {
                // load filter
                inf.loadFilter(src.getByteStream(), src.getSystemId());
            } else if (name.equals(Constants.CONFIG_XML)) {
                // load config
                inf.loadConfig(src.getByteStream(), src.getSystemId());
            } else if (name.equals(Constants.SETTINGS_XML)) {
                // load settings
                inf.loadSettings(src.getByteStream(), src.getSystemId());
            } else if (name.equals(Constants.PROPERTIES_XML)) {
                // load properties
                inf.loadProperties(src.getByteStream(), src.getSystemId());
            } else if (name.equals(Constants.PRIVILEGES_XML)) {
                // load privileges
                inf.loadPrivileges(src.getByteStream(), src.getSystemId());
            } else if (name.equals(Constants.PACKAGE_DEFINITION_XML)) {
                inf.setHasDefinition(true);
                log.info("Contains package definition {}.", src.getSystemId());
            } else if (name.endsWith(".cnd")) {
                try {
                    Reader r = new InputStreamReader(src.getByteStream(), "utf8");
                    CNDReader reader = ServiceProviderFactory.getProvider().getCNDReader();
                    reader.read(r, entry.getName(), null);
                    inf.getNodeTypes().add(reader);
                    log.info("Loaded nodetypes from {}.", src.getSystemId());
                } catch (IOException e1) {
                    log.error("Error while reading CND: {}", e1.toString());
                    if (strict) {
                        throw e1;
                    }
                }
            }
        }
        if (inf.getFilter() == null) {
            log.info("Archive {} does not contain filter definition.", this);
        }
        if (inf.getConfig() == null) {
            log.info("Archive {} does not contain vault config.", this);
        }
        if (inf.getSettings() == null) {
            log.info("Archive {} does not contain vault settings. using default.", this);
            VaultSettings settings = new VaultSettings();
            settings.getIgnoredNames().add(".svn");
            inf.setSettings(settings);
        }
        if (inf.getProperties() == null) {
            log.info("Archive {} does not contain properties.", this);
        }
        if (inf.getNodeTypes().isEmpty()) {
            log.info("Archive {} does not contain nodetypes.", this);
        }
        return inf;
    }

    public void close() {
        archiveRoot = null;
        jcrRoot = null;
    }

    public Entry getJcrRoot() {
        return jcrRoot;
    }

    public Entry getRoot() throws IOException {
        return new JcrEntry(archiveRoot, "", true);
    }

    public MetaInf getMetaInf() {
        return inf;
    }

    public InputStream openInputStream(Entry entry) throws IOException {
        if (entry == null || entry.isDirectory()) {
            return null;
        }
        try {
            Node content = ((JcrEntry) entry).node.getNode(JcrConstants.JCR_CONTENT);
            return content.getProperty(JcrConstants.JCR_DATA).getBinary().getStream();
        } catch (RepositoryException e) {
            IOException e1 = new IOException("Unable to open input source.");
            e1.initCause(e);
            throw e1;
        }
    }

    public VaultInputSource getInputSource(final Entry entry) throws IOException {
        if (entry == null || entry.isDirectory()) {
            return null;
        }
        try {
            final Node content = ((JcrEntry) entry).node.getNode(JcrConstants.JCR_CONTENT);
            final String systemId = ((JcrEntry) entry).node.getPath();
            return new VaultInputSource() {

                {
                    setSystemId(systemId);
                }

                public InputStream getByteStream() {
                    try {
                        // todo: handle releasing of binary ?
                        return content.getProperty(JcrConstants.JCR_DATA).getBinary().getStream();
                    } catch (RepositoryException e) {
                        log.error("Error while opening input stream of " + content, e);
                        return null;
                    }
                }

                /**
                 * {@inheritDoc}
                 */
                public long getContentLength() {
                    try {
                        return content.getProperty(JcrConstants.JCR_DATA).getLength();
                    } catch (RepositoryException e) {
                        log.error("Error while retrieving length of " + content, e);
                        return -1;
                    }
                }

                /**
                 * {@inheritDoc}
                 */
                public long getLastModified() {
                    try {
                        return content.getProperty(JcrConstants.JCR_LASTMODIFIED).getDate().getTimeInMillis();
                    } catch (RepositoryException e) {
                        log.error("Error while retrieving last modified of " + content, e);
                        return 0;
                    }
                }

            };

        } catch (RepositoryException e) {
            IOException e1 = new IOException("Unable to open input source.");
            e1.initCause(e);
            throw e1;
        }
    }

    @Override
    public String toString() {
        try {
            return archiveRoot.getPath();
        } catch (RepositoryException e) {
            return archiveRoot.toString();
        }
    }

    private static class VirtualEntry implements Entry {

        private final String name;

        private Map<String, Entry> children = new LinkedHashMap<String, Entry>();

        private VirtualEntry(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public boolean isDirectory() {
            return true;
        }

        public Collection<? extends Entry> getChildren() {
            return children.values();
        }

        public Entry getChild(String name) {
            return children.get(name);
        }
    }

    private static class JcrEntry implements Entry {

        private final Node node;

        private final boolean isDir;

        private final String name;

        private JcrEntry(Node node, String name, boolean isDir) {
            this.node = node;
            this.isDir = isDir;
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public boolean isDirectory() {
            return isDir;
        }

        public Collection<Entry> getChildren() {
            if (isDir) {
                try {
                    NodeIterator iter = node.getNodes();
                    long size = iter.getSize();
                    if (size < 0) {
                        size = 0;
                    }
                    List<Entry> ret = new ArrayList<Entry>((int) size);
                    while (iter.hasNext()) {
                        Node child = iter.nextNode();
                        String name = child.getName();
                        if (name.equals(".svn")) {
                            // skip already
                            continue;
                        }
                        boolean isDir;
                        if (child.isNodeType("nt:folder")) {
                            isDir = true;
                        } else if (child.isNodeType("nt:file")) {
                            isDir = false;
                        } else {
                            log.info("Skipping node {} with unknown type {}.", child.getPath(), child.getPrimaryNodeType().getName());
                            continue;
                        }
                        ret.add(new JcrEntry(child, name, isDir));
                    }
                    return ret;
                } catch (RepositoryException e) {
                    log.error("Error while listing child nodes of {}", node, e);
                    throw new IllegalStateException("Error while listing child nodes of " + node, e);
                }
            }
            return Collections.emptyList();
        }

        public Entry getChild(String name) {
            try {
                if (isDir && node.hasNode(name) && !name.equals(".svn")) {
                    Node child = node.getNode(name);
                    boolean isDir;
                    if (child.isNodeType("nt:folder")) {
                        isDir = true;
                    } else if (child.isNodeType("nt:file")) {
                        isDir = false;
                    } else {
                        log.info("Skipping node {} with unknown type {}.", child.getPath(), child.getPrimaryNodeType().getName());
                        return null;
                    }
                    return new JcrEntry(child, name, isDir);
                }
                return null;
            } catch (RepositoryException e) {
                log.error("Error while retrieving child node of {}", node, e);
                throw new IllegalStateException("Error while retrieving child node of " + node, e);
            }
        }
    }

}