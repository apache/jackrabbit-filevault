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
package org.apache.jackrabbit.vault.util.console.platform;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.jackrabbit.vault.util.console.ConsoleFile;
import org.apache.jackrabbit.vault.util.console.ExecutionException;

/**
 * <code>PlatformFile</code>...
 */
public class PlatformFile implements ConsoleFile {

    private final URI uri;

    private final File file;

    public PlatformFile(File file) {
        if (file == null) {
            throw new NullPointerException();
        }
        this.file = file;
        try {
            this.uri = new URI("file", file.getPath(), null);
        } catch (URISyntaxException e) {
            throw new ExecutionException(e);
        }
    }


    public Object unwrap() {
        return file;
    }

    public ConsoleFile getFile(String path, boolean mustExist)
            throws IOException {
        File newFile = new File(path);
        if (newFile.isAbsolute()) {
            newFile = newFile.getCanonicalFile();
        } else {
            newFile = new File(file, path).getCanonicalFile();
        }
        if (!newFile.exists() && mustExist) {
            throw new FileNotFoundException(newFile.getPath());
        }
        return new PlatformFile(newFile);
    }

    public String getPath() {
        return file.getPath();
    }

    public String getName() {
        return file.getName();
    }

    public ConsoleFile[] listFiles() throws IOException {
        File[] files = file.listFiles();
        if (files.length == 0) {
            return ConsoleFile.EMPTY_ARRAY;
        } else {
            PlatformFile[] ret = new PlatformFile[files.length];
            for (int i=0; i<files.length; i++) {
                ret[i] = new PlatformFile(files[i]);
            }
            return ret;
        }
    }

    public boolean allowsChildren() {
        return file.isDirectory();
    }
}