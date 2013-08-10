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
package org.apache.jackrabbit.vault.util.console.platform;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.commons.cli2.CommandLine;
import org.apache.commons.cli2.Option;
import org.apache.commons.cli2.builder.ArgumentBuilder;
import org.apache.commons.cli2.builder.CommandBuilder;
import org.apache.commons.cli2.builder.DefaultOptionBuilder;
import org.apache.commons.cli2.builder.GroupBuilder;
import org.apache.commons.cli2.option.Command;
import org.apache.commons.cli2.validation.NumberValidator;
import org.apache.jackrabbit.vault.util.console.ConsoleExecutionContext;
import org.apache.jackrabbit.vault.util.console.ConsoleFile;
import org.apache.jackrabbit.vault.util.console.ExecutionException;
import org.apache.jackrabbit.vault.util.console.commands.AbstractConsoleCommand;
import org.apache.jackrabbit.vault.util.console.util.Table;

/**
 * <code>CmdLs</code>...
 */
public class CmdLs extends AbstractConsoleCommand {

    private static final SimpleDateFormat dateFmt = new SimpleDateFormat("yyyy MMM dd HH:mm");

    // format flags
    private static int F_FLAG_TIME = 0x01;
    private static int F_FLAG_SIZE = 0x02;
    private static int F_FLAG_LONG = 0x03;
    private static int F_MASK      = 0x0f;

    // list switches
    private static int L_FLAG_ALL = 0x10;

    private Option optLong;

    private Option optTime;

    private Option optSize;

    private Option optAll;

    private Option optRecursive;

    private Option argPath;

    protected void doExecute(ConsoleExecutionContext ctx, CommandLine cl)
            throws Exception {
        int fmtFlag = 0;
        fmtFlag |= cl.hasOption(optTime) ? F_FLAG_TIME : 0;
        fmtFlag |= cl.hasOption(optSize) ? F_FLAG_SIZE : 0;
        fmtFlag |= cl.hasOption(optLong) ? F_FLAG_LONG : 0;
        fmtFlag |= cl.hasOption(optAll) ? L_FLAG_ALL : 0;
        int depth = 0;
        if (cl.hasOption(optRecursive)) {
            depth = Integer.parseInt((String) cl.getValue(optRecursive, "10000"));
        }
        String path = (String) cl.getValue(argPath);
        ConsoleFile file = ctx.getFile(path, true);
        if (!(file instanceof PlatformFile)) {
            throw new ExecutionException("wrong file system.");
        }
        ls((PlatformFile) file, fmtFlag, depth);
    }

    public String getShortDescription() {
        return "print a list of files and directories";
    }


    public String getExample() {
        return "$ ls -al";
    }

    protected Command createCommand() {
        return new CommandBuilder()
                .withName("ls")
                .withDescription(getShortDescription())
                .withChildren(new GroupBuilder()
                        .withName("Options:")
                        .withOption(optAll = new DefaultOptionBuilder()
                                .withShortName("a")
                                .withDescription("display hidden files")
                                .create())
                        .withOption(optTime = new DefaultOptionBuilder()
                                .withShortName("t")
                                .withDescription("display the last modification date")
                                .create())
                        .withOption(optSize = new DefaultOptionBuilder()
                                .withShortName("s")
                                .withDescription("display the file size")
                                .create())
                        .withOption(optLong = new DefaultOptionBuilder()
                                .withShortName("l")
                                .withDescription("combines the flags 't' and 's'")
                                .create())
                        .withOption(optRecursive = new DefaultOptionBuilder()
                                .withShortName("r")
                                .withDescription("do a recursive listing")
                                .withArgument(new ArgumentBuilder()
                                        .withName("depth")
                                        .withDescription("the depth of the recursion.")
                                        .withMinimum(0)
                                        .withMaximum(1)
                                        .withValidator(NumberValidator.getIntegerInstance())
                                        .create())
                                .create())
                        .withOption(argPath = new ArgumentBuilder()
                                        .withName("path")
                                        .withDescription("the path to list")
                                        .withMinimum(0)
                                        .withMaximum(1)
                                        .create())
                        .create())
                .create();
    }

    private void ls(PlatformFile file, int flags, int maxDepth) throws IOException {
        int numCols = 1;
        int f = flags & F_MASK;
        while (f != 0) {
            if ((f & 1) == 1) {
                numCols++;
            }
            f >>= 1;
        }
        Table t = new Table(numCols);
        ls(t, (File) file.unwrap(), flags, 0, maxDepth);
        t.print();
    }

    private void ls(Table t, File file, int flags, int indent, int maxDepth) throws IOException {
        File[] files = file.listFiles();
        if (files == null) {
            return;
        }
        for (int i=0; i<files.length; i++) {
            File f = files[i];
            if (f.getName().charAt(0) == '.' && !((flags & L_FLAG_ALL) > 0)) {
                continue;
            }
            Table.Row r = t.createRow();
            if ((flags & F_FLAG_TIME) > 0) {
                r.addCol(dateFmt.format(new Date(f.lastModified())));
            }
            if ((flags & F_FLAG_SIZE) > 0) {
                r.addCol(String.valueOf(f.length()), true);
            }
            String name = f.getName();
            if (f.isDirectory()) {
                name += "/";
            }
            r.addCol(name);
            t.addRow(r);
            if (maxDepth > 0) {
                ls(t, f, flags, indent + 1, maxDepth -1);
            }
        }
    }


}