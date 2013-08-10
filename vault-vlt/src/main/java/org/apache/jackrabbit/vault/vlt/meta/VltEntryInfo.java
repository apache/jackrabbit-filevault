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

package org.apache.jackrabbit.vault.vlt.meta;

import java.io.File;
import java.io.IOException;

import org.apache.jackrabbit.vault.fs.api.VaultFile;
import org.apache.jackrabbit.vault.util.MD5;

/**
 * <code>Entry</code>...
 *
 */
public interface VltEntryInfo {

    public enum Type {
        BASE,
        WORK,
        MINE,
        THEIRS
    }

    VltEntryInfo copyAs(Type type);

    Type getType();

    long getDate();

    void setDate(long date);

    MD5 getMd5();

    void setMd5(MD5 md5);

    String getContentType();

    void setContentType(String contentType);

    long getSize();

    void setSize(long size);

    boolean checkModified(VaultFile remoteFile);

    void update(VltEntryInfo base);

    void update(File file, boolean force) throws IOException;

    void update(MetaFile file, boolean force) throws IOException;

    boolean isDirectory();
    
    boolean isSame(VltEntryInfo base);

}