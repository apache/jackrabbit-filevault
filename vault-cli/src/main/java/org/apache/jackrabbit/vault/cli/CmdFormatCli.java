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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.cli2.CommandLine;
import org.apache.commons.cli2.Option;
import org.apache.commons.cli2.builder.ArgumentBuilder;
import org.apache.commons.cli2.builder.CommandBuilder;
import org.apache.commons.cli2.builder.DefaultOptionBuilder;
import org.apache.commons.cli2.builder.GroupBuilder;
import org.apache.commons.cli2.option.Command;
import org.apache.jackrabbit.vault.fs.io.DocViewFormat;
import org.apache.jackrabbit.vault.util.console.CliCommand;

public class CmdFormatCli extends AbstractVaultCommand {

    private static final Pattern DEFAULT_PATTERN = Pattern.compile(".*\\.xml");

    private Option optCheckOnly;
    private Option optPatterns;
    private Option argPaths;


    @Override
    protected void doExecute(VaultFsApp app, CommandLine cl) throws Exception {
        boolean checkOnly = cl.hasOption(optCheckOnly);
        boolean verbose = cl.hasOption(OPT_VERBOSE);
        List<String> givenPatterns = (List<String>) cl.getValues(optPatterns);
        List<String> localPaths = new LinkedList<String>(cl.getValues(argPaths));

        List<Pattern> parsedPatterns = new ArrayList<>(givenPatterns.size());
        boolean hasArgSeparator = false;
        for (String pattern : givenPatterns) {
            if ("--".equals(pattern)) {
                // little hack to separate the patterns from the files
                hasArgSeparator = true;
            } else {
                if (hasArgSeparator) {
                    localPaths.add(0, pattern);
                } else {
                    parsedPatterns.add(Pattern.compile(pattern));
                }
            }
        }
        if (parsedPatterns.isEmpty()) {
            parsedPatterns.add(DEFAULT_PATTERN);
        }

        List<File> localFiles = app.getPlatformFiles(localPaths, true);
        if (localFiles.isEmpty()) {
            localFiles.add(app.getPlatformFile(".", true));
        }

        List<String> formattedFiles = new LinkedList<>();
        DocViewFormat format = new DocViewFormat();
        for (File file: localFiles) {
            if (file.isDirectory()) {
                if (verbose) {
                    System.out.printf("traversing: %s%n", file);
                    for (Pattern p: parsedPatterns) {
                        System.out.printf("scanning for files matching: %s%n", p);
                    }
                }
                formattedFiles.addAll(format.format(file, parsedPatterns, checkOnly));
            } else {
                if (verbose) {
                    System.out.printf("processing: %s%n", file);
                }
                if (format.format(file, checkOnly)) {
                    formattedFiles.add(file.getPath());
                }
            }
        }
        if (formattedFiles.isEmpty()) {
            System.out.println("All files already properly formatted.");
            return;
        }

        final Path cwd = Paths.get(new File("").getAbsolutePath());
        if (checkOnly) {
            System.out.println("The following files are not properly formatted:\n");
            for (String path: formattedFiles) {
                System.out.println(cwd.relativize(Paths.get(path)));
            }
        } else {
            System.out.println("reformatted files:\n");
            for (String path: formattedFiles) {
                System.out.println(cwd.relativize(Paths.get(path)));
            }
        }
    }

    @Override
    protected Command createCommand() {
        return new CommandBuilder()
                .withName("format")
                .withDescription(getShortDescription())
                .withChildren(new GroupBuilder()
                        .withOption(CliCommand.OPT_VERBOSE)
                        .withOption(optCheckOnly = new DefaultOptionBuilder()
                            .withShortName("c")
                            .withLongName("check-only")
                            .withDescription("Only check the format.")
                            .create()
                        )
                        .withOption(optPatterns = new DefaultOptionBuilder()
                                .withShortName("p")
                                .withLongName("pattern")
                                .withDescription("pattern for recursive format. defaults to match all xml files.")
                                .withArgument(new ArgumentBuilder()
                                        .withMinimum(0)
                                        .withConsumeRemaining("**dummy**")
                                        .create())
                                .create())
                        .withOption(argPaths = new ArgumentBuilder()
                                .withName("paths")
                                .withDescription("files or directories to format.")
                                .withMinimum(0)
                                .create()
                        )
                        .create())
                .create();
    }

    @Override
    public String getShortDescription() {
        return "Formats vault docview files.";
    }

    @Override
    public String getLongDescription() {
        return  "Formats the file specified by <path> according to the vault specific docview format." +
                "If the <path> points at a directory, the files matching the patterns are processed recursively.\n\n" +
                "Examples:\n" +
                "  vlt format -c -p '\\.content\\.xml' content/jcr_root\n\n" +
                "" +
                "  vlt format -p \\.content\\.xml -p _jcr_content\\.xml -- apps/";
    }

}
