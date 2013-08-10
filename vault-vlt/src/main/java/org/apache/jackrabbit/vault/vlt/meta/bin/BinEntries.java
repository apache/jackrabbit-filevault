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
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.jackrabbit.vault.fs.api.DumpContext;
import org.apache.jackrabbit.vault.vlt.VltFile;
import org.apache.jackrabbit.vault.vlt.meta.VltEntries;
import org.apache.jackrabbit.vault.vlt.meta.VltEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <code>BinEntries</code>...
 */
public class BinEntries implements VltEntries {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(BinEntries.class);

    protected final BinMetaFile file;

    private String path;

    private Map<String, VltEntry> entries;

    public BinEntries(File file) {
        this.file = new BinMetaFile(file);
    }

    public boolean exists() {
        return file.exists();
    }

    public void close() {
        try {
            file.close();
        } catch (IOException e) {
            log.warn("Error while closing entries", e);
        }
    }

    public void create(String path) throws IOException {
        HeaderBlock header = file.open();
        PropertiesBlock pBlk = file.getLinkedBlock(header, 0, PropertiesBlock.class, true);
        pBlk.setProperty("path", path);
        file.sync();
        this.path = path;
    }

    private void loadPaths() {
        if (path == null) {
            try {
                HeaderBlock header = file.open();
                PropertiesBlock pBlk = file.getLinkedBlock(header, 0, PropertiesBlock.class, true);
                path = pBlk.getProperty("path");
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    public String getPath() {
        loadPaths();
        return path;
    }

    public void update(VltFile file) {
        VltEntry e = file.getEntry();
        if (e == null) {
            BinEntry old = (BinEntry) entries.remove(file.getName());
            if (old != null) {
                try {
                    this.file.delete(old.block);
                } catch (IOException e1) {
                    throw new IllegalStateException(e1);
                }
            }
        }
    }

    public VltEntry getEntry(String localName) {
        entries();
        return entries.get(localName);
    }

    public VltEntry update(String localName, String aggregatePath, String repoRelPath) {
        entries();

        EntryBlock blk = new EntryBlock(localName, aggregatePath, repoRelPath);
        BinEntry e = new BinEntry(this, blk);

        BinEntry old = (BinEntry) entries.remove(localName);
        if (old != null) {
            e.put(old.work());
            e.put(old.base());
            e.put(old.mine());
            e.put(old.theirs());
            try {
                this.file.delete(old.block);
            } catch (IOException e1) {
                throw new IllegalStateException(e1);
            }
        }
        file.add(blk);
        entries.put(localName, e);
        return e;
    }

    public boolean hasEntry(String localName) {
        entries();
        return entries.containsKey(localName);
    }

    public Collection<VltEntry> entries() {
        try {
            if (entries == null) {
                entries = new HashMap<String, VltEntry>();
                file.open();
                Iterator<Block> iter = file.blocks();
                while (iter.hasNext()) {
                    Block blk = iter.next();
                    if (blk instanceof EntryBlock) {
                        VltEntry e = new BinEntry(this, (EntryBlock) blk);
                        entries.put(e.getName(), e);
                    }
                }
            }
            return entries.values();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public void dump(DumpContext ctx, boolean isLast) {
        file.dump(ctx, isLast);
    }
}