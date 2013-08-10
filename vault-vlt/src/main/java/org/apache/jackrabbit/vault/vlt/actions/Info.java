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
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.apache.jackrabbit.vault.fs.api.RepositoryAddress;
import org.apache.jackrabbit.vault.fs.api.VaultFile;
import org.apache.jackrabbit.vault.vlt.VltDirectory;
import org.apache.jackrabbit.vault.vlt.VltException;
import org.apache.jackrabbit.vault.vlt.VltFile;
import org.apache.jackrabbit.vault.vlt.meta.VltEntry;
import org.apache.jackrabbit.vault.vlt.meta.VltEntryInfo;

/**
 * <code>Info</code>...
 *
 */
public class Info extends BaseAction {

    public Info(File localDir, List<File> localFiles, boolean nonRecursive) {
        super(localDir, localFiles, nonRecursive);
    }

    public void run(VltDirectory dir, VltFile file, VaultFile remoteFile) throws VltException {
        if (file == null) {
            return;
        }
        PrintStream out = dir.getContext().getStdout();

        VltEntry e = file.getEntry();
        out.printf("  Path: %s%n", dir.getContext().getCwdRelativePath(file.getPath()));
        out.printf("Status: %s%n", file.getStatus().name().toLowerCase());
        if (e != null) {
            RepositoryAddress root = dir.getContext().getMountpoint();
            RepositoryAddress addr = root.resolve(e.getAggregatePath());
            addr = addr.resolve(e.getRepoRelPath());
            out.printf("   URL: %s%n", addr.toString());
            print(out, "  Work", e.work());
            print(out, "  Base", e.base());
            print(out, "  Mine", e.mine());
            print(out, "Theirs", e.theirs());
        }
        out.println();
    }

    private static final SimpleDateFormat dateFmt = new SimpleDateFormat("dd MMM yyyy HH:mm:ss");

    private static void print(PrintStream out, String name, VltEntryInfo info) {
        if (info == null) {
            return;
        }
        out.printf("%s: %s, %s, %d, %s%n",
                name,
                dateFmt.format(new Date(info.getDate())),
                info.getContentType(),
                info.getSize(),
                info.getMd5()
        );
    }
}