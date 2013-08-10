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

package org.apache.jackrabbit.vault.vlt.meta.xml.zip;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.zip.ZipEntry;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.jackrabbit.vault.util.Constants;
import org.apache.jackrabbit.vault.util.MD5;
import org.apache.jackrabbit.vault.vlt.meta.MetaDirectory;
import org.apache.jackrabbit.vault.vlt.meta.MetaFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <code>ZipMetaFile</code>...
 */
public class ZipMetaFile implements MetaFile {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(ZipMetaFile.class);

    private final ZipMetaDir parent;

    private final String relPath;

    private final String name;

    private File tmpFile;

    public ZipMetaFile(ZipMetaDir parent, String relPath) {
        this.parent = parent;
        this.relPath = relPath;
        this.name = relPath.substring(relPath.lastIndexOf('/') + 1);
    }

    public MetaDirectory getDirectory() {
        return parent;
    }

    public String getName() {
        return name;
    }

    public InputStream getInputStream() throws IOException {
        InputStream in = parent.getZip().getInputStream(relPath);
        if (in == null) {
            throw new FileNotFoundException(parent.getFile() + ":" + relPath);
        }
        return in;
    }

    public Reader getReader() throws IOException {
        return new InputStreamReader(getInputStream(), Constants.ENCODING);
    }

    public long length() {
        ZipEntry entry = parent.getZip().getEntry(relPath);
        return entry == null ? -1 : entry.getSize();
    }

    public MD5 md5() throws IOException {
        InputStream in = parent.getZip().getInputStream(relPath);
        if (in == null) {
            throw new FileNotFoundException(parent.getFile() + ":" + relPath);
        }
        try {
            return MD5.digest(in);
        } finally {
            IOUtils.closeQuietly(in);
        }
    }

    public long lastModified() {
        ZipEntry entry = parent.getZip().getEntry(relPath);
        return entry == null ? 0 : entry.getTime();
    }

    public void delete() throws IOException {
        parent.getZip().delete(relPath);
        // todo: really sync here?
        parent.getZip().sync();
    }

    public File openTempFile() throws IOException {
        if (tmpFile == null) {
            File parentDir = parent.getZip().getZipFile().getParentFile();
            tmpFile = File.createTempFile(".vlt-", ".tmp", parentDir);
            tmpFile.createNewFile();
            copyToSilent(tmpFile, true);
        }
        return tmpFile;
    }

    public void closeTempFile(boolean discard) throws IOException {
        if (tmpFile == null) {
            log.warn("tmp file never opened: " + parent.getFile() + ":" + relPath);
        } else {
            if (!discard) {
                InputStream in = new FileInputStream(tmpFile);
                try {
                    parent.getZip().update(relPath, in);
                    // todo: really sync here ?
                    parent.getZip().sync();
                } finally {
                    IOUtils.closeQuietly(in);
                }
            }
            FileUtils.deleteQuietly(tmpFile);
            tmpFile = null;
        }
    }

    public void moveTo(MetaFile dst) throws IOException {
        ZipMetaFile dest = (ZipMetaFile) dst;
        parent.getZip().move(relPath, dest.relPath);
        // todo: really sync here ?
        parent.getZip().sync();
    }

    public void copyTo(File file, boolean preserveFileDate) throws IOException {
        if (!copyToSilent(file, preserveFileDate)) {
            throw new FileNotFoundException(parent.getFile() + ":" + relPath);
        }
    }

    public String getPath() {
        return parent.getFile() + ":" + relPath;
    }

    private boolean copyToSilent(File file, boolean preserveFileDate) throws IOException {
        InputStream in = parent.getZip().getInputStream(relPath);
        if (in == null) {
            return false;
        }
        FileOutputStream out = null;
        try {
            out = FileUtils.openOutputStream(file);
            IOUtils.copy(in, out);
            if (preserveFileDate) {
                ZipEntry entry = parent.getZip().getEntry(relPath);
                file.setLastModified(entry.getTime());
            }
        } finally {
            IOUtils.closeQuietly(in);
            IOUtils.closeQuietly(out);
        }
        return true;
    }

    public String toString() {
        return getPath();
    }
}