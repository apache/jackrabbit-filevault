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
import org.apache.jackrabbit.vault.vlt.actions.Action;
import org.apache.jackrabbit.vault.vlt.actions.RemoteStatus;
import org.apache.jackrabbit.vault.vlt.actions.Status;

/**
 * Implements the 'status' command.
 *
 */
public class CmdStatus extends AbstractVaultCommand {

    private Option optOnlyControlled;
    private Option optShowUpdate;
    private Option optNonRecursive;
    private Option argLocalPath;
    private Options options;

    @SuppressWarnings("unchecked")
    protected void doExecute(VaultFsApp app, CommandLine cl) throws Exception {
        String[] localPaths = cl.getOptionValues("file");
        List<String> localPathList = new ArrayList<String>();
        if (localPaths != null) {
            localPathList = Arrays.asList(localPaths);
        }
        List<File> localFiles = app.getPlatformFiles(localPathList, false);
        File localDir = app.getPlatformFile("", true);

        VltContext vCtx = app.createVaultContext(localDir);
        vCtx.setVerbose(cl.hasOption(OPT_VERBOSE.getOpt()));
        vCtx.setQuiet(cl.hasOption(OPT_QUIET.getOpt()));
        Action a = cl.hasOption(optShowUpdate.getOpt())
                ? new RemoteStatus(localDir, localFiles, cl.hasOption(optNonRecursive.getOpt()))
                : new Status(localDir, localFiles, cl.hasOption(optNonRecursive.getOpt()));
        vCtx.execute(a);
    }

    /**
     * {@inheritDoc}
     */
    public String getShortDescription() {
        return "Print the status of working copy files and directories.";
    }


    /**
     * {@inheritDoc}
     */
    public String getLongDescription() {
        return "Print the status of working copy files and directories.\n" +
                "\n" +
                "If --show-update is specified, each file is checked against " +
                "the remote version. the second letter then specifies what " +
                "action would be performed by an update operation.";
    }

    public CmdStatus() {
        options = new Options();
        options.addOption(OPT_VERBOSE);
        options.addOption(OPT_QUIET);
        optOnlyControlled = Option.builder("q")
                .longOpt("quiet")
                .desc("show only status of controlled files")
                .build();
        // note: OPT_QUIET already added; keep compatibility
        optShowUpdate = Option.builder("u")
                .longOpt("show-update")
                .desc("display update information")
                .build();
        options.addOption(optShowUpdate);
        optNonRecursive = Option.builder("N")
                .longOpt("non-recursive")
                .desc("operate on single directory")
                .build();
        options.addOption(optNonRecursive);
        argLocalPath = Option.builder()
                .argName("file")
                .desc("file or directory to display the status")
                .hasArgs()
                .build();
        options.addOption(argLocalPath);
    }

    public Options getOptions() {
        return options;
    }
}