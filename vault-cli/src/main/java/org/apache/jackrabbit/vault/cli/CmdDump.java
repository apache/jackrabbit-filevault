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
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.jackrabbit.vault.fs.api.DumpContext;
import org.apache.jackrabbit.vault.fs.api.Dumpable;
import org.apache.jackrabbit.vault.fs.api.VaultFileSystem;
import org.apache.jackrabbit.vault.util.console.ConsoleFile;
import org.apache.jackrabbit.vault.util.console.platform.PlatformFile;

/**
 * Implements the 'dump' command.
 *
 */
public class CmdDump extends AbstractJcrFsCommand {

    private Option argPath;

    private Option optConfig;

    private Option optFilter;

    private Options options;

    public CmdDump() {
        options = new Options();
        optConfig = Option.builder("c")
                .longOpt("config")
                .desc("writes the config to the local file")
                .build();
        options.addOption(optConfig);
        optFilter = Option.builder("f")
                .longOpt("filter")
                .desc("writes the workspace filter to the local file")
                .build();
        options.addOption(optFilter);
        argPath = Option.builder()
                .argName("path")
                .desc("the path")
                .hasArg()
                .build();
        options.addOption(argPath);
    }

    protected void doExecute(VaultFsConsoleExecutionContext ctx, CommandLine cl)
            throws Exception {
        String path = cl.getOptionValue("path");
        VaultFileSystem fs = ctx.getVaultFsApp().getVaultFileSystem();
        if (fs == null) {
            VaultFsApp.log.info("Not mounted.");
        } else if (path != null && !path.equals("")) {
            if (cl.hasOption(optConfig.getOpt()) || cl.hasOption(optFilter.getOpt())) {
                ConsoleFile f = ctx.getCurrentFile();
                File file;
                if (f instanceof PlatformFile) {
                    f = f.getFile(path, false);
                    file = (File) f.unwrap();
                } else {
                    file = ctx.getVaultFsApp().getPlatformFile(path, false);
                }
                if (cl.hasOption(optConfig.getOpt())) {
                    try (InputStream input = fs.getConfig().getSource()) {
                        Files.copy(input, file.toPath());
                    }
                    VaultFsApp.log.info("VaultFs config written to {}", file.getPath());
                } else {
                    try (InputStream input = fs.getWorkspaceFilter().getSource()) {
                        Files.copy(input, file.toPath());
                    }
                    VaultFsApp.log.info("VaultFs workspace filter written to {}", file.getPath());
                }
            } else {
                Object f = ctx.getCurrentFile().getFile(path, false).unwrap();
                if (f instanceof Dumpable) {
                    DumpContext dCtx = new DumpContext(new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.US_ASCII)));
                    ((Dumpable) f).dump(dCtx, true);
                    dCtx.flush();
                } else {
                    VaultFsApp.log.info("Object not dumpable: {}", f);
                }
            }
        } else {
            fs.getAggregateManager().dumpConfig(new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.US_ASCII)));
        }
    }

    /**
     * {@inheritDoc}
     */
    public String getShortDescription() {
        return "Dump internal structures. Can also be used to write the current " +
                "config or filter to the local file system.";
    }

    public Options getOptions() {
        return options;
    }

}