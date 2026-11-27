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
import java.util.Set;

import org.apache.jackrabbit.vault.util.Constants;
import org.apache.jackrabbit.vault.util.Text;

/**
 * Base class for archives
 */
abstract class AbstractArchive implements Archive {

    @Override
    public Entry getEntry(String path) throws IOException {
        String[] segs = Text.explode(path, '/');
        Entry root = getRoot();
        for (String name: segs) {
            root = root.getChild(name);
            if (root == null) {
                break;
            }
        }
        return root;
    }

    @Override
    public Entry getJcrRoot() throws IOException {
        return getRoot().getChild(Constants.ROOT_DIR);
    }

    @Override
    public Archive getSubArchive(String rootPath, boolean asJcrRoot) throws IOException {
        Entry root = getEntry(rootPath);
        return root == null ? null : new SubArchive(this, root, asJcrRoot);
    }
    /**
     * Rejects a non-directory zip entry whose name was already seen. The zip format allows two
     * entries to share a name, but the various archive implementations (JarFile/zipfs based vs.
     * stream based) resolve such a clash differently (central-directory vs. local-header order),
     * so registration-time and install-time content could silently disagree (GH-17).
     *
     * @param seenEntryNames the non-directory entry names already scanned; updated in place
     * @param entryName the current entry's name
     * @param isDirectory whether the current entry is a directory (directory entries may legitimately repeat)
     * @param location a human-readable identifier of the zip being scanned, used in the error message
     * @throws IOException if {@code entryName} is a non-directory entry that was already seen
     */
    protected static void checkNotDuplicateEntry(
            Set<String> seenEntryNames, String entryName, boolean isDirectory, String location) throws IOException {
        if (!isDirectory && !seenEntryNames.add(entryName)) {
            throw new IOException("Duplicate entry '" + entryName + "' in zip file '" + location + "'");
        }
    }
}
