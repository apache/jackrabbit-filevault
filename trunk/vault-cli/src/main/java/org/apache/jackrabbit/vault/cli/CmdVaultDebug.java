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
import org.apache.commons.cli2.builder.ArgumentBuilder;
import org.apache.commons.cli2.builder.CommandBuilder;
import org.apache.commons.cli2.builder.GroupBuilder;
import org.apache.commons.cli2.option.Command;
import org.apache.jackrabbit.vault.vlt.VltContext;
import org.apache.jackrabbit.vault.vlt.actions.Debug;

/**
 * Implements the 'export' command.
 *
 */
public class CmdVaultDebug extends AbstractVaultCommand {

    private Argument argCommand;

    @SuppressWarnings("unchecked")
    protected void doExecute(VaultFsApp app, CommandLine cl) throws Exception {
        File localDir = app.getPlatformFile("", true);
        List<String> commands = cl.getValues(argCommand);
        for (String cmd: commands) {
            if (cmd.equals("binary")) {
                Debug dbg = new Debug(localDir);
                VltContext vCtx = app.createVaultContext(localDir);
                vCtx.setVerbose(cl.hasOption(OPT_VERBOSE));
                vCtx.setQuiet(cl.hasOption(OPT_QUIET));
                vCtx.execute(dbg);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public String getShortDescription() {
        return "Debug.";
    }

    protected Command createCommand() {
        return new CommandBuilder()
                .withName("vltdebug")
                .withDescription(getShortDescription())
                .withChildren(new GroupBuilder()
                        .withName("Options:")
                        .withOption(OPT_VERBOSE)
                        .withOption(OPT_QUIET)
                        .withOption(argCommand = new ArgumentBuilder()
                                .withName("cmd")
                                .withDescription("command")
                                .withMinimum(0)
                                .create()
                        )
                        .create()
                )
                .create();
    }
}