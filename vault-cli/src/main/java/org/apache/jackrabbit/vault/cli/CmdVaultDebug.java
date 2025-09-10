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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.jackrabbit.vault.vlt.VltContext;
import org.apache.jackrabbit.vault.vlt.actions.Debug;

/**
 * Implements the 'vltdebug' command.
 *
 */
public class CmdVaultDebug extends AbstractVaultCommand {

    private Option argCommand;
    private Options options;

    @SuppressWarnings("unchecked")
    protected void doExecute(VaultFsApp app, CommandLine cl) throws Exception {
        File localDir = app.getPlatformFile("", true);
        String[] commands = cl.getOptionValues("cmd");
        List<String> cmds = new ArrayList<String>();
        if (commands != null) {
            cmds = Arrays.asList(commands);
        }
        for (String cmd: cmds) {
            if (cmd.equals("binary")) {
                Debug dbg = new Debug(localDir);
                VltContext vCtx = app.createVaultContext(localDir);
                vCtx.setVerbose(cl.hasOption(OPT_VERBOSE.getOpt()));
                vCtx.setQuiet(cl.hasOption(OPT_QUIET.getOpt()));
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

    public CmdVaultDebug() {
        options = new Options();
        options.addOption(OPT_VERBOSE);
        options.addOption(OPT_QUIET);
        argCommand = Option.builder()
                .argName("cmd")
                .desc("command")
                .hasArgs()
                .build();
        options.addOption(argCommand);
    }

    public Options getOptions() {
        return options;
    }

}