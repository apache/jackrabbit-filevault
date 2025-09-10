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
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.io.IOUtils;
import org.apache.jackrabbit.vault.fs.api.DumpContext;
import org.apache.jackrabbit.vault.fs.api.VaultFile;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.util.console.ConsoleFile;
import org.apache.jackrabbit.vault.util.console.ExecutionException;
import org.apache.jackrabbit.vault.util.console.platform.PlatformFile;

/**
 * Implements the 'export' command.
 *
 */
public class CmdDebug extends AbstractJcrFsCommand {

    private Option optCommand;
    private Option optArgs;
    private Options options;

    public CmdDebug() {
        options = new Options();
        optCommand = Option.builder()
                .argName("command")
                .desc("the debug command")
                .hasArg()
                .build();
        options.addOption(optCommand);
        optArgs = Option.builder()
                .argName("args")
                .desc("arguments for the debug command")
                .hasArgs()
                .build();
        options.addOption(optArgs);
    }

    protected void doExecute(VaultFsConsoleExecutionContext ctx, CommandLine cl) throws Exception {
        String command = cl.getOptionValue("command");
        String[] args = cl.getOptionValues("args");
        if (command.equals("getRelated")) {
            if (args.length<1) {
                throw new ExecutionException("getRelated. needs path argument.");
            }
            String path = args[0];
            ConsoleFile wo = ctx.getFile(path, true);
            if (wo instanceof VaultFsCFile) {
                VaultFile file = (VaultFile) wo.unwrap();
                Collection<? extends VaultFile> related = file.getRelated();
                if (related == null) {
                    System.out.println("(null)");                    
                } else {
                    for (VaultFile f: related) {
                        System.out.println(f.getPath());
                    }
                }
            } else {
                VaultFsApp.log.info("File not a jcrfs file.: {}", path);
            }

        }
        if (command.equals("test")) {
            if (args.length<1) {
                throw new ExecutionException("test. needs path argument.");
            }
            String path = args[0];
            ConsoleFile wo = ctx.getFile(path, true);
            if (wo instanceof PlatformFile) {
                File file = (File) wo.unwrap();
                DefaultWorkspaceFilter r = new DefaultWorkspaceFilter();
                try {
                    r.load(file);
                    DumpContext dCtx = new DumpContext(new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.US_ASCII)));
                    r.dump(dCtx, false);
                    dCtx.flush();
                    
                    IOUtils.copy(r.getSource(), System.out);
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new ExecutionException(e);
                }

            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public String getShortDescription() {
        return "Issue debug commands.";
    }

    /**
     * {@inheritDoc}
     */
    public String getLongDescription() {
        return "Issue debug commands.\n" +
                "\n" +
                "Sub commands:\n" +
                "  getRelated <jcr-path>\n"+
                "  test <repo-path>";
    }
}