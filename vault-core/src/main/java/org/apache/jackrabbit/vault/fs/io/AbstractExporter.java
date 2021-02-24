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
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.nodetype.NodeDefinition;
import javax.jcr.nodetype.NodeType;
import javax.jcr.nodetype.NodeTypeManager;

import org.apache.jackrabbit.util.ISO8601;
import org.apache.jackrabbit.vault.fs.api.AggregateManager;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.api.ProgressTrackerListener;
import org.apache.jackrabbit.vault.fs.api.SimplePathMapping;
import org.apache.jackrabbit.vault.fs.api.VaultFile;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.config.MetaInf;
import org.apache.jackrabbit.vault.fs.spi.CNDWriter;
import org.apache.jackrabbit.vault.fs.spi.ProgressTracker;
import org.apache.jackrabbit.vault.fs.spi.ServiceProviderFactory;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.PackageType;
import org.apache.jackrabbit.vault.util.Constants;
import org.apache.jackrabbit.util.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.jackrabbit.vault.packaging.PackageProperties.NAME_DEPENDENCIES;
import static org.apache.jackrabbit.vault.packaging.PackageProperties.NAME_DESCRIPTION;
import static org.apache.jackrabbit.vault.packaging.PackageProperties.NAME_GROUP;
import static org.apache.jackrabbit.vault.packaging.PackageProperties.NAME_NAME;
import static org.apache.jackrabbit.vault.packaging.PackageProperties.NAME_PACKAGE_TYPE;
import static org.apache.jackrabbit.vault.packaging.PackageProperties.NAME_VERSION;

/**
 * Generic context for exporters
 *
 */
public abstract class AbstractExporter implements AutoCloseable {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(AbstractExporter.class);

    /**
     * name of the manifest property for the package id
     */
    private static final String MF_PACKAGE_ID = "Content-Package-Id";

    /**
     * name of the manifest property for the package dependencies
     */
    private static final String MF_PACKAGE_DEPENDENCIES = "Content-Package-Dependencies";

    /**
     * name of the manifest property for the package roots
     */
    private static final String MF_PACKAGE_ROOTS = "Content-Package-Roots";

    /**
     * name of the manifest property for the package description
     */
    private static final String MF_PACKAGE_DESC = "Content-Package-Description";

    /**
     * name of the manifest property for the package type
     */
    private static final String MF_PACKAGE_TYPE = "Content-Package-Type";

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
     * @param noClose if {@code true} exporter will not be closed after export
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
            // update properties
            setProperty(MetaInf.CREATED, Calendar.getInstance());
            setProperty(MetaInf.CREATED_BY, mgr.getUserId());
            setProperty(MetaInf.PACKAGE_FORMAT_VERSION, String.valueOf(MetaInf.FORMAT_VERSION_2));

            // get filter and translate if necessary
            WorkspaceFilter filter = mgr.getWorkspaceFilter();
            String mountPath = mgr.getRoot().getPath();
            String rootPath = parent.getPath();
            if ("/".equals(rootPath)) {
                rootPath = "";
            }
            if (mountPath.length() > 0 || rootPath.length() > 0) {
                filter = filter.translate(new SimplePathMapping(mountPath, rootPath));
            }

            // check for package type
            if (!properties.containsKey(NAME_PACKAGE_TYPE)) {
                properties.setProperty(NAME_PACKAGE_TYPE, detectPackageType(filter).name().toLowerCase());
            }

            // write Manifest
            Manifest mf = new Manifest();
            mf.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            String version = properties.getProperty(NAME_VERSION);
            if (version == null) {
                version = "";
            }
            String group = properties.getProperty(NAME_GROUP);
            String name = properties.getProperty(NAME_NAME);
            PackageId id = new PackageId(group, name, version);
            Set<String> rts = new HashSet<String>();
            for (PathFilterSet p: filter.getFilterSets()) {
                rts.add(p.getRoot());
            }
            String[] filterRoots = rts.toArray(new String[rts.size()]);
            addManifestAttribute(mf, MF_PACKAGE_ID, id.toString());
            addManifestAttribute(mf, MF_PACKAGE_DESC, properties.getProperty(NAME_DESCRIPTION));
            addManifestAttribute(mf, MF_PACKAGE_ROOTS, Text.implode(filterRoots, ","));
            addManifestAttribute(mf, MF_PACKAGE_DEPENDENCIES, properties.getProperty(NAME_DEPENDENCIES));
            addManifestAttribute(mf, MF_PACKAGE_TYPE, properties.getProperty(NAME_PACKAGE_TYPE));
            ByteArrayOutputStream tmpOut = new ByteArrayOutputStream();
            mf.write(tmpOut);
            writeFile(new ByteArrayInputStream(tmpOut.toByteArray()), JarFile.MANIFEST_NAME);

            createDirectory(Constants.META_INF);
            createDirectory(Constants.META_DIR);
            // add some 'fake' tracking
            track("A", Constants.META_INF);
            track("A", JarFile.MANIFEST_NAME);
            track("A", Constants.META_DIR);
            track("A", Constants.META_DIR + "/" + Constants.CONFIG_XML);
            track("A", Constants.META_DIR + "/" + Constants.FILTER_XML);
            track("A", Constants.META_DIR + "/" + Constants.NODETYPES_CND);
            track("A", Constants.META_DIR + "/" + Constants.PROPERTIES_XML);

            // write properties
            tmpOut = new ByteArrayOutputStream();
            properties.storeToXML(tmpOut, "FileVault Package Properties", "utf-8");
            writeFile(new ByteArrayInputStream(tmpOut.toByteArray()), Constants.META_DIR + "/" + Constants.PROPERTIES_XML);
            writeFile(mgr.getConfig().getSource(), Constants.META_DIR + "/" + Constants.CONFIG_XML);
            writeFile(filter.getSource(), Constants.META_DIR + "/" + Constants.FILTER_XML);
        }
        export(parent, "");
        if (!noMetaInf) {
            // write node types last, as they are calculated during export.
            writeFile(getNodeTypes(mgr.getSession(), mgr.getNodeTypes()), Constants.META_DIR + "/" + Constants.NODETYPES_CND);
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
     * Adds a new attribute to the given manifest.
     * @param manifest the manifest
     * @param key attribute name
     * @param value attribute value
     */
    private static void addManifestAttribute(Manifest manifest, String key, String value) {
        if (value != null && value.length() > 0) {
            Attributes.Name name = new Attributes.Name(key);
            manifest.getMainAttributes().put(name, value);
        }
    }

    /**
     * Detects the package type based on the workspace filter.
     * @param filter the workspace filter
     * @return the package type
     */
    private static PackageType detectPackageType(WorkspaceFilter filter)  {
        boolean hasApps = false;
        boolean hasOther = false;
        for (PathFilterSet p: filter.getFilterSets()) {
            if ("cleanup".equals(p.getType())) {
                continue;
            }
            String root = p.getRoot();
            if ("/apps".equals(root) || root.startsWith("/apps/") || "/libs".equals(root) || root.startsWith("/libs/")) {
                hasApps = true;
            } else {
                hasOther = true;
            }
        }
        if (hasApps && !hasOther) {
            return PackageType.APPLICATION;
        } else if (hasOther && !hasApps) {
            return PackageType.CONTENT;
        }
        return PackageType.MIXED;
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

    /**
     * <p>The specified stream remains open after this method returns.
     * @param in
     * @param relPath
     * @throws IOException
     */
    public abstract void writeFile(InputStream in, String relPath)
            throws IOException;

    public abstract void writeFile(VaultFile file, String relPath)
            throws RepositoryException, IOException;

}