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

import org.apache.commons.cli2.Argument;
import org.apache.commons.cli2.CommandLine;
import org.apache.commons.cli2.Option;
import org.apache.commons.cli2.builder.ArgumentBuilder;
import org.apache.commons.cli2.builder.CommandBuilder;
import org.apache.commons.cli2.builder.DefaultOptionBuilder;
import org.apache.commons.cli2.builder.GroupBuilder;
import org.apache.commons.cli2.option.Command;
import org.apache.commons.cli2.validation.NumberValidator;
import org.apache.jackrabbit.vault.util.console.ConsoleFile;

/**
 * Implements the 'tree' command.
 *
 */
public class CmdTree extends AbstractJcrFsCommand {

    protected void doExecute(VaultFsConsoleExecutionContext ctx, CommandLine cl) throws Exception {
        String path = (String) cl.getValue(argPath);
        int depth = Integer.MAX_VALUE;
        if (cl.hasOption(optRecursive)) {
            depth = ((Long) cl.getValue(optRecursive)).intValue();
        }
        ConsoleFile file = ctx.getFile(path, true);
        tree(file, depth, "");
    }

    /**
     * {@inheritDoc}
     */
    public String getShortDescription() {
        return "Provide a tree-dump of files";
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

    private Option optRecursive;
    private Argument argPath;

    protected Command createCommand() {
        return new CommandBuilder()
                .withName("tree")
                .withDescription(getShortDescription())
                .withChildren(new GroupBuilder()
                        .withName("Options:")
                        .withOption(optRecursive = new DefaultOptionBuilder()
                                .withShortName("r")
                                .withDescription("limit depth")
                                .withArgument(new ArgumentBuilder()
                                        .withName("depth")
                                        .withDescription("limit tree to <depth>")
                                        .withMinimum(1)
                                        .withMaximum(1)
                                        .withValidator(NumberValidator.getIntegerInstance())
                                        .create())
                                .create())
                        .withOption(argPath = new ArgumentBuilder()
                                        .withName("path")
                                        .withDescription("the path of the tree")
                                        .withMinimum(0)
                                        .withMaximum(1)
                                        .create())
                        .create())
                .create();
    }

}