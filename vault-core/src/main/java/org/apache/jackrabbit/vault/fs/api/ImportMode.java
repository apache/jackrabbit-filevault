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

/**
 * {@code ImportMode} is used to define how importing content is applied
 * to the existing content in the repository.
 * 
 * <table border="1">
 * <caption>"Import Mode Effects"</caption>
 * <tr><th rowspan="2">Import Mode</th><th colspan="3">Property/Node (at a specific path)</th></tr>
 * <tr><th>In Package</th><th>In Repository Before Installation</th><th>In Repository After Installation</th></tr>
 * <tr><td rowspan="3">{@link #REPLACE}</td><td>non-existing</td><td>existing</td><td>removed</td></tr>
 * <tr><td>existing</td><td>existing</td><td>replaced</td></tr>
 * <tr><td>existing</td><td>non-existing</td><td>created</td></tr>
 * <tr><td rowspan="3">{@link #MERGE_PROPERTIES}</td><td>non-existing</td><td>existing</td><td>not touched</td></tr>
 * <tr><td>existing</td><td>existing</td><td>not touched</td></tr>
 * <tr><td>existing</td><td>non-existing</td><td>created</td></tr>
 * <tr><td rowspan="3">{@link #UPDATE_PROPERTIES}</td><td>non-existing</td><td>existing</td><td>not touched</td></tr>
 * <tr><td>existing</td><td>existing</td><td>replaced</td></tr>
 * <tr><td>existing</td><td>non-existing</td><td>created</td></tr>
 * </table>
 */
public enum ImportMode {

    /**
     * Normal behavior. Existing content is replaced completely by the imported
     * content, i.e. is overridden or deleted accordingly.
     */
    REPLACE,

    /**
     * Existing content is not modified, i.e. only new content is added and
     * none is deleted or modified
     * <p>
     * <strong>Only considered for</strong>
     * <ul>
     * <li>Binaries: they will never be imported if the parent node has this import mode.</li>
     * <li>Authorizable nodes: only {@code rep:members} of existing authorizables is updated, no other property on those node types is added/modified.</li>
     * <li>Simple files: i.e. they will never be imported in case the repo has this file already.
     * <li>Other docview files: It will ignore them in case the docview's root node does already exist in the repo (both full coverage and .content.xml). It skips non-existing child nodes/properties in the docview as well.</li>
     * </ul>
     * 
     * @deprecated As this behaves inconsistently for the different serialization formats, rather use {@link #MERGE_PROPERTIES}.
     */
    @Deprecated()
    MERGE,

    /**
     * Existing properties are replaced (except for {@code jcr:primaryType}), new properties and nodes are added and no existing properties or nodes are deleted. 
     * <strong>Only affects authorizable nodes (not their child nodes). Other nodes are imported in mode {@link #REPLACE}.</strong>
     * @deprecated rather use {@link #UPDATE_PROPERTIES}
     */
    @Deprecated()
    UPDATE,

    /**
     * Existing properties are not touched, new nodes/properties are added, no existing nodes/properties are deleted
     * The only existing properties potentially touched is the multi value property {@code jcr:mixinType}. 
     * As the primary type is never changed
     * it will skip new properties/nodes which are not allowed by the node type definition of primary + mixin types.
     * Authorizable nodes: only {@code rep:members} of existing authorizables is updated, no other property on those node types is added/modified.
     */
    MERGE_PROPERTIES,

    /**
     * Existing properties are replaced, new nodes/properties are added, no existing nodes/properties are deleted
     * Existing multi-value properties are replaced and not extended except for {@code jcr:mixinType} which is extended.
     * As the primary type is never changed
     * it will skip new properties/child nodes which are not allowed by the node type definition of primary + mixin types.
     */
    UPDATE_PROPERTIES
}