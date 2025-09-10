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

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.api.RepositoryAddress;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.fs.filter.DefaultPathFilter;
import org.apache.jackrabbit.vault.util.DefaultProgressListener;
import org.apache.jackrabbit.vault.util.RepositoryCopier;

/**
 * Implements the 'rcp' command.
 *
 */
public class CmdRcp extends AbstractVaultCommand {

    protected void doExecute(VaultFsApp app, CommandLine cl) throws Exception {
        boolean quiet = cl.hasOption(OPT_QUIET.getOpt());
        RepositoryAddress src = new RepositoryAddress(cl.getOptionValue("src"));
        RepositoryAddress dst = new RepositoryAddress(cl.getOptionValue("dst"));
        boolean recursive = cl.hasOption("r");
        RepositoryCopier rcp = new RepositoryCopier();
        if (!quiet) {
            rcp.setTracker(new DefaultProgressListener());
        }
        if (cl.hasOption("b")) {
            rcp.setBatchSize(Integer.parseInt(cl.getOptionValue("b")));
        }
        if (cl.hasOption("t")) {
            rcp.setThrottle(Integer.parseInt(cl.getOptionValue("t")));
        }
        if (cl.hasOption("R")) {
            rcp.setResumeFrom(cl.getOptionValue("R"));
        }
        rcp.setUpdate(cl.hasOption("u"));
        rcp.setOnlyNewer(cl.hasOption("n"));
        rcp.setNoOrdering(cl.hasOption("no-ordering"));
        rcp.setCredentialsProvider(app.getCredentialsStore());
        DefaultWorkspaceFilter srcFilter = new DefaultWorkspaceFilter();
        PathFilterSet excludes = new PathFilterSet("/");
        String[] excl = cl.getOptionValues("e");
        if (excl != null) {
            for (String e: excl) {
                excludes.addExclude(new DefaultPathFilter(e));
            }
        }
        srcFilter.add(excludes);
        rcp.setSourceFilter(srcFilter);
        rcp.copy(src, dst, recursive);
    }

    /**
     * {@inheritDoc}
     */
    public String getShortDescription() {
        return "Remote copy of repository content.";
    }

    @Override
    public String getLongDescription() {
        return "Copies a node tree from one remote repository to another. " +
               "Note that <src> points at the source node, and <dst>" +
               "points at the destination path, which parent node must exist.\n" +
                "\n" +
                "Note: Due to bug in command line processing, the --exclude options need " +
                "to be followed by another option before the <src> and <dst> arguments." +
                "\n" +
                "Example:\n" +
                "  vlt rcp -e \".*\\.txt\" -r http://localhost:4502/crx/-/jcr:root/content http://admin:admin@localhost:4503/crx/-/jcr:root/content_copy\n";
    }

    // argument/options
    private Option optRecursive;
    private Option optSize;
    private Option optExclude;
    private Option optThrottle;
    private Option optResumeFrom;
    private Option optUpdate;
    private Option optNewer;
    private Option optNoOrdering;
    private Option srcAddr;
    private Option dstAddr;
    private Options options;

    public CmdRcp() {
        options = new Options();
        options.addOption(OPT_QUIET);
        optRecursive = Option.builder("r")
                .longOpt("recursive")
                .desc("descend recursively")
                .build();
        options.addOption(optRecursive);
        optSize = Option.builder("b")
                .longOpt("batchSize")
                .desc("number of nodes until intermediate save")
                .hasArg()
                .argName("size")
                .build();
        options.addOption(optSize);
        optThrottle = Option.builder("t")
                .longOpt("throttle")
                .desc("number of seconds to wait after an intermediate save")
                .hasArg()
                .argName("seconds")
                .build();
        options.addOption(optThrottle);
        optResumeFrom = Option.builder("R")
                .longOpt("resume")
                .desc("source path to resume operation after a restart")
                .hasArg()
                .argName("path")
                .build();
        options.addOption(optResumeFrom);
        optUpdate = Option.builder("u")
                .longOpt("update")
                .desc("overwrite/delete existing nodes.")
                .build();
        options.addOption(optUpdate);
        optNewer = Option.builder("n")
                .longOpt("newer")
                .desc("respect lastModified properties for update.")
                .build();
        options.addOption(optNewer);
        optExclude = Option.builder("e")
                .longOpt("exclude")
                .desc("regexp of excluded source paths.")
                .hasArgs()
                .build();
        options.addOption(optExclude);
        optNoOrdering = Option.builder()
                .longOpt("no-ordering")
                .desc("disable node ordering for updated content")
                .build();
        options.addOption(optNoOrdering);
        srcAddr = Option.builder()
                .argName("src")
                .desc("the repository address of the source tree")
                .hasArg()
                .build();
        options.addOption(srcAddr);
        dstAddr = Option.builder()
                .argName("dst")
                .desc("the repository address of the destination node")
                .hasArg()
                .build();
        options.addOption(dstAddr);
    }

    public Options getOptions() {
        return options;
    }
}
