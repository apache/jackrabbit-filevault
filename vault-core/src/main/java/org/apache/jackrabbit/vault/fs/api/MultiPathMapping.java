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

import java.util.HashMap;
import java.util.Map;

import org.apache.jackrabbit.vault.util.Text;

/**
 * Implements a path mapping that supports multiple synlinks
 * @since 3.1.10
 */
public class MultiPathMapping implements PathMapping {

    private final Map<String, String> links = new HashMap<String, String>();

    private final Map<String, String> reverseLinks = new HashMap<String, String>();

    /**
     * Creates a new link from the path {@code src} to the path {@code dst}
     * @param src source path
     * @param dst destination path
     * @return this
     */
    public MultiPathMapping link(String src, String dst) {
        links.put(src, dst);
        reverseLinks.put(dst, src);
        return this;
    }

    /**
     * Merges the links from the given base mapping
     * @param base base mapping
     * @return this
     */
    public MultiPathMapping merge(MultiPathMapping base) {
        if (base != null) {
            this.links.putAll(base.links);
            this.reverseLinks.putAll(base.reverseLinks);
        }
        return this;
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
        if (path == null || path.length() == 0 || "/".equals(path)) {
            return path;
        }
        Map<String, String> lookup = reverse ? reverseLinks : links;
        String[] segs = Text.explode(path, '/');
        String ret = "";
        for (String name: segs) {
            ret += "/" + name;
            String link = lookup.get(ret);
            if (link != null) {
                ret = link;
            }
        }
        return ret;
    }
}