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

import org.apache.jackrabbit.vault.fs.api.VaultFile;
import org.apache.jackrabbit.vault.util.MD5;
import org.apache.jackrabbit.vault.vlt.meta.MetaFile;
import org.apache.jackrabbit.vault.vlt.meta.VltEntryInfo;

/**
 * <code>BinEntryInfo</code>...
 */
public class BinEntryInfo implements VltEntryInfo {

    protected final Type type;

    protected final InfoBlock block;

    public BinEntryInfo(Type type, InfoBlock block) {
        this.type = type;
        this.block = block;
    }

    public VltEntryInfo copyAs(Type type) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public Type getType() {
        return type;
    }

    public long getDate() {
        return block.getDate();
    }

    public void setDate(long date) {
        block.setDate(date);
    }

    public MD5 getMd5() {
        return block.getMd5();
    }

    public void setMd5(MD5 md5) {
        block.setMd5(md5);
    }

    public String getContentType() {
        return block.getContentType();
    }

    public void setContentType(String contentType) {
        block.setContentType(contentType);
    }

    public long getSize() {
        return block.getSize();
    }

    public void setSize(long size) {
        block.setSize(size);
    }

    public boolean checkModified(VaultFile remoteFile) {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void update(VltEntryInfo base) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void update(File file, boolean force) throws IOException {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void update(MetaFile file, boolean force) throws IOException {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public boolean isDirectory() {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public boolean isSame(VltEntryInfo base) {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }
}