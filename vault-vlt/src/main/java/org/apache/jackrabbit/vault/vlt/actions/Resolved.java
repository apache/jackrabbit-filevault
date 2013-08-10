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

import java.io.File;
import java.util.List;

import org.apache.jackrabbit.vault.fs.api.VaultFile;
import org.apache.jackrabbit.vault.vlt.VltDirectory;
import org.apache.jackrabbit.vault.vlt.VltException;
import org.apache.jackrabbit.vault.vlt.VltFile;

/**
 * <code>Resolved</code>...
 *
 */
public class Resolved extends BaseAction {

    private boolean forced;

    public Resolved(File localDir, List<File> localFiles, boolean nonRecursive,
                    boolean forced) {
        super(localDir, localFiles, nonRecursive);
        this.forced = forced;
    }

    public void run(VltDirectory dir, VltFile file, VaultFile remoteFile)
            throws VltException {
        if (file.resolved(forced)) {
            dir.getContext().printMessage(file, "resolved.");
        }
    }
}