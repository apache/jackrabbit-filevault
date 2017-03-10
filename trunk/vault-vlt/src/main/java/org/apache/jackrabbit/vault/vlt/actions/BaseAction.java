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
import java.util.LinkedList;
import java.util.List;

import org.apache.jackrabbit.vault.vlt.VltContext;
import org.apache.jackrabbit.vault.vlt.VltDirectory;
import org.apache.jackrabbit.vault.vlt.VltException;
import org.apache.jackrabbit.vault.vlt.meta.Ignored;

/**
 * Basic implementation of an abstract action
 */
public class BaseAction extends AbstractAction {

    private final File localDir;

    private final List<File> localFiles;

    private final boolean nonRecursive;

    private boolean dirsAsFiles;

    private boolean withRemote;

    public BaseAction(File localDir, List<File> localFiles, boolean nonRecursive) {
        this.localDir = localDir;
        this.localFiles = localFiles == null ? new LinkedList<File>() : localFiles;
        this.nonRecursive = nonRecursive;
    }

    public boolean isWithRemote() {
        return withRemote;
    }

    public void setWithRemote(boolean withRemote) {
        this.withRemote = withRemote;
    }

    public void setDirsAsFiles(boolean dirsAsFiles) {
        this.dirsAsFiles = dirsAsFiles;
    }

    public void run(VltContext ctx, VltTree infos) throws VltException {
        if (withRemote) {
            for (VltTree.Info i: infos.infos()) {
                i.dir.applyWithRemote(this, i.names, nonRecursive);
            }
        } else {
            for (VltTree.Info i: infos.infos()) {
                // special check for jcr_root
                if (!ctx.getExportRoot().getJcrRoot().getParentFile().equals(i.dir.getDirectory())) {
                    i.dir.assertControlled();
                }
                i.dir.apply(this, i.names, nonRecursive);
            }
        }
    }
    
    public void run(VltContext ctx) throws VltException {
        VltTree infos = new VltTree(ctx, nonRecursive);
        infos.setDirsAsFiles(dirsAsFiles);
        try {
            if (localFiles.isEmpty()) {
                infos.add(localDir);
            } else {
                infos.addAll(localFiles);
            }
        } catch (IOException e) {
            throw new VltException("Unable to perform command.", e);
        }
        // get common ancestor
        VltDirectory root = new VltDirectory(ctx, new File(infos.getRootPath()));
        // mount fs at the top most directory
        if (root.isControlled()) {
            ctx.setFsRoot(root);
        }
        // define globally ignored
        ctx.setGlobalIgnored(new Ignored(ctx, root.getDirectory()));

        run(ctx, infos);
    }

}