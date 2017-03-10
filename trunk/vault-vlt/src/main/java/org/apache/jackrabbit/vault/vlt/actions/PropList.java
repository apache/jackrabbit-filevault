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
import java.util.Properties;

import org.apache.jackrabbit.vault.fs.api.VaultFile;
import org.apache.jackrabbit.vault.vlt.VltDirectory;
import org.apache.jackrabbit.vault.vlt.VltException;
import org.apache.jackrabbit.vault.vlt.VltFile;

/**
 */
public class PropList extends BaseAction {

    public PropList(File localDir, List<File> localFiles, boolean nonRecursive) {
        super(localDir, localFiles, nonRecursive);
    }

    public void run(VltDirectory dir, VltFile file, VaultFile remoteFile)
            throws VltException {
        if (file == null) {
            // nothing to do
            return;
        }
        Properties props = file.getProperties();
        if (props.isEmpty()) {
            dir.getContext().printMessage("  (empty properties)");
        } else {
            for (Object name: props.keySet()) {
                String value = (String) props.get(name);
                dir.getContext().printMessage("  " + name + "=" + value);
            }
        }
    }

}