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

package org.apache.jackrabbit.vault.fs.api;

import java.util.Set;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

/**
 * <code>NodeTypesCollector</code>...
 */
public interface NodeTypesCollector {

    /**
     * Returns the node types used in the aggregates (so far).
     * @return node types
     */
    Set<String> getNodeTypes();

    /**
     * Add the primary and mixin node types of that node to the internal set
     * of used node types.
     * @param node the node
     * @throws RepositoryException if an error occurs
     */
    void addNodeTypes(Node node) throws RepositoryException;

}