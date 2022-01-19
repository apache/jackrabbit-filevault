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

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

import org.apache.jackrabbit.vault.fs.api.VaultInputSource;
import org.apache.jackrabbit.vault.fs.config.MetaInf;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Specifies a FileVault archive.
 */
public interface Archive extends Closeable {

    /**
     * Opens the archive.
     * @param strict if {@code true} open will fail if there was an
     *        internal error while parsing meta data.
     * @throws IOException if an error occurs
     */
    void open(boolean strict) throws IOException;

    /**
     * Opens an input stream for the given entry.
     * Requires a previous call to {@link #open(boolean)}.
     * @param entry the entry
     * @return the input stream or {@code null} if the entry can't be read
     * @throws IOException if an error occurs
     */
    @Nullable
    InputStream openInputStream(@Nullable Entry entry) throws IOException;

    /**
     * Returns an input source for the given entry.
     * Requires a previous call to {@link #open(boolean)}.
     * @param entry the entry
     * @return the input source or {@code null} if the entry can't be read
     * @throws IOException if an error occurs
     */
    @Nullable
    VaultInputSource getInputSource(@Nullable Entry entry) throws IOException;

    /**
     * Returns the entry that specifies the "jcr_root". if no such
     * entry exists, {@code null} is returned.
     * Requires a previous call to {@link #open(boolean)}.
     * @return the jcr_root entry or {@code null}
     * @throws IOException if an error occurs
     */
    @Nullable
    Entry getJcrRoot() throws IOException;

    /**
     * Returns the root entry.
     * Requires a previous call to {@link #open(boolean)}.
     * @return the root entry.
     * @throws IOException if an error occurs
     */
    @NotNull
    Entry getRoot() throws IOException;

    /**
     * Returns the meta inf. If the archive provides no specific meta data,
     * a default, empty meta inf is returned.
     * Requires a previous call to {@link #open(boolean)}.
     *
     * @return the meta inf.
     */
    @NotNull
    MetaInf getMetaInf();

    /**
     * Returns the entry specified by path.
     * Requires a previous call to {@link #open(boolean)}.
     * @param path the path
     * @return the entry or {@code null} if not found.
     * @throws IOException if an error occurs
     */
    @Nullable
    Entry getEntry(@NotNull String path) throws IOException;
    
    /**
     * Returns a sub archive that is rooted at the given path.
     * Note that sub archives currently can't have their own meta inf and are
     * closed automatically if their container archive is closed.
     * Requires a previous call to {@link #open(boolean)}. 
     * @param root root path
     * @param asJcrRoot if {@code true} the given root is the jcr_root
     * @return the archive or {@code null} if entry specified by root
     *         does not exist.
     * @throws IOException if an error occurs
     */
    @Nullable
    Archive getSubArchive(@NotNull String root, boolean asJcrRoot) throws IOException;

    /**
     * Closes the archive. Only necessary to call if the archive has been opened.
     */
    void close();

    /**
     * Entry of an archive
     */
    interface Entry {

        /**
         * Returns the (file) name of the entry
         * @return the name
         */
        @NotNull
        String getName();

        /**
         * Returns {@code true} if the entry designates a directory.
         * @return {@code true} if the entry designates a directory.
         */
        boolean isDirectory();

        /**
         * Returns a collection of child entries.
         * @return a collection of child entries.
         */
        @NotNull
        Collection<? extends Entry> getChildren();

        /**
         * Returns the child entry with the given name.
         * @param name name of the child entry
         * @return the entry or {@code null} if does not exist.
         */
        @Nullable
        Entry getChild(@NotNull String name);
    }

}
