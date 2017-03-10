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

import javax.jcr.RepositoryException;

import org.apache.commons.cli2.Argument;
import org.apache.commons.cli2.CommandLine;
import org.apache.commons.cli2.builder.ArgumentBuilder;
import org.apache.commons.cli2.builder.CommandBuilder;
import org.apache.commons.cli2.builder.GroupBuilder;
import org.apache.commons.cli2.option.Command;
import org.apache.jackrabbit.vault.fs.api.VaultFile;
import org.apache.jackrabbit.vault.util.console.ConsoleFile;
import org.apache.jackrabbit.vault.util.console.ExecutionException;

/**
 * Implements the 'get' command.
 *
 */
public class CmdCat extends AbstractJcrFsCommand {

    protected void doExecute(VaultFsConsoleExecutionContext ctx, CommandLine cl) throws Exception {
        String jcrPath = (String) cl.getValue(argJcrPath);

        ConsoleFile wo = ctx.getFile(jcrPath, true);
        if (wo instanceof VaultFsCFile) {
            VaultFile file = (VaultFile) wo.unwrap();
            try {
                String ct = file.getContentType();
                if (ct == null) {
                    ct = "application/octet-stream";
                }
                if (ct.startsWith("text/")) {
                    file.getArtifact().spool(System.out);
                    System.out.flush();
                } else {
                    System.out.printf("Refusing to print contents of a '%s' file.%n", ct);
                }
            } catch (IOException e) {
                throw new ExecutionException("Error while downloading file.",e);
            } catch (RepositoryException e) {
                throw new ExecutionException("Error while downloading file", e);
            }
        } else {
            throw new ExecutionException("'cat' only possible in jcr fs context");
        }
    }

    /**
     * {@inheritDoc}
     */
    public String getShortDescription() {
        return "Print a file";
    }


    public String getLongDescription() {
        return  "Retrieve a Jcr file from the repository and print its " +
                "content to the console.";

    }

    private Argument argJcrPath;

    protected Command createCommand() {
        return new CommandBuilder()
                .withName("cat")
                .withDescription(getShortDescription())
                .withChildren(new GroupBuilder()
                        .withName("Options:")
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