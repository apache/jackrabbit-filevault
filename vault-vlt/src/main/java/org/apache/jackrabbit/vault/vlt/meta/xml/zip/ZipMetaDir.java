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
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.jackrabbit.vault.util.Constants;
import org.apache.jackrabbit.vault.vlt.VltException;
import org.apache.jackrabbit.vault.vlt.meta.MetaDirectory;
import org.apache.jackrabbit.vault.vlt.meta.MetaFile;
import org.apache.jackrabbit.vault.vlt.meta.VltEntries;
import org.apache.jackrabbit.vault.vlt.meta.xml.XmlEntries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <code>ZipMetaDir</code>...
 */
public class ZipMetaDir implements MetaDirectory {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(ZipMetaDir.class);

    public static final String ADDRESS_FILE_NAME = "repository.url";

    public static final String BASE_DIR_NAME = "base";

    public static final String TMP_DIR_NAME = "tmp";

    public static final String ENTRIES_FILE_NAME = "entries.xml";

    private final UpdateableZipFile zip;

    private XmlEntries entries;

    public ZipMetaDir(File file) throws IOException {
        zip = new UpdateableZipFile(file);
    }

    protected UpdateableZipFile getZip() {
        return zip;
    }
    
    public File getFile() {
        return zip.getZipFile();
    }

    public String getRepositoryUrl() throws IOException {
        InputStream in = zip.getInputStream(ADDRESS_FILE_NAME);
        if (in == null) {
            return null;
        }
        try {
            List lines = IOUtils.readLines(in, Constants.ENCODING);
            if (lines.isEmpty()) {
                throw new IOException(getFile() + ":" + ADDRESS_FILE_NAME + " is empty.");
            }
            return (String) lines.get(0);
        } finally {
            IOUtils.closeQuietly(in);
        }
    }

    public void setRepositoryUrl(String url) throws IOException {
        zip.update(ADDRESS_FILE_NAME, url.getBytes(Constants.ENCODING));
        // todo: really sync here ?
        zip.sync();
    }


    public boolean exists() {
        return zip.exists();
    }

    public void create(String path) throws IOException {
        if (!zip.exists()) {
            entries = new XmlEntries(path, true);
            sync();
        }
    }

    public void delete() throws IOException {
        zip.delete();
    }

    public VltEntries getEntries() throws VltException {
        try {
            if (entries == null) {
                InputStream in = zip.getInputStream(ENTRIES_FILE_NAME);
                if (in != null) {
                    try {
                        entries = XmlEntries.load(in);
                    } finally {
                        IOUtils.closeQuietly(in);
                    }
                }
            }
            return entries;
        } catch (IOException e) {
            throw new VltException("Error while reading entries.", e);
        }
    }

    public MetaFile getFile(String name) throws IOException {
        return getFile(name, false);
    }

    public MetaFile getFile(String name, boolean create) throws IOException {
        if (create || zip.getEntry(name) != null) {
            return new ZipMetaFile(this, name);
        } else {
            return null;
        }
    }

    public MetaFile getTmpFile(String name, boolean create) throws IOException {
        return getFile(TMP_DIR_NAME + "/" + name, create);
    }

    public MetaFile getBaseFile(String name, boolean create) throws IOException {
        return getFile(BASE_DIR_NAME + "/" + name, create);
    }

    public boolean hasFile(String name) throws IOException {
        return zip.getEntry(name) != null;
    }

    public void sync() throws IOException {
        if (entries != null) {
            if (entries.isDirty()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                entries.save(out);
                zip.update(ENTRIES_FILE_NAME, out.toByteArray());
            }
        }
        zip.sync();
    }

    public void close() throws IOException {
        sync();
        zip.close();
    }
}