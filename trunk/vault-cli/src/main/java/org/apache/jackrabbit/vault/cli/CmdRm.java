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
import org.apache.jackrabbit.vault.fs.impl.AggregateImpl;
import org.apache.jackrabbit.vault.util.console.ConsoleFile;
import org.apache.jackrabbit.vault.util.console.ExecutionException;

/**
 * Implements the 'rm' command.
 *
 */
public class CmdRm extends AbstractJcrFsCommand {

    protected void doExecute(VaultFsConsoleExecutionContext ctx, CommandLine cl) throws Exception {
        String path = (String) cl.getValue(argPath);
        boolean recursive = cl.hasOption(optRecursive);
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

    private Option optRecursive;
    private Argument argPath;

    protected Command createCommand() {
        return new CommandBuilder()
                .withName("rm")
                .withDescription(getShortDescription())
                .withChildren(new GroupBuilder()
                        .withName("Options:")
                        .withOption(optRecursive = new DefaultOptionBuilder()
                                .withShortName("r")
                                .withDescription("remove the directory artifacts recursively")
                                .create())
                        .withOption(argPath = new ArgumentBuilder()
                                .withName("path")
                                .withDescription("the jcrfs path")
                                .withMinimum(1)
                                .withMaximum(1)
                                .create()
                        )
                        .create()
                )
                .create();
    }

}