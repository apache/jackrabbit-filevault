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
import java.io.PrintWriter;
import java.util.Collections;

import javax.jcr.RepositoryException;

import org.apache.jackrabbit.vault.fs.api.DumpContext;
import org.apache.jackrabbit.vault.fs.api.RepositoryAddress;
import org.apache.jackrabbit.vault.fs.api.VaultFile;
import org.apache.jackrabbit.vault.fs.config.DefaultMetaInf;
import org.apache.jackrabbit.vault.util.PathUtil;
import org.apache.jackrabbit.vault.vlt.VltContext;
import org.apache.jackrabbit.vault.vlt.VltDirectory;
import org.apache.jackrabbit.vault.vlt.VltException;

/**
 * <code>Checkout</code>...
 *
 */
public class Checkout extends AbstractAction {

    private RepositoryAddress mountPoint;

    private String remoteDir;

    private File localDir;

    private boolean force;

    public Checkout(RepositoryAddress mountPoint, String remoteDir, File localDir) {
        this.mountPoint = mountPoint;
        this.remoteDir = remoteDir;
        this.localDir = localDir;
    }

    public boolean isForce() {
        return force;
    }

    public void setForce(boolean force) {
        this.force = force;
    }

    public void run(VltContext ctx) throws VltException {
        if (!localDir.exists()) {
            if (!localDir.mkdir()) {
                throw ctx.error(localDir.getPath(), "could not create directory");
            }
        }
        // check for meta inf
        if (ctx.getExportRoot().isValid()) {
            String path = ctx.getExportRoot().getJcrRoot().getPath();
            if (force) {
                ctx.printMessage("Checkout " + mountPoint.resolve(remoteDir) + " with local files using root at " + path);
                // ensure we use the calculated jcr_root
                localDir = ctx.getExportRoot().getJcrRoot();
            } else {
                throw ctx.error(localDir.getPath(),
                        "there seems to be already a checkout at " + path + ". " +
                        "Use --force option to overwrite local files with remote.");
            }
        } else {
            // turn of sync
            if (force) {
                ctx.printMessage("Warning: --force was specified but no prior checkout detected. disabling it.");
                force = false;
            }
            try {
                ctx.getExportRoot().create();
                localDir = ctx.getExportRoot().getJcrRoot();
            } catch (IOException e) {
                throw ctx.exception(localDir.getPath(), "Error while creating meta-info", e);
            }
        }
        try {
            if (remoteDir == null) {
                remoteDir = "/";
            }

            // ensure that we use proper filter (JCRVLT-60)
            if (ctx.getDefaultFilter() != null) {
                ((DefaultMetaInf) ctx.getExportRoot().getMetaInf()).setFilter(null);
            }

            VaultFile vaultFile = ctx.getFileSystem(mountPoint).getFile(remoteDir);
            if (vaultFile == null) {
                throw new VltException(remoteDir, "Error during checkout. Remote directory does not exit.");
            }

            // store filter and config
            DefaultMetaInf inf = (DefaultMetaInf) ctx.getMetaInf();
            inf.setConfig(vaultFile.getFileSystem().getConfig());
            inf.setFilter(vaultFile.getFileSystem().getWorkspaceFilter());

            if (!force) {
                inf.save(ctx.getExportRoot().getMetaDir());
            }

            if (ctx.isVerbose()) {
                DumpContext dc = new DumpContext(new PrintWriter(ctx.getStdout()));
                dc.println("Filter");
                ctx.getMetaInf().getFilter().dump(dc, true);
                dc.outdent();
                dc.flush();
            }

            String path = PathUtil.getRelativeFilePath(ctx.getCwd().getPath(), localDir.getPath());
            ctx.printMessage("Checking out " + vaultFile.getPath() + " to " + path);
            VltDirectory dir = new VltDirectory(ctx, localDir);
            if (dir.isControlled()) {
                if (!force) {
                    throw dir.getContext().error(dir.getPath(), "already under vault control.");
                }
            } else {
                dir.control(vaultFile.getPath(), vaultFile.getAggregate().getPath());
            }
            ctx.setMountpoint(vaultFile.getAggregate().getManager().getMountpoint());
            // re-open parent dir to avoid problems with zip-meta-dirs
            dir = new VltDirectory(ctx, localDir);
            Update up = new Update(localDir, null, false);
            up.setOnlyControlled(true);
            up.setForce(force);
            dir.applyWithRemote(up, Collections.<String>emptyList(), false);
            ctx.printMessage("Checkout done.");
        } catch (IOException e) {
            throw new VltException(localDir.getPath(), "Error during checkout", e);
        } catch (RepositoryException e) {
            throw new VltException(remoteDir, "Error during checkout", e);
        }
    }

}