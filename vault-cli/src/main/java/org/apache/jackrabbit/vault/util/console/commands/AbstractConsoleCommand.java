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
package org.apache.jackrabbit.vault.util.console.commands;

import org.apache.commons.cli2.CommandLine;
import org.apache.jackrabbit.vault.util.console.ConsoleCommand;
import org.apache.jackrabbit.vault.util.console.ConsoleExecutionContext;
import org.apache.jackrabbit.vault.util.console.ExecutionContext;

/**
 * <code>AbstractCommand</code>...
 */
public abstract class AbstractConsoleCommand extends AbstractCommand implements ConsoleCommand {

    public boolean execute(ConsoleExecutionContext ctx, CommandLine cl)
            throws Exception {
        if (cl.hasOption(getCommand())) {
            doExecute(ctx, cl);
            return true;
        } else {
            return false;
        }
    }

    protected void doExecute(ExecutionContext ctx, CommandLine cl) throws Exception {
        // overload this in order to support app-level commands
        throw new IllegalStateException("Wrong setup. command not allowed to be invoked non-interactively.");
    }

    protected abstract void doExecute(ConsoleExecutionContext ctx, CommandLine cl)
            throws Exception;

}