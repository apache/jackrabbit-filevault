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

package org.apache.jackrabbit.vault.fs.impl.io;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;

/**
 * The content analyzer traverses the tree and checks the following:
 * - retrieves all namespaces used in this tree
 * - retrieve all binary properties in this tree
 *
 */
public class ContentAnalyzer implements AggregateWalkListener {

    private final HashMap<String, String> namespaces = new HashMap<String, String>();

    private final List<Property> binaries = new LinkedList<Property>();

    private final List<String> ignoredPaths = new LinkedList<String>();

    private boolean isEmpty = true;

    public String[] getNamespaceURIs() {
        return namespaces.keySet().toArray(new String[namespaces.keySet().size()]);
    }

    public String getNamespacePrefix(String uri) {
        return namespaces.get(uri);
    }

    public Collection<Property> getBinaries() {
        return binaries;
    }

    public Collection<String> getIgnoredPaths() {
        return ignoredPaths;
    }

    public boolean isEmpty() {
        return isEmpty;
    }

    public void onNodeBegin(Node node, boolean included, int depth)
            throws RepositoryException {
        if (included) {
            isEmpty = false;
        }
        addNamespace(node.getSession(), node.getName());
    }

    public void onNodeEnd(Node node, boolean included, int depth)
            throws RepositoryException {
        // ignore
    }

    public void onProperty(Property prop, int depth) throws RepositoryException {
        isEmpty = false;
        addNamespace(prop.getSession(), prop.getName());
        switch (prop.getType()) {
            case PropertyType.BINARY:
                binaries.add(prop);
                break;
            case PropertyType.NAME:
                if (prop.getDefinition().isMultiple()) {
                    Value[] values = prop.getValues();
                    for (Value value: values) {
                        addNamespace(prop.getSession(), value.getString());
                    }
                } else {
                    addNamespace(prop.getSession(), prop.getValue().getString());
                }
                break;
            case PropertyType.PATH:
                if (prop.getDefinition().isMultiple()) {
                    Value[] values = prop.getValues();
                    for (Value value: values) {
                        addNamespacePath(prop.getSession(), value.getString());
                    }
                } else {
                    addNamespacePath(prop.getSession(), prop.getValue().getString());
                }
                break;

        }
    }

    public void onNodeIgnored(Node node, int depth) throws RepositoryException {
        ignoredPaths.add(node.getPath());
    }

    public void onWalkBegin(Node root) throws RepositoryException {
        // ignore
    }

    public void onChildren(Node node, int depth) throws RepositoryException {
        // ignore
    }

    public void onWalkEnd(Node root) throws RepositoryException {
        // ignore
    }

    private void addNamespace(Session s, String name) throws RepositoryException {
        int idx = name.indexOf(':');
        if (idx > 0) {
            String prefix = name.substring(0, idx);
            String uri = s.getNamespaceURI(prefix);
            namespaces.put(uri, prefix);
        }
    }

    private void addNamespacePath(Session s, String path) throws RepositoryException {
        String[] names = path.split("/");
        for (String name: names) {
            addNamespace(s, name);
        }
    }


}