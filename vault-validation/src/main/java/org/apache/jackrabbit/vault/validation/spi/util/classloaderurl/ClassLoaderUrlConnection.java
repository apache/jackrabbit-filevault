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
package org.apache.jackrabbit.vault.validation.spi.util.classloaderurl;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

public class ClassLoaderUrlConnection extends URLConnection {
    private final ClassLoader classLoader;

    protected ClassLoaderUrlConnection(ClassLoader classLoader, URL url) {
        super(url);
        this.classLoader = classLoader;
    }

    @Override
    public void connect() throws IOException {
        
    }

    @Override
    public InputStream getInputStream() throws IOException {
        InputStream input = classLoader.getResourceAsStream(url.getFile());
        if (input == null) {
            throw new IOException("Could not load resource '" + url.getFile() + "' from classLoader '" + classLoader + "'");
        }
        return input;
    }
}
