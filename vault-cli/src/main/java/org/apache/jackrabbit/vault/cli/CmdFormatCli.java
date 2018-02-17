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

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.cli2.Argument;
import org.apache.commons.cli2.CommandLine;
import org.apache.commons.cli2.Option;
import org.apache.commons.cli2.builder.ArgumentBuilder;
import org.apache.commons.cli2.builder.CommandBuilder;
import org.apache.commons.cli2.builder.DefaultOptionBuilder;
import org.apache.commons.cli2.builder.GroupBuilder;
import org.apache.commons.cli2.option.Command;
import org.apache.jackrabbit.vault.fs.impl.io.DocViewSerializer;
import org.apache.jackrabbit.vault.fs.io.DocViewFormat;
import org.apache.jackrabbit.vault.util.console.CliCommand;
import org.apache.jackrabbit.vault.util.console.ExecutionContext;
import org.apache.jackrabbit.vault.util.console.commands.AbstractCommand;

public class CmdFormatCli extends AbstractCommand {

    private static final Option OPT_CHECK_ONLY = new DefaultOptionBuilder()
            .withShortName("c")
            .withLongName("check-only")
            .withDescription("Only check the format.")
            .create();
    private static final Argument ARG_PATTERN = new ArgumentBuilder()
            .withName("pattern")
            .withDescription("Regular expression matched against file names.")
            .withMinimum(1)
            .create();

    @Override protected void doExecute(ExecutionContext ctx, CommandLine cl) throws Exception {
        boolean checkOnly = cl.hasOption(OPT_CHECK_ONLY);
        boolean verbose = cl.hasOption(OPT_VERBOSE);
        List<String> givenPatterns = (List<String>) cl.getValues(ARG_PATTERN);
        List<Pattern> parsedPatterns = new ArrayList<>(givenPatterns.size());

        for (String pattern : givenPatterns) {
            if (verbose) {
                System.out.println("parsing pattern: " + pattern);
            }
            parsedPatterns.add(Pattern.compile(pattern));
        }

        if (parsedPatterns.isEmpty()) {
            throw new IllegalArgumentException("No pattern given");
        }

        File currentDir = Paths.get("").toFile();

        if (checkOnly) {
            List<String> malformedFiles = DocViewFormat.checkFormat(currentDir, parsedPatterns);
            if (!malformedFiles.isEmpty()) {
                throw new IllegalStateException("One or more files are malformed. Malformed files: " + malformedFiles.toString());
            }
        } else {
            DocViewFormat.format(currentDir, parsedPatterns);
        }
    }

    @Override
    protected Command createCommand() {
        return new CommandBuilder()
                .withName("format")
                .withDescription(getShortDescription())
                .withChildren(new GroupBuilder()
                        .withOption(CliCommand.OPT_VERBOSE)
                        .withOption(OPT_CHECK_ONLY)
                        .withOption(ARG_PATTERN)
                        .create())
                .create();
    }

    @Override
    public String getShortDescription() {
        return "Formats vault controlled files in the current directory.";
    }

}
