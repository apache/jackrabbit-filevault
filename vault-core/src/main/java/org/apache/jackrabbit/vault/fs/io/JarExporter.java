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
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.jcr.RepositoryException;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.CloseShieldOutputStream;
import org.apache.jackrabbit.vault.fs.api.Artifact;
import org.apache.jackrabbit.vault.fs.api.VaultFile;
import org.apache.jackrabbit.vault.util.PlatformNameFormat;

/**
 * Implements a Vault filesystem exporter that exports Vault files to a jar file.
 * It uses the {@link PlatformNameFormat} for formatting the jcr file
 * names to local ones.
 *
 */
public class JarExporter extends AbstractExporter {

    private JarOutputStream jOut;

    private OutputStream out;

    private File jarFile;

    /**
     * Constructs a new jar exporter that writes to the given file.
     * @param jarFile the jar file
     */
    public JarExporter(File jarFile) {
        this.jarFile = jarFile;
    }

    /**
     * Constructs a new jar exporter that writes to the output stream.
     * @param out the output stream
     */
    public JarExporter(OutputStream out) {
        this.out = out;
    }

    /**
     * Opens the exporter and initializes the undelying structures.
     * @throws IOException if an I/O error occurs
     */
    public void open() throws IOException {
        if (jOut == null) {
            if (jarFile != null) {
                jOut = new JarOutputStream(new FileOutputStream(jarFile));
            } else if (out != null) {
                jOut = new JarOutputStream(out);
            } else {
                throw new IllegalArgumentException("Either out or jarFile needs to be set.");
            }
        }
    }

    public void close() throws IOException {
        if (jOut != null) {
            jOut.close();
            jOut = null;
        }
    }

    public void createDirectory(VaultFile file, String relPath)
            throws RepositoryException, IOException {
        ZipEntry e = new ZipEntry(getPlatformFilePath(file, relPath) + "/");
        jOut.putNextEntry(e);
        jOut.closeEntry();
        track("A", relPath);
        exportInfo.update(ExportInfo.Type.MKDIR, e.getName());
    }

    public void createDirectory(String relPath) throws IOException {
        ZipEntry e = new ZipEntry(relPath + "/");
        jOut.putNextEntry(e);
        jOut.closeEntry();
        exportInfo.update(ExportInfo.Type.MKDIR, e.getName());
    }

    public void writeFile(VaultFile file, String relPath)
            throws RepositoryException, IOException {
        ZipEntry e = new ZipEntry(getPlatformFilePath(file, relPath));
        Artifact a = file.getArtifact();
        if (a.getLastModified() > 0) {
            e.setTime(a.getLastModified());
        }
        track("A", relPath);
        exportInfo.update(ExportInfo.Type.ADD, e.getName());
        jOut.putNextEntry(e);
        switch (a.getPreferredAccess()) {
            case NONE:
                throw new RepositoryException("Artifact has no content.");

            case SPOOL:
                OutputStream nout = new CloseShieldOutputStream(jOut);
                a.spool(nout);
                break;

            case STREAM:
                nout = new CloseShieldOutputStream(jOut);
                InputStream in = a.getInputStream();
                IOUtils.copy(in, nout);
                in.close();
                break;
        }
        jOut.closeEntry();
    }

    public void writeFile(InputStream in, String relPath) throws IOException {
        ZipEntry e = new ZipEntry(relPath);
        exportInfo.update(ExportInfo.Type.ADD, e.getName());
        jOut.putNextEntry(e);
        OutputStream nout = new CloseShieldOutputStream(jOut);
        IOUtils.copy(in, nout);
        in.close();
        jOut.closeEntry();
    }

    public void write(ZipFile zip, ZipEntry entry) throws IOException {
        track("A", entry.getName());
        exportInfo.update(ExportInfo.Type.ADD, entry.getName());
        ZipEntry copy = new ZipEntry(entry);
        jOut.putNextEntry(copy);
        if (!entry.isDirectory()) {
            // copy
            InputStream in = zip.getInputStream(entry);
            IOUtils.copy(in, jOut);
            in.close();
        }
        jOut.closeEntry();
    }


}