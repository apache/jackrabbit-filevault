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
import org.apache.commons.cli.Options;
import org.apache.commons.cli.HelpFormatter;
import org.apache.jackrabbit.vault.fs.api.VaultFile;
import org.apache.jackrabbit.vault.util.console.ConsoleFile;
import org.apache.jackrabbit.vault.util.console.ExecutionException;

/**
 * Implements the 'get' command.
 *
 */
public class CmdInvalidate extends AbstractJcrFsCommand {

     protected void doExecute(VaultFsConsoleExecutionContext ctx, CommandLine cl) throws Exception {
        String[] args = cl.getArgs();
        String jcrPath = (args != null && args.length > 0) ? args[0] : null;

        ConsoleFile wo = ctx.getFile(jcrPath, true);
        if (wo instanceof VaultFsCFile) {
            VaultFile file = (VaultFile) wo.unwrap();
            file.invalidate();
        } else {
            throw new ExecutionException("'cat' only possible in jcr fs context");
        }
    }

    /**
     * {@inheritDoc}
     */
    public String getShortDescription() {
        return "Invalidate a file";
    }


    public String getLongDescription() {
        return  "Invalidates a file and it's related.";

    }

    private Options options;

    public CmdInvalidate() {
        options = new Options();
    }

    public Options getOptions() { return options; }
    public void printHelp() { new HelpFormatter().printHelp("invalidate", options); }
}