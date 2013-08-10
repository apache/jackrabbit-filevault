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
import java.util.Iterator;

import org.apache.commons.cli2.CommandLine;
import org.apache.commons.cli2.DisplaySetting;
import org.apache.commons.cli2.Group;
import org.apache.commons.cli2.builder.GroupBuilder;
import org.apache.commons.cli2.commandline.Parser;
import org.apache.commons.cli2.util.HelpFormatter;
import org.apache.jackrabbit.vault.util.console.util.CliHelpFormatter;
import org.apache.jackrabbit.vault.util.console.util.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

/**
 * <code>Console</code>...
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

    private Group grpCommands;

    public ExecutionContext(AbstractApplication app) {
        this.app = app;
    }

    public void printHelp(String cmd) {
        CliCommand cc = cmd == null ? null : getCommand(cmd);
        getCmdHelpFormatter(cc).print();
    }

    public AbstractApplication getApplication() {
        return app;
    }

    protected HelpFormatter getCmdHelpFormatter(CliCommand cmd) {
        CliHelpFormatter hf = CliHelpFormatter.create();
        if (cmd != null) {
            hf.setCmd(cmd);
            hf.getLineUsageSettings().add(DisplaySetting.DISPLAY_ARGUMENT_BRACKETED);
        } else {
            hf.setGroup(getCommandsGroup());
            hf.setShowUsage(false);
            hf.getDisplaySettings().remove(DisplaySetting.DISPLAY_PARENT_CHILDREN);
        }
        return hf;
    }

    public Group getCommandsGroup() {
        if (grpCommands == null) {
            try {
                GroupBuilder gbuilder = new GroupBuilder()
                        .withName("Commands:")
                        .withMinimum(0)
                        .withMaximum(1);
                Iterator iter = commands.iterator();
                while (iter.hasNext()) {
                    CliCommand c = (CliCommand) iter.next();
                    gbuilder.withOption(c.getCommand());
                }
                grpCommands = gbuilder.create();
            } catch (Exception e) {
                log.error("Error while building command group.", e);
                throw new ExecutionException("Error while building command gorup.", e);
            }
        }
        return grpCommands;
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
        Parser parser = new Parser();
        parser.setHelpFormatter(getCmdHelpFormatter(null));
        parser.setGroup(getCommandsGroup());
        CommandLine cl = parser.parseAndHelp(args);
        return cl != null && execute(cl);
    }

    public boolean execute(CommandLine cl) {
        Iterator iter = commands.iterator();
        while (iter.hasNext()) {
            CliCommand c = (CliCommand) iter.next();
            try {
                if (doExecute(c, cl)) {
                    return true;
                }
            } catch (ExecutionException e) {
                if (e.getCause() == null || e.getCause() == e) {
                    log.error("{}: {}", c.getName(), e.getMessage());
                } else {
                    StringBuffer buf = new StringBuffer();
                    addCause(buf, e.getCause(), null);
                    log.error("{}: {}", c.getName(), buf.toString());

                }
            } catch (Throwable e) {
                StringBuffer buf = new StringBuffer();
                addCause(buf, e, null);
                log.error("{}: {}", c.getName(), buf.toString());
                return true;
            }
        }
        return false;
    }

    private static void addCause(StringBuffer buf, Throwable e, StringBuffer indent) {
        if (indent == null) {
            indent = new StringBuffer();
        }
        buf.append(e.getClass().getName()).append(": ").append(e.getMessage());
        Throwable t = e.getCause();
        if (e instanceof SAXException) {
            t = ((SAXException) e).getException();
        }
        if (t != null && t != e) {
            buf.append("\n").append(indent).append("caused by: ");
            indent.append("  ");
            addCause(buf, t, indent);
        }
    }

    protected boolean doExecute(CliCommand cmd, CommandLine cl) throws Exception {
        return cmd.execute(this, cl);
    }
}