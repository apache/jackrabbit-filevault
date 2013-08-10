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
package org.apache.jackrabbit.vault.vlt.actions;

import org.apache.jackrabbit.vault.fs.api.VaultFile;
import org.apache.jackrabbit.vault.vlt.VltContext;
import org.apache.jackrabbit.vault.vlt.VltDirectory;
import org.apache.jackrabbit.vault.vlt.VltException;
import org.apache.jackrabbit.vault.vlt.VltFile;

/**
 * <code>Action</code>...
 *
 */
public interface Action {

    /**
     * Executes this action on the given context.
     * @param ctx context
     * @throws VltException if an error occurs
     */
    public void run(VltContext ctx) throws VltException;

    /**
     * Executes this action for the given files.
     * @param dir vlt directory
     * @param file local file
     * @param remoteFile remote file
     * @throws VltException if an error occurs
     */
    public void run(VltDirectory dir, VltFile file, VaultFile remoteFile)
            throws VltException;


    /**
     * Executes this action on the given directory
     * @param dir local directory
     * @param remoteDir remote directory
     * @throws VltException if an error occurs
     *
     * @return <code>true</code> if proceed; <code>false</code> to abort
     */
    public boolean run(VltDirectory dir, VaultFile remoteDir) throws VltException;
}