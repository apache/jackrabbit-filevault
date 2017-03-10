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

import org.apache.commons.cli2.Argument;
import org.apache.commons.cli2.CommandLine;
import org.apache.commons.cli2.Option;
import org.apache.commons.cli2.builder.ArgumentBuilder;
import org.apache.commons.cli2.builder.CommandBuilder;
import org.apache.commons.cli2.builder.DefaultOptionBuilder;
import org.apache.commons.cli2.builder.GroupBuilder;
import org.apache.commons.cli2.option.Command;
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
        boolean quiet = cl.hasOption(OPT_QUIET);
        RepositoryAddress src = new RepositoryAddress((String) cl.getValue(srcAddr));
        RepositoryAddress dst = new RepositoryAddress((String) cl.getValue(dstAddr));
        boolean recursive = cl.hasOption(optRecursive);
        RepositoryCopier rcp = new RepositoryCopier();
        if (!quiet) {
            rcp.setTracker(new DefaultProgressListener());
        }
        if (cl.hasOption(optSize)) {
            rcp.setBatchSize(Integer.parseInt(cl.getValue(optSize).toString()));
        }
        if (cl.hasOption(optThrottle)) {
            rcp.setThrottle(Integer.parseInt(cl.getValue(optThrottle).toString()));
        }
        if (cl.hasOption(optResumeFrom)) {
            rcp.setResumeFrom(cl.getValue(optResumeFrom).toString());
        }
        rcp.setUpdate(cl.hasOption(optUpdate));
        rcp.setOnlyNewer(cl.hasOption(optNewer));
        rcp.setNoOrdering(cl.hasOption(optNoOrdering));
        rcp.setCredentialsProvider(app.getCredentialsStore());
        DefaultWorkspaceFilter srcFilter = new DefaultWorkspaceFilter();
        PathFilterSet excludes = new PathFilterSet("/");
        for (Object e: cl.getValues(optExclude)) {
            excludes.addExclude(new DefaultPathFilter(e.toString()));
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

    private Argument srcAddr;
    private Argument dstAddr;
    private Option optRecursive;
    private Option optSize;
    private Option optExclude;
    private Option optThrottle;
    private Option optResumeFrom;
    private Option optUpdate;
    private Option optNewer;
    private Option optNoOrdering;

    protected Command createCommand() {
        return new CommandBuilder()
                .withName("rcp")
                .withDescription(getShortDescription())
                .withChildren(new GroupBuilder()
                        .withName("Options:")
                        .withOption(OPT_QUIET)
                        .withOption(optRecursive = new DefaultOptionBuilder()
                                .withShortName("r")
                                .withLongName("recursive")
                                .withDescription("descend recursively")
                                .create())
                        .withOption(optSize = new DefaultOptionBuilder()
                                .withShortName("b")
                                .withLongName("batchSize")
                                .withDescription("number of nodes until intermediate save")
                                .withArgument(new ArgumentBuilder()
                                        .withName("size")
                                        .withMinimum(0)
                                        .withMaximum(1)
                                        .create())
                                .create())
                        .withOption(optThrottle = new DefaultOptionBuilder()
                                .withShortName("t")
                                .withLongName("throttle")
                                .withDescription("number of seconds to wait after an intermediate save")
                                .withArgument(new ArgumentBuilder()
                                        .withName("seconds")
                                        .withMinimum(0)
                                        .withMaximum(1)
                                        .create())
                                .create())
                        .withOption(optResumeFrom = new DefaultOptionBuilder()
                                .withShortName("R")
                                .withLongName("resume")
                                .withDescription("source path to resume operation after a restart")
                                .withArgument(new ArgumentBuilder()
                                        .withName("path")
                                        .withMinimum(0)
                                        .withMaximum(1)
                                        .create())
                                .create())
                        .withOption(optUpdate = new DefaultOptionBuilder()
                                .withShortName("u")
                                .withLongName("update")
                                .withDescription("overwrite/delete existing nodes.")
                                .create())
                        .withOption(optNewer = new DefaultOptionBuilder()
                                .withShortName("n")
                                .withLongName("newer")
                                .withDescription("respect lastModified properties for update.")
                                .create())
                        .withOption(optExclude = new DefaultOptionBuilder()
                                .withShortName("e")
                                .withLongName("exclude")
                                .withDescription("regexp of excluded source paths.")
                                .withArgument(new ArgumentBuilder()
                                        .withMinimum(0)
                                        .create())
                                .create())
                        .withOption(optNoOrdering = new DefaultOptionBuilder()
                                .withLongName("no-ordering")
                                .withDescription("disable node ordering for updated content")
                                .create())
                        .withOption(srcAddr = new ArgumentBuilder()
                                .withName("src")
                                .withDescription("the repository address of the source tree")
                                .withMinimum(1)
                                .withMaximum(1)
                                .create()
                        )
                        .withOption(dstAddr = new ArgumentBuilder()
                                .withName("dst")
                                .withDescription("the repository address of the destination node")
                                .withMinimum(1)
                                .withMaximum(1)
                                .create()
                        )
                        .create()
                )
                .create();
    }

}