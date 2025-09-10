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

import java.io.IOException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.jackrabbit.vault.util.console.ConsoleFile;

/**
 * Implements the 'tree' command.
 *
 */
public class CmdTree extends AbstractJcrFsCommand {
    private Option optRecursive;
    private Option optPath;
    private Options options;

    public CmdTree() {
        options = new Options();
        optRecursive = Option.builder("r")
                .longOpt("recursive")
                .desc("depth to recurse")
                .hasArg()
                .build();
        options.addOption(optRecursive);
        optPath = Option.builder()
                .argName("path")
                .desc("the path to display")
                .hasArg()
                .required()
                .build();
        options.addOption(optPath);
    }

    protected void doExecute(VaultFsConsoleExecutionContext ctx, CommandLine cl) throws Exception {
        String path = cl.getOptionValue(optPath.getOpt());
        int depth = Integer.MAX_VALUE;
        if (cl.hasOption(optRecursive.getOpt())) {
            depth = Integer.parseInt(cl.getOptionValue(optRecursive.getOpt()));
        }
        ConsoleFile file = ctx.getFile(path, true);
        tree(file, depth, "");
    }

    /**
     * {@inheritDoc}
     */
    public String getShortDescription() {
        return "Display the tree structure of a path.";
    }

    public Options getOptions() {
        return options;
    }

    public void printHelp() {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("tree", options);
    }

    /**
     * Provides a tree dump
     */
    private void tree(ConsoleFile file, int depth, String indent)
            throws IOException {
        if (file.allowsChildren()) {
            ConsoleFile[] files = file.listFiles();
            if (files.length > 0) {
                for (int i = 0; i < files.length; i++) {
                    String pfx = i == files.length -1 ? "`" : "|";
                    System.out.println(indent + pfx + "-- " + files[i].getName());
                    String ind = i == files.length -1 ? "    " : "|   ";
                    if (depth > 0) {
                        tree(files[i], depth -1, indent + ind);
                    }
                }
            }
        }
    }

}