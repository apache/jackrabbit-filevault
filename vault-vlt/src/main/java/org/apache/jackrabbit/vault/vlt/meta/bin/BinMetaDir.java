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
package org.apache.jackrabbit.vault.vlt.meta.bin;

import java.io.File;
import java.io.IOException;

import org.apache.jackrabbit.vault.fs.api.DumpContext;
import org.apache.jackrabbit.vault.vlt.VltException;
import org.apache.jackrabbit.vault.vlt.meta.MetaDirectory;
import org.apache.jackrabbit.vault.vlt.meta.MetaFile;
import org.apache.jackrabbit.vault.vlt.meta.VltEntries;

/**
 * <code>MetaDir</code>...
 */
public class BinMetaDir implements MetaDirectory {

    private BinMetaFile file;

    public BinMetaDir(File file) {
        this.file = new BinMetaFile(file);
    }

    public File getFile() {
        return file.getFile();
    }

    public String getRepositoryUrl() throws IOException {
        HeaderBlock hdr = file.open();
        PropertiesBlock blk = file.getLinkedBlock(hdr, HeaderBlock.ID_PROPERTIES, PropertiesBlock.class, true);
        return blk.getProperty("repository.url");
    }

    public void setRepositoryUrl(String url) throws IOException {
        HeaderBlock hdr = file.open();
        PropertiesBlock blk = file.getLinkedBlock(hdr, HeaderBlock.ID_PROPERTIES, PropertiesBlock.class, true);
        blk.setProperty("repository.url", url);
        file.sync();
    }

    public boolean exists() {
        return file.exists();
    }

    public void create(String path) throws IOException {
    }

    public void delete() throws IOException {
    }

    public VltEntries getEntries() throws VltException {
        return null;
    }

    public MetaFile getFile(String name) throws IOException {
        return null;
    }

    public MetaFile getFile(String name, boolean create) throws IOException {
        return null;
    }

    public MetaFile getTmpFile(String name, boolean create) throws IOException {
        return null;
    }

    public MetaFile getBaseFile(String name, boolean create) throws IOException {
        return null;
    }

    public boolean hasFile(String name) throws IOException {
        return false;
    }

    public void sync() throws IOException {
    }

    public void close() throws IOException {
    }

    public void dump(DumpContext ctx, boolean isLast) {
        file.dump(ctx, isLast);
    }
}