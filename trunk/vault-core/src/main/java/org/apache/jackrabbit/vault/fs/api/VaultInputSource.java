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

package org.apache.jackrabbit.vault.fs.api;

import java.io.InputStream;
import java.io.Reader;

import org.xml.sax.InputSource;

/**
 * Extends the {@link InputSource} by a content length and last modified.
 *
 */
public abstract class VaultInputSource extends InputSource {

    protected VaultInputSource() {
    }

    protected VaultInputSource(String systemId) {
        super(systemId);
    }

    protected VaultInputSource(InputStream byteStream) {
        super(byteStream);
    }

    protected VaultInputSource(Reader characterStream) {
        super(characterStream);
    }

    /**
     * Returns the content length of the underlying file.
     * @return the content length of the underlying file or -1 if unknown.
     */
    public abstract long getContentLength();

    /**
     * Returns the last modified date of the underlying file.
     * @return the last modified date of the underlying file or 0 if unknown.
     */
    public abstract long getLastModified();


}