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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Properties;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.NameFileFilter;
import org.apache.commons.io.filefilter.SuffixFileFilter;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.fs.config.DefaultMetaInf;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.PackageInfo;
import org.apache.jackrabbit.vault.packaging.PackageProperties;
import org.apache.jackrabbit.vault.packaging.PackageType;
import org.apache.jackrabbit.vault.util.Constants;

/** Very simple class that reads basic package info from a file.
 * 
 * TODO: take over some logic from {@link DefaultMetaInf#load} */
public class DefaultPackageInfo implements PackageInfo {
    private static final File PROPERTIES_FILE = new File(Constants.META_DIR + "/" + Constants.PROPERTIES_XML);
    private static final File FILTER_FILE = new File(Constants.META_DIR + "/" + Constants.FILTER_XML);
    private static final File MANIFEST_FILE = new File(JarFile.MANIFEST_NAME);

    private final PackageId id;

    private final WorkspaceFilter filter;

    private final PackageType packageType;

    public DefaultPackageInfo(PackageId id, WorkspaceFilter filter, PackageType packageType) {
        this.id = id;
        this.filter = filter;
        this.packageType = packageType;
    }

    /** Reads the package info from a given file
     * 
     * @param file the package file as zip or an exploded directory containing metadata.
     * @return the package info if the package is valid, otherwise {@code null}.
     * @throws IOException if an error occurs. */
    public static @CheckForNull PackageInfo read(@Nonnull File file) throws IOException {
        DefaultPackageInfo info = new DefaultPackageInfo(null, null, PackageType.MIXED);
        if (!file.exists()) {
            throw new FileNotFoundException("Could not find file " + file);
        }
        if (file.isDirectory()) {
            for (File directoryFile : FileUtils.listFiles(file, new NameFileFilter(new String[] { "MANIFEST.MF", Constants.PROPERTIES_XML, Constants.FILTER_XML}),
                    new SuffixFileFilter(new String[] { Constants.META_INF, Constants.VAULT_DIR }))) {
                try (InputStream input = new BufferedInputStream(new FileInputStream(directoryFile))) {
                    info = readFromInputStream(new File(file.toURI().relativize(directoryFile.toURI()).getPath()), input, info);
                    // bail out as soon as all info was found
                    if (info.getId() != null && info.getFilter() != null) {
                        break;
                    }
                }

            }
            if (info.getId() == null || info.getFilter() == null) {
                return null;
            } else {
                return info;
            }
        } else if (file.getName().endsWith(".zip")) {
            // try to derive from vault-work?
            try (ZipFile zip = new ZipFile(file)) {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry e = entries.nextElement();
                    try (InputStream input = zip.getInputStream(e)) {
                        info = readFromInputStream(new File(e.getName()), input, info);
                        // bail out as soon as all info was found
                        if (info.getId() != null && info.getFilter() != null) {
                            break;
                        }
                    }
                }
            }
            if (info.getId() == null || info.getFilter() == null) {
                return null;
            } else {
                return info;
            }
        } else {
            throw new IOException("Only metadata from zip files could be extracted but the given file is not a zip:" + file);
        }
    }

    private static DefaultPackageInfo readFromInputStream(File file, InputStream input, PackageInfo alreadyFoundInfo) throws IOException {
        PackageId id = alreadyFoundInfo.getId();
        WorkspaceFilter filter = alreadyFoundInfo.getFilter();
        DefaultWorkspaceFilter defaultFilter = new DefaultWorkspaceFilter();
        PackageType packageType = alreadyFoundInfo.getPackageType();
        if (MANIFEST_FILE.equals(file)) {
            Manifest mf = new Manifest(input);
            String idStr = mf.getMainAttributes().getValue(PackageProperties.MF_KEY_PACKAGE_ID);
            if (idStr != null) {
                id = PackageId.fromString(idStr);
            }
            String roots = mf.getMainAttributes().getValue(PackageProperties.MF_KEY_PACKAGE_ROOTS);
            if (roots != null) {
                for (String root : roots.split(",")) {
                    defaultFilter.add(new PathFilterSet(root));
                }
                filter = defaultFilter;
            }
            String type = mf.getMainAttributes().getValue(PackageProperties.MF_KEY_PACKAGE_TYPE);
            if (type != null) {
                packageType = PackageType.valueOf(type.toUpperCase());
            }
        } else if (PROPERTIES_FILE.equals(file)) {
            Properties props = new Properties();
            props.loadFromXML(input);

            String version = props.getProperty(PackageProperties.NAME_VERSION);
            if (version == null) {
                version = "";
            }
            String group = props.getProperty(PackageProperties.NAME_GROUP);
            String name = props.getProperty(PackageProperties.NAME_NAME);
            if (group != null && name != null) {
                id = new PackageId(group, name, version);
            } else {
                // check for legacy packages that only contains a 'path' property
                String path = props.getProperty("path");
                if (path == null || path.length() == 0) {
                    path = "/etc/packages/unknown";
                }
                id = new PackageId(path, version);
            }
        } else if (FILTER_FILE.equals(file)) {
            try {
                defaultFilter.load(input);
            } catch (ConfigurationException e1) {
                throw new IOException(e1);
            }
            filter = defaultFilter;
        }
        return new DefaultPackageInfo(id, filter, packageType);
    }

    /** Returns the package id.
     * 
     * @return the package id. */
    public PackageId getId() {
        return id;
    }

    /** Returns the workspace filter
     * 
     * @return the filter */
    public WorkspaceFilter getFilter() {
        return filter;
    }

    /** Returns the package type.
     * 
     * @return the package type */
    public PackageType getPackageType() {
        return packageType;
    }
}