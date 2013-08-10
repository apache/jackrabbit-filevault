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
import java.util.EnumMap;
import java.util.Map;

import org.apache.jackrabbit.vault.vlt.meta.MetaFile;
import org.apache.jackrabbit.vault.vlt.meta.VltEntry;
import org.apache.jackrabbit.vault.vlt.meta.VltEntryInfo;

/**
 * <code>BinEntry</code>...
 */
public class BinEntry implements VltEntry {

    private final BinEntries entries;

    protected final EntryBlock block;

    private Map<VltEntryInfo.Type, VltEntryInfo> infos =
            new EnumMap<VltEntryInfo.Type, VltEntryInfo>(VltEntryInfo.Type.class);


    public BinEntry(BinEntries entries, EntryBlock block) {
        this.entries = entries;
        this.block = block;

        try {
            init(VltEntryInfo.Type.BASE, EntryBlock.ID_BASE);
            init(VltEntryInfo.Type.WORK, EntryBlock.ID_WORK);
            init(VltEntryInfo.Type.MINE, EntryBlock.ID_MINE);
            init(VltEntryInfo.Type.THEIRS, EntryBlock.ID_THEIRS);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private void init(VltEntryInfo.Type type, int id) throws IOException {
        InfoBlock blk = entries.file.getLinkedBlock(block, id, InfoBlock.class, false);
        if (blk != null) {
            infos.put(type, new BinEntryInfo(type, blk));
        }
    }
    public String getName() {
        return block.getName();
    }

    public String getRepoRelPath() {
        return block.getRelPath();
    }

    public String getAggregatePath() {
        return block.getAggregatePath();
    }

    public VltEntryInfo create(VltEntryInfo.Type type) {
        return new BinEntryInfo(type, new InfoBlock());
    }

    public void put(VltEntryInfo info) {
        if (info == null) {
            return;
        }
        BinEntryInfo old = (BinEntryInfo) infos.put(info.getType(), info);
        Block newBlk = ((BinEntryInfo) info).block;
        if (old != null) {
            // need to save the offset
            newBlk.offset = old.block.offset;
        } else {
            try {
                switch (info.getType()) {
                    case BASE:
                        entries.file.linkBlock(block, EntryBlock.ID_BASE, newBlk);
                        break;
                    case MINE:
                        entries.file.linkBlock(block, EntryBlock.ID_MINE, newBlk);
                        break;
                    case THEIRS:
                        entries.file.linkBlock(block, EntryBlock.ID_THEIRS, newBlk);
                        break;
                    case WORK:
                        entries.file.linkBlock(block, EntryBlock.ID_WORK, newBlk);
                        break;
                }
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    public VltEntryInfo work() {
        return infos.get(VltEntryInfo.Type.WORK);
    }

    public VltEntryInfo base() {
        return infos.get(VltEntryInfo.Type.BASE);
    }

    public VltEntryInfo mine() {
        return infos.get(VltEntryInfo.Type.MINE);
    }

    public VltEntryInfo theirs() {
        return infos.get(VltEntryInfo.Type.THEIRS);
    }


    public VltEntryInfo remove(VltEntryInfo.Type type) {
        BinEntryInfo old = (BinEntryInfo) infos.remove(type);
        if (old != null) {
            switch (type) {
                case BASE:
                    block.linkModified(0, EntryBlock.ID_BASE);
                    break;
                case MINE:
                    block.linkModified(0, EntryBlock.ID_MINE);
                    break;
                case THEIRS:
                    block.linkModified(0, EntryBlock.ID_THEIRS);
                    break;
                case WORK:
                    block.linkModified(0, EntryBlock.ID_WORK);
                    break;
            }
        }
        return old;
    }

    public State getState() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void resolved(MetaFile fileTmp, File fileWork, MetaFile fileBase) throws IOException {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public boolean delete(File fileWork) {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public boolean revertConflict(File work) throws IOException {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void conflict(File work, MetaFile base, MetaFile tmp) throws IOException {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public boolean isDirty() {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public boolean isDirectory() {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

}