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

package org.apache.jackrabbit.vault.fs.io;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;
import java.util.zip.CheckedOutputStream;
import java.util.zip.Checksum;

import org.apache.commons.io.IOUtils;
import org.apache.jackrabbit.vault.util.xml.serialize.AttributeNameComparator;
import org.apache.jackrabbit.vault.util.xml.serialize.OutputFormat;
import org.apache.jackrabbit.vault.util.xml.serialize.XMLSerializer;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

/**
 * This class provides access to the commonly used doc view xml format and functionality that checks files for the format or reformats
 * them accordingly.
 */
public class DocViewFormat {

    private final OutputFormat format;
    private WeakReference<ByteArrayOutputStream> formattingBuffer;

    public DocViewFormat() {
        format = new OutputFormat("xml", "UTF-8", true);
        format.setIndent(4);
        format.setLineWidth(0);
        format.setBreakEachAttribute(true);
        format.setSortAttributeNamesBy(AttributeNameComparator.INSTANCE);
    }

    /**
     * Returns the {@link OutputFormat} used by {@link org.apache.jackrabbit.vault.fs.impl.io.DocViewSerializer} when writing doc view xml
     * files.
     *
     * @return the output format
     */
    public OutputFormat getXmlOutputFormat() {
        return format;
    }

    /**
     * Formats a given file using the {@link OutputFormat} returned by {@link DocViewFormat#getXmlOutputFormat()}.
     * The file is replaced on disk but only if wasn't already formatted correctly and if {@code dryRun} is {@code false}.
     *
     * @param file the file to format
     * @return {@code true} if the formatted version differs from the original.
     * @throws IOException if an I/O error occurs
     */
    public boolean format(File file, boolean dryRun) throws IOException {
        CRC32 originalCrc32 = new CRC32();
        CRC32 formattedCrc32 = new CRC32();
        byte[] formatted = format(file, originalCrc32, formattedCrc32);

        final boolean changed = originalCrc32.getValue() != formattedCrc32.getValue();
        if (changed && !dryRun) {
            try (OutputStream out = new BufferedOutputStream(new FileOutputStream(file))) {
                IOUtils.copy(new ByteArrayInputStream(formatted), out);
            }
        }
        return changed;
    }

    /**
     * Formats given files using the {@link OutputFormat} returned by {@link DocViewFormat#getXmlOutputFormat()} by traversing the directory
     * tree given as file. Only those files will be formatted, that have a filename matching at least one of the given filenamePatterns,
     * and only if {@code dryRun} is {@code false}.
     *
     * @param directory the start directory
     * @param filenamePatterns list of regexp patterns
     * @return a list of relative paths of those files which are not formatted correctly according to {@link #format(File, boolean)}
     * @throws IOException in case there is an exception during traversal or formatting. That means formatting will fail on the first error that appeared
     */
    public List<String> format(File directory, List<Pattern> filenamePatterns, final boolean dryRun) throws IOException {
        final List<String> changed = new LinkedList<>();
        Files.walkFileTree(directory.toPath(), new AbstractFormattingVisitor(filenamePatterns) {
            @Override protected void process(File file) throws IOException {
                if (format(file, dryRun)) {
                    changed.add(file.getPath());
                };
            }
        });
        return changed;
    }

    /**
     * internally formats the given file and computes their checksum
     * @param file the file
     * @param original checksum of the original file
     * @param formatted checksum of the formatted file
     * @return the formatted bytes
     * @throws IOException if an error occurs
     */
    private byte[] format(File file, Checksum original, Checksum formatted) throws IOException {
        try (InputStream in = new CheckedInputStream(new BufferedInputStream(new FileInputStream(file)), original)) {
            @SuppressWarnings("resource")
            ByteArrayOutputStream buffer = formattingBuffer != null ? formattingBuffer.get() : null;
            if (buffer == null) {
                buffer = new ByteArrayOutputStream();
                formattingBuffer = new WeakReference<>(buffer);
            } else {
                buffer.reset();
            }

            XMLSerializer serializer = new XMLSerializer(new CheckedOutputStream(buffer, formatted), format);
            XMLReader reader = XMLReaderFactory.createXMLReader();
            reader.setContentHandler(serializer);
            reader.setDTDHandler(serializer);
            reader.parse(new InputSource(in));

            return buffer.toByteArray();
        } catch (SAXException ex) {
            throw new IOException(ex);
        }
    }

    private abstract static class AbstractFormattingVisitor extends SimpleFileVisitor<Path> {

        private final List<Pattern> patterns;

        AbstractFormattingVisitor(List<Pattern> patterns) {
            this.patterns = patterns;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            if (Files.isRegularFile(file) && isIncluded(file)) {
                process(file.toFile());
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

        protected abstract void process(File file) throws IOException;
    }
}
