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

package org.apache.jackrabbit.vault.fs.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Map;
import java.util.Properties;

import javax.jcr.NamespaceException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.jackrabbit.spi.commons.privilege.ParseException;
import org.apache.jackrabbit.spi.commons.privilege.PrivilegeDefinitionReader;
import org.apache.jackrabbit.vault.fs.api.VaultFsConfig;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.spi.CNDReader;
import org.apache.jackrabbit.vault.fs.spi.NodeTypeSet;
import org.apache.jackrabbit.vault.fs.spi.PrivilegeDefinitions;
import org.apache.jackrabbit.vault.fs.spi.ServiceProviderFactory;
import org.apache.jackrabbit.vault.packaging.PackageProperties;
import org.apache.jackrabbit.vault.packaging.impl.PackagePropertiesImpl;
import org.apache.jackrabbit.vault.util.Constants;
import org.apache.jackrabbit.util.Text;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstracts the way of accessing the vault specific meta-info of a checkout.
 */
public class DefaultMetaInf implements MetaInf {

    private static Logger log = LoggerFactory.getLogger(DefaultMetaInf.class);

    private VaultSettings settings;

    private WorkspaceFilter filter;

    private VaultFsConfig config;

    private Properties properties;

    private Collection<NodeTypeSet> cnds = new LinkedList<>();

    private PrivilegeDefinitions privileges = new PrivilegeDefinitions();

    private boolean hasDefinition;

    /**
     * Returns the package format version of this package. If the package
     * lacks this information, {@link #FORMAT_VERSION_2} is returned, since this
     * feature was implemented recently.
     *
     * @return the package format version
     * @since 2.0
     */
    public int getPackageFormatVersion() {
        String prop = properties == null
                ? null
                : properties.getProperty(PACKAGE_FORMAT_VERSION);
        if (prop != null) {
            try {
                return Integer.parseInt(prop);
            } catch (Exception e) {
                // ignore
            }
        }
        return FORMAT_VERSION_2;
    }

    /**
     * Loads a setting based on the name of the system id.
     * <p>The specified stream remains open after this method returns.
     * 
     * @param systemId the system id of the setting to load
     * @param in the input stream
     * @return {@code true} if the setting was loaded.
     * @throws IOException if an I/O error occurrs
     * @throws ConfigurationException if a configuration error occurrs
     *
     * @since 3.1.32
     */
    public boolean load(@Nullable InputStream in, @NotNull String systemId) throws IOException, ConfigurationException {
        if (systemId.endsWith("/" + Constants.PACKAGE_DEFINITION_XML)) {
            setHasDefinition(true);
            log.trace("Contains package definition {}.", systemId);
            return true;
        }
        if (in == null) {
            return false;
        }

        final String name = Text.getName(systemId);
        if (Constants.FILTER_XML.equals(name)) {
            // load filter
            loadFilter(in, systemId);
            return true;
        } else if (Constants.CONFIG_XML.equals(name)) {
            // load config
            loadConfig(in, systemId);
            return true;
        } else if (Constants.SETTINGS_XML.equals(name)) {
            // load settings
            loadSettings(in, systemId);
            return true;
        } else if (Constants.PROPERTIES_XML.equals(name)) {
            // load properties
            loadProperties(in, systemId);
            return true;
        } else if (Constants.PRIVILEGES_XML.equals(name)) {
            // load privileges
            loadPrivileges(in, systemId);
            return true;
        } else if (name.endsWith(".cnd")) {
            Reader r = new InputStreamReader(in, "utf8");
            CNDReader reader = ServiceProviderFactory.getProvider().getCNDReader();
            reader.read(r, systemId, null);
            getNodeTypes().add(reader);
            log.trace("Loaded nodetypes from {}.", systemId);
            return true;
        }
        return false;
    }

    /**
     * 
     * <p>The specified stream remains open after this method returns.
     * 
     * @param in
     * @param systemId
     * @throws ConfigurationException
     * @throws IOException
     */
    public void loadFilter(@NotNull InputStream in, @NotNull String systemId)
            throws ConfigurationException, IOException {
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        filter.load(in);
        setFilter(filter);
        log.trace("Loaded filter from {}.", systemId);
    }

    /**
     * <p>The specified stream remains open after this method returns.
     * @param in
     * @param systemId
     * @throws ConfigurationException
     * @throws IOException
     */
    public void loadConfig(@NotNull InputStream in, @NotNull String systemId)
            throws ConfigurationException, IOException {
        VaultFsConfig config = AbstractVaultFsConfig.load(in, systemId);
        setConfig(config);
        log.trace("Loaded config from {}.", systemId);
    }

    public void loadSettings(@NotNull InputStream in, @NotNull String systemId)
            throws ConfigurationException, IOException {
        VaultSettings settings = new VaultSettings();
        settings.load(in);
        setSettings(settings);
        log.trace("Loaded settings from {}.", systemId);
    }

    /**
     * <p>The specified stream remains open after this method returns.
     * @param in
     * @param systemId
     * @throws IOException
     */
    public void loadProperties(@NotNull InputStream in, @NotNull String systemId)
            throws IOException {
        Properties props = new Properties();
        // prevent the input stream from being closed for achieving a consistent behaviour
        props.loadFromXML(new CloseShieldInputStream(in));
        setProperties(props);
        log.trace("Loaded properties from {}.", systemId);
    }

    /**
     * <p>The specified stream remains open after this method returns.
     * @param in
     * @param systemId
     * @throws IOException
     */
    public void loadPrivileges(@NotNull InputStream in, @NotNull String systemId)
            throws IOException {
        try {
            PrivilegeDefinitionReader reader = new PrivilegeDefinitionReader(in, "text/xml");
            Collections.addAll(privileges.getDefinitions(), reader.getPrivilegeDefinitions());
            for (Map.Entry<String, String> e: reader.getNamespaces().entrySet()) {
                privileges.getNamespaceMapping().setMapping(e.getKey(), e.getValue());
            }
        } catch (ParseException | NamespaceException e) {
            log.error("Error while reading Privileges: {}", e.toString());
            throw new IOException("Error while reading privileges.", e);
        }
        log.trace("Loaded privileges from {}.", systemId);
    }

    public void save(@NotNull File metaDir) throws IOException {
        if (metaDir.isDirectory()) {
            saveConfig(metaDir);
            saveFilter(metaDir);
            saveSettings(metaDir);
            saveProperties(metaDir);
        } else {
            throw new IOException("meta directory does not exist or is non a directory: " + metaDir.getAbsolutePath());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public VaultSettings getSettings() {
        return settings;
    }

    public void setSettings(VaultSettings settings) {
        this.settings = settings;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public WorkspaceFilter getFilter() {
        return filter;
    }

    public void setFilter(WorkspaceFilter filter) {
        this.filter = filter;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public VaultFsConfig getConfig() {
        return config;
    }

    public void setConfig(VaultFsConfig config) {
        this.config = config;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Properties getProperties() {
        return properties;
    }

    public void setProperties(Properties properties) {
        this.properties = properties;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<NodeTypeSet> getNodeTypes() {
        return cnds;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PrivilegeDefinitions getPrivileges() {
        return privileges;
    }

    public void setCNDs(Collection<NodeTypeSet> cnds) {
        this.cnds = cnds;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasDefinition() {
        return hasDefinition;
    }

    public void setHasDefinition(boolean hasDefinition) {
        this.hasDefinition = hasDefinition;
    }

    protected void loadSettings(@NotNull File metaDir)
            throws ConfigurationException, IOException {
        File file = new File(metaDir, Constants.SETTINGS_XML);
        if (file.isFile()) {
            VaultSettings settings = new VaultSettings();
            settings.load(file);
            this.settings = settings;
        } else {
            settings = VaultSettings.createDefault();
        }
    }

    protected void saveSettings(@NotNull File metaDir) throws IOException {
        if (settings != null) {
            File file = new File(metaDir, Constants.SETTINGS_XML);
            settings.save(file);
        }
    }

    protected void loadConfig(@NotNull File metaDir)
            throws ConfigurationException, IOException {
        File file = new File(metaDir, Constants.CONFIG_XML);
        if (file.isFile()) {
            this.config = AbstractVaultFsConfig.load(file);
        }
    }

    protected void saveConfig(@NotNull File metaDir)
            throws IOException {
        if (config != null) {
            File file = new File(metaDir, Constants.CONFIG_XML);
            try (OutputStream output = FileUtils.openOutputStream(file)) {
                IOUtils.copy(config.getSource(), output);
            }
        }
    }

    protected void loadFilter(@NotNull File metaDir, boolean vltMode)
            throws ConfigurationException, IOException {
        File file = new File(metaDir, Constants.FILTER_XML);
        if (vltMode) {
            File altFile = new File(metaDir, Constants.FILTER_VLT_XML);
            if (altFile.isFile()) {
                file = altFile;
                log.debug("Using alternative filter from {}", altFile.getPath());
            }
        }
        if (file.isFile()) {
            DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
            filter.load(file);
            this.filter = filter;
        }
    }

    protected void saveFilter(@NotNull File metaDir)
            throws IOException {
        if (filter != null) {
            File file = new File(metaDir, Constants.FILTER_XML);
            try (OutputStream output = FileUtils.openOutputStream(file)) {
                IOUtils.copy(filter.getSource(), output);
            }
        }
    }

    protected void loadProperties(@NotNull File metaDir) throws IOException {
        File file = new File(metaDir, Constants.PROPERTIES_XML);
        if (file.isFile()) {
            try (InputStream input = FileUtils.openInputStream(file)) {
                Properties properties = new Properties();
                properties.loadFromXML(input);
                this.properties = properties;
            }
        }
    }

    protected void saveProperties(@NotNull File metaDir) throws IOException {
        if (properties != null) {
            File file = new File(metaDir, Constants.PROPERTIES_XML);
            try (OutputStream output = FileUtils.openOutputStream(file)) {
                properties.storeToXML(output, "Custom Vault Properties", "utf-8");
            }
        }
    }

    protected void loadPrivileges(@NotNull File metaDir) throws IOException {
        File file = new File(metaDir, Constants.PRIVILEGES_XML);
        if (file.isFile()) {
            try (InputStream in = FileUtils.openInputStream(file)) {
                loadPrivileges(in, file.getPath());
            }
        }
    }

    protected void loadCNDs(@NotNull File metaDir) throws IOException {
        File[] files = metaDir.listFiles();
        if (files == null) {
            return;
        }
        for (File file: files) {
            if (file.getName().endsWith(".cnd")) {
                try(Reader r = new InputStreamReader(new FileInputStream(file), "utf8")) {
                    CNDReader reader = ServiceProviderFactory.getProvider().getCNDReader();
                    reader.read(r, file.getName(), null);
                    cnds.add(reader);
                } catch (IOException e) {
                    log.error("Error while reading CND: {}", e.toString());
                    throw new IOException("Error while reading CND.", e);
                }
            }
        }
    }

    @Override
    public PackageProperties getPackageProperties() {
        return new PackagePropertiesImpl() {
            
            @Override
            protected Properties getPropertiesMap() {
                return getProperties();
            }
        };
    }

}
