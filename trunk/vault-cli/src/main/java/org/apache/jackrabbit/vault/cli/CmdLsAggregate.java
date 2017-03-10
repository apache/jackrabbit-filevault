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

import org.apache.commons.cli2.CommandLine;
import org.apache.commons.cli2.Option;
import org.apache.commons.cli2.builder.CommandBuilder;
import org.apache.commons.cli2.builder.DefaultOptionBuilder;
import org.apache.commons.cli2.builder.GroupBuilder;
import org.apache.commons.cli2.option.Command;
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

    protected int getFormatFlags(VaultFsConsoleExecutionContext ctx, CommandLine cl) {
        int fmtFlag = 0;
        fmtFlag |= cl.hasOption(optType) ? F_FLAG_TYPE : 0;
        return fmtFlag;
    }

    protected Command createCommand() {
        return new CommandBuilder()
                .withName("ls")
                .withDescription(getShortDescription())
                .withChildren(new GroupBuilder()
                        .withName("Options:")
                        .withOption(optType = new DefaultOptionBuilder()
                                .withShortName("t")
                                .withDescription("display the artfiact type")
                                .create())
                        .withOption(argPath)
                        .create())
                .create();
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