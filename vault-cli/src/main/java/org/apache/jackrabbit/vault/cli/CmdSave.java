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

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.apache.commons.cli2.Argument;
import org.apache.commons.cli2.CommandLine;
import org.apache.commons.cli2.builder.ArgumentBuilder;
import org.apache.commons.cli2.builder.CommandBuilder;
import org.apache.commons.cli2.builder.GroupBuilder;
import org.apache.commons.cli2.option.Command;
import org.apache.jackrabbit.vault.util.console.ConsoleFile;
import org.apache.jackrabbit.vault.util.console.ExecutionException;

/**
 * Implements the 'get' command.
 *
 */
public class CmdSave extends AbstractJcrFsCommand {

    protected void doExecute(VaultFsConsoleExecutionContext ctx, CommandLine cl)
            throws Exception {
        String jcrPath = (String) cl.getValue(argJcrPath);
        if (jcrPath == null) {
            jcrPath = "/";
        }
        ConsoleFile wo = ctx.getFile(jcrPath, true);
        if (wo instanceof RepositoryCFile) {
            Node node = (Node) wo.unwrap();
            try {
                node.save();
                System.out.println("Modifications persisted.");
            } catch (RepositoryException e) {
                throw new ExecutionException("Error while saving: " + e);
            }
        } else {
            throw new ExecutionException("'save' only possible in repcontext");
        }
    }

    /**
     * {@inheritDoc}
     */
    public String getShortDescription() {
        return "Saves the repository node.";
    }


    private Argument argJcrPath;

    protected Command createCommand() {
        return new CommandBuilder()
                .withName("save")
                .withDescription(getShortDescription())
                .withChildren(new GroupBuilder()
                        .withName("Options:")
                        .withOption(argJcrPath = new ArgumentBuilder()
                                .withName("jcr-path")
                                .withDescription("the jcr path")
                                .withMinimum(0)
                                .withMaximum(1)
                                .create()
                        )
                        .create()
                )
                .create();
    }

}