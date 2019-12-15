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

package org.apache.jackrabbit.vault.packaging.impl;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.ValueFactory;

import org.apache.jackrabbit.util.ISO8601;
import org.apache.jackrabbit.vault.fs.Mounter;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.api.ProgressTrackerListener;
import org.apache.jackrabbit.vault.fs.api.RepositoryAddress;
import org.apache.jackrabbit.vault.fs.api.VaultFileSystem;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.fs.config.DefaultMetaInf;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.fs.config.MetaInf;
import org.apache.jackrabbit.vault.fs.io.AbstractExporter;
import org.apache.jackrabbit.vault.fs.io.AccessControlHandling;
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.Importer;
import org.apache.jackrabbit.vault.packaging.Dependency;
import org.apache.jackrabbit.vault.packaging.ExportPostProcessor;
import org.apache.jackrabbit.vault.packaging.JcrPackageDefinition;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.apache.jackrabbit.vault.util.Constants;
import org.apache.jackrabbit.vault.util.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The JCR package definition is used to operate with a unwrapped package
 * in the repository.
 */
public class JcrPackageDefinitionImpl implements JcrPackageDefinition {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(JcrPackageDefinitionImpl.class);

    /**
     * underlying node
     */
    @Nonnull
    private Node defNode;

    @Nullable
    private String userId;

    /**
     * Creates a new definition base on the underlying node.
     * @param definitionNode the definition node
     */
    public JcrPackageDefinitionImpl(@Nonnull Node definitionNode) {
        this.defNode = definitionNode;
    }

    /**
     * {@inheritDoc}
     */
    @Nonnull
    public Node getNode() {
        return defNode;
    }

    /**
     * {@inheritDoc}
     */
    public PackageId getId() {
        String group = get(PN_GROUP);
        String name = get(PN_NAME);
        String version = get(PN_VERSION);
        if (group == null || name == null || name.length() == 0) {
            // backward compatible
            String path = getInstallationPath();
            if (path == null) {
                log.warn("Unable to calculate installation path. setting to 'unknown'");
                path = "unknown";
            }
            //noinspection deprecation
            return new PackageId(path, version);
        } else {
            return new PackageId(group, name, version);
        }
    }

    /**
     * Writes the properties derived from the package id to the content
     * @param id the package id
     * @param autoSave if {@code true} the changes are saved automatically.
     */
    public void setId(PackageId id, boolean autoSave) {
        set(PN_GROUP, id.getGroup(), false);
        set(PN_NAME, id.getName(), false);
        set(PN_VERSION, id.getVersionString(), false);
    }

    /**
     * Returns the installation path. If the path is not defined in the definition,
     * the grand-parent of the underlying node is returned. if the path would end
     * with .zip or .jar, the extension is truncated.
     * @return the installation path or {@code null} if it cannot be determined.
     */
    private String getInstallationPath() {
        try {
            String path = get("path");
            if (path == null || path.length() == 0) {
                // get grand parent
                path = defNode.getParent().getParent().getPath();
            }
            int idx = path.lastIndexOf('.');
            if (idx > 0) {
                String ext = path.substring(idx);
                if (".zip".equalsIgnoreCase(ext) || ".jar".equalsIgnoreCase(ext)) {
                    path = path.substring(0, idx);
                }
            }
            return path;
        } catch (RepositoryException e) {
            log.warn("Error during getInstallationPath()", e);
            return null;
        }
    }

    /**
     * {@inheritDoc}
     */
    public boolean isUnwrapped() {
        try {
            // backward compat check
            return defNode.hasProperty("unwrapped")
                    || defNode.hasProperty(PN_LAST_UNWRAPPED);
        } catch (RepositoryException e) {
            log.warn("Error during isUnwrapped()", e);
            return false;
        }
    }

    /**
     * {@inheritDoc}
     */
    public boolean isModified() {
        if (!isUnwrapped()) {
            return false;
        }
        Calendar mod = getLastModified();
        if (mod == null) {
            return false;
        }
        Calendar uw = getLastWrapped();
        if (uw == null) {
            uw = getLastUnwrapped();
        }
        if (uw == null) {
            // backward compat check
            try {
                if (defNode.hasProperty("unwrapped")) {
                    return true;
                }
            } catch (RepositoryException e) {
                log.warn("Error while checking unwrapped property", e);
            }
            return false;
        }
        return mod.after(uw);
    }

    /**
     * {@inheritDoc}
     */
    public void unwrap(VaultPackage pack, boolean force)
            throws RepositoryException, IOException {
        unwrap(pack, force, true);
    }

    /**
     * {@inheritDoc}
     */
    public void unwrap(VaultPackage pack, boolean force, boolean autoSave)
            throws RepositoryException, IOException {
        if (!force && isUnwrapped()) {
            return;
        }
        log.debug("unwrapping package {}", pack == null ? "(unknown)" : pack.getId());
        long now = System.currentTimeMillis();
        unwrap(pack == null ? null : pack.getArchive(), autoSave);
        if (log.isDebugEnabled()) {
            log.debug("unwrapping package {} completed in {}ms", getId(), System.currentTimeMillis() - now);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void unwrap(Archive archive, boolean autoSave)
            throws RepositoryException, IOException {
        if (archive != null) {
            MetaInf inf = archive.getMetaInf();
            // explode definition if present
            if (inf.hasDefinition()) {
                extractDefinition(archive);
            }
            if (inf.getFilter() != null) {
                JcrWorkspaceFilter.saveFilter(inf.getFilter(), defNode, false);
            }
            if (inf.getProperties() != null) {
                writeProperties(inf.getProperties());
            }
        }
        defNode.setProperty("unwrapped", (Value) null);
        defNode.setProperty(PN_LAST_UNWRAPPED, Calendar.getInstance());
        if (autoSave) {
            defNode.getSession().save();
        }
    }

    /**
     * Extracts the content representation of a definition store the given
     * package to this node.
     *
     * @param packArchive the archive of the package
     * @throws RepositoryException if an error occurs
     */
    private void extractDefinition(Archive packArchive)
            throws RepositoryException {
        Archive archive = null;
        try {
            archive = packArchive.getSubArchive("META-INF/vault/definition", true);
        } catch (IOException e) {
            log.error("Error while accessing sub archive", e);
        }
        if (archive == null) {
            log.warn("Unable to extract definition. No such entry in archive.");
            return;
        }
        // need to 'save' last unpacked props
        Value lastUnpacked = null;
        if (defNode.hasProperty(PN_LAST_UNPACKED)) {
            lastUnpacked = defNode.getProperty(PN_LAST_UNPACKED).getValue();
        }
        Value lastUnpackedBy = null;
        if (defNode.hasProperty(PN_LAST_UNPACKED_BY)) {
            lastUnpackedBy = defNode.getProperty(PN_LAST_UNPACKED_BY).getValue();
        }

        Session session = defNode.getSession();
        String rootPath = defNode.getPath();
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        filter.add(new PathFilterSet(rootPath));

        try {
            Importer importer = new Importer();
            // disable saving
            importer.getOptions().setAutoSaveThreshold(Integer.MAX_VALUE);
            importer.getOptions().setFilter(filter);
            importer.run(archive, session, rootPath);

            // refresh defNode if it was replaced during unwrap
            defNode = session.getNode(rootPath);

            // set props again
            if (lastUnpacked != null) {
                defNode.setProperty(PN_LAST_UNPACKED, lastUnpacked);
            }
            if (lastUnpackedBy != null) {
                defNode.setProperty(PN_LAST_UNPACKED_BY, lastUnpackedBy);
            }
        } catch (Exception e) {
            log.error("Unable to extract definition: {}", e.toString());
        }
    }

    /**
     * {@inheritDoc}
     */
    public Dependency[] getDependencies() {
        try {
            if (defNode.hasProperty(PN_DEPENDENCIES)) {
                Property p = defNode.getProperty(PN_DEPENDENCIES);
                List<Dependency> deps = new LinkedList<>();
                if (p.getDefinition().isMultiple()) {
                    for (Value v: p.getValues()) {
                        Dependency dep = Dependency.fromString(v.getString());
                        if (dep != null) {
                            deps.add(dep);
                        }
                    }
                } else {
                    Dependency dep = Dependency.fromString(p.getString());
                    if (dep != null) {
                        deps.add(dep);
                    }
                }
                return deps.toArray(new Dependency[deps.size()]);
            }
        } catch (RepositoryException e) {
            log.error("Error during getDependencies()", e);
        }
        return Dependency.EMPTY;
    }

    /**
     * {@inheritDoc}
     */
    public void setDependencies(@Nonnull Dependency[] dependencies, boolean autoSave) {
        try {
            final List<Value> values = new ArrayList<>(dependencies.length);
            final ValueFactory fac = defNode.getSession().getValueFactory();
            for (Dependency d: dependencies) {
                if (d != null) {
                    values.add(fac.createValue(d.toString()));
                }
            }
            defNode.setProperty(PN_DEPENDENCIES, values.toArray(new Value[values.size()]));
            if (autoSave) {
                defNode.getSession().save();
            }
        } catch (RepositoryException e) {
            log.error("Error during setDependencies()", e);
        }
    }

    /**
     * Load the given properties from the content
     * @param props the properties to load
     */
    private void loadProperties(Properties props) {
        PackageId id = getId();
        setProperty(props, VaultPackage.NAME_VERSION, id.getVersionString());
        setProperty(props, VaultPackage.NAME_NAME, id.getName());
        setProperty(props, VaultPackage.NAME_GROUP, id.getGroup());
        setProperty(props, VaultPackage.NAME_BUILD_COUNT, get(PN_BUILD_COUNT));
        setProperty(props, VaultPackage.NAME_DESCRIPTION, get(PN_DESCRIPTION));
        setProperty(props, VaultPackage.NAME_REQUIRES_ROOT, get(PN_REQUIRES_ROOT));
        setProperty(props, VaultPackage.NAME_REQUIRES_RESTART, get(PN_REQUIRES_RESTART));
        setProperty(props, VaultPackage.NAME_LAST_MODIFIED, getCalendar(PN_LASTMODIFIED));
        setProperty(props, VaultPackage.NAME_LAST_MODIFIED_BY, get(PN_LASTMODIFIED_BY));
        setProperty(props, VaultPackage.NAME_LAST_WRAPPED, getCalendar(PN_LAST_WRAPPED));
        setProperty(props, VaultPackage.NAME_LAST_WRAPPED_BY, get(PN_LAST_WRAPPED_BY));
        setProperty(props, VaultPackage.NAME_CREATED, getCalendar(PN_CREATED));
        setProperty(props, VaultPackage.NAME_CREATED_BY, get(PN_CREATED_BY));
        setProperty(props, VaultPackage.NAME_DEPENDENCIES, Dependency.toString(getDependencies()));
        setProperty(props, VaultPackage.NAME_AC_HANDLING, get(PN_AC_HANDLING));
        setProperty(props, VaultPackage.NAME_CND_PATTERN, get(PN_CND_PATTERN));
    }

    /**
     * internal method that adds or removes a property
     * @param props the properties
     * @param name the name of the properties
     * @param value the value
     */
    private static void setProperty(Properties props, String name, String value) {
        if (value == null) {
            props.remove(name);
        } else {
            props.put(name, value);
        }
    }

    /**
     * internal method that adds or removes a property
     * @param props the properties
     * @param name the name of the properties
     * @param value the value
     */
    private static void setProperty(Properties props, String name, Calendar value) {
        if (value == null) {
            props.remove(name);
        } else {
            props.put(name, ISO8601.format(value));
        }
    }

    /**
     * Writes the given properties to the content.
     * @param props the properties
     */
    private void writeProperties(Properties props) {
        try {
            // sanitize lastModBy property due to former bug that used the
            // lastMod value
            if (props.getProperty(VaultPackage.NAME_LAST_MODIFIED) != null
                    && props.getProperty(VaultPackage.NAME_LAST_MODIFIED).equals(props.getProperty(VaultPackage.NAME_LAST_MODIFIED_BY))) {
                props = new Properties(props);
                props.setProperty(VaultPackage.NAME_LAST_MODIFIED_BY, "unknown");
            }

            // Note that the 'path', 'group' and 'name' properties are usually
            // directly linked to the location of the package in the content.
            // however, if a definition is unwrapped at another location, eg
            // in package-share, it is convenient if the original properties
            // are available in the content.
            defNode.setProperty(PN_VERSION, props.getProperty(VaultPackage.NAME_VERSION));
            defNode.setProperty(PN_BUILD_COUNT, props.getProperty(VaultPackage.NAME_BUILD_COUNT));
            defNode.setProperty(PN_NAME, props.getProperty(VaultPackage.NAME_NAME));
            defNode.setProperty(PN_GROUP, props.getProperty(VaultPackage.NAME_GROUP));
            String deps = props.getProperty(VaultPackage.NAME_DEPENDENCIES);
            if (defNode.hasProperty(PN_DEPENDENCIES)) {
                defNode.getProperty(PN_DEPENDENCIES).remove();
            }
            if (deps != null) {
                List<String> ds = new ArrayList<>();
                for (Dependency d: Dependency.parse(deps)) {
                    if (d != null) {
                        ds.add(d.toString());
                    }
                }
                defNode.setProperty(PN_DEPENDENCIES, ds.toArray(new String[ds.size()]));
            }
            defNode.setProperty(PN_DESCRIPTION, props.getProperty(VaultPackage.NAME_DESCRIPTION));
            defNode.setProperty(PN_REQUIRES_ROOT, Boolean.valueOf(props.getProperty(VaultPackage.NAME_REQUIRES_ROOT, "false")));
            defNode.setProperty(PN_REQUIRES_RESTART, Boolean.valueOf(props.getProperty(VaultPackage.NAME_REQUIRES_RESTART, "false")));
            defNode.setProperty(PN_LASTMODIFIED, getDate(props.getProperty(VaultPackage.NAME_LAST_MODIFIED)));
            defNode.setProperty(PN_LASTMODIFIED_BY, props.getProperty(VaultPackage.NAME_LAST_MODIFIED_BY));
            defNode.setProperty(PN_CREATED, getDate(props.getProperty(VaultPackage.NAME_CREATED)));
            defNode.setProperty(PN_CREATED_BY, props.getProperty(VaultPackage.NAME_CREATED_BY));
            defNode.setProperty(PN_LAST_WRAPPED, getDate(props.getProperty(VaultPackage.NAME_LAST_WRAPPED)));
            defNode.setProperty(PN_LAST_WRAPPED_BY, props.getProperty(VaultPackage.NAME_LAST_WRAPPED_BY));
            defNode.setProperty(PN_AC_HANDLING, props.getProperty(VaultPackage.NAME_AC_HANDLING));
            defNode.setProperty(PN_CND_PATTERN, props.getProperty(VaultPackage.NAME_CND_PATTERN));
            defNode.setProperty(PN_DISABLE_INTERMEDIATE_SAVE, props.getProperty(VaultPackage.NAME_DISABLE_INTERMEDIATE_SAVE));
        } catch (RepositoryException e) {
            log.error("error while saving properties.", e);
        }
    }

    /**
     * Internal method that converts a ISO date to a calendar.
     * @param iso the iso8601 formatted date
     * @return the calendar or {@code null}
     */
    private static Calendar getDate(String iso) {
        if (iso == null) {
            return null;
        }
        // check for missing : in timezone part
        String tzd = iso.substring(iso.length() - 4);
        if (tzd.indexOf(':') < 0) {
            iso = iso.substring(0, iso.length() - 4);
            iso += tzd.substring(0, 2);
            iso += ":";
            iso += tzd.substring(2);
        }
        return ISO8601.parse(iso);
    }

    /**
     * {@inheritDoc}
     */
    public void dumpCoverage(ProgressTrackerListener listener)
            throws RepositoryException {
        WorkspaceFilter filter = getMetaInf().getFilter();
        if (filter != null) {
            filter.dumpCoverage(defNode.getSession(), listener, false);
        }
    }

    /**
     * {@inheritDoc}
     */
    public String get(String name) {
        try {
            if (defNode.hasProperty(name)) {
                return defNode.getProperty(name).getString();
            }
        } catch (RepositoryException e) {
            log.error("Error during get({})", name, e);
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    public boolean getBoolean(String name) {
        try {
            if (defNode.hasProperty(name)) {
                return defNode.getProperty(name).getBoolean();
            }
        } catch (RepositoryException e) {
            log.error("Error during getBoolean({})", name, e);
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public Calendar getCalendar(String name) {
        try {
            if (defNode.hasProperty(name)) {
                return defNode.getProperty(name).getDate();
            }
        } catch (RepositoryException e) {
            log.error("Error during getCalendar({})", name, e);
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    public void set(String name, String value, boolean autoSave) {
        try {
            defNode.setProperty(name, value);
            touch(null, autoSave);
        } catch (RepositoryException e) {
            log.error("Error during set({})", name, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void set(String name, Calendar value, boolean autoSave) {
        try {
            defNode.setProperty(name, value);
            touch(null, autoSave);
        } catch (RepositoryException e) {
            log.error("Error during set({})", name, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void set(String name, boolean value, boolean autoSave) {
        try {
            defNode.setProperty(name, value);
            touch(null, autoSave);
        } catch (RepositoryException e) {
            log.error("Error during set({})", name, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void touch(Calendar now, boolean autoSave) {
        try {
            defNode.setProperty(PN_LASTMODIFIED,
                    now == null ? Calendar.getInstance() : now);
            defNode.setProperty(PN_LASTMODIFIED_BY, getUserId());
            if (autoSave) {
                defNode.getSession().save();
            }
        } catch (RepositoryException e) {
            log.error("Error during touch()", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void setFilter(WorkspaceFilter filter, boolean autoSave) {
        try {
            JcrWorkspaceFilter.saveFilter(filter, defNode, autoSave);
        } catch (RepositoryException e) {
            log.error("Error while saving filter.", e);
        }
    }

    /**
     * Seals the package for assembly:
     * - touches this package
     * - increments the build count
     * - updates the created(by) properties
     * - updates the lastUnwrapped(by) properties
     * - clears the unwrapped property
     *
     * @param now the date or {@code null}
     */
    void sealForAssembly(Calendar now) {
        try {
            if (now == null) {
                now = Calendar.getInstance();
            }
            defNode.setProperty(PN_BUILD_COUNT, String.valueOf(getBuildCount() + 1));
            defNode.setProperty(PN_CREATED, now);
            defNode.setProperty(PN_CREATED_BY, getUserId());
            defNode.setProperty(PN_LAST_WRAPPED, now);
            defNode.setProperty(PN_LAST_WRAPPED_BY, getUserId());
            defNode.setProperty(PN_LAST_UNWRAPPED, now);
            defNode.setProperty(PN_LAST_UNWRAPPED_BY, getUserId());
            defNode.setProperty("unwrapped", (Value) null);
            touch(now, false);
            defNode.getSession().save();
        } catch (RepositoryException e) {
            log.error("Error during sealForAssembly()", e);
        }
    }

    /**
     * Seals the package for assembly:
     * - touches this package
     * - increments the build count
     * - updates the lastUnwrapped(by) properties
     * - clears the unwrapped property
     *
     * @param now the date or {@code null}
     */
    void sealForRewrap(Calendar now) {
        try {
            if (now == null) {
                now = Calendar.getInstance();
            }
            defNode.setProperty(PN_BUILD_COUNT, String.valueOf(getBuildCount() + 1));
            if (!defNode.hasProperty(PN_CREATED)) {
                defNode.setProperty(PN_CREATED, now);
                defNode.setProperty(PN_CREATED_BY, getUserId());
            }
            defNode.setProperty(PN_LAST_WRAPPED, now);
            defNode.setProperty(PN_LAST_WRAPPED_BY, getUserId());
            defNode.setProperty(PN_LAST_UNWRAPPED, now);
            defNode.setProperty(PN_LAST_UNWRAPPED_BY, getUserId());
            defNode.setProperty("unwrapped", (Value) null);
            touch(now, false);
            defNode.getSession().save();
        } catch (RepositoryException e) {
            log.error("Error during sealForRewrap()", e);
        }
    }

    /**
     * Touches the lastUnpacked (i.e. installed) properties.
     */
    void touchLastUnpacked() {
        try {
            defNode.setProperty(PN_LAST_UNPACKED, Calendar.getInstance());
            defNode.setProperty(PN_LAST_UNPACKED_BY, getUserId());
            defNode.getSession().save();
        } catch (RepositoryException e) {
            log.error("Error during touchLastUnpacked()", e);
        }
    }

    /**
     * Clears the last unpacked properties.
     * @param autoSave saves the changes automatically if {@code true}
     */
    void clearLastUnpacked(boolean autoSave) {
        try {
            if (defNode.hasProperty(PN_LAST_UNPACKED)) {
                defNode.getProperty(PN_LAST_UNPACKED).remove();
            }
            if (defNode.hasProperty(PN_LAST_UNPACKED_BY)) {
                defNode.getProperty(PN_LAST_UNPACKED_BY).remove();
            }
            if (autoSave) {
                defNode.getSession().save();
            }
        } catch (RepositoryException e) {
            log.error("Error during clearLastUnpacked()", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    public Calendar getLastModified() {
        return getCalendar(PN_LASTMODIFIED);
    }

    /**
     * {@inheritDoc}
     */
    public String getLastModifiedBy() {
        return get(PN_LASTMODIFIED_BY);
    }

    /**
     * {@inheritDoc}
     */
    public Calendar getCreated() {
        return getCalendar(PN_CREATED);
    }

    /**
     * {@inheritDoc}
     */
    public String getCreatedBy() {
        return get(PN_CREATED_BY);
    }

    /**
     * {@inheritDoc}
     */
    public Calendar getLastUnwrapped() {
        return getCalendar(PN_LAST_UNWRAPPED);
    }

    /**
     * {@inheritDoc}
     */
    public String getLastWrappedBy() {
        return get(PN_LAST_WRAPPED_BY);
    }

    /**
     * {@inheritDoc}
     */
    public Calendar getLastWrapped() {
        return getCalendar(PN_LAST_WRAPPED);
    }

    /**
     * {@inheritDoc}
     */
    public String getLastUnwrappedBy() {
        return get(PN_LAST_UNWRAPPED_BY);
    }

    /**
     * {@inheritDoc}
     */
    public Calendar getLastUnpacked() {
        return getCalendar(PN_LAST_UNPACKED);
    }

    /**
     * {@inheritDoc}
     */
    public String getLastUnpackedBy() {
        return get(PN_LAST_UNPACKED_BY);
    }

    /**
     * {@inheritDoc}
     */
    @Deprecated
    public boolean requiresRoot() {
        return getBoolean(PN_REQUIRES_ROOT);
    }

    /**
     * {@inheritDoc}
     */
    public boolean requiresRestart() {
        return getBoolean(PN_REQUIRES_RESTART);
    }

    /**
     * {@inheritDoc}
     */
    public AccessControlHandling getAccessControlHandling() {
        String acHandling = get(PN_AC_HANDLING);
        try {
            return acHandling == null
                    ? null
                    : AccessControlHandling.valueOf(acHandling.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("invalid access control handling in definition: {} of {}", acHandling, getId());
            return null;
        }
    }

    /**
     * {@inheritDoc}
     */
    public String getDescription() {
        return get(PN_DESCRIPTION);
    }

    /**
     * {@inheritDoc}
     */
    public long getBuildCount() {
        try {
            String bc = get(PN_BUILD_COUNT);
            return bc == null ? 0 : Long.valueOf(bc);
        } catch (NumberFormatException e) {
            log.warn("Wrong build count in {}.", getId(), e);
            return 0;
        }
    }

    /**
     * {@inheritDoc}
     */
    public MetaInf getMetaInf() throws RepositoryException {
        DefaultMetaInf inf = new DefaultMetaInf();
        inf.setFilter(JcrWorkspaceFilter.loadFilter(defNode));

        // add properties
        Properties props = new Properties();
        loadProperties(props);
        inf.setProperties(props);

        return inf;
    }

    /**
     * Returns a export processor that adds the inline definition package to
     * the exporter.
     * @return the export processor for this definition
     */
    ExportPostProcessor getInjectProcessor() {
        return new InjectProcessor(defNode);
    }

    /**
     * Returns the user id of the current session. if the userid provided by
     * the session is {@code null}, "system" is returned.
     * @return the user id
     */
    private String getUserId() {
        if (userId == null) {
            try {
                userId = defNode.getSession().getUserID();
            } catch (RepositoryException e) {
                // ignore
            }
            if (userId == null) {
                userId = "system";
            }
        }
        return userId;
    }

    /**
     * Returns the list of sub packages set on the definition node via the {@link #PN_SUB_PACKAGES} property.
     * @return the list of sub package ids
     * @throws RepositoryException if an error occurs.
     */
    List<PackageId> getSubPackages() throws RepositoryException {
        LinkedList<PackageId> subPackages = new LinkedList<>();
        if (defNode.hasProperty(PN_SUB_PACKAGES)) {
            Value[] subIds = defNode.getProperty(PN_SUB_PACKAGES).getValues();
            for (Value v : subIds) {
                // reverse installation order
                subPackages.add(PackageId.fromString(v.getString()));
            }
        }
        return subPackages;
    }

    /**
     * Sets the package Ids to the definition node
     * @param subPackageIds the package Ids
     * @throws RepositoryException if an error occurs
     */
    void setSubPackages(Collection<PackageId> subPackageIds) throws RepositoryException {
        String[] subIds = new String[subPackageIds.size()];
        int i =0;
        for (PackageId subId: subPackageIds) {
            subIds[i++] = subId.toString();
        }
        defNode.setProperty(PN_SUB_PACKAGES, subIds);
    }

    /**
     * Returns a new state object that can be used to save modification information.
     * @return a new state object.
     */
    public State getState() {
        return new State().load(this);
    }

    /**
     * Sets the information stored in the state object back to this definition.
     * @param state the sate
     */
    public void setState(State state) {
        state.save(this);
    }

    public static class State {

        private final String[] PROPERTY_NAMES = {
                PN_LAST_UNPACKED,    PN_LAST_UNWRAPPED,    PN_LASTMODIFIED,    PN_LAST_WRAPPED,
                PN_LAST_UNPACKED_BY, PN_LAST_UNWRAPPED_BY, PN_LASTMODIFIED_BY, PN_LAST_WRAPPED_BY
        };
        private final Value[] values = new Value[PROPERTY_NAMES.length];

        private State load(JcrPackageDefinitionImpl def) {
            for (int i=0; i<PROPERTY_NAMES.length; i++) {
                try {
                    if (def.defNode.hasProperty(PROPERTY_NAMES[i])) {
                        values[i] = def.defNode.getProperty(PROPERTY_NAMES[i]).getValue();
                    } else {
                        values[i] = null;
                    }
                } catch (RepositoryException e) {
                    log.error("Error while reading property {}: {}", PROPERTY_NAMES[i], e.toString());
                }
            }
            return this;
        }

        private void save(JcrPackageDefinitionImpl def) {
            for (int i=0; i<PROPERTY_NAMES.length; i++) {
                if (values[i] != null) {
                    try {
                        def.defNode.setProperty(PROPERTY_NAMES[i], values[i]);
                    } catch (RepositoryException e) {
                        log.error("Error while setting {}: {}", PROPERTY_NAMES[i], e.toString());
                    }
                }
            }
        }
    }

    private static class InjectProcessor implements ExportPostProcessor {

        private final Node defNode;

        private InjectProcessor(Node defNode) {
            this.defNode = defNode;
        }

        public void process(AbstractExporter exporter) {
            try {
                // remove temporarily the 'unpacked' properties.
                // todo: do differently as soon we can filter properties
                if (defNode.hasProperty(PN_LAST_UNPACKED)) {
                    defNode.getProperty(PN_LAST_UNPACKED).remove();
                }
                if (defNode.hasProperty(PN_LAST_UNPACKED_BY)) {
                    defNode.getProperty(PN_LAST_UNPACKED_BY).remove();
                }

                String rootPath = defNode.getPath();
                DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
                filter.add(new PathFilterSet(rootPath));
                RepositoryAddress addr;
                try {
                    addr = new RepositoryAddress(
                            Text.escapePath("/" + defNode.getSession().getWorkspace().getName() + rootPath));
                } catch (URISyntaxException e) {
                    throw new IllegalArgumentException(e);
                }
                VaultFileSystem jcrfs = Mounter.mount(null, filter, addr, "/definition", defNode.getSession());
                exporter.setRelativePaths(true);
                exporter.setRootPath("");
                exporter.createDirectory(Constants.META_DIR + "/definition");
                exporter.export(jcrfs.getRoot(), Constants.META_DIR + "/definition");
                jcrfs.unmount();
            } catch (Exception e) {
                log.error("Error during post processing", e);
            } finally {
                try {
                    // revert removed properties
                    defNode.getSession().refresh(false);
                } catch (RepositoryException e) {
                    // ignore
                }
            }
        }
    }
}