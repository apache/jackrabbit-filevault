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
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.jackrabbit.vault.vlt.VltContext;
import org.apache.jackrabbit.vault.vlt.actions.Add;

/**
 * Implements the 'add' command.
 *
 */
public class CmdAdd extends AbstractVaultCommand {

    private Option optForce;
    private Option optNonRecursive;
    private Option optLocalPath;
    private Options options;

    public CmdAdd() {
        options = new Options();
        options.addOption(OPT_VERBOSE);
        options.addOption(OPT_QUIET);
        optNonRecursive = Option.builder("N")
                .longOpt("non-recursive")
                .desc("operate on single directory")
                .build();
        options.addOption(optNonRecursive);
        optForce = Option.builder()
                .longOpt("force")
                .desc("force operation to run")
                .build();
        options.addOption(optForce);
        optLocalPath = Option.builder()
                .argName("file")
                .desc("local file or directory to add")
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
        vCtx.setVerbose(cl.hasOption(OPT_VERBOSE.getOpt()));
        vCtx.setQuiet(cl.hasOption(OPT_QUIET.getOpt()));
        Add a = new Add(localDir, List.of(localFile), cl.hasOption(optNonRecursive.getOpt()), cl.hasOption(optForce.getOpt()));
        vCtx.execute(a);
    }

    /**
     * {@inheritDoc}
     */
    public String getShortDescription() {
        return "Put files and directories under version control.";
    }

    /**
     * {@inheritDoc}
     */
    public String getLongDescription() {
        return "Put files and directories under version control, scheduling " +
               "them for addition to repository. They will be added in next commit.";
    }

    public Options getOptions() {
        return options;
    }

    public void printHelp() {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("add", options);
    }
}