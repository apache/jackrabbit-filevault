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
import java.util.Map;
import java.util.TreeMap;

/**
 * <code>ImportInfo</code>...
 */
public interface ImportInfo {

    /**
     * @deprecated since 3.1
     */
    @Deprecated
    NodeNameList getNameList();

    /**
     * Marks that the node at {@code path} was modified.
     * @param path the path
     */
    void onModified(String path);

    /**
     * Marks that nothing changed at {@code path}
     * @param path the path
     */
    void onNop(String path);

    /**
     * Marks that the node at {@code path} was created.
     * @param path the path
     */
    void onCreated(String path);

    /**
     * Marks that the node at {@code path} was deleted.
     * @param path the path
     */
    void onDeleted(String path);

    /**
     * Marks that the node at {@code path} was replaced.
     * @param path the path
     */
    void onReplaced(String path);

    /**
     * Marks that the node at {@code path} is missing.
     * @param path the path
     */
    void onMissing(String path);

    /**
     * Marks that the node at {@code path} caused an error.
     * @param path the path
     * @param e exception
     */
    void onError(String path, Exception e);

    /**
     * Returns the import information
     * @return the import information
     * @since 3.1
     */
    TreeMap<String, Info> getInfos();

    /**
     * Returns the info at {@code path}
     * @param path path
     * @return the info or {@code null}
     * @since 3.1
     */
    Info getInfo(String path);

    /**
     * Returns the modifications of all infos
     * @return the modifications
     */
    TreeMap<String, Type> getModifications();

    /**
     * @deprecated since 3.1. use getInfo(path).getError();
     */
    @Deprecated
    Exception getError(String path);

    /**
     * Returns a collection of UUIDs of the nodes that need to be versioned.
     * @return a collection of UUIDs.
     */
    Collection<String> getToVersion();

    /**
     * Returns a list of memberships that need to be resolved
     * @return a list of memberships
     */
    Map<String, String[]> getMemberships();

    /**
     * Merges an import info into this one.
     * @param info the other info
     * @return a new, merged info.
     */
    ImportInfo merge(ImportInfo info);
    
    /**
     * returns the number of non-NOP entries.
     * @return the number of modified entries.
     */
    int numModified();

    /**
     * returns the number of errors
     * @return the number of errors
     */
    int numErrors();

    /**
     * The detailed information about an imported path
     * @since 3.1
     */
    interface Info {

        /**
         * The path
         * @return the path
         */
        String getPath();

        /**
         * The modification type
         * @return the type
         */
        Type getType();

        /**
         * the child node name list if relevant
         * @return the child node name list
         */
        NodeNameList getNameList();

        /**
         * The error or {@code null}
         * @return the error
         */
        Exception getError();
    }

    /**
     * The modification type
     */
    public static enum Type {
        /** created */
        CRE,
        /** modified */
        MOD,
        /** deleted */
        DEL,
        /** replaced */
        REP,
        /** nothing changed */
        NOP,
        /** error */
        ERR,
        /** missing */
        MIS
    }
}