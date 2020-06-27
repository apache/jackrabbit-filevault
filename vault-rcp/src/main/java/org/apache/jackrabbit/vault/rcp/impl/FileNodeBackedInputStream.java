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

import java.io.IOException;
import java.io.InputStream;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.sling.jcr.api.SlingRepository;

public class FileNodeBackedInputStream extends InputStream {

    private final Session session;
    private final InputStream input;
    
    FileNodeBackedInputStream(SlingRepository repository, String nodePath) throws RepositoryException {
        session = repository.loginService(null, null);
        Node node = session.getNode(nodePath);
        input = JcrUtils.readFile(node);
    }

    @Override
    public int read() throws IOException {
        return input.read();
    }

    @Override
    public void close() throws IOException {
        super.close();
        session.logout();
    }

    
}
