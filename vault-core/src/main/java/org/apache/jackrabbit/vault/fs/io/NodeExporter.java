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
import java.io.InputStream;
import java.io.OutputStream;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.apache.commons.io.IOUtils;
import org.apache.jackrabbit.vault.fs.api.Artifact;
import org.apache.jackrabbit.vault.fs.api.ArtifactType;
import org.apache.jackrabbit.vault.fs.api.VaultFile;

public class NodeExporter extends AbstractExporter {

    private OutputStream out;

    /**
     * Constructs a new node exporter that writes to the output stream.
     *
     * @param out the output stream
     */
    public NodeExporter(OutputStream out) {
        this.out = out;
    }

    public void writeFile(VaultFile file, String relPath)
            throws RepositoryException, IOException {
        Artifact a = file.getArtifact();
        if (a.getType() == ArtifactType.PRIMARY) {
            track("A", relPath);
            exportInfo.update(ExportInfo.Type.ADD, relPath);
            switch (a.getPreferredAccess()) {
                case NONE:
                    throw new RepositoryException("Artifact has no content.");

                case SPOOL:
                    a.spool(out);
                    break;

                case STREAM:
                    try (InputStream in = a.getInputSource().getByteStream()) {
                        IOUtils.copy(in, out);
                    }
                    break;
            }
        }
    }

    //----------------------- empty implementations ------------------------------------------------------------

    @Override
    public void writeFile(InputStream in, String relPath) throws IOException {}

    @Override
    public void open() throws IOException, RepositoryException {}

    @Override
    public void close() throws IOException, RepositoryException {}

    @Override
    public void createDirectory(String relPath) throws IOException {}

    @Override
    public void createDirectory(VaultFile file, String relPath) throws RepositoryException, IOException {}
}
