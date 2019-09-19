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

import java.io.File;
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

/**
 * Very simple class that reads basic package info from a file.
 * 
 * TODO: take over some logic from {@link DefaultMetaInf#load}
 */
public class DefaultPackageInfo implements PackageInfo {
    private static final String PROPERTIES_FILE = Constants.META_DIR + "/" + Constants.PROPERTIES_XML;
    private static final String FILTER_FILE = Constants.META_DIR + "/" + Constants.FILTER_XML;

    private final PackageId id;

    private final DefaultWorkspaceFilter filter;

    private final PackageType packageType;

    public DefaultPackageInfo(PackageId id, DefaultWorkspaceFilter filter, PackageType packageType) {
        this.id = id;
        this.filter = filter;
        this.packageType = packageType;
    }

    /**
     * Reads the package file.
     * @param file the file.
     * @return the package info if the package is valid, otherwise {@code null}.
     * @throws IOException if an error occurs.
     */
    public static @CheckForNull PackageInfo read(@Nonnull File file) throws IOException {
        PackageId id = null;
        DefaultWorkspaceFilter filter = null;
        PackageType packageType = PackageType.MIXED;
        
        try (ZipFile zip = new ZipFile(file)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements() && (id == null || filter == null)) {
                ZipEntry e = entries.nextElement();
                if (JarFile.MANIFEST_NAME.equalsIgnoreCase(e.getName())) {
                    Manifest mf = new Manifest(zip.getInputStream(e));
                    String idStr = mf.getMainAttributes().getValue(PackageProperties.MF_KEY_PACKAGE_ID);
                    if (idStr != null) {
                        id = PackageId.fromString(idStr);
                    }
                    String roots = mf.getMainAttributes().getValue(PackageProperties.MF_KEY_PACKAGE_ROOTS);
                    filter = new DefaultWorkspaceFilter();
                    if (roots != null) {
                        for (String root: roots.split(",")) {
                            filter.add(new PathFilterSet(root));
                        }
                    }
                    String type = mf.getMainAttributes().getValue(PackageProperties.MF_KEY_PACKAGE_TYPE);
                    if (type != null) {
                        packageType = PackageType.valueOf(type.toUpperCase());
                    }
                } else if (PROPERTIES_FILE.equalsIgnoreCase(e.getName())) {
                    
                    Properties props = new Properties();
                    try (InputStream input = zip.getInputStream(e)) {
                        props.loadFromXML(input);
                    }
                    String version = props.getProperty("version");
                    if (version == null) {
                        version = "";
                    }
                    String group = props.getProperty("group");
                    String name = props.getProperty("name");
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
                } else if (FILTER_FILE.equalsIgnoreCase(e.getName())) {
                    filter = new DefaultWorkspaceFilter();
                    try {
                        filter.load(zip.getInputStream(e));
                    } catch (ConfigurationException e1) {
                        throw new IOException(e1);
                    }
                }
                // bail out as soon as all info was found
                if (id != null && filter != null) {
                    break;
                }
            }
        }
        if (id == null || filter == null) {
            return null;
        } else {
            return new DefaultPackageInfo(id, filter, packageType);
        }
    }

    /**
     * Returns the package id.
     * @return the package id.
     */
    public PackageId getId() {
        return id;
    }

    /**
     * Returns the workspace filter
     * @return the filter
     */
    public WorkspaceFilter getFilter() {
        return filter;
    }

    /**
     * Returns the package type.
     * @return the package type
     */
    public PackageType getPackageType() {
        return packageType;
    }
}