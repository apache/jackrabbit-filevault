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
package org.apache.jackrabbit.vault.util.console;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.jackrabbit.vault.util.console.util.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code Console}...
 */
public class ExecutionContext {

    /**
     * the default logger
     */
    static final Logger log = LoggerFactory.getLogger(ExecutionContext.class);

    private final AbstractApplication app;

    /**
     * list of commands
     */
    protected final ArrayList commands = new ArrayList();

    public ExecutionContext(AbstractApplication app) {
        this.app = app;
    }

    public void printHelp(String cmd) {
        if (cmd == null) {
            System.out.println("Available commands:");
            for (Object o : commands) {
                CliCommand c = (CliCommand) o;
                System.out.printf("  %s - %s\n", c.getName(), c.getShortDescription());
            }
        } else {
            CliCommand c = getCommand(cmd);
            if (c != null) {
                c.printHelp();
            } else {
                System.out.println("Unknown command: " + cmd);
            }
        }
    }

    public AbstractApplication getApplication() {
        return app;
    }

    protected HelpFormatter getCmdHelpFormatter(CliCommand cmd) {
        return new HelpFormatter();
    }

    public List getCommandsGroup() {
        return commands;
    }

    public void installCommand(CliCommand c) {
        commands.add(c);
    }

    protected CliCommand getCommand(String name) {
        Iterator iter = commands.iterator();
        while (iter.hasNext()) {
            CliCommand c = (CliCommand) iter.next();
            if (c.hasName(name)) {
                return c;
            }
        }
        return null;
    }

    public boolean execute(String line) {
        return execute(Text.parseLine(line));
    }

    public boolean execute(String[] args) {
        if (args == null || args.length == 0) {
            return false;
        }
        String cmdName = args[0];
        CliCommand cmd = getCommand(cmdName);
        if (cmd == null) {
            log.error("Unknown command: {}", cmdName);
            return false;
        }
        String[] subArgs = args.length > 1 ? Arrays.copyOfRange(args, 1, args.length) : new String[0];
        DefaultParser parser = new DefaultParser();
        try {
            CommandLine cl = parser.parse(cmd.getOptions(), subArgs, true);
            return doExecute(cmd, cl);
        } catch (ParseException e) {
            log.error("Error parsing command options: {}", e.getMessage());
            cmd.printHelp();
            return true;
        } catch (Exception e) {
            log.error("Error executing command {}: {}", cmdName, e.getMessage(), e);
            return true;
        }
    }

    protected boolean doExecute(CliCommand cmd, CommandLine cl) throws Exception {
        return cmd.execute(this, cl);
    }
}