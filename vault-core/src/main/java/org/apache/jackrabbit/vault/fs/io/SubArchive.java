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

package org.apache.jackrabbit.vault.fs.io;

import java.io.IOException;
import java.io.InputStream;

import org.apache.jackrabbit.vault.fs.api.VaultInputSource;
import org.apache.jackrabbit.vault.fs.config.DefaultMetaInf;
import org.apache.jackrabbit.vault.fs.config.MetaInf;

/**
 * <code>SubArchive</code>...
 */
public class SubArchive extends AbstractArchive {

    private Archive base;

    private Entry root;

    private final boolean isJcrRoot;

    private DefaultMetaInf inf = new DefaultMetaInf();

    public SubArchive(Archive base, Entry root, boolean isJcrRoot) {
        this.base = base;
        this.root = root;
        this.isJcrRoot = isJcrRoot;
        inf.setSettings(base.getMetaInf().getSettings());
        inf.setConfig(base.getMetaInf().getConfig());
    }

    public Entry getRoot() throws IOException {
        return root;
    }

    @Override
    public Entry getJcrRoot() throws IOException {
        if (isJcrRoot) {
            return root;
        } else {
            return super.getJcrRoot();
        }
    }

    public void open(boolean strict) throws IOException {
        // assume open
    }

    public MetaInf getMetaInf() {
        return inf;
    }

    public void close() {
        base = null;
    }

    public InputStream openInputStream(Entry entry) throws IOException {
        return base.openInputStream(entry);
    }

    public VaultInputSource getInputSource(Entry entry) throws IOException {
        return base.getInputSource(entry);
    }
}