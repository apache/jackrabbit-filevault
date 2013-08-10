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

package org.apache.jackrabbit.vault.cli;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;

import javax.jcr.RepositoryException;

import org.apache.jackrabbit.vault.fs.api.VaultFile;
import org.apache.jackrabbit.vault.util.console.ConsoleFile;

/**
 * <code>OsWorkObject</code>...
 *
 */
public class VaultFsCFile implements ConsoleFile {

    private final VaultFile file;

    public VaultFsCFile(VaultFile file) {
        this.file = file;
    }

    public Object unwrap() {
        return file;
    }

    public String getPath() {
        return file.getPath();
    }

    public ConsoleFile getFile(String path, boolean mustExist) throws IOException {
        try {
            VaultFile ret = file.getFileSystem().getFile(file, path);
            if (ret == null) {
                throw new FileNotFoundException(path);
            }
            return new VaultFsCFile(ret);
        } catch (RepositoryException e) {
            throw new IOException(e.toString());
        }
    }
    
    public ConsoleFile[] listFiles() throws IOException {
        try {
            Collection<? extends VaultFile> files = file.getChildren();
            if (files.isEmpty()) {
                return ConsoleFile.EMPTY_ARRAY;
            } else {
                VaultFsCFile[] ret = new VaultFsCFile[files.size()];
                int i=0;
                for (VaultFile file: files) {
                    ret[i++] = new VaultFsCFile(file);
                }
                return ret;
            }
        } catch (RepositoryException e) {
            throw new IOException(e.toString());
        }
    }

    public boolean allowsChildren() {
        return file.isDirectory();
    }

    public String getName() {
        return file.getName();
    }
}