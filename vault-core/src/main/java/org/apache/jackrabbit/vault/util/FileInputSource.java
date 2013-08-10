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

package org.apache.jackrabbit.vault.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.FileUtils;
import org.apache.jackrabbit.vault.fs.api.VaultInputSource;

/**
 * Implements a input source that is based on a {@link File}. The path of the
 * file is used as systemId.
 * <p/>
 * Currently only {@link #getByteStream()} is implemented.
 *
 */
public class FileInputSource extends VaultInputSource {

    /**
     * the file
     */
    private final File file;

    /**
     * possible line feed
     */
    private byte[] lineSeparator;

    /**
     * Creates a new input source that is based on a file.
     * @param file the file.
     */
    public FileInputSource(File file) {
        super(file.getPath());
        this.file = file;
    }

    /**
     * Sets the linefeed to use. If this is not <code>null</code> the output
     * stream of the file is wrapped by a {@link LineInputStream} with that
     * given line feed
     * @param lineSeparator the linefeed for text
     */
    public void setLineSeparator(byte[] lineSeparator) {
        this.lineSeparator = lineSeparator;
    }

    /**
     * {@inheritDoc}
     *
     * @return a {@link FileInputStream} on the internal file.
     */
    public InputStream getByteStream() {
        try {
            if (lineSeparator != null) {
                return new LineInputStream(new FileInputStream(file), lineSeparator);
            } else {
                return FileUtils.openInputStream(file);
            }
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Returns the content length of the underlying file.
     * @return the content length of the underlying file.
     */
    public long getContentLength() {
        return file.length();
    }

    /**
     * Returns the last modified date of the underlying file.
     * @return the last modified date of the underlying file.
     */
    public long getLastModified() {
        return file.lastModified();
    }

    /**
     * deletes the underlying file
     */
    public void discard() {
        file.delete();
        file.deleteOnExit();
    }
}