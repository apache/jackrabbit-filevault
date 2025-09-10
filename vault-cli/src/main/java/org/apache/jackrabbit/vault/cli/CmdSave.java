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

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.jackrabbit.vault.util.console.ConsoleFile;
import org.apache.jackrabbit.vault.util.console.ExecutionException;

/**
 * Implements the 'save' command.
 *
 */
public class CmdSave extends AbstractJcrFsCommand {

    private Option argJcrPath;
    private Options options;

    public CmdSave() {
        options = new Options();
        argJcrPath = Option.builder()
                .argName("jcr-path")
                .desc("the jcr path")
                .hasArg()
                .build();
        options.addOption(argJcrPath);
    }

    protected void doExecute(VaultFsConsoleExecutionContext ctx, CommandLine cl)
            throws Exception {
        String jcrPath = cl.getOptionValue("jcr-path");
        if (jcrPath == null) {
            jcrPath = "/";
        }
        ConsoleFile wo = ctx.getFile(jcrPath, true);
        if (wo instanceof RepositoryCFile) {
            Node node = (Node) wo.unwrap();
            try {
                node.getSession().save();
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

    public Options getOptions() {
        return options;
    }

}