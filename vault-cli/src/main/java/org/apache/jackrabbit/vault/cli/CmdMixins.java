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

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.HelpFormatter;
import org.apache.jackrabbit.vault.util.console.ConsoleFile;
import org.apache.jackrabbit.vault.util.console.ExecutionException;

/**
 * Implements the 'get' command.
 *
 */
public class CmdMixins extends AbstractJcrFsCommand {

    protected void doExecute(VaultFsConsoleExecutionContext ctx, CommandLine cl)
            throws Exception {
        String[] args = cl.getArgs();
        String jcrPath = args != null && args.length > 0 ? args[0] : null;

        String[] addedArr = cl.getOptionValues(optAdd.getOpt());
        String[] remsArr = cl.getOptionValues(optRemove.getOpt());
        List added = addedArr == null ? java.util.Collections.emptyList() : java.util.Arrays.asList(addedArr);
        List rems = remsArr == null ? java.util.Collections.emptyList() : java.util.Arrays.asList(remsArr);

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

    private Option optAdd;

    private Option optRemove;
    private Options options;

    public CmdMixins() {
        options = new Options();
        optAdd = Option.builder("a")
                .longOpt("add")
                .desc("adds a mixin")
                .hasArgs()
                .build();
        options.addOption(optAdd);
        optRemove = Option.builder("r")
                .longOpt("remove")
                .desc("removes a mixin")
                .hasArgs()
                .build();
        options.addOption(optRemove);
    }

    public Options getOptions() { return options; }
    public void printHelp() { new HelpFormatter().printHelp("mixins [options] <jcr-path>", options); }
}