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

package org.apache.jackrabbit.vault.cli;

import javax.jcr.RepositoryException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.jackrabbit.vault.fs.api.Aggregate;
import org.apache.jackrabbit.vault.fs.api.Artifact;
import org.apache.jackrabbit.vault.util.console.ConsoleFile;
import org.apache.jackrabbit.vault.util.console.ExecutionException;
import org.apache.jackrabbit.vault.util.console.util.Table;

/**
 * Implements the 'ls' command.
 *
 */
public class CmdLsAggregate extends AbstractCmdLs {

    /**
     * afct flags
     */
    private static final int F_FLAG_TYPE = 0x01;

    private Option optType;
    private Options options;

    protected int getFormatFlags(VaultFsConsoleExecutionContext ctx, CommandLine cl) {
        int fmtFlag = 0;
        fmtFlag |= cl.hasOption("t") ? F_FLAG_TYPE : 0;
        return fmtFlag;
    }

    public CmdLsAggregate() {
        options = new Options();
        optType = Option.builder("t")
                .desc("display the artifact type")
                .build();
        options.addOption(optType);
        // argPath handled by AbstractCmdLs via args
    }

    public Options getOptions() {
        return options;
    }

    protected void formatFile(ConsoleFile file, Table.Row row, int flags) {
        try {
            boolean isDir = false;
            Aggregate f = (Aggregate) file.unwrap();
            if (f.allowsChildren()) {
                isDir = true;
            }
            if ((flags & F_FLAG_TYPE) > 0) {
                if (f.getArtifacts().isEmpty()) {
                    row.addCol("???");
                } else {
                    Artifact primary = f.getArtifacts().getPrimaryData();
                    if (primary == null) {
                        row.addCol("");
                    } else {
                        row.addCol(primary.getSerializationType().toString());
                    }
                }
            }
            row.addCol(f.getRelPath() + (isDir ? "/" : ""));
        } catch (RepositoryException e) {
            throw new ExecutionException(e);
        }
    }
}