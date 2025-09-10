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
import org.apache.jackrabbit.vault.vlt.actions.Info;

/**
 * Implements the 'info' command.
 *
 */
public class CmdInfo extends AbstractVaultCommand {

    private Option optRecursive;
    private Option optLocalPath;
    private Options options;

    @SuppressWarnings("unchecked")
    protected void doExecute(VaultFsApp app, CommandLine cl) throws Exception {
        String[] localPathsArr = cl.getOptionValues(optLocalPath.getOpt());
        List<String> localPaths = localPathsArr == null ? java.util.Collections.emptyList() : java.util.Arrays.asList(localPathsArr);
        List<File> localFiles = app.getPlatformFiles(localPaths, false);
        File localDir = app.getPlatformFile("", true);

        VltContext vCtx = app.createVaultContext(localDir);
        vCtx.setVerbose(cl.hasOption(OPT_VERBOSE.getOpt()));
        vCtx.setQuiet(cl.hasOption(OPT_QUIET.getOpt()));
        Info u = new Info(localDir, localFiles, !cl.hasOption(optRecursive.getOpt()));
        vCtx.execute(u);
    }

    /**
     * {@inheritDoc}
     */
    public String getShortDescription() {
        return "Displays information about a local file.";
    }

    public CmdInfo() {
        options = new Options();
        options.addOption(OPT_VERBOSE);
        options.addOption(OPT_QUIET);
        optRecursive = Option.builder("R")
                .longOpt("recursive")
                .desc("operate recursive")
                .build();
        options.addOption(optRecursive);
        optLocalPath = Option.builder()
                .argName("file")
                .desc("file or directory to display info")
                .hasArgs()
                .build();
        options.addOption(optLocalPath);
    }

    public Options getOptions() {
        return options;
    }

    public void printHelp() {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("info", options);
    }
}