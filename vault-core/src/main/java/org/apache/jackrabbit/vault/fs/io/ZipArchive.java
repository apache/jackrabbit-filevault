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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.jackrabbit.vault.fs.api.VaultInputSource;
import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.fs.config.DefaultMetaInf;
import org.apache.jackrabbit.vault.fs.config.MetaInf;
import org.apache.jackrabbit.vault.fs.config.VaultSettings;
import org.apache.jackrabbit.vault.fs.spi.CNDReader;
import org.apache.jackrabbit.vault.fs.spi.ServiceProviderFactory;
import org.apache.jackrabbit.vault.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The zip archive implements bridge between the ZipStreamArchive and the
 * ZipFileArchive. the former is needed due to a bug in ZipFile of jdk1.5 that
 * causes problems with zip files with a lot of entries.
 */
public class ZipArchive extends AbstractArchive {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(ZipArchive.class);

    private DefaultMetaInf inf;

    private int numEntries;

    private Archive base;

    private File zipFile;

    private final boolean isTempFile;

    public ZipArchive(File zipFile) {
        this(zipFile, false);
    }

    public ZipArchive(File zipFile, boolean isTempFile) {
        this.zipFile = zipFile;
        this.isTempFile = isTempFile;
    }

    public void open(boolean strict) throws IOException {
        if (inf != null) {
            return;
        }
        // first load the meta info and count the entries
        ZipInputStream zin = new ZipInputStream(
                new BufferedInputStream(
                        new FileInputStream(zipFile)
                )
        );
        numEntries = 0;
        inf = new DefaultMetaInf();
        try {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                numEntries++;
                String name = entry.getName();

                // check for meta inf
                if (!name.startsWith(Constants.META_DIR + "/")) {
                    continue;
                }
                String path = zipFile.getPath() + ":" + name;
                name = name.substring((Constants.META_DIR + "/").length());
                if (name.equals(Constants.FILTER_XML)) {
                    // load filter
                    inf.loadFilter(new CloseShieldInputStream(zin), path);
                } else if (name.equals(Constants.CONFIG_XML)) {
                    // load config
                    inf.loadConfig(new CloseShieldInputStream(zin), path);
                } else if (name.equals(Constants.SETTINGS_XML)) {
                    // load settings
                    inf.loadSettings(new CloseShieldInputStream(zin), path);
                } else if (name.equals(Constants.PROPERTIES_XML)) {
                    // load properties
                    inf.loadProperties(new CloseShieldInputStream(zin), path);
                } else if (name.equals(Constants.PRIVILEGES_XML)) {
                    // load privileges
                    inf.loadPrivileges(new CloseShieldInputStream(zin), path);
                } else if (name.equals(Constants.PACKAGE_DEFINITION_XML)) {
                    inf.setHasDefinition(true);
                    log.debug("Contains package definition {}.", path);
                } else if (name.endsWith(".cnd")) {
                    try {
                        Reader r = new InputStreamReader(new CloseShieldInputStream(zin), "utf8");
                        CNDReader reader = ServiceProviderFactory.getProvider().getCNDReader();
                        reader.read(r, entry.getName(), null);
                        inf.getNodeTypes().add(reader);
                        log.debug("Loaded nodetypes from {}.", path);
                    } catch (IOException e1) {
                        log.error("Error while reading CND: {}", e1.toString());
                        if (strict) {
                            throw e1;
                        }
                    }
                }
            }
            if (inf.getFilter() == null) {
                log.debug("Zip {} does not contain filter definition.", zipFile.getPath());
            }
            if (inf.getConfig() == null) {
                log.debug("Zip {} does not contain vault config.", zipFile.getPath());
            }
            if (inf.getSettings() == null) {
                log.debug("Zip {} does not contain vault settings. using default.", zipFile.getPath());
                VaultSettings settings = new VaultSettings();
                settings.getIgnoredNames().add(".svn");
                inf.setSettings(settings);
            }
            if (inf.getProperties() == null) {
                log.debug("Zip {} does not contain properties.", zipFile.getPath());
            }
            if (inf.getNodeTypes().isEmpty()) {
                log.debug("Zip {} does not contain nodetypes.", zipFile.getPath());
            }

        } catch (IOException e) {
            log.error("Error while loading zip {}.", zipFile.getPath());
            throw e;
        } catch (ConfigurationException e) {
            log.error("Error while loading zip {}.", zipFile.getPath());
            IOException io = new IOException(e.toString());
            io.initCause(e);
            throw io;
        } finally {
            IOUtils.closeQuietly(zin);
        }

    }

    private Archive getBase() throws IOException {
        if (inf == null) {
            throw new IOException("Archive not open.");
        }
        if (base == null) {
            // zip file only supports sizes up to 2GB
            if (zipFile.length() > (long) Integer.MAX_VALUE) {
                log.warn("ZipFile is larger than 2GB. Fallback to streaming archive.");
            } else {
                // check if the zip file provides the correct size (# entries)
                ZipFile zip = new ZipFile(zipFile, ZipFile.OPEN_READ);
                if (zip.size() != numEntries) {
                    log.warn("ZipFile reports {} entries, but stream counts {} entries. " +
                            "Fallback to streaming archive.",
                            String.valueOf(zip.size()), String.valueOf(numEntries));
                    try {
                        zip.close();
                    } catch (IOException e) {
                        // ignore
                    }
                } else {
                    base = new ZipFileArchive(zip);
                }
            }
            if (base == null) {
                base = new ZipStreamArchive(zipFile);
            }
            base.open(false);
        }
        return base;
    }

    public MetaInf getMetaInf() {
        if (inf == null) {
            throw new IllegalStateException("Archive not open.");
        }
        return inf;
    }

    public InputStream openInputStream(Entry entry) throws IOException {
        return getBase().openInputStream(entry);
    }

    public VaultInputSource getInputSource(Entry entry) throws IOException {
        return getBase().getInputSource(entry);
    }

    public Entry getRoot() throws IOException {
        return getBase().getRoot();
    }

    public void close() {
        if (base != null) {
            base.close();
            base = null;
        }
        inf = null;
        if (zipFile != null && isTempFile) {
            FileUtils.deleteQuietly(zipFile);
        }
        zipFile = null;

    }

    public File getFile() {
        return zipFile;
    }

    public long getFileSize() {
        return zipFile == null ? -1 : zipFile.length();
    }

    @Override
    public String toString() {
        return zipFile.getPath();
    }
}