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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.jackrabbit.vault.fs.Mounter;
import org.apache.jackrabbit.vault.fs.api.RepositoryAddress;
import org.apache.jackrabbit.vault.fs.api.VaultFileSystem;
import org.apache.jackrabbit.vault.fs.api.VaultFsConfig;
import org.apache.jackrabbit.vault.fs.config.DefaultMetaInf;
import org.apache.jackrabbit.vault.fs.config.MetaInf;
import org.apache.jackrabbit.vault.fs.impl.AggregateManagerImpl;
import org.apache.jackrabbit.vault.fs.io.AbstractExporter;
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.JarExporter;
import org.apache.jackrabbit.vault.fs.spi.ProgressTracker;
import org.apache.jackrabbit.vault.packaging.ExportOptions;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.PackageManager;
import org.apache.jackrabbit.vault.packaging.PackageProperties;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.apache.jackrabbit.vault.packaging.events.PackageEvent;
import org.apache.jackrabbit.vault.packaging.events.impl.PackageEventDispatcher;
import org.apache.jackrabbit.vault.util.Constants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Implements the package manager
 */
public class PackageManagerImpl implements PackageManager {

    /**
     * event dispatcher
     */
    @Nullable
    private PackageEventDispatcher dispatcher;

    /**
     * {@inheritDoc}
     */
    @Override
    public @NotNull VaultPackage open(@NotNull Archive archive) throws IOException {
        return new ZipVaultPackage(archive, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NotNull VaultPackage open(@NotNull Archive archive, boolean strict) throws IOException {
        return new ZipVaultPackage(archive, strict);
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public VaultPackage open(File file) throws IOException {
        return open(file, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public VaultPackage open(File file, boolean strict) throws IOException {
        return new ZipVaultPackage(file, false, strict);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public VaultPackage assemble(Session s, ExportOptions opts, File file)
            throws IOException, RepositoryException {
        OutputStream out = null;
        boolean isTmp = false;
        boolean success = false;
        try {
            if (file == null) {
                file = File.createTempFile("filevault", ".zip");
                isTmp = true;
            }
            out = FileUtils.openOutputStream(file);
            assemble(s, opts, out);
            IOUtils.closeQuietly(out);
            success = true;
            return new ZipVaultPackage(file, isTmp);
        } finally {
            IOUtils.closeQuietly(out);
            if (isTmp && !success) {
                FileUtils.deleteQuietly(file);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void assemble(Session s, ExportOptions opts, OutputStream out)
            throws IOException, RepositoryException {
        try (JarExporter exporter = new JarExporter(out, opts.getCompressionLevel())) {
            RepositoryAddress addr;
            try {
                String mountPath = opts.getMountPath();
                if (mountPath == null || mountPath.length() == 0) {
                    mountPath = "/";
                }
                addr = new RepositoryAddress("/" + s.getWorkspace().getName() + mountPath);
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException(e);
            }
            MetaInf metaInf = opts.getMetaInf();
            if (metaInf == null) {
                metaInf = new DefaultMetaInf();
            }

            VaultFsConfig config = metaInf.getConfig();
            if (metaInf.getProperties() != null) {
                if ("true".equals(metaInf.getProperties().getProperty(PackageProperties.NAME_USE_BINARY_REFERENCES))) {
                    config = AggregateManagerImpl.getDefaultBinaryReferencesConfig();
                }
            }

            VaultFileSystem jcrfs = Mounter.mount(config, metaInf.getFilter(), addr, opts.getRootPath(), s);
            exporter.setProperties(metaInf.getProperties());
            if (opts.getListener() != null) {
                exporter.setVerbose(opts.getListener());
            }
            exporter.export(jcrfs.getRoot(), true);
            if (opts.getPostProcessor() != null) {
                opts.getPostProcessor().process(exporter);
            }
            jcrfs.unmount();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public VaultPackage rewrap(ExportOptions opts, VaultPackage src, File file)
            throws IOException, RepositoryException {
        boolean isTmp = false;
        boolean success = false;
        if (file == null) {
            file = File.createTempFile("filevault", ".zip");
            isTmp = true;
        }
        try (OutputStream out = FileUtils.openOutputStream(file);){
            rewrap(opts, src, out);
            success = true;
            VaultPackage pack =  new ZipVaultPackage(file, isTmp);
            dispatch(PackageEvent.Type.REWRAPP, pack.getId(), null);
            return pack;
        } finally {
            if (isTmp && !success) {
                FileUtils.deleteQuietly(file);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void rewrap(ExportOptions opts, VaultPackage src, OutputStream out)
            throws IOException {
        try (JarExporter exporter = new JarExporter(out, opts.getCompressionLevel())) {
            MetaInf metaInf = opts.getMetaInf();
            if (metaInf == null) {
                metaInf = new DefaultMetaInf();
            }
            exporter.open();
            exporter.setProperties(metaInf.getProperties());
            ProgressTracker tracker = null;
            if (opts.getListener() != null) {
                tracker = new ProgressTracker();
                exporter.setVerbose(opts.getListener());
            }

            // merge
            MetaInf inf = opts.getMetaInf();
            if (inf == null || inf.getProperties() == null) {
                throw new IllegalArgumentException("The export options didn't set the mandatory properties");
            }

            final Set<String> metaInfIncludes;
            final Set<String> metaInfExcludes;
            if (opts.getPostProcessor() == null) {
                // no post processor, we keep all metadata files except the properties
                metaInfIncludes = Collections.emptySet();
                metaInfExcludes = Collections.singleton(Constants.META_DIR + "/" + Constants.PROPERTIES_XML);
            } else {
                
                metaInfIncludes = new HashSet<>();
                metaInfIncludes.add(Constants.META_DIR + "/");
                metaInfIncludes.add(Constants.META_DIR + "/" + Constants.NODETYPES_CND);
                metaInfIncludes.add(Constants.META_DIR + "/" + Constants.CONFIG_XML);
                metaInfIncludes.add(Constants.META_DIR + "/" + Constants.FILTER_XML);
                metaInfExcludes = Collections.emptySet();
            }

            try (Archive archive = src.getArchive()) {
                archive.open(false);
                addArchiveEntryToExporter(exporter, archive, "", archive.getRoot(), metaInfIncludes, metaInfExcludes);
            }

            // write updated properties
            ByteArrayOutputStream tmpOut = new ByteArrayOutputStream();
            inf.getProperties().storeToXML(tmpOut, "FileVault Package Properties", "utf-8");
            exporter.writeFile(new ByteArrayInputStream(tmpOut.toByteArray()), Constants.META_DIR + "/" + Constants.PROPERTIES_XML);
            if (tracker != null) {
                tracker.track("A", Constants.META_DIR + "/" + Constants.PROPERTIES_XML);
            }

            if (opts.getPostProcessor() != null) {
                opts.getPostProcessor().process(exporter);
            }
        }
    }

    private static void addArchiveEntryToExporter(AbstractExporter exporter, Archive archive, String parentPath, Archive.Entry entry, Set<String> metaInfIncludes, Set<String> metaInfExcludes) throws IOException {
        String path = parentPath + entry.getName();
        if (path.startsWith(Constants.META_INF + "/")) {
            if ((!metaInfIncludes.isEmpty() && !metaInfIncludes.contains(path)) || metaInfExcludes.contains(path)) {
                return;
            }
        }
        if (entry.isDirectory()) {
            exporter.createDirectory(path);
            for (Archive.Entry child : entry.getChildren()) {
                addArchiveEntryToExporter(exporter, archive, path.isEmpty() ? path : path + "/", child, metaInfIncludes, metaInfExcludes);
            }
        } else {
            try (InputStream input = archive.openInputStream(entry)) {
                exporter.writeFile(input, path);
            }
        }
    }

    @Nullable
    PackageEventDispatcher getDispatcher() {
        return dispatcher;
    }

    public void setDispatcher(@Nullable PackageEventDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    void dispatch(@NotNull PackageEvent.Type type, @NotNull PackageId id, @Nullable PackageId[] related) {
        if (dispatcher == null) {
            return;
        }
        dispatcher.dispatch(type, id, related);
    }

}
