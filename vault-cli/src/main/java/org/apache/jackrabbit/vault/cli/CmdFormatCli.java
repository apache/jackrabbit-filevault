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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;
import java.util.zip.CheckedOutputStream;

import org.apache.commons.cli2.Argument;
import org.apache.commons.cli2.CommandLine;
import org.apache.commons.cli2.Option;
import org.apache.commons.cli2.builder.ArgumentBuilder;
import org.apache.commons.cli2.builder.CommandBuilder;
import org.apache.commons.cli2.builder.DefaultOptionBuilder;
import org.apache.commons.cli2.builder.GroupBuilder;
import org.apache.commons.cli2.option.Command;
import org.apache.commons.io.IOUtils;
import org.apache.jackrabbit.vault.fs.impl.io.DocViewSerializer;
import org.apache.jackrabbit.vault.util.console.CliCommand;
import org.apache.jackrabbit.vault.util.console.ExecutionContext;
import org.apache.jackrabbit.vault.util.console.commands.AbstractCommand;
import org.apache.jackrabbit.vault.util.xml.serialize.XMLSerializer;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

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

        Visitor visitor = new Visitor(parsedPatterns, checkOnly, verbose);
        Files.walkFileTree(Paths.get(""), visitor);

        if (checkOnly && !visitor.malformedFiles.isEmpty()) {
            throw new IllegalStateException("One or more files are malformed. Malformed files: " + visitor.malformedFiles.toString());
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

    private static class Visitor extends SimpleFileVisitor<Path> {

        private final boolean verbose;
        private final boolean checkOnly;
        private final List<Pattern> patterns;
        private final ByteArrayOutputStream buffer;
        private final XMLSerializer serializer;
        private final List<String> malformedFiles = new LinkedList<>();

        Visitor(List<Pattern> patterns, boolean checkOnly, boolean verbose) {
            this.patterns = patterns;
            this.buffer = new ByteArrayOutputStream();
            this.verbose = verbose;
            this.checkOnly = checkOnly;
            this.serializer = new XMLSerializer(DocViewSerializer.FORMAT);
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            if (Files.isRegularFile(file) && isIncluded(file)) {
                if (verbose) {
                    System.out.println("process " + file.toString());
                }

                format(file);
            } else {
                if (verbose) {
                    System.out.println("exclude " + file.toString());
                }
            }

            return super.visitFile(file, attrs);
        }

        private boolean isIncluded(Path file) {
            for (Pattern pattern : patterns) {
                if (pattern.matcher(file.getFileName().toString()).matches()) {
                    return true;
                }
            }

            return false;
        }

        private void format(Path file) throws IOException {
            // read the file, and write it to raf
            CRC32 formattedChecksum = new CRC32();
            CRC32 readChecksum = new CRC32();

            try (InputStream in = new CheckedInputStream(new BufferedInputStream(new FileInputStream(file.toFile())), readChecksum)) {
                serializer.setOutputByteStream(new CheckedOutputStream(buffer, formattedChecksum));
                XMLReader reader = XMLReaderFactory.createXMLReader();
                reader.setContentHandler(serializer);
                reader.setDTDHandler(serializer);
                reader.parse(new InputSource(in));
            } catch (SAXException ex) {
                throw new IOException(ex);
            }

            if (formattedChecksum.getValue() != readChecksum.getValue()) {
                if (checkOnly) {
                    malformedFiles.add(file.toString());
                } else {
                    if (verbose) {
                        System.out.println("formatting " + file.toString());
                    }
                    try (OutputStream out = new BufferedOutputStream(new FileOutputStream(file.toFile()))) {
                        IOUtils.copy(new ByteArrayInputStream(buffer.toByteArray()), out);
                    }
                }
            }

            buffer.reset();
        }
    }
}
