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
import java.util.List;

import org.apache.commons.cli2.Argument;
import org.apache.commons.cli2.CommandLine;
import org.apache.commons.cli2.Option;
import org.apache.commons.cli2.builder.ArgumentBuilder;
import org.apache.commons.cli2.builder.CommandBuilder;
import org.apache.commons.cli2.builder.DefaultOptionBuilder;
import org.apache.commons.cli2.builder.GroupBuilder;
import org.apache.commons.cli2.option.Command;
import org.apache.commons.cli2.validation.InvalidArgumentException;
import org.apache.commons.cli2.validation.Validator;
import org.apache.jackrabbit.vault.fs.api.RepositoryAddress;
import org.apache.jackrabbit.vault.util.console.ExecutionException;
import org.apache.jackrabbit.vault.vlt.VltContext;
import org.apache.jackrabbit.vault.vlt.actions.Sync;

/**
 */
public class CmdSync extends AbstractVaultCommand {

    private Option optUri;

    private Option optForce;

    private Argument argCommand;

    private Argument argLocalPath;

    protected void doExecute(VaultFsConsoleExecutionContext ctx, CommandLine cl)
            throws Exception {
        throw new ExecutionException("internal error. command not supported in console");
    }

    protected void doExecute(VaultFsApp app, CommandLine cl) throws Exception {
        Sync.Command cmd = (Sync.Command) cl.getValue(argCommand);
        if (cmd == null) {
            cmd = Sync.Command.INIT;
        }
        String root = (String) cl.getValue(optUri);
        RepositoryAddress addr = root == null
                ? null
                : new RepositoryAddress(root);

        String localPath = (String) cl.getValue(argLocalPath);
        File localFile = app.getPlatformFile(localPath == null ? "." : localPath, false).getCanonicalFile();
        VltContext vCtx = app.createVaultContext(localFile);
        vCtx.setVerbose(cl.hasOption(OPT_VERBOSE));
        vCtx.setQuiet(cl.hasOption(OPT_QUIET));
        Sync sc = new Sync(cmd, addr, localFile);
        sc.setForce(cl.hasOption(optForce));
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

    protected Command createCommand() {
        argCommand = new ArgumentBuilder()
            .withName("command")
            .withDescription("Sync Command")
            .withMinimum(0)
            .withMaximum(1)
            .withValidator(new Validator() {
                public void validate(List list) throws InvalidArgumentException {
                    if (list.size() > 0) {
                        String cmd = list.get(0).toString();
                        try {
                            list.set(0, Sync.Command.valueOf(cmd.toUpperCase()));
                        } catch (IllegalArgumentException e) {
                            throw new InvalidArgumentException("Invalid command: " + cmd);
                        }
                    }
                }
            })
            .create();
        argLocalPath = new ArgumentBuilder()
            .withName("localPath")
            .withDescription("local path (optional)")
            .withMinimum(0)
            .withMaximum(1)
            .create();
        return new CommandBuilder()
                .withName("sync")
                .withDescription(getShortDescription())
                .withChildren(new GroupBuilder()
                        .withName("Options:")
                        .withOption(OPT_VERBOSE)
                        .withOption(optForce = new DefaultOptionBuilder()
                                .withLongName("force")
                                .withDescription("force certain commands to execute.")
                                .create())
                        .withOption(optUri = new DefaultOptionBuilder()
                            .withShortName("u")
                            .withLongName("uri")
                            .withDescription("Specifies the URI of the sync host.")
                            .withArgument(new ArgumentBuilder()
                                    .withName("uri")
                                    .withMaximum(1)
                                    .withMinimum(1)
                                    .create())
                            .create())
                        .withOption(argCommand)
                        .withOption(argLocalPath)
                        .create()
                )
                .create();
    }
}