/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.jackrabbit.vault.fs.io;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import org.apache.jackrabbit.vault.fs.api.VaultInputSource;
import org.apache.jackrabbit.vault.fs.config.MetaInf;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An archive backed by a ZIP entry in another {@link Archive}, including another
 * {@code NestedZipArchive}.
 * The container is opened on demand by {@link #open(boolean)} and the nested ZIP
 * is read using {@link ZipStreamArchive}, with its own package metadata.
 * The caller retains ownership of all container archives and is responsible
 * for closing them, including when opening fails.
 */
public class NestedZipArchive extends AbstractArchive {

    private final Archive container;

    private final Archive rootContainer;

    private final String path;

    private ZipStreamArchive archive;

    /**
     * Creates an archive without opening the container or reading the nested ZIP.
     *
     * @param container the container archive, which remains owned by the caller
     * @param path the path of the nested ZIP entry within the container
     */
    public NestedZipArchive(@NotNull Archive container, @NotNull String path) {
        this.container = Objects.requireNonNull(container, "container");
        this.rootContainer =
                container instanceof NestedZipArchive ? ((NestedZipArchive) container).getRootContainer() : container;
        this.path = Objects.requireNonNull(path, "path");
    }

    /**
     * Returns the outermost container that is not a {@code NestedZipArchive},
     * without opening it. This archive does not own or close the root container.
     *
     * @return the root container archive
     */
    @NotNull
    public Archive getRootContainer() {
        return rootContainer;
    }

    @Override
    public void open(boolean strict) throws IOException {
        if (archive != null) {
            return;
        }
        container.open(strict);
        Entry entry = container.getEntry(path);
        if (entry == null || entry.isDirectory()) {
            throw new IOException("Nested ZIP entry is missing or is a directory: " + path);
        }
        InputStream input = container.openInputStream(entry);
        if (input == null) {
            throw new IOException("Nested ZIP entry cannot be read: " + path);
        }
        ZipStreamArchive nested = new ZipStreamArchive(input);
        try {
            nested.open(strict);
        } catch (IOException | RuntimeException e) {
            nested.close();
            throw e;
        }
        archive = nested;
    }

    @Override
    @Nullable
    public InputStream openInputStream(@Nullable Entry entry) throws IOException {
        return getArchive().openInputStream(entry);
    }

    @Override
    @Nullable
    public VaultInputSource getInputSource(@Nullable Entry entry) throws IOException {
        return getArchive().getInputSource(entry);
    }

    @Override
    @NotNull
    public Entry getRoot() throws IOException {
        return getArchive().getRoot();
    }

    @Override
    @NotNull
    public MetaInf getMetaInf() {
        return getArchive().getMetaInf();
    }

    @Override
    public void close() {
        if (archive != null) {
            archive.close();
            archive = null;
        }
    }

    private ZipStreamArchive getArchive() {
        if (archive == null) {
            throw new IllegalStateException("Archive not open.");
        }
        return archive;
    }
}
