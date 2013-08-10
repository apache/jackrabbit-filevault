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

import java.util.Iterator;
import java.util.List;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.nodetype.NodeType;

import org.apache.commons.cli2.Argument;
import org.apache.commons.cli2.CommandLine;
import org.apache.commons.cli2.Option;
import org.apache.commons.cli2.builder.ArgumentBuilder;
import org.apache.commons.cli2.builder.CommandBuilder;
import org.apache.commons.cli2.builder.DefaultOptionBuilder;
import org.apache.commons.cli2.builder.GroupBuilder;
import org.apache.commons.cli2.option.Command;
import org.apache.jackrabbit.vault.util.console.ConsoleFile;
import org.apache.jackrabbit.vault.util.console.ExecutionException;

/**
 * Implements the 'get' command.
 *
 */
public class CmdMixins extends AbstractJcrFsCommand {

    protected void doExecute(VaultFsConsoleExecutionContext ctx, CommandLine cl)
            throws Exception {
        String jcrPath = (String) cl.getValue(argJcrPath);

        List added = cl.getValues(optAdd);
        List rems = cl.getValues(optRemove);

        ConsoleFile wo = ctx.getFile(jcrPath, true);
        if (wo instanceof RepositoryCFile) {
            Node node = (Node) wo.unwrap();
            try {
                for (Iterator it = added.iterator(); it.hasNext();) {
                    node.addMixin((String) it.next());
                }
                for (Iterator it = rems.iterator(); it.hasNext();) {
                    node.removeMixin((String) it.next());
                }
                String delim = "Mixins: ";
                for (NodeType nt: node.getMixinNodeTypes()) {
                    System.out.print(delim);
                    System.out.print(nt.getName());
                    delim = ", ";
                }
                System.out.println();
            } catch (RepositoryException e) {
                throw new ExecutionException("Error while downloading file: " + e);
            }
        } else {
            throw new ExecutionException("'mixins' only possible in repcontext");
        }
    }

    /**
     * {@inheritDoc}
     */
    public String getShortDescription() {
        return "Prints or manipulates the mixin node types of a repository node.";
    }


    public String getLongDescription() {
        return  "Prints or manipulates the mixin node types of a repository node.\n" +
                "\n" +
                "Usage: mixins <jcr-path> {-a nodetype} {-r nodetype}\n" +
                "\n" +
                "Please note that the changes are not saved. use the 'save' command " +
                "to persist the changes.";

    }

    private Argument argJcrPath;

    private Option optAdd;

    private Option optRemove;

    protected Command createCommand() {
        return new CommandBuilder()
                .withName("mixins")
                .withDescription(getShortDescription())
                .withChildren(new GroupBuilder()
                        .withName("Options:")
                        .withOption(optAdd = new DefaultOptionBuilder()
                                .withShortName("a")
                                .withLongName("add")
                                .withDescription("adds a mixin")
                                .withArgument(new ArgumentBuilder()
                                        .withName("nodetype")
                                        .withMinimum(1)
                                        .create())
                                .create())
                        .withOption(optRemove = new DefaultOptionBuilder()
                                .withShortName("r")
                                .withLongName("remove")
                                .withDescription("removes a mixin")
                                .withArgument(new ArgumentBuilder()
                                        .withName("nodetype")
                                        .withMinimum(1)
                                        .create())
                                .create())

                        .withOption(argJcrPath = new ArgumentBuilder()
                                .withName("jcr-path")
                                .withDescription("the jcr path")
                                .withMinimum(1)
                                .withMaximum(1)
                                .create()
                        )
                        .create()
                )
                .create();
    }

}