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
 * Implements a simple path mapping that strips and prefixes a path.
 * @since 2.4.10
 */
public class SimplePathMapping implements PathMapping {

    private final String strip;

    private final String root;

    /**
     * Create a simple path mapping.
     * @param strip the string to strip from the beginning of the path
     * @param root the prefix to add to the path.
     */
    public SimplePathMapping(String strip, String root) {
        this.strip = strip;
        this.root = root;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String map(String path) {
        return map(path, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String map(String path, boolean reverse) {
        String strip = reverse ? this.root : this.strip;
        String root = reverse ? this.strip : this.root;
        if (path.startsWith(strip)) {
            StringBuilder b = new StringBuilder(root);
            b.append(path.substring(strip.length()));
            return b.toString();
        } else {
            return path;
        }
    }
}