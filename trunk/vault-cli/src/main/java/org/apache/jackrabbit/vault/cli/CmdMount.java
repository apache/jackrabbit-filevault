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

import org.apache.commons.cli2.Argument;
import org.apache.commons.cli2.CommandLine;
import org.apache.commons.cli2.Option;
import org.apache.commons.cli2.builder.ArgumentBuilder;
import org.apache.commons.cli2.builder.CommandBuilder;
import org.apache.commons.cli2.builder.DefaultOptionBuilder;
import org.apache.commons.cli2.builder.GroupBuilder;
import org.apache.commons.cli2.option.Command;
import org.apache.jackrabbit.vault.util.console.util.Table;

/**
 * Implements the 'mount' command.
 *
 */
public class CmdMount extends AbstractJcrFsCommand {

    protected void doExecute(VaultFsConsoleExecutionContext ctx, CommandLine cl) throws Exception {
        String path = (String) cl.getValue(argPath);
        if (path == null) {
            // display mount information
            if (!ctx.getVaultFsApp().isMounted()) {
                VaultFsApp.log.info("Not mounted.");
            } else {
                Table t = new Table(2);
                t.addRow("      User:", ctx.getVaultFsApp().getProperty(VaultFsApp.KEY_USER));
                t.addRow(" Workspace:", ctx.getVaultFsApp().getProperty(VaultFsApp.KEY_WORKSPACE));
                t.addRow("Mountpoint:", ctx.getVaultFsApp().getProperty(VaultFsApp.KEY_MOUNTPOINT));
                t.print();
            }
        } else {
            if (!ctx.getVaultFsApp().isLoggedIn()) {
                VaultFsApp.log.info("Not logged in.");
            } else {
                ctx.getVaultFsApp().mount(
                        null,
                        null,
                        path,
                        (String) cl.getValue(optConfigFile),
                        (String) cl.getValue(optFilterFile),
                        cl.hasOption(optForce));
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public String getShortDescription() {
        return "Mount a Vault filesystem.";
    }

    private Option optForce;
    private Option optConfigFile;
    private Option optFilterFile;
    private Argument argPath;

    protected Command createCommand() {
        return new CommandBuilder()
                .withName("mount")
                .withDescription(getShortDescription())
                .withChildren(new GroupBuilder()
                        .withName("Options:")
                        .withOption(optForce = new DefaultOptionBuilder()
                                .withShortName("f")
                                .withLongName("force")
                                .withDescription("force remount if already mounted")
                                .create())
                        .withOption(optConfigFile = new DefaultOptionBuilder()
                                .withLongName("file")
                                .withDescription("config.xml for jcrfs")
                                .withArgument(new ArgumentBuilder()
                                        .withName("file")
                                        .withMinimum(0)
                                        .withMaximum(1)
                                        .create())
                                .create())
                        .withOption(optFilterFile = new DefaultOptionBuilder()
                                .withLongName("filter")
                                .withDescription("filter.xml for jcrfs")
                                .withArgument(new ArgumentBuilder()
                                        .withName("filter")
                                        .withMinimum(0)
                                        .withMaximum(1)
                                        .create())
                                .create())
                        .withOption(argPath = new ArgumentBuilder()
                                .withName("root")
                                .withDescription("the repository path that forms the mount root")
                                .withMinimum(0)
                                .withMaximum(1)
                                .create()
                        )
                        .create()
                )
                .create();
    }

    /**
     * {@inheritDoc}
     */
    public String getHelp() {
        return "Synopsis:\n" +
               "    mount [option] <path>\n"+
               "\n" +
               "Description:\n" +
               "    Mounts a Vault filesystem. If <path> is omitted it lists\n" +
                "   the current mountpoint.\n"+
                "\n" +
               "Options:\n"+
               "    -c <user:pwd>  credentials for mount.\n" +
               "    -w <workspace> workspace for mount\n" +
               "    -u             force remount if already mounted.\n" +
               "    -f <file>      config.xml.\n";
    }
}