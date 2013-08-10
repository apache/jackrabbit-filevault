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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.nodetype.NodeDefinition;
import javax.jcr.nodetype.NodeType;
import javax.jcr.nodetype.NodeTypeManager;

import org.apache.jackrabbit.util.ISO8601;
import org.apache.jackrabbit.vault.fs.api.AggregateManager;
import org.apache.jackrabbit.vault.fs.api.ProgressTrackerListener;
import org.apache.jackrabbit.vault.fs.api.SimplePathMapping;
import org.apache.jackrabbit.vault.fs.api.VaultFile;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.config.MetaInf;
import org.apache.jackrabbit.vault.fs.spi.CNDWriter;
import org.apache.jackrabbit.vault.fs.spi.ProgressTracker;
import org.apache.jackrabbit.vault.fs.spi.ServiceProviderFactory;
import org.apache.jackrabbit.vault.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generic context for exporters
 *
 */
public abstract class AbstractExporter {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(AbstractExporter.class);

    private ProgressTracker tracker;

    private boolean relativePaths;

    private String rootPath = Constants.ROOT_DIR;

    private Properties properties = new Properties();

    private boolean noMetaInf;

    protected ExportInfo exportInfo = new ExportInfo();

    public boolean isVerbose() {
        return tracker != null;
    }

    public void setVerbose(ProgressTrackerListener out) {
        if (out == null) {
            tracker = null;
        } else {
            if (tracker == null) {
                tracker = new ProgressTracker();
            }
            tracker.setListener(out);
        }
    }

    public boolean isRelativePaths() {
        return relativePaths;
    }

    public void setProperty(String name, String value) {
        properties.put(name, value);
    }

    public void setProperty(String name, Calendar value) {
        properties.put(name, ISO8601.format(value));
    }

    public void setProperties(Properties properties) {
        if (properties != null) {
            this.properties.putAll(properties);
        }
    }

    public String getRootPath() {
        return rootPath;
    }

    public void setRootPath(String rootPath) {
        this.rootPath = rootPath;
    }

    public boolean isNoMetaInf() {
        return noMetaInf;
    }

    public void setNoMetaInf(boolean noMetaInf) {
        this.noMetaInf = noMetaInf;
    }

    public ExportInfo getExportInfo() {
        return exportInfo;
    }

    /**
     * Defines if the exported files should include their entire path or just
     * be relative to the export root. eg.: exporting /apps/components relative
     * would not include /apps in the path.
     *
     * @param relativePaths relative flag
     */
    public void setRelativePaths(boolean relativePaths) {
        this.relativePaths = relativePaths;
    }

    /**
     * Exports the given vault file and writes the META-INF data.
     * @param parent the vault file
     * @throws RepositoryException if an error occurs
     * @throws IOException if an I/O error occurs
     */
    public void export(VaultFile parent) throws RepositoryException, IOException {
        export(parent, false);
    }

    /**
     * Exports the given vault file and writes the META-INF data.
     * @param parent the vault file
     * @param noClose if <code>true</code> exporter will not be closed after export
     * @throws RepositoryException if an error occurs
     * @throws IOException if an I/O error occurs
     */
    public void export(VaultFile parent, boolean noClose)
            throws RepositoryException, IOException {
        exportInfo.getEntries().clear();
        open();
        AggregateManager mgr = parent.getFileSystem().getAggregateManager();
        mgr.startTracking(tracker == null ? null : tracker.getListener());
        if (!noMetaInf) {
            createDirectory(Constants.META_INF);
            createDirectory(Constants.META_DIR);
            // add some 'fake' tracking
            track("A", Constants.META_INF);
            track("A", Constants.META_DIR);
            track("A", Constants.META_DIR + "/" + Constants.CONFIG_XML);
            track("A", Constants.META_DIR + "/" + Constants.FILTER_XML);
            track("A", Constants.META_DIR + "/" + Constants.NODETYPES_CND);
            track("A", Constants.META_DIR + "/" + Constants.PROPERTIES_XML);
            writeFile(mgr.getConfig().getSource(), Constants.META_DIR + "/" + Constants.CONFIG_XML);
            // get filter and translate if necessary
            WorkspaceFilter filter = mgr.getWorkspaceFilter();
            String mountPath = mgr.getRoot().getPath();
            String rootPath = parent.getPath();
            if (rootPath.equals("/")) {
                rootPath = "";
            }
            if (mountPath.length() > 0 || rootPath.length() > 0) {
                filter = filter.translate(new SimplePathMapping(mountPath, rootPath));
            }
            writeFile(filter.getSource(), Constants.META_DIR + "/" + Constants.FILTER_XML);
        }
        export(parent, "");
        if (!noMetaInf) {
            writeFile(getNodeTypes(mgr.getSession(), mgr.getNodeTypes()), Constants.META_DIR + "/" + Constants.NODETYPES_CND);
            // update properties
            setProperty(MetaInf.CREATED, Calendar.getInstance());
            setProperty(MetaInf.CREATED_BY, mgr.getUserId());
            setProperty(MetaInf.PACKAGE_FORMAT_VERSION, String.valueOf(MetaInf.FORMAT_VERSION_2));
            ByteArrayOutputStream tmpOut = new ByteArrayOutputStream();
            properties.storeToXML(tmpOut, "FileVault Package Properties", "utf-8");
            writeFile(new ByteArrayInputStream(tmpOut.toByteArray()), Constants.META_DIR + "/" + Constants.PROPERTIES_XML);
        }
        if (!noClose) {
            close();
        }
        mgr.stopTracking();
    }

    /**
     * Exports the vault file to the relative path.
     * @param parent the file
     * @param relPath the path
     * @throws RepositoryException if an error occurs
     * @throws IOException if an I/O error occurs
     */
    public void export(VaultFile parent, String relPath)
            throws RepositoryException, IOException {
        for (VaultFile vaultFile : parent.getChildren()) {
            String path = relPath + "/" + vaultFile.getName();
            if (vaultFile.isDirectory()) {
                createDirectory(vaultFile, path);
                export(vaultFile, path);
            } else {
                writeFile(vaultFile, path);
            }
        }
    }

    protected void track(String action, String path) {
        if ("E".equals(action)) {
            log.error("{} {}", action, path);
        } else {
            log.debug("{} {}", action, path);
        }
        if (tracker != null) {
            tracker.track(action, path);
        }
    }

    protected void track(Exception e, String path) {
        log.error("E {} ({})", path, e.toString());
        if (tracker != null) {
            tracker.track(e, path);
        }
    }
    
    protected String getPlatformFilePath(VaultFile file, String relPath) {
        StringBuilder buf = new StringBuilder(rootPath);
        if (isRelativePaths()) {
            // relative paths are only needed for special exports, like the definition export of packaging
            if (buf.length() > 0) {
                buf.append("/");
            }
            buf.append(relPath);
        } else {
            buf.append(file.getPath());
        }
        return buf.toString();
    }
    
    private InputStream getNodeTypes(Session s, Collection<String> nodeTypes)
            throws IOException, RepositoryException {
        NodeTypeManager ntMgr = s.getWorkspace().getNodeTypeManager();
        // init with repository predefined node types
        Set<String> written = new HashSet<String>();
        written.addAll(ServiceProviderFactory.getProvider().getBuiltInNodeTypeNames());
        StringWriter out = new StringWriter();
        CNDWriter w = ServiceProviderFactory.getProvider().getCNDWriter(out, s, true);
        for (String nt: nodeTypes) {
            writeNodeType(ntMgr.getNodeType(nt), w, written);
        }
        w.close();
        return new ByteArrayInputStream(out.getBuffer().toString().getBytes("utf-8"));
    }

    private void writeNodeType(NodeType nt, CNDWriter w, Set<String> written)
            throws IOException, RepositoryException {
        if (nt != null && !written.contains(nt.getName())) {
            written.add(nt.getName());
            w.write(nt);
            for (NodeType s: nt.getSupertypes()) {
                writeNodeType(s, w, written);
            }
            for (NodeDefinition n: nt.getChildNodeDefinitions()) {
                writeNodeType(n.getDefaultPrimaryType(), w, written);
                if (n.getRequiredPrimaryTypes() != null) {
                    for (NodeType r: n.getRequiredPrimaryTypes()) {
                        writeNodeType(r, w, written);
                    }
                }
            }
        }
    }

    /**
     * Opens the exporter and initializes the undelying structures.
     * @throws IOException if an I/O error occurs
     * @throws RepositoryException if a repository error occurs
     */
    public abstract void open() throws IOException, RepositoryException;

    /**
     * Closes the exporter and releases the undelying structures.
     * @throws IOException if an I/O error occurs
     * @throws RepositoryException if a repository error occurs
     */
    public abstract void close() throws IOException, RepositoryException;

    public abstract void createDirectory(String relPath)
            throws IOException;

    public abstract void createDirectory(VaultFile file, String relPath)
            throws RepositoryException, IOException;

    public abstract void writeFile(InputStream in, String relPath)
            throws IOException;

    public abstract void writeFile(VaultFile file, String relPath)
            throws RepositoryException, IOException;

}