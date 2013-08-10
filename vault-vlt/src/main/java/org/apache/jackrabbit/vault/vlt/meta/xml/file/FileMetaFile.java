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

package org.apache.jackrabbit.vault.vlt.meta.xml.file;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import org.apache.commons.io.FileUtils;
import org.apache.jackrabbit.vault.util.Constants;
import org.apache.jackrabbit.vault.util.MD5;
import org.apache.jackrabbit.vault.vlt.meta.MetaDirectory;
import org.apache.jackrabbit.vault.vlt.meta.MetaFile;

/**
 * <code>FileMetaFile</code>...
 */
public class FileMetaFile implements MetaFile {

    private final FileMetaDir parent;

    private File file;

    public FileMetaFile(FileMetaDir parent, File file) {
        this.parent = parent;
        this.file = file;
    }

    public MetaDirectory getDirectory() {
        return parent;
    }

    public String getName() {
        return file.getName();
    }

    public InputStream getInputStream() throws IOException {
        return FileUtils.openInputStream(file);
    }

    public Reader getReader() throws IOException {
        return new InputStreamReader(getInputStream(), Constants.ENCODING);
    }

    public long length() {
        return file.length();
    }

    public MD5 md5() throws IOException {
        return MD5.digest(file);
    }

    public long lastModified() {
        return file.lastModified();
    }

    public void delete() throws IOException {
        FileUtils.forceDelete(file);
    }

    public File openTempFile() throws IOException {
        if (!file.exists()) {
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            file.createNewFile();
        }
        return file;
    }

    public void closeTempFile(boolean discard) throws IOException {
        // ignore
    }

    public void moveTo(MetaFile dst) throws IOException {
        FileMetaFile dest = (FileMetaFile) dst;
        FileUtils.copyFile(file, dest.file, true);
        FileUtils.forceDelete(file);
        file = dest.file;
    }

    public void copyTo(MetaFile dst) throws IOException {
        FileMetaFile dest = (FileMetaFile) dst;
        FileUtils.copyFile(file, dest.file, true);
    }

    public void copyTo(File dst, boolean preserveFileDate) throws IOException {
        FileUtils.copyFile(file, dst, preserveFileDate);
    }

    public String getPath() {
        return file.getPath();
    }
}