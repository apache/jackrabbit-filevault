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
package org.apache.jackrabbit.vault.validation.spi;

import java.nio.file.Path;

import org.apache.jackrabbit.spi.Name;
import org.jetbrains.annotations.NotNull;

/**
 * Meta information about a node:
 * <ul>
 * <li>JCR path</li>
 * <li>file system path of the file which defined the node</li>
 * <li>optionally line and column in the the file which defined the node</li>
 * </ul>
 */
public interface NodeContext {

    /**
     * 
     * @return the JCR node path
     */
    @NotNull String getNodePath();

    /**
     * 
     * @return the file path relative to jcr_root
     */
    @NotNull Path getFilePath();

    /**
     * 
     * @return the absolute file path of jcr_root (base for {@link #getFilePath()})
     */
    @NotNull Path getBasePath();

    /**
     * 
     * @return the line where the serialization of the node was found, 0 for unspecified. This is only set for a node context originating from a DocView XML file.
     * @since 3.7.0
     */
    default int getLine() {
        return 0;
    }

    /**
     * 
     * @return the column where the serialization of the node was found, 0 for unspecified. This is only set for a node context originating from a DocView XML file.
     * @since 3.7.0
     */
    default int getColumn() {
        return 0;
    }

    /**
     * Returns a readable String from the given name object.
     * The return value's format depends on the actual underlying context.
     * 
     * @param name the name object
     * @return the <a href="https://s.apache.org/jcr-2.0-spec/3_Repository_Model.html#3.2.5.2%20Qualified%20Form">JCR qualified name</a> 
     * or as fallback the <a href="https://s.apache.org/jcr-2.0-spec/3_Repository_Model.html#3.2.5.1%20Expanded%20Form">JCR expanded name</a>
     * from the given name object
     * @since 3.8.0
     */
    default @NotNull String getJcrName(@NotNull Name name) {
        return name.toString();
    }
}