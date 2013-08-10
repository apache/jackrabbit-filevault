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

package org.apache.jackrabbit.vault.cli;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;

import javax.jcr.Item;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.vault.util.console.ConsoleFile;
import org.apache.jackrabbit.vault.util.console.ExecutionException;

/**
 * <code>RepositoryWorkObject</code>...
 *
 */
public class RepositoryCFile implements ConsoleFile {

    private final Item item;

    public RepositoryCFile(Item item) {
        this.item = item;
    }

    public Object unwrap() {
        return item;
    }

    public String getPath() {
        try {
            return item.getPath();
        } catch (RepositoryException e) {
            return "";
        }
    }

    public ConsoleFile getFile(String path, boolean mustExist)
            throws IOException {
        try {
            if (item.isNode()) {
                if (path.startsWith("/")) {
                    return new RepositoryCFile(item.getSession().getItem(path));
                } else {
                    return new RepositoryCFile(((Node) item).getNode(path));
                }
            } else {
                throw new ExecutionException("can't cd into property");
            }
        } catch (PathNotFoundException e) {
            throw new FileNotFoundException(path);
        } catch (RepositoryException e) {
            throw new IOException(e.toString());
        }
    }

    public ConsoleFile[] listFiles() throws IOException {
        try {
            if (item.isNode()) {
                Node node = (Node) item;
                ArrayList<RepositoryCFile> ret = new ArrayList<RepositoryCFile>();
                PropertyIterator piter = node.getProperties();
                while (piter.hasNext()) {
                    ret.add(new RepositoryCFile(piter.nextProperty()));
                }
                NodeIterator niter = node.getNodes();
                while (niter.hasNext()) {
                    ret.add(new RepositoryCFile(niter.nextNode()));
                }
                return ret.toArray(new RepositoryCFile[ret.size()]);
            } else {
                return ConsoleFile.EMPTY_ARRAY;
            }
        } catch (RepositoryException e) {
            throw new IOException(e.toString());
        }
    }

    public boolean allowsChildren() {
        return item.isNode();
    }

    public String getName() {
        try {
            return item.getName();
        } catch (RepositoryException e) {
            return "";
        }
    }
}