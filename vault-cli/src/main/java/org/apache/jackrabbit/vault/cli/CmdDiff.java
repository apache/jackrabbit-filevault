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
import org.apache.jackrabbit.vault.vlt.actions.Diff;

/**
 * Implements the 'diff' command.
 *
 */
public class CmdDiff extends AbstractVaultCommand {

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
        Diff a = new Diff(localDir, localFiles, cl.hasOption(optNonRecursive.getOpt()));
        vCtx.execute(a);
    }

    /**
     * {@inheritDoc}
     */
    public String getShortDescription() {
        return "Display the differences between two paths.";
    }

    public CmdDiff() {
        options = new Options();
        optNonRecursive = Option.builder("N")
                .longOpt("non-recursive")
                .desc("operate on single directory")
                .build();
        options.addOption(optNonRecursive);
        argLocalPath = Option.builder()
                .argName("file")
                .desc("file or directory to display the diffs from")
                .hasArgs()
                .build();
        options.addOption(argLocalPath);
        options.addOption(OPT_VERBOSE);
    }

    public Options getOptions() {
        return options;
    }
}