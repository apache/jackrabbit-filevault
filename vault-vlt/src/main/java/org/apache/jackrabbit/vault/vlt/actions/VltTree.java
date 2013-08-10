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
package org.apache.jackrabbit.vault.vlt.actions;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.jackrabbit.vault.util.Constants;
import org.apache.jackrabbit.vault.util.Tree;
import org.apache.jackrabbit.vault.vlt.VltContext;
import org.apache.jackrabbit.vault.vlt.VltDirectory;
import org.apache.jackrabbit.vault.vlt.VltException;

/**
 * <code>VltTree</code>...
 */
public class VltTree {

    private final VltContext ctx;

    private final boolean nonRecursive;

    private boolean dirsAsFiles;

    private final Tree<Info> infos = new Tree<Info>(Constants.FS_NATIVE.charAt(0));

    public VltTree(VltContext ctx, boolean nonRecursive) {
        this.ctx = ctx;
        this.nonRecursive = nonRecursive;
    }

    public String getRootPath() {
        return infos.getRootPath();
    }

    public void setDirsAsFiles(boolean dirsAsFiles) {
        this.dirsAsFiles = dirsAsFiles;
    }

    public void clear() {
        infos.clear();
    }

    public void add(File file) throws VltException, IOException {
        File dir;
        if (file.isDirectory() && !nonRecursive && !dirsAsFiles) {
            // if recursive, treat directories as such
            dir = file;
            file = null;
        } else {
            // if non recursive, treat directories as files
            dir = file.getParentFile();
        }
        String path = dir.getCanonicalPath();
        Info info = infos.get(path);
        if (info == null) {
            info = new Info(new VltDirectory(ctx, dir));
            infos.put(path, info);
        }
        if (file != null) {
            info.names.add(file.getName());
        }
    }

    public void addAll(Collection<File> localFiles) throws IOException, VltException {
        for (File file: localFiles) {
            add(file);
        }
    }

    public void put(VltDirectory dir) {
        Info di = infos.get(dir.getPath());
        if (di == null) {
            di = new Info(dir);
            infos.put(di.path, di);
        }
    }

    public List<Info> infos() {
        if (!nonRecursive) {
            // strip all redundant entries
            for (Map.Entry<String, Info> e: infos.map().entrySet()) {
                if (e.getValue().names.isEmpty()) {
                    infos.removeChildren(e.getKey());
                }
            }
        }
        LinkedList<Info> dirs = new LinkedList<Info>();
        dirs.addAll(infos.map().values());
        return dirs;
    }

    public static class Info {

        final Set<String> names = new HashSet<String>();

        final VltDirectory dir;

        final String path;

        final String ePath;

        private Info(VltDirectory dir) {
            this.dir = dir;
            this.path = dir.getPath();
            this.ePath = path + Constants.FS_NATIVE;
        }

        private void dump(VltContext ctx) {
            ctx.printMessage("dir: " + path);
            for (String n: names) {
                ctx.printMessage("   ./" + n);
            }
        }
    }

}