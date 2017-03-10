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
 * Provides an interface for a general path mapping.
 *
 * @since 2.4.10
 */
public interface PathMapping {

    /**
     * Implements an identity mapping
     */
    PathMapping IDENTITY = new PathMapping() {

        @Override
        public String map(String path) {
            return path;
        }

        @Override
        public String map(String path, boolean reverse) {
            return path;
        }
    };

    /**
     * Maps the given path to a new location.
     * @param path the path
     * @return the mapped path.
     */
    String map(String path);

    /**
     * Maps the given path to a new location.
     * @param path the path
     * @param reverse if {@code true} a reverse mapping is applied
     * @return the mapped path.
     */
    String map(String path, boolean reverse);


}