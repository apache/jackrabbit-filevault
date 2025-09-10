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
import java.util.Locale;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.jackrabbit.vault.fs.api.RepositoryAddress;
import org.apache.jackrabbit.vault.util.console.ExecutionException;
import org.apache.jackrabbit.vault.vlt.VltContext;
import org.apache.jackrabbit.vault.vlt.actions.Sync;

/**
 */
public class CmdSync extends AbstractVaultCommand {

    private Option optUri;

    private Option optForce;

    private Option argCommand;

    private Option argLocalPath;

    private Options options;

    protected void doExecute(VaultFsConsoleExecutionContext ctx, CommandLine cl)
            throws Exception {
        throw new ExecutionException("internal error. command not supported in console");
    }

    protected void doExecute(VaultFsApp app, CommandLine cl) throws Exception {
        String cmdStr = cl.getOptionValue("command");
        Sync.Command cmd = null;
        if (cmdStr != null) {
            try {
                cmd = Sync.Command.valueOf(cmdStr.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new ExecutionException("Invalid command: " + cmdStr);
            }
        }
        if (cmd == null) {
            cmd = Sync.Command.INIT;
        }
        String root = cl.getOptionValue("uri");
        RepositoryAddress addr = root == null ? null : new RepositoryAddress(root);

        String localPath = cl.getOptionValue("localPath");
        File localFile = app.getPlatformFile(localPath == null ? "." : localPath, false).getCanonicalFile();
        VltContext vCtx = app.createVaultContext(localFile);
        vCtx.setVerbose(cl.hasOption(OPT_VERBOSE.getOpt()));
        vCtx.setQuiet(cl.hasOption(OPT_QUIET.getOpt()));
        Sync sc = new Sync(cmd, addr, localFile);
        sc.setForce(cl.hasOption(optForce.getOpt()));
        vCtx.execute(sc);
    }

    /**
     * {@inheritDoc}
     */
    public String getShortDescription() {
        return "Control vault sync service";
    }

    public String getLongDescription() {
        return  "Allows to control the vault sync service. Without any arguments this command " +
                "tries to put the current CWD under sync control. If executed within a vlt checkout, " +
                "it uses the respective filter and host to configure the syncing. If executed outside of " +
                "a vlt checkout, it registers synchronization only if the directory is empty.\n\n" +
                "Subcommands: \n" +
                "  install      Installs the Vault Sync service on the remote server\n" +
                "  status (st)  Display status information.\n" +
                "  register     Register a new sync directory\n" +
                "  unregister   Unregisters a sync directory\n" +
                "\n" +
                "Most of the commands take an optional local path as argument which then specifies the sync directory. " +
                "If the --uri is omitted it is auto-detected from the current vault checkout.\n" +
                "\n" +
                "Note: the vault sync service creates a .vlt-sync-config.properties in the sync directory. See the inline " +
                "comments for further options." +
                "\n\n" +
                "Examples:\n" +
                "\n" +
                "Listing sync roots of a server:\n" +
                "  vlt --credentials admin:admin sync --uri http://localhost:8080/crx status\n" +
                "\n" +
                "Add the current CWD as sync directory:\n" +
                "  vlt sync register";
    }

    public CmdSync() {
        options = new Options();
        options.addOption(OPT_VERBOSE);
        optForce = Option.builder()
                .longOpt("force")
                .desc("force certain commands to execute.")
                .build();
        options.addOption(optForce);
        optUri = Option.builder("u")
                .longOpt("uri")
                .desc("Specifies the URI of the sync host.")
                .hasArg()
                .argName("uri")
                .build();
        options.addOption(optUri);
        argCommand = Option.builder()
                .argName("command")
                .desc("Sync Command")
                .hasArg()
                .build();
        options.addOption(argCommand);
        argLocalPath = Option.builder()
                .argName("localPath")
                .desc("local path (optional)")
                .hasArg()
                .build();
        options.addOption(argLocalPath);
    }

    public Options getOptions() {
        return options;
    }
}