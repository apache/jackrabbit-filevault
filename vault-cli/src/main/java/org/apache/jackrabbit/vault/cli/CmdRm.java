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
import org.apache.commons.cli.HelpFormatter;
import org.apache.jackrabbit.vault.fs.impl.AggregateImpl;
import org.apache.jackrabbit.vault.util.console.ConsoleFile;
import org.apache.jackrabbit.vault.util.console.ExecutionException;

/**
 * Implements the 'rm' command.
 *
 */
public class CmdRm extends AbstractJcrFsCommand {

    private Option optRecursive;
    private Options options;

    public CmdRm() {
        options = new Options();
        optRecursive = Option.builder("r")
                .longOpt("recursive")
                .desc("remove the directory artifacts recursively")
                .build();
        options.addOption(optRecursive);
    }

    protected void doExecute(VaultFsConsoleExecutionContext ctx, CommandLine cl) throws Exception {
        String[] args = cl.getArgs();
        String path = (args != null && args.length > 0) ? args[0] : null;
        boolean recursive = cl.hasOption(optRecursive.getOpt());
        ConsoleFile file = ctx.getFile(path, true);
        if (file instanceof AggregateCFile) {
            AggregateImpl node = (AggregateImpl) file.unwrap();
            node.remove(recursive);
        } else {
            throw new ExecutionException("remove only allowed in afct mode.");
        }
    }

    /**
     * {@inheritDoc}
     */
    public String getShortDescription() {
        return "Remove a file";
    }

    public String getLongDescription() {
        return "Removes the file at the given <path>. If the file contains " +
                "a directory artifact, only the non-directory artifacts are " +
                "removed unless -r is specified.";
    }

    public Options getOptions() { return options; }
    public void printHelp() { new HelpFormatter().printHelp("rm", options); }
}