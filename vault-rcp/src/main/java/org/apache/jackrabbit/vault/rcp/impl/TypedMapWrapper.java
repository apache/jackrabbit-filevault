/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.jackrabbit.vault.rcp.impl;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Adds helper methods to a wrapped Map containing objects of different types.
 * Those additional getter methods allow to retrieve a specific typed value.
 * The method names are inspired by <a href="https://sling.apache.org/apidocs/sling8/org/apache/sling/commons/json/JSONObject.html">org.apache.sling.commons.json.JSONObject</a>.
 */
public class TypedMapWrapper extends AbstractMap<String, Object> implements Map<String, Object> {

    private final Map<String, Object> wrappedMap;

    public TypedMapWrapper(Map<String, Object> wrappedMap) {
        this.wrappedMap = wrappedMap;
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
        return wrappedMap.entrySet();
    }

    public String getString(String key) {
        return getTypedOrThrow(key, String.class);
    }

    public String optString(String key, String defaultValue) {
        return getTypedOrDefault(key, String.class, defaultValue);
    }

    public List<String> getStringList(String key) {
        List<?> objects = getTypedOrThrow(key, List.class);
        return objects.stream()
                .map(o -> {
                    if (o instanceof String) {
                        return String.class.cast(o);
                    }
                    throw new IllegalArgumentException("List does not contain out of strings");
                })
                .collect(Collectors.toList());
    }

    public long getLong(String key) throws IllegalArgumentException {
        return getTypedOrThrow(key, Long.class);
    }

    public boolean getBoolean(String key) throws IllegalArgumentException {
        return getTypedOrThrow(key, Boolean.class);
    }

    public boolean optBoolean(String key, boolean defaultValue) {
        return getTypedOrDefault(key, Boolean.class, defaultValue);
    }

    public int getInt(String key) throws IllegalArgumentException {
        return getTypedOrThrow(key, Integer.class);
    }

    public int optInt(String key, int defaultValue) {
        return getTypedOrDefault(key, Integer.class, defaultValue);
    }

    private <T> T getTypedOrDefault(String key, Class<T> clazz, T defaultValue) {
        return getTyped(key, clazz).orElse(defaultValue);
    }

    private <T> T getTypedOrThrow(String key, Class<T> clazz) {
        return getTyped(key, clazz)
                .orElseThrow(() -> new IllegalArgumentException("Key " + key + " is unknown or value is no " + clazz));
    }

    private <T> Optional<T> getTyped(String key, Class<T> clazz) {
        Object object = get(key);
        if (clazz.isInstance(object)) {
            return Optional.of(clazz.cast(object));
        } else {
            return Optional.empty();
        }
    }
}
