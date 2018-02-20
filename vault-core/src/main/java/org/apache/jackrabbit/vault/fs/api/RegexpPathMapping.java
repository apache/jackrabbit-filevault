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
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Implements a path mapping that supports regular expressions, i.e. {@code /etc/(.*)=/dummy/$1/custom}
 * @since 3.1.42
 */
public final class RegexpPathMapping implements PathMapping {

    @Nonnull
    private final Map<Pattern, String> pathsMapping = new HashMap<>();

    /**
     * Allows importing mappings specified in data structure such as Map or Properties.
     *
     * All null entries (both keys and values) are ignored.
     *
     * @param pathsMappingMap the data structure containing the mapping
     * @param <V> Value type
     * @param <K> KEey type
     * @return this
     */
    @Nonnull
    public <K, V> RegexpPathMapping addAllMappings(@Nonnull Map<K, V> pathsMappingMap) {
        for (Entry<K, V> entry : pathsMappingMap.entrySet()) {
            final K key = entry.getKey();
            final V value = entry.getValue();
            if (key != null && value != null) {
                addMapping(String.valueOf(key), String.valueOf(value));
            }
        }
        return this;
    }

    /**
     * Add a new mapping based on regular expression.
     *
     * @param fromPattern the matching pattern, i.e. <code>/etc/(.*)</code>
     * @param toPattern the replacing pattern, i.e. <code>/dummy/$1/custom</code>
     * @return this
     */
    @Nonnull
    public RegexpPathMapping addMapping(@Nonnull String fromPattern, @Nonnull String toPattern) {
        pathsMapping.put(Pattern.compile(fromPattern), toPattern);
        return this;
    }

    /**
     * Merges the regexp mapping from the given base mapping.
     *
     * @param base base mapping
     * @return this
     */
    @Nonnull
    public RegexpPathMapping merge(@Nullable RegexpPathMapping base) {
        if (base != null) {
            this.pathsMapping.putAll(base.pathsMapping);
        }
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nonnull
    public String map(@Nonnull String path) {
        for (Entry<Pattern, String> pathMapping : pathsMapping.entrySet()) {
            Matcher matcher = pathMapping.getKey().matcher(path);
            if (matcher.matches()) {
                return matcher.replaceAll(pathMapping.getValue());
            }
        }
        return path;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nonnull
    public String map(@Nonnull String path, boolean reverse) {
        if (reverse) {
            throw new IllegalArgumentException("No reverse mapping not supported with regexp mapping");
        }
        return map(path);
    }

}
