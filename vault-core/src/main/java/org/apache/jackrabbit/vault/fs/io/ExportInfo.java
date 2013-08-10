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

package org.apache.jackrabbit.vault.fs.io;

import java.util.Map;
import java.util.TreeMap;

import org.apache.jackrabbit.vault.util.PathComparator;

/**
 * Provides information about an export
 */
public class ExportInfo {

    private final TreeMap<String, Entry> entries = new TreeMap<String, Entry>(new PathComparator(true));

    public enum Type {
        ADD,
        DELETE,
        UPDATE,
        MKDIR,
        RMDIR,
        NOP
    }

    public void update(Type type, String path) {
        Entry e = entries.get(path);
        if (e == null) {
            e = new Entry(type, path);
        } else if (e.type != Type.ADD) {
            // don't overwrite ADDs
            e = new Entry(type, path);
        }
        entries.put(path, e);
    }

    public Map<String, Entry> getEntries() {
        return entries;
    }

    public static class Entry {

        public final Type type;

        public final String path;

        public Entry(Type type, String path) {
            this.type = type;
            this.path = path;
        }

        @Override
        public String toString() {
            return type + " " + path;
        }
    }
}