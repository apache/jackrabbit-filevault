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
import org.apache.jackrabbit.vault.vlt.actions.PropList;

/**
 * Implements the 'proplist' command.
 *
 */
public class CmdPropList extends AbstractVaultCommand {

    private Option optRecursive;
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
        vCtx.setQuiet(cl.hasOption(OPT_QUIET.getOpt()));
        PropList a = new PropList(localDir,
                localFiles,
                !cl.hasOption(optRecursive.getOpt()));
        vCtx.execute(a);
    }

    /**
     * {@inheritDoc}
     */
    public String getShortDescription() {
        return "Print the properties on files or directories.";
    }

    public CmdPropList() {
        options = new Options();
        options.addOption(OPT_QUIET);
        optRecursive = Option.builder("R")
                .longOpt("recursive")
                .desc("descend recursively")
                .build();
        options.addOption(optRecursive);
        argLocalPath = Option.builder()
                .argName("file")
                .desc("file or directory to list the properties from")
                .hasArgs()
                .build();
        options.addOption(argLocalPath);
    }

    public Options getOptions() {
        return options;
    }
}