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
package org.apache.jackrabbit.vault.rcp.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.JcrConstants;
import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.jackrabbit.vault.util.Text;
import org.apache.sling.jcr.api.SlingRepository;

public class FileNodeBackedOutputStream extends OutputStream {

    private final Session session;
    private final ByteArrayOutputStream output;
    private final String nodePath;
    private final String mimeType;

    public FileNodeBackedOutputStream(SlingRepository repository, String nodePath, String mimeType) throws RepositoryException {
        session = repository.loginService(null, null);
        output = new ByteArrayOutputStream();
        this.nodePath = nodePath;
        this.mimeType = mimeType;
    }

    @Override
    public void close() throws IOException {
        output.close();
        byte[] data = output.toByteArray();
        String parentNodePath = Text.getRelativeParent(nodePath, 1);
        try {
            Node parentNode = JcrUtils.getOrCreateByPath(parentNodePath, JcrConstants.NT_FOLDER, session);
            try (ByteArrayInputStream in = new ByteArrayInputStream(data)) {
                JcrUtils.putFile(parentNode, Text.getName(nodePath), mimeType, in);
            }
            session.logout();
        } catch (RepositoryException e) {
            throw new IOException("Could not persist in repository", e);
        }
    }

    @Override
    public void write(int b) throws IOException {
        output.write(b);
    }

}
