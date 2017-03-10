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

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements an updateable zip file. Please note that the java 1.5 ZipFile
 * showed some issues when creating many files.
 */
public class UpdateableZipFile {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(UpdateableZipFile.class);


    private final File file;

    private Set<String> toDelete = new HashSet<String>();

    private Map<String, InputStream> toUpdate = new HashMap<String, InputStream>();

    private Map<String, String> toMove = new HashMap<String, String>();

    public UpdateableZipFile(File file) throws IOException {
        this.file = file;
    }

    public File getZipFile() {
        return file;
    }

    public boolean exists() {
        return file.exists();
    }

    public String getPath() {
        return file.getPath();
    }

    public void delete() {
        if (file.exists()) {
            file.delete();
        }
        toDelete.clear();
        toUpdate.clear();
    }

    public ZipEntry getEntry(String name) {
        if (!file.exists()) {
            return null;
        }
        try {
            InputStream in = FileUtils.openInputStream(file);
            ZipInputStream zin = new ZipInputStream(in);
            try {
                ZipEntry entry = zin.getNextEntry();
                while (entry != null) {
                    if (entry.getName().equals(name)) {
                        zin.closeEntry();
                        break;
                    }
                    entry = zin.getNextEntry();
                }
                return entry;
            } finally {
                IOUtils.closeQuietly(zin);
                IOUtils.closeQuietly(in);
            }
        } catch (IOException e) {
            log.error("Error while retrieving zip entry {}: {}", name, e.toString());
            return null;
        }
    }

    public InputStream getInputStream(String name) throws IOException {
        if (!file.exists()) {
            return null;
        }
        InputStream in = FileUtils.openInputStream(file);
        ZipInputStream zin = new ZipInputStream(in);
        ZipEntry entry = zin.getNextEntry();
        while (entry != null) {
            if (entry.getName().equals(name)) {
                return zin;
            }
            entry = zin.getNextEntry();
        }
        IOUtils.closeQuietly(zin);
        IOUtils.closeQuietly(in);
        return null;
    }

    public void delete(String name) {
        toDelete.add(name);
    }

    public void update(String name, InputStream in) {
        toUpdate.put(name, in);
    }

    public void update(String name, byte[] bytes) {
        toUpdate.put(name, new ByteArrayInputStream(bytes));
    }

    public void move(String src, String dst) {
        toMove.put(src, dst);
        toDelete.add(dst);
    }

    public void sync() throws IOException {
        if (toDelete.isEmpty() && toUpdate.isEmpty() && toMove.isEmpty()) {
            return;
        }
        // create tmp file
        File newZip = File.createTempFile(file.getName(), ".tmp", file.getParentFile());
        ZipOutputStream out = new ZipOutputStream(
                new BufferedOutputStream(new FileOutputStream(newZip)));
        out.setLevel(Deflater.NO_COMPRESSION);

        // add new files on top
        for (Map.Entry<String, InputStream> update: toUpdate.entrySet()) {
            ZipEntry entry = new ZipEntry(update.getKey());
            out.putNextEntry(entry);
            InputStream in = update.getValue();
            copy(in, out);
            IOUtils.closeQuietly(in);
        }
        // process existing zip entries
        if (file.exists()) {
            InputStream in = FileUtils.openInputStream(file);
            ZipInputStream zin = new ZipInputStream(in);
            ZipEntry entry = zin.getNextEntry();
            while (entry != null) {
                if (!toUpdate.containsKey(entry.getName()) && !toDelete.contains(entry.getName())) {
                    ZipEntry newEntry = entry;
                    if (toMove.containsKey(entry.getName())) {
                        newEntry = new ZipEntry(toMove.get(entry.getName()));
                        newEntry.setTime(entry.getTime());
                        //newEntry.setSize(entry.getSize());
                    }
                    out.putNextEntry(newEntry);
                    copy(zin, out);
                }
                entry = zin.getNextEntry();
            }
            IOUtils.closeQuietly(zin);
            IOUtils.closeQuietly(in);
        }
        out.close();
        toDelete.clear();
        toUpdate.clear();
        
        // rotate files
        FileUtils.deleteQuietly(file);
        FileUtils.moveFile(newZip, file);
    }

    protected static void copy(InputStream in, ZipOutputStream out) throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) >= 0) {
            out.write(buffer, 0, read);
        }
    }

    public void close() throws IOException {
        sync();
    }
}