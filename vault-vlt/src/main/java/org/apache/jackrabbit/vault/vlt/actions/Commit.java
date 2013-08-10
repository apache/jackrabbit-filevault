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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.jackrabbit.vault.fs.api.VaultFsTransaction;
import org.apache.jackrabbit.vault.fs.impl.TransactionImpl;
import org.apache.jackrabbit.vault.util.PathComparator;
import org.apache.jackrabbit.vault.util.Text;
import org.apache.jackrabbit.vault.vlt.FileAction;
import org.apache.jackrabbit.vault.vlt.VltContext;
import org.apache.jackrabbit.vault.vlt.VltDirectory;
import org.apache.jackrabbit.vault.vlt.VltException;

/**
 * <code>Resolved</code>...
 *
 */
public class Commit extends AbstractAction {

    private File localDir;

    private List<File> localFiles;

    private boolean nonRecursive;

    private boolean force;

    public Commit(File localDir, List<File> localFiles, boolean nonRecursive, boolean force) {
        this.localDir = localDir;
        this.localFiles = localFiles;
        this.nonRecursive = nonRecursive;
        this.force = force;
    }

    public void run(VltContext ctx) throws VltException {
        VltTree infos = new VltTree(ctx, nonRecursive);
        try {
            if (localFiles.isEmpty()) {
                infos.add(localDir);
            } else {
                infos.setDirsAsFiles(true);
                infos.addAll(localFiles);
            }
        } catch (IOException e) {
            throw new VltException("Unable to commit changes.", e);
        }

        // get common ancestor
        String localRoot = infos.getRootPath();
        VltDirectory root = new VltDirectory(ctx, new File(localRoot));
        // mount fs at the top most directory
        if (root.isControlled()) {
            ctx.setFsRoot(root);
        }
        int rootLen = ctx.getFsRoot().length();

        // create transaction with all changes
        VaultFsTransaction tx = ctx.getFileSystem(ctx.getMountpoint()).startTransaction();
        ctx.printMessage("Collecting commit information...");
        for (VltTree.Info i: infos.infos()) {
            i.dir.prepareCommit(tx, i.names, nonRecursive, force);
        }

        // do commit (assuming all files from the same repo)
        ctx.printMessage("Transmitting file data...");
        List<TransactionImpl.Info> txInfos;
        try {
            txInfos = new ArrayList<TransactionImpl.Info>(tx.commit());
        } catch (Exception e) {
            throw new VltException("Error while committing", e);
        }
        // sort them deepest first
        Collections.sort(txInfos, new Comparator<TransactionImpl.Info>(){
            private final PathComparator pc = new PathComparator();
            public int compare(TransactionImpl.Info o1, TransactionImpl.Info o2) {
                return -pc.compare(o1.getPath(), o2.getPath());
            }
        });

        // updating entries
        infos.clear();
        for (TransactionImpl.Info info: txInfos) {
            if (info.getType() == TransactionImpl.Type.ERROR) {
                ctx.printMessage("Could not process " + info.getPath());
                continue;
            }
            String fileName = Text.getName(info.getPath());
            // get vlt directory
            String dirPath = Text.getRelativeParent(info.getPath(), 1);
            if (dirPath.length() > rootLen && !localRoot.endsWith(dirPath)) {
                // calculate the fs-root relative path, in case the repo was not mounted at jcr:root
                dirPath = dirPath.substring(rootLen + 1);
            } else if (dirPath.length() < rootLen) {
                // dir path outside of the mounted repo. special case
                dirPath = "";
                fileName = "";
            } else {
                dirPath = "";
            }
            File localDir = new File(localRoot, dirPath);
            if (!localDir.exists()) {
                // directory was already deleted, just print message and continue
                File file = new File(localDir, fileName);
                ctx.printAction(file.getPath(), FileAction.DELETED, null);
                continue;
            }

            VltDirectory dir = new VltDirectory(ctx, localDir);

            // check if local file is a directory
            File localFile = new File(localDir, fileName);
            if (localFile.isDirectory()) {
                dir = new VltDirectory(ctx, localFile);
            } else {
                dir.updateComitted(info.getPath(), fileName);
            }

            // remember directory
            infos.put(dir);
        }

        // to be on the save side, issue an update on all directories
        Update upd = new Update(localDir, null, nonRecursive);
        infos.put(root);
        upd.setOnlyControlled(true);
        for (VltTree.Info i: infos.infos()) {
            i.dir.applyWithRemote(upd, Collections.<String>emptyList(), nonRecursive);
        }

        ctx.printMessage("done.");
    }

}