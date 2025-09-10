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

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.jackrabbit.vault.util.console.util.Table;

/**
 * Implements the 'mount' command.
 *
 */
public class CmdMount extends AbstractJcrFsCommand {
    private Option optForce;
    private Option optConfigFile;
    private Option optFilterFile;
    private Option optPath;
    private Options options;

    public CmdMount() {
        options = new Options();
        optForce = Option.builder("f")
                .longOpt("force")
                .desc("force remount if already mounted")
                .build();
        options.addOption(optForce);
        optConfigFile = Option.builder()
                .longOpt("file")
                .desc("config.xml for jcrfs")
                .hasArg()
                .build();
        options.addOption(optConfigFile);
        optFilterFile = Option.builder()
                .longOpt("filter")
                .desc("filter.xml for jcrfs")
                .hasArg()
                .build();
        options.addOption(optFilterFile);
        optPath = Option.builder()
                .argName("root")
                .desc("the repository path that forms the mount root")
                .hasArg()
                .build();
        options.addOption(optPath);
    }

    protected void doExecute(VaultFsConsoleExecutionContext ctx, CommandLine cl) throws Exception {
        String path = cl.getOptionValue(optPath.getOpt());
        if (path == null) {
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
                        cl.getOptionValue(optConfigFile.getOpt()),
                        cl.getOptionValue(optFilterFile.getOpt()),
                        cl.hasOption(optForce.getOpt()));
            }
        }
    }

    public String getShortDescription() {
        return "Mount a Vault filesystem.";
    }

    public Options getOptions() {
        return options;
    }

    public void printHelp() {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("mount", options);
    }
}