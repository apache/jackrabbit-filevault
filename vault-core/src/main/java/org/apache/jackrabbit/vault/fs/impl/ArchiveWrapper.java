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
package org.apache.jackrabbit.vault.fs.impl;

import java.io.IOException;
import java.io.InputStream;

import org.apache.jackrabbit.vault.fs.api.VaultInputSource;
import org.apache.jackrabbit.vault.fs.config.MetaInf;
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Wrapper class that hides all implementation details from the underlying archive.
 * this is mainly use to hide the {@link org.apache.jackrabbit.vault.fs.io.ZipArchive#getFile()}.
 */
public final class ArchiveWrapper implements Archive {

    private final Archive archive;

    public ArchiveWrapper(Archive archive) {
        this.archive = archive;
    }

    @Override
    public void open(boolean strict) throws IOException {
        archive.open(strict);
    }

    @Override
    @Nullable
    public InputStream openInputStream(@Nullable Entry entry) throws IOException {
        return archive.openInputStream(entry);
    }

    @Override
    @Nullable
    public VaultInputSource getInputSource(@Nullable Entry entry) throws IOException {
        return archive.getInputSource(entry);
    }

    @Override
    @Nullable
    public Entry getJcrRoot() throws IOException {
        return archive.getJcrRoot();
    }

    @Override
    @NotNull
    public Entry getRoot() throws IOException {
        return archive.getRoot();
    }

    @Override
    @NotNull
    public MetaInf getMetaInf() {
        return archive.getMetaInf();
    }

    @Override
    @Nullable
    public Entry getEntry(@NotNull String path) throws IOException {
        return archive.getEntry(path);
    }

    @Override
    @Nullable
    public Archive getSubArchive(@NotNull String root, boolean asJcrRoot) throws IOException {
        return archive.getSubArchive(root, asJcrRoot);
    }

    @Override
    public void close() {
        archive.close();
    }
}
