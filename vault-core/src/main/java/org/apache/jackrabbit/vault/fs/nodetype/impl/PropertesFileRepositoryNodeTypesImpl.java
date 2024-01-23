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
package org.apache.jackrabbit.vault.fs.nodetype.impl;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.util.TraversingItemVisitor;

import org.apache.jackrabbit.vault.fs.nodetype.RepositoryNodeTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Holds information about primary and mixin types for certain JCR paths.
 * The data is read from a {@link Properties} file with each entry having the format
 * <pre>&lt;path&gt;=&lt;primary type&gt;{,&lt;mixinType&gt;}</pre>
 */
public class PropertesFileRepositoryNodeTypesImpl extends AbstractRepositoryNodeTypes implements RepositoryNodeTypes {

    private final Properties properties;
    
    protected PropertesFileRepositoryNodeTypesImpl(Reader reader) throws IOException {
        super();
        this.properties = new Properties();
        this.properties.load(reader);
    }

    public static void createPropertiesFromSession(Writer writer, Session session, int maxDepth, String... relevantRoots) throws RepositoryException, IOException {
        Properties properties = new Properties();
        for (String relevantRoot : relevantRoots) {
            // traverse repository up till maxDepth
            Node node = session.getNode(relevantRoot);
            node.accept(new NodeTypeCollectorVisitor(properties));
        }
        properties.store(writer, null);
    }

    private static final class NodeTypeCollectorVisitor extends TraversingItemVisitor.Default {

        private final Properties properties;
        public NodeTypeCollectorVisitor(Properties properties) {
           this.properties = properties;
        }

        @Override
        protected void entering(Node node, int level) throws RepositoryException {
            StringBuilder types = new StringBuilder(node.getPrimaryNodeType().getName());
            Arrays.stream(node.getMixinNodeTypes()).forEach(mt -> types.append(",").append(mt.getName()));
            properties.setProperty(node.getPath(), types.toString());
        }
    }

    @Override
    public boolean containsNodeTypes(String path) {
        return getPrimaryNodeType(path) != null;
    }
 
    @Override
    public @Nullable String getPrimaryNodeType(String path) {
        String allTypes = properties.getProperty(path, "");
        if (allTypes.isEmpty()) {
            return null;
        } else {
            int pos = allTypes.indexOf(',');
            if (pos == -1) {
                pos = allTypes.length();
            }
            return allTypes.substring(0, pos);
        }
    }

    @Override
    public @NotNull Set<String> getMixinNodeTypes(String path) {
        List<String> allTypes = Arrays.asList(properties.getProperty(path, "").split(","));
        if (allTypes.size() > 1) {
            return Collections.unmodifiableSet(new HashSet<>(allTypes.subList(1, allTypes.size())));
        } else {
            return Collections.emptySet();
        }
    }


}
