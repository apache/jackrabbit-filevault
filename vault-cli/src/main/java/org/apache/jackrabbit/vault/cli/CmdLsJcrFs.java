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
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.HelpFormatter;
import org.apache.jackrabbit.vault.fs.api.VaultFile;
import org.apache.jackrabbit.vault.util.console.ConsoleFile;
import org.apache.jackrabbit.vault.util.console.util.Table;

/**
 * Implements the 'ls' command.
 *
 */
public class CmdLsJcrFs extends AbstractCmdLs {

    private static final int F_FLAG_SIZE = 0x01;
    private static final int F_FLAG_TIME = 0x02;
    private static final int F_FLAG_MIME = 0x04;
    private static final int F_FLAG_LONG = 0x07;

    private Option optLong;

    private Option optTime;

    private Option optSize;

    private Option optMime;
    private Options options;

    public CmdLsJcrFs() {
        options = new Options();
        optTime = Option.builder("t")
                .desc("display the last modification date")
                .build();
        options.addOption(optTime);
        optSize = Option.builder("s")
                .desc("display the file size")
                .build();
        options.addOption(optSize);
        optMime = Option.builder("m")
                .desc("display the mime type")
                .build();
        options.addOption(optMime);
        optLong = Option.builder("l")
                .desc("combines all format flags")
                .build();
        options.addOption(optLong);
        options.addOption(Option.builder().argName("path").hasArg().build());
    }

    protected int getFormatFlags(VaultFsConsoleExecutionContext ctx, CommandLine cl) {
        int fmtFlag = 0;
        fmtFlag |= cl.hasOption(optTime.getOpt()) ? F_FLAG_TIME : 0;
        fmtFlag |= cl.hasOption(optSize.getOpt()) ? F_FLAG_SIZE : 0;
        fmtFlag |= cl.hasOption(optMime.getOpt()) ? F_FLAG_MIME : 0;
        fmtFlag |= cl.hasOption(optLong.getOpt()) ? F_FLAG_LONG : 0;
        return fmtFlag;
    }

    public Options getOptions() { return options; }

    public void printHelp() { new HelpFormatter().printHelp("ls", options); }

    protected void formatFile(ConsoleFile file, Table.Row row, int flags) {
            VaultFile f = (VaultFile) file.unwrap();
            if ((flags & F_FLAG_TIME) > 0) {
                row.addCol(formatDate(f.lastModified()), true);
            }
            if ((flags & F_FLAG_SIZE) > 0) {
                row.addCol(formatSize(f.length()), true);
            }
            if ((flags & F_FLAG_MIME) > 0) {
                row.addCol(f.getContentType() == null ? "" : f.getContentType());
            }
            row.addCol(f.getName() + (f.isDirectory() ? "/" : ""));
    }

}