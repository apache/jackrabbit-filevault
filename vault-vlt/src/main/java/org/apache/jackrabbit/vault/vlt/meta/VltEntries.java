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

import java.util.Collection;

import org.apache.jackrabbit.vault.vlt.VltFile;

/**
 * The vault entries provide meta information about the entries under vault control.
 * Note that entries are managed by their respective {@link MetaDirectory} and updates to this
 * entries are only persisted if the directory is synced or closed.
 */
public interface VltEntries {

    /**
     * Returns the platform path of this entries relative to the vault root.
     * @return the platform path
     */
    String getPath();

    /**
     * Checks if the entry with the given name exists
     * @param localName name of the entry
     * @return <code>true</code> if exists
     */
    boolean hasEntry(String localName);

    /**
     * Returns the vault entry for the given name
     * @param localName the name of the entry
     * @return the entry or <code>null</code> if not exists.
     */
    VltEntry getEntry(String localName);

    /**
     * Updates the entry with the state contained in the vault file.
     * @param file the vault file.
     */
    void update(VltFile file);

    /**
     * Updates the paths properties of the entry with <code>localName</code>. If the entry did not exist yet,
     * a new one is created.
     * @param localName the name of the entry
     * @param aggregatePath the new aggregate path
     * @param repoRelPath the new repository path
     * @return the entry that was updated.
     */
    VltEntry update(String localName, String aggregatePath, String repoRelPath);

    /**
     * Returns all entries.
     * @return the entries
     */
    Collection<VltEntry> entries();
}