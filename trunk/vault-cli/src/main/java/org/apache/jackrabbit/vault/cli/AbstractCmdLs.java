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
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.commons.cli2.CommandLine;
import org.apache.commons.cli2.Option;
import org.apache.commons.cli2.builder.ArgumentBuilder;
import org.apache.jackrabbit.vault.util.console.ConsoleFile;
import org.apache.jackrabbit.vault.util.console.util.Table;

/**
 * Implements the 'ls' command.
 *
 */
abstract public class AbstractCmdLs extends AbstractJcrFsCommand {

    protected static final int F_MASK = 0x0f;

    protected final Option argPath = new ArgumentBuilder()
                                        .withName("path")
                                        .withDescription("the path to list")
                                        .withMinimum(0)
                                        .withMaximum(1)
                                        .create();

    protected void doExecute(VaultFsConsoleExecutionContext ctx, CommandLine cl) throws Exception {
        int fmtFlag = getFormatFlags(ctx, cl);
        String path = (String) cl.getValue(argPath);
        ConsoleFile file = ctx.getFile(path, true);
        ls(file, fmtFlag, 0);
    }

    abstract protected int getFormatFlags(VaultFsConsoleExecutionContext ctx, CommandLine cl);

    abstract protected void formatFile(ConsoleFile file, Table.Row row, int flags);

    private void ls(ConsoleFile file, int flags, int maxDepth)
            throws IOException {
        int numCols = 1;
        int f = flags & F_MASK;
        while (f != 0) {
            if ((f & 1) == 1) {
                numCols++;
            }
            f >>= 1;
        }
        Table t = new Table(numCols);

        ConsoleFile[] files = file.listFiles();
        for (ConsoleFile file1 : files) {
            Table.Row r = t.createRow();
            formatFile(file1, r, flags);
            t.addRow(r);
        }

        t.print();
    }

    /**
     * {@inheritDoc}
     */
    public String getShortDescription() {
        return "Print a list of files and directories";
    }

    public static String formatSize(long size) {
        if (size < 0) {
            return "";
        } else {
            String[] units = new String[]{"B", "K", "M", "G", "T"};
            int i=0;
            while (size > 1000) {
                size /= 1000;
                i++;
            }
            return String.valueOf(size) + units[i];
        }
    }

    public static String formatDate(long date) {
        final SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        return date == 0 ? "" : fmt.format(new Date(date));
    }
}