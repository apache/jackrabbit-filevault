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
package org.apache.jackrabbit.vault.fs.nodetype;

import java.util.Set;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.vault.packaging.UncoveredAncestorHandling;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// TODO: a better name is needed
public interface RepositoryNodeTypes {

    /**
     * @param path the repository path 
     * @return {@code true} if node types for the given path are known, {@code false} otherwise
     */
    boolean containsNodeTypes(String path);
    
    /**
     * Retrieves the primary node type either expected or set at a certain JCR path.
     * @param path the repository path for which to retrieve the node type
     * 
     * @return the primary node type for the given path, may be {@code null}
     */
    @Nullable String getPrimaryNodeType(String path);
    
    /**
     * Retrieves the (non-effective) mixin types either expected or set at a certain JCR path.
     * This is expected to be one primary type (the first one) and arbitrarily many mixin types.
     * @param path the repository path for which to retrieve the node types (non-effective)
     * 
     * @return the (immutable) set of node types for the given path, may be empty if not known or if no mixins are set/expected at the given path
     */
    @NotNull Set<String> getMixinNodeTypes(String path);

    void enforce(UncoveredAncestorHandling enforcementType, String path, Session session) throws RepositoryException;

    public static final class IncompatibleNodeTypeException extends RepositoryException {

        private static final long serialVersionUID = -1720934303752421933L;
        
    }
}
