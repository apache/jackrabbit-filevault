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
import java.io.InputStream;
import java.io.Reader;

import org.apache.jackrabbit.vault.util.MD5;

/**
 * Provides an abstraction for a file of a {@link MetaDirectory}.
 */
public interface MetaFile {

    /**
     * Returns the meta directory that created this file.
     * @return the meta directory.
     */
    MetaDirectory getDirectory();

    /**
     * Returns a path of this file which is composed of the path of the meta directory and the name of this file.
     * @return the path
     */
    String getPath();

    /**
     * Returns the name of this file.
     * @return the name of this file.
     */
    String getName();

    /**
     * Open an input stream to this file.
     * @return an input stream
     * @throws IOException if an I/O error occurs
     */
    InputStream getInputStream() throws IOException;

    /**
     * Opens a reader using the utf-8 encoding
     * @return a read
     * @throws IOException if an I/O error occurs
     */
    Reader getReader() throws IOException;

    /**
     * Returns the length in bytes of this file
     * @return the length.
     */
    long length();

    /**
     * Returns the MD5 checksum of this file. Note that this operation reads the file fully in order to calculate
     * the checksum.
     * @return the MD5 checksum.
     * @throws IOException if an I/O error occurs
     */
    MD5 md5() throws IOException;

    /**
     * Returns the last modified date of this file.
     * @return the last modified
     */
    long lastModified();

    /**
     * Deletes the file from it's meta directory
     * @throws IOException if an I/O error occurs
     */
    void delete() throws IOException;

    /**
     * Opens a temporary file that is tied to this file and initializes it with the same content as this file.
     * @return a temporary file.
     * @throws IOException if an I/O error occurs
     */
    File openTempFile() throws IOException;

    /**
     * Close the previously opened temporary file. if <code>discard</code> is <code>false</code>, the contents of
     * the temporary file are copied back to this file.
     * @param discard <code>true</code> to discard the changes to the temp file
     * @throws IOException if an I/O error occurs
     */
    void closeTempFile(boolean discard) throws IOException;

    /**
     * Moves this file to another meta file of the same directory
     * @param dst destination file
     * @throws IOException if an I/O error occurs
     */
    void moveTo(MetaFile dst) throws IOException;

    /**
     * Copies the contents of this file to the indicated platform file.
     * @param file destination file
     * @param preserveFileDate <code>true</code> to update the modification date of the destination file
     * @throws IOException if an I/O error occurs
     */
    void copyTo(File file, boolean preserveFileDate) throws IOException;

}