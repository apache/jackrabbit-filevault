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
package org.apache.jackrabbit.vault.vlt.meta;

import java.io.File;
import java.io.IOException;

/**
 * Represents an entry in of the {@link VltEntries}
 */
public interface VltEntry {

    /**
     * Describes the state of an entry
     */
    public enum State {

        CLEAN(" "),
        ADDED("A"),
        CONFLICT("C"),
        DELETED("D");

        public final String letter;

        private State(String shortName) {
            this.letter = shortName;
        }

        public String toString() {
            return name().toLowerCase() + " (" + letter + ")";
        }

    }

    /**
     * Returns the name of an entry.
     * @return the name.
     */
    String getName();

    /**
     * Returns the repository path of this entry.
     * @return the repository path.
     */
    String getRepoRelPath();

    /**
     * Returns the aggregate path of this entry.
     * @return the aggregate path.
     */
    String getAggregatePath();

    /**
     * Creates a new entry info for the given type.
     * @param type info type.
     * @return the entry info
     */
    VltEntryInfo create(VltEntryInfo.Type type);

    /**
     * Puts and entry info to this entry.
     * @param info the entry info
     */
    void put(VltEntryInfo info);

    /**
     * Returns the entry info of type {@link VltEntryInfo.Type#WORK}
     * @return the "work" entry info or <code>null</code> if not defined.
     */
    VltEntryInfo work();

    /**
     * Returns the entry info of type {@link VltEntryInfo.Type#BASE}
     * @return the "base" entry info or <code>null</code> if not defined.
     */
    VltEntryInfo base();

    /**
     * Returns the entry info of type {@link VltEntryInfo.Type#MINE}
     * @return the "mine" entry info or <code>null</code> if not defined.
     */
    VltEntryInfo mine();

    /**
     * Returns the entry info of type {@link VltEntryInfo.Type#THEIRS}
     * @return the "theirs" entry info or <code>null</code> if not defined.
     */
    VltEntryInfo theirs();

    /**
     * Removes the entry info with the given type.
     * @param type the info type
     * @return the previously assigned info or <code>null</code>
     */
    VltEntryInfo remove(VltEntryInfo.Type type);

    /**
     * Returns the state of this entry.
     * @return the vault state
     */
    State getState();

    void resolved(MetaFile fileTmp, File fileWork, MetaFile fileBase) throws IOException;

    boolean delete(File fileWork);

    boolean revertConflict(File work) throws IOException;

    void conflict(File work, MetaFile base, MetaFile tmp) throws IOException;

    boolean isDirty();

    boolean isDirectory();

}