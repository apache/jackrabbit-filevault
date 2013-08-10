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
import java.util.Collection;
import java.util.List;

import org.apache.commons.cli2.Argument;
import org.apache.commons.cli2.CommandLine;
import org.apache.commons.cli2.builder.ArgumentBuilder;
import org.apache.commons.cli2.builder.CommandBuilder;
import org.apache.commons.cli2.builder.GroupBuilder;
import org.apache.commons.cli2.option.Command;
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

    private Argument argCommand;
    private Argument argArgs;

    protected void doExecute(VaultFsConsoleExecutionContext ctx, CommandLine cl) throws Exception {
        String cmd = (String) cl.getValue(argCommand);
        List args = cl.getValues(argArgs);
        if (cmd.equals("getRelated")) {
            if (args.size()<1) {
                throw new ExecutionException("getRelated. needs path argument.");
            }
            String path = (String) args.get(0);
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
        if (cmd.equals("test")) {
            if (args.size()<1) {
                throw new ExecutionException("test. needs path argument.");
            }
            String path = (String) args.get(0);
            ConsoleFile wo = ctx.getFile(path, true);
            if (wo instanceof PlatformFile) {
                File file = (File) wo.unwrap();
                DefaultWorkspaceFilter r = new DefaultWorkspaceFilter();
                try {
                    r.load(file);
                    DumpContext dCtx = new DumpContext(new PrintWriter(System.out));
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

    protected Command createCommand() {
        return new CommandBuilder()
                .withName("debug")
                .withDescription(getShortDescription())
                .withChildren(new GroupBuilder()
                        .withName("Options:")
                        .withOption(argCommand = new ArgumentBuilder()
                                .withName("cmd")
                                .withDescription("the sub command")
                                .withMinimum(1)
                                .withMaximum(1)
                                .create()
                        )
                        .withOption(argArgs = new ArgumentBuilder()
                                .withName("args")
                                .withDescription("command arguments")
                                .create()
                        )
                        .create()
                )
                .create();
    }
}