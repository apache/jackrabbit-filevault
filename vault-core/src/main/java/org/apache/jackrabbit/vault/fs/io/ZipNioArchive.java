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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.ProviderNotFoundException;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.jackrabbit.vault.fs.api.VaultInputSource;
import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.fs.config.DefaultMetaInf;
import org.apache.jackrabbit.vault.fs.config.MetaInf;
import org.apache.jackrabbit.vault.fs.config.VaultSettings;
import org.apache.jackrabbit.vault.util.Constants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Archive leveraging <a href="https://docs.oracle.com/javase/8/docs/api/java/nio/file/package-summary.html">Java NIO File</a> and its <a href="https://docs.oracle.com/javase/8/docs/technotes/guides/io/fsp/zipfilesystemprovider.html">Zip File System Provider</a>.
 * <p>
 * It doesn't support accessing nested zip files (for subpackages, compare with <a href="https://bugs.openjdk.java.net/browse/JDK-8247441">JDK-8247441</a>)
 * @since 3.5.2 (package version 2.11.0)
 */
public class ZipNioArchive extends AbstractArchive {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(ZipNioArchive.class);

    private final Path path;

    private final boolean deleteAtClose;
 
    /**
     * The (loaded) meta info
     */
    private DefaultMetaInf inf;

    private FileSystem zipFileSystem;

    /**
     * Shortcut for {@link ZipNioArchive#ZipNioArchive(Path, boolean)} with {@code false} as second parameter.
     * 
     * @param path the path of the zip file
     */
    public ZipNioArchive(Path path) {
        this(path, false);
    }

    /**
     * 
     * @param path the path of the zip file
     * @param deleteAtClose if {@code true} removes the file with the given path during {@link #close()}
     */
    public ZipNioArchive(Path path, boolean deleteAtClose) {
        super();
        this.path = path;
        zipFileSystem = null;
        this.deleteAtClose = deleteAtClose;
    }

    @Override
    public void open(boolean strict) throws IOException {
        if (zipFileSystem != null) {
            log.debug("Archive already open");
            return;
        }
        try {
            zipFileSystem = FileSystems.newFileSystem(path, (ClassLoader)null);
        } catch (ProviderNotFoundException e) {
            throw new IOException("Can not open zip file '" + path + "'", e);
        }
    }

    @Override
    @Nullable
    public InputStream openInputStream(@Nullable Entry entry) throws IOException {
        if (entry == null) {
            return null;
        }
        if (zipFileSystem == null) {
            throw new ArchiveNotOpenException(path.toString());
        }
        return Files.newInputStream(((EntryImpl)entry).path);
    }

    @Override
    @Nullable
    public VaultInputSource getInputSource(@Nullable Entry entry) throws IOException {
        if (entry == null) {
            return null;
        }
        if (zipFileSystem == null) {
            throw new ArchiveNotOpenException(path.toString());
        }
        final EntryImpl entryImpl = (EntryImpl)entry;
        return new VaultInputSourceImpl(entryImpl);
    }

    @Override
    public @NotNull Entry getRoot() throws IOException {
        // use first root directory
        return new EntryImpl(zipFileSystem.getRootDirectories().iterator().next());
    }

    @Override
    public @NotNull MetaInf getMetaInf() {
        if (inf == null) {
            try {
                inf = parseMetaInf();
            } catch (IOException e) {
                throw new IllegalStateException("Could not retrieve meta data from " + path.toString(), e);
            }
        }
        return inf;
    }

    private @NotNull DefaultMetaInf parseMetaInf() throws IOException {
        if (zipFileSystem == null) {
            throw new ArchiveNotOpenException(path.toString());
        }
        final DefaultMetaInf defaultMetaInf = new DefaultMetaInf();
        // check for meta inf
        try (Stream<Path> metaDirFiles = Files.list(zipFileSystem.getPath(Constants.META_INF, Constants.VAULT_DIR))) {
            Iterator<Path> filesIterator = metaDirFiles.iterator();
            while (filesIterator.hasNext()) {
                Path file = filesIterator.next();
                if (Files.isDirectory(file)) {
                    continue;
                }
                try (InputStream input = Files.newInputStream(file)) {
                    defaultMetaInf.load(input, getSystemId(path, file));
                } catch (ConfigurationException e1) {
                    throw new IOException(e1);
                }
            }
        }
        if (defaultMetaInf.getFilter() == null) {
            log.debug("Zip {} does not contain filter definition.", path);
        }
        if (defaultMetaInf.getConfig() == null) {
            log.debug("Zip {} does not contain vault config.", path);
        }
        if (defaultMetaInf.getSettings() == null) {
            log.debug("Zip {} does not contain vault settings. using default.", path);
            VaultSettings settings = new VaultSettings();
            settings.getIgnoredNames().add(".svn");
            defaultMetaInf.setSettings(settings);
        }
        if (defaultMetaInf.getProperties() == null) {
            log.debug("Zip {} does not contain properties.", path);
        }
        if (defaultMetaInf.getNodeTypes().isEmpty()) {
            log.debug("Zip {} does not contain nodetypes.", path);
        }
        return defaultMetaInf;
    }

    private static String getSystemId(Path zipPath, Path pathInZip) {
        String systemId = zipPath.toString();
        systemId += "!/" + pathInZip;
        return systemId;
    }

    @Override
    public void close() {
        if (zipFileSystem != null) {
            try {
                zipFileSystem.close();
            } catch (IOException e) {
                log.warn("Error during close.", e);
            }
        }
        if (deleteAtClose) {
            try {
                Files.delete(path);
            } catch (IOException e) {
                log.warn("Could not delete " + path, e);
            }
        }
    }

    private static final class VaultInputSourceImpl extends VaultInputSource {
        private final EntryImpl entryImpl;

        private VaultInputSourceImpl(EntryImpl entryImpl) {
            this.entryImpl = entryImpl;
        }

        @Override
        public InputStream getByteStream() {
            try {
                return Files.newInputStream(entryImpl.path);
            } catch (IOException e) {
                throw new IllegalStateException("Could not retrieve byte stream", e);
            }
        }

        @Override
        public long getContentLength() {
            try {
                return Files.size(entryImpl.path);
            } catch (IOException e) {
                throw new IllegalStateException("Could not retrieve file size", e);
            }
        }

        @Override
        public long getLastModified() {
            try {
                return Files.getLastModifiedTime(entryImpl.path).toMillis();
            } catch (IOException e) {
                throw new IllegalStateException("Could not retrieve last modification time", e);
            }
        }
    }

    /**
     * Implements the entry for this archive
     */
    private static final class EntryImpl implements Entry {

        protected final Path path;

        private Map<String, EntryImpl> children;

        private EntryImpl(@NotNull Path path) {
            this.path = path;
        }

        @Override
        @NotNull
        public String getName() {
            String name = path.getName(path.getNameCount()-1).toString();
            // strip trailing slashes (returned by Zip File System provider for directories)
            if (name.endsWith("/")) {
                name = name.substring(0, name.length()-1);
            }
            return name;
        }

        @Override
        public boolean isDirectory() {
            return Files.isDirectory(path);
        }

        @Override
        @NotNull
        public Collection<? extends Entry> getChildren() {
            if (children == null) {
                children = populateChildren();
            }
            return children.values();
        }

        private @NotNull Map<String, EntryImpl> populateChildren() {
            try (Stream<Path> childStream = Files.list(path)) {
                return childStream.map(EntryImpl::new).collect(Collectors.toMap(EntryImpl::getName, Function.identity()));
            } catch (IOException e) {
                throw new IllegalStateException("Could not retrieve children", e);
            }
        }

        @Override
        @Nullable
        public Entry getChild(@NotNull String name) {
            if (children == null) {
                children = populateChildren();
            }
            return children.get(name);
        }
    }

    public final class ArchiveNotOpenException extends IllegalStateException {
        /**
         * 
         */
        private static final long serialVersionUID = 5634035426424134529L;

        public ArchiveNotOpenException(String archivePath) {
            super("Archive '" + archivePath + "' not open");
        }
    }
}
