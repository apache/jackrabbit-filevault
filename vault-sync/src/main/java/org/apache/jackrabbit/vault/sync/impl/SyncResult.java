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
package org.apache.jackrabbit.vault.sync.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
* <code>SyncResult</code>...
*/
public class SyncResult {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(SyncResult.class);

    private final Map<String, Entry> byFsPath = new HashMap<String, Entry>();

    private final Map<String, Entry> byJcrPath = new HashMap<String, Entry>();

    public void addEntry(Entry e) {
        byFsPath.put(e.fsPath, e);
        byJcrPath.put(e.jcrPath, e);
    }

    public void addEntry(String jcrPath, String fsPath, Operation ops) {
        addEntry(new Entry(jcrPath, fsPath, ops));
    }

    public Entry getByJcrPath(String path) {
        return byJcrPath.get(path);
    }

    public Entry getByFsPath(String path) {
        return byFsPath.get(path);
    }

    public Set<String> getFsPaths() {
        return byFsPath.keySet();
    }

    public Set<String> getJcrPaths() {
        return byJcrPath.keySet();
    }

    public void dump() {
        for (Entry e: byFsPath.values()) {
            log.info("SyncResult: fs={} jcr={} ops={}", new Object[]{e.fsPath, e.jcrPath, e.ops});
        }
    }

    public void merge(SyncResult syncResult) {
        byFsPath.putAll(syncResult.byFsPath);
        byJcrPath.putAll(syncResult.byJcrPath);
    }

    public static class Entry {

        private final String jcrPath;

        private final String fsPath;

        private final Operation ops;

        public Entry(String jcrPath, String fsPath, Operation ops) {
            this.jcrPath = jcrPath;
            this.fsPath = fsPath;
            this.ops = ops;
        }

    }

    public enum Operation {
        UPDATE_FS,
        UPDATE_JCR,
        DELETE_FS,
        DELETE_JCR
    }
}