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

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

import org.apache.jackrabbit.vault.fs.api.VaultInputSource;
import org.apache.jackrabbit.vault.fs.config.MetaInf;

/**
 * Specifies a read-only archive.
 */
public interface Archive {

    /**
     * Opens the archive.
     * @param strict if <code>true</code> open will fail if there was an
     *        internal error while parsing meta data.
     * @throws IOException if an error occurs
     */
    void open(boolean strict) throws IOException;

    /**
     * Opens an input stream for the given entry
     * @param entry the entry
     * @return the input stream
     * @throws IOException if an error occurs
     */
    InputStream openInputStream(Entry entry) throws IOException;

    /**
     * Returns an input source for the given entry
     * @param entry the entry
     * @return the input source
     * @throws IOException if an error occurs
     */
    VaultInputSource getInputSource(Entry entry) throws IOException;

    /**
     * Returns the entry that specifies the "jcr_root". if no such
     * entry exists, <code>null</code> is returned.
     * @return the jcr_root entry or <code>null</code>
     * @throws IOException if an error occurs
     */
    Entry getJcrRoot() throws IOException;

    /**
     * Returns the root entry.
     * @return the root entry.
     * @throws IOException if an error occurs
     */
    Entry getRoot() throws IOException;

    /**
     * Returns the meta inf. If the archive provides no specific meta data,
     * a default, empty meta inf is returned.
     *
     * @return the meta inf.
     */
    MetaInf getMetaInf();

    /**
     * Returns the entry specified by path.
     * @param path the path
     * @return the entry or <code>null</code> if not found.
     * @throws IOException if an error occurs
     */
    Entry getEntry(String path) throws IOException;
    
    /**
     * Returns a sub archive that is rooted at the given path.
     * Note that sub archives currently can't have they own meta inf and are
     * closed automatically if they base is closed.
     * 
     * @param root root path
     * @param asJcrRoot if <code>true</code> the given root is the jcr_root
     * @return the archive or <code>null</code> if entry specified by root
     *         does not exist.
     * @throws IOException if an error occurs
     */
    Archive getSubArchive(String root, boolean asJcrRoot) throws IOException;

    /**
     * closes the archive
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
        public String getName();

        /**
         * Returns <code>true</code> if the entry designates a directory.
         * @return <code>true</code> if the entry designates a directory.
         */
        public boolean isDirectory();

        /**
         * Returns a collection of child entries.
         * @return a collection of child entries.
         */
        public Collection<? extends Entry> getChildren();

        /**
         * Returns the child entry with the given name.
         * @param name name of the child entry
         * @return the entry or <code>null</code> if does not exist.
         */
        public Entry getChild(String name);
    }

}