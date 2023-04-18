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
     * @since 3.6.10
     */
    default int getLine() {
        return 0;
    }

    /**
     * 
     * @return the column where the serialization of the node was found, 0 for unspecified. This is only set for a node context originating from a DocView XML file.
     * @since 3.6.10
     */
    default int getColumn() {
        return 0;
    }
}