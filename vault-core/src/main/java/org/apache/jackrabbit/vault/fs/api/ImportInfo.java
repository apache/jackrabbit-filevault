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

import java.util.Collection;
import java.util.TreeMap;

/**
 * <code>ImportInfo</code>...
 */
public interface ImportInfo {

    NodeNameList getNameList();

    void onModified(String path);

    void onNop(String path);

    void onCreated(String path);

    void onDeleted(String path);

    void onReplaced(String path);

    void onMissing(String path);

    void onError(String path, Exception e);

    TreeMap<String, Type> getModifications();

    Exception getError(String path);

    Collection<String> getToVersion();

    ImportInfo merge(ImportInfo info);
    
    /**
     * returns the number of non-NOP entries.
     * @return the number of modfiied entries.
     */
    int numModified();

    /**
     * returns the number of errors
     * @return the number of errors
     */
    int numErrors();

    public static enum Type {
        CRE,
        MOD,
        DEL,
        REP,
        NOP,
        ERR,
        MIS
    }
}