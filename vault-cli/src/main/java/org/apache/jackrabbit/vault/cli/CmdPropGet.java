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

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.jackrabbit.vault.vlt.VltContext;
import org.apache.jackrabbit.vault.vlt.actions.PropGet;

/**
 * Implements the 'export' command.
 *
 */
public class CmdPropGet extends AbstractVaultCommand {
    private Option optRecursive;
    private Option optLocalPath;
    private Option optPropName;
    private Options options;

    public CmdPropGet() {
        options = new Options();
        options.addOption(OPT_QUIET);
        optRecursive = Option.builder("R")
                .longOpt("recursive")
                .desc("descend recursively")
                .build();
        options.addOption(optRecursive);
        optPropName = Option.builder()
                .argName("propname")
                .desc("the property name")
                .hasArg()
                .required()
                .build();
        options.addOption(optPropName);
        optLocalPath = Option.builder()
                .argName("file")
                .desc("file or directory to get the property from")
                .hasArg()
                .required()
                .build();
        options.addOption(optLocalPath);
    }

    protected void doExecute(VaultFsApp app, CommandLine cl) throws Exception {
        String localPath = cl.getOptionValue(optLocalPath.getOpt());
        File localFile = app.getPlatformFile(localPath, false);
        File localDir = app.getPlatformFile("", true);
        VltContext vCtx = app.createVaultContext(localDir);
        vCtx.setQuiet(cl.hasOption(OPT_QUIET.getOpt()));
        PropGet a = new PropGet(localDir,
            List.of(localFile),
            !cl.hasOption(optRecursive.getOpt()),
            cl.getOptionValue(optPropName.getOpt()));
        vCtx.execute(a);
    }

    public String getShortDescription() {
        return "Print the value of a property on files or directories.";
    }

    public Options getOptions() {
        return options;
    }

    public void printHelp() {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("propget", options);
    }
}