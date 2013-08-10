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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.jcr.RepositoryException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.jackrabbit.vault.fs.api.Artifact;
import org.apache.jackrabbit.vault.fs.api.VaultFile;
import org.apache.jackrabbit.vault.util.Constants;
import org.apache.jackrabbit.vault.util.PathUtil;
import org.apache.jackrabbit.vault.util.PlatformNameFormat;

/**
 * Implements a Vault filesystem exporter that exports Vault files to a platform
 * file system.
 * It uses the {@link PlatformNameFormat} for formatting the jcr file
 * names to local ones.
 *
 */
public class PlatformExporter extends AbstractExporter {

    private final File localParent;

    private boolean pruneMissing;

    /**
     * Constructs a new jar exporter that writes to the given file.
     * @param localFile the local parent directory
     */
    public PlatformExporter(File localFile) {
        this.localParent = localFile;
    }

    /**
     * Checks if 'prune-missing' is enabled.
     * @return <code>true</code> if prune-missing is enabled
     */
    public boolean pruneMissing() {
        return pruneMissing;
    }

    /**
     * Sets the 'prune-missing' flag.
     * @param pruneMissing the flag
     */
    public void setPruneMissing(boolean pruneMissing) {
        this.pruneMissing = pruneMissing;
    }

    /**
     * {@inheritDoc}
     */
    public void open() throws IOException, RepositoryException {
        scan(new File(localParent, Constants.ROOT_DIR));
    }

    /**
     * {@inheritDoc}
     */
    public void close() throws IOException {
        if (pruneMissing) {
            for (ExportInfo.Entry e: exportInfo.getEntries().values()) {
                if (e.type == ExportInfo.Type.DELETE) {
                    File file = new File(e.path);
                    FileUtils.deleteQuietly(file);
                    track("D", PathUtil.getRelativePath(localParent.getAbsolutePath(), e.path));
                }
            }
        }
    }

    private void scan(File dir) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file: files) {
            String name = file.getName();
            if (name.equals(".svn") || name.equals(".vlt")) {
                continue;
            }
            if (file.isDirectory()) {
                exportInfo.update(ExportInfo.Type.RMDIR, file.getPath());
                scan(file);
            } else {
                exportInfo.update(ExportInfo.Type.DELETE, file.getPath());
            }
        }
    }

    public void createDirectory(VaultFile file, String relPath)
            throws RepositoryException, IOException {
        File dir = new File(localParent, getPlatformFilePath(file, relPath));
        mkdirs(dir);
        track("A", PathUtil.getRelativeFilePath(localParent.getAbsolutePath(), dir.getAbsolutePath()));
    }

    public void createDirectory(String relPath) throws IOException {
        File dir = new File(localParent, relPath);
        mkdirs(dir);
    }

    public void writeFile(VaultFile file, String relPath)
            throws RepositoryException, IOException {
        File local = new File(localParent, getPlatformFilePath(file, relPath));
        if (!local.getParentFile().exists()) {
            mkdirs(local.getParentFile());
        }
        if (local.exists()) {
            exportInfo.update(ExportInfo.Type.UPDATE, local.getPath());
        } else {
            exportInfo.update(ExportInfo.Type.ADD, local.getPath());
        }
        track("A", PathUtil.getRelativeFilePath(localParent.getAbsolutePath(), local.getAbsolutePath()));
        Artifact a = file.getArtifact();
        switch (a.getPreferredAccess()) {
            case NONE:
                throw new RepositoryException("Artifact has no content.");

            case SPOOL:
                FileOutputStream out = new FileOutputStream(local);
                a.spool(out);
                out.close();
                break;

            case STREAM:
                InputStream in = a.getInputStream();
                out = new FileOutputStream(local);
                IOUtils.copy(in, out);
                in.close();
                out.close();
                break;
        }
        if (a.getLastModified() >= 0) {
            local.setLastModified(a.getLastModified());
        }
    }

    public void writeFile(InputStream in, String relPath) throws IOException {
        File local = new File(localParent, relPath);
        if (!local.getParentFile().exists()) {
            mkdirs(local.getParentFile());
        }
        if (local.exists()) {
            exportInfo.update(ExportInfo.Type.UPDATE, local.getPath());
        } else {
            exportInfo.update(ExportInfo.Type.ADD, local.getPath());
        }
        OutputStream out = new FileOutputStream(local);
        IOUtils.copy(in, out);
        in.close();
        out.close();
    }

    private void mkdirs(File dir) throws IOException {
        dir.mkdirs();
        exportInfo.update(ExportInfo.Type.MKDIR, dir.getPath());
    }

}