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
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;
import java.util.zip.CheckedOutputStream;

import org.apache.commons.cli2.CommandLine;
import org.apache.commons.cli2.builder.ArgumentBuilder;
import org.apache.commons.cli2.builder.CommandBuilder;
import org.apache.commons.cli2.builder.GroupBuilder;
import org.apache.commons.cli2.option.Command;
import org.apache.commons.io.IOUtils;
import org.apache.jackrabbit.vault.fs.impl.io.DocViewSerializer;
import org.apache.jackrabbit.vault.util.console.CliCommand;
import org.apache.jackrabbit.vault.util.console.ExecutionContext;
import org.apache.jackrabbit.vault.util.console.commands.AbstractCommand;
import org.apache.jackrabbit.vault.util.xml.serialize.XMLSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

public class CmdFormatCli extends AbstractCommand {

    private static final Logger LOG = LoggerFactory.getLogger(CmdFormatCli.class);

    @Override protected void doExecute(ExecutionContext ctx, CommandLine cl) throws Exception {
        boolean verbose = cl.hasOption(OPT_VERBOSE);
        List<String> givenPatterns = cl.getValues("pattern");
        List<Pattern> parsedPatterns = new ArrayList<>(givenPatterns.size());

        for (String pattern : givenPatterns) {
            if (verbose) {
                LOG.info("parsing pattern: {}", pattern);
            }
            parsedPatterns.add(Pattern.compile(pattern));
        }

        if (parsedPatterns.isEmpty()) {
            throw new IllegalArgumentException("No pattern given");
        }

        Files.walkFileTree(Paths.get(""), new Visitor(parsedPatterns, verbose));
    }

    @Override
    protected Command createCommand() {
        return new CommandBuilder()
                .withName("format")
                .withDescription(getShortDescription())
                .withChildren(new GroupBuilder()
                        .withOption(CliCommand.OPT_VERBOSE)
                        .withOption(new ArgumentBuilder()
                                .withName("pattern")
                                .withDescription("Regular expression matched against file names.")
                                .withMinimum(1)
                                .create())
                        .create())
                .create();
    }

    @Override
    public String getShortDescription() {
        return "Formats vault controlled files in the current directory.";
    }

    private static class Visitor extends SimpleFileVisitor<Path> {

        private final boolean verbose;
        private final List<Pattern> patterns;
        private final ByteArrayOutputStream buffer;
        private final XMLSerializer serializer;

        Visitor(List<Pattern> patterns, boolean verbose) {
            this.patterns = patterns;
            this.buffer = new ByteArrayOutputStream();
            this.verbose = verbose;
            this.serializer = new XMLSerializer(DocViewSerializer.FORMAT);
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            if (Files.isRegularFile(file) && isIncluded(file)) {
                format(file);
            } else if (verbose) {
                LOG.info("exclude {}", file.toString());
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
                if (verbose) {
                    LOG.info("formatting {}", file.toString());
                }
                try (OutputStream out = new BufferedOutputStream(new FileOutputStream(file.toFile()))) {
                    IOUtils.copy(new ByteArrayInputStream(buffer.toByteArray()), out);
                }
            }

            buffer.reset();
        }
    }
}
