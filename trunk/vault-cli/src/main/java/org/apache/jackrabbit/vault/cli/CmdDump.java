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

import java.io.File;
import java.io.PrintWriter;

import org.apache.commons.cli2.Argument;
import org.apache.commons.cli2.CommandLine;
import org.apache.commons.cli2.Option;
import org.apache.commons.cli2.builder.ArgumentBuilder;
import org.apache.commons.cli2.builder.CommandBuilder;
import org.apache.commons.cli2.builder.DefaultOptionBuilder;
import org.apache.commons.cli2.builder.GroupBuilder;
import org.apache.commons.cli2.option.Command;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.jackrabbit.vault.fs.api.DumpContext;
import org.apache.jackrabbit.vault.fs.api.Dumpable;
import org.apache.jackrabbit.vault.fs.api.VaultFileSystem;
import org.apache.jackrabbit.vault.util.console.ConsoleFile;
import org.apache.jackrabbit.vault.util.console.platform.PlatformFile;

/**
 * Implements the 'mount' command.
 *
 */
public class CmdDump extends AbstractJcrFsCommand {

    protected void doExecute(VaultFsConsoleExecutionContext ctx, CommandLine cl)
            throws Exception {
        String path = (String) cl.getValue(argPath);
        VaultFileSystem fs = ctx.getVaultFsApp().getVaultFileSystem();
        if (fs == null) {
            VaultFsApp.log.info("Not mounted.");
        } else if (path != null && !path.equals("")) {
            if (cl.hasOption(optConfig) || cl.hasOption(optFilter)) {
                ConsoleFile f = ctx.getCurrentFile();
                File file;
                if (f instanceof PlatformFile) {
                    f = f.getFile(path, false);
                    file = (File) f.unwrap();
                } else {
                    file = ctx.getVaultFsApp().getPlatformFile(path, false);
                }
                if (cl.hasOption(optConfig)) {
                    IOUtils.copy(
                        fs.getConfig().getSource(),
                        FileUtils.openOutputStream(file)
                    );
                    VaultFsApp.log.info("VaultFs config written to {}", file.getPath());
                } else {
                    IOUtils.copy(
                        fs.getWorkspaceFilter().getSource(),
                        FileUtils.openOutputStream(file)
                    );
                    VaultFsApp.log.info("VaultFs workspace filter written to {}", file.getPath());
                }
            } else {
                Object f = ctx.getCurrentFile().getFile(path, false).unwrap();
                if (f instanceof Dumpable) {
                    DumpContext dCtx = new DumpContext(new PrintWriter(System.out));
                    ((Dumpable) f).dump(dCtx, true);
                    dCtx.flush();
                } else {
                    VaultFsApp.log.info("Object not dumpable: {}", f);
                }
            }
        } else {
            fs.getAggregateManager().dumpConfig(new PrintWriter(System.out));
        }
    }

    /**
     * {@inheritDoc}
     */
    public String getShortDescription() {
        return "Dump internal structures. Can also be used to write the current " +
                "config or filter to the local file system.";
    }

    private Argument argPath;

    private Option optConfig;

    private Option optFilter;

    protected Command createCommand() {
        return new CommandBuilder()
                .withName("dump")
                .withDescription(getShortDescription())
                .withChildren(new GroupBuilder()
                        .withName("Options:")
                        .withOption(optConfig = new DefaultOptionBuilder()
                                .withShortName("c")
                                .withLongName("config")
                                .withDescription("writes the config to the local file")
                                .create())
                        .withOption(optFilter = new DefaultOptionBuilder()
                                .withShortName("f")
                                .withLongName("filter")
                                .withDescription("writes the workspace filter to the local file")
                                .create())
                        .withOption(argPath = new ArgumentBuilder()
                                .withName("path")
                                .withDescription("the path")
                                .withMinimum(0)
                                .withMaximum(1)
                                .create())
                        .create())
                .create();
    }

}