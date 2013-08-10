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

package org.apache.jackrabbit.vault.vlt.meta;

import java.io.File;
import java.io.IOException;

import org.apache.jackrabbit.vault.vlt.VltException;

/**
 * Provides an abstraction of a virtual directory that holds additional information about the files under vault
 * control.
 */
public interface MetaDirectory {

    /**
     * Returns the underlying system file or directory.
     * @return the system file.
     */
    File getFile();

    /**
     * Checks if this meta directory already exists on the filesystem.
     * @return <code>true</code> if this meta directory exists.
     */
    boolean exists();

    /**
     * Creates the internal structures for this meta directory and initializes it with the given path.
     * @param path the platform path relative to the jcr_root of this meta directory.
     * @throws IOException if an I/O error occurs
     */
    void create(String path) throws IOException;

    /**
     * Deletes the internal structures of this meta directory
     * @throws IOException if an I/O error occurs
     */
    void delete() throws IOException;

    /**
     * Synchronizes any internal changes to this meta directory with the underlying structures.
     * @throws IOException if an I/O error occurs
     */
    void sync() throws IOException;

    /**
     * Synchronizes and closes this meta directory.
     * @throws IOException if an I/O error occurs
     */
    void close() throws IOException;

    /**
     * Returns the repository url defined for this meta directory.
     * @return the url or <code>null</code> if not defined.
     * @throws IOException if an I/O error occurs
     */
    String getRepositoryUrl() throws IOException;

    /**
     * Sets the repository url define for this meta directory
     * @param url the url
     * @throws IOException if an I/O error occurs
     */
    void setRepositoryUrl(String url) throws IOException;

    /**
     * Returns the entries that are recorded in this meta directory
     * @return the entries.
     * @throws VltException if an error during reading of the entries occurrs.
     */
    VltEntries getEntries() throws VltException;

    /**
     * Returns a file of this meta directory.
     * @param name name of the file
     * @return the file or <code>null</code> if not found
     * @throws IOException if an I/O error occurs
     */
    MetaFile getFile(String name) throws IOException;

    /**
     * Returns a file of this meta directory.
     * @param name name of the file
     * @param create if <code>true</code> a new file will be created if not exists.
     * @return the file or <code>null</code> if not found and create is <code>false</code>.
     * @throws IOException if an I/O error occurs
     */
    MetaFile getFile(String name, boolean create) throws IOException;

    /**
     * Returns a file from the internal temporary storage of this directory.
     * @param name name of the file.
     * @param create if <code>true</code> a new file will be created if not exists.
     * @return the file or <code>null</code> if not found and create is <code>false</code>.
     * @throws IOException if an I/O error occurs
     */
    MetaFile getTmpFile(String name, boolean create) throws IOException;

    /**
     * Returns a file from the internal base storage of this directory.
     * @param name name of the file.
     * @param create if <code>true</code> a new file will be created if not exists.
     * @return the file or <code>null</code> if not found and create is <code>false</code>.
     * @throws IOException if an I/O error occurs
     */
    MetaFile getBaseFile(String name, boolean create) throws IOException;

    /**
     * Checks if the file with the given name exists.
     * @param name name of the file.
     * @return <code>true</code> if the file exists.
     * @throws IOException if an I/O error occurs
     */
    boolean hasFile(String name) throws IOException;

}