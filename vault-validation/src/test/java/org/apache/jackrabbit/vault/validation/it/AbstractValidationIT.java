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
package org.apache.jackrabbit.vault.validation.it;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.util.Constants;
import org.apache.jackrabbit.vault.validation.ValidationExecutor;
import org.apache.jackrabbit.vault.validation.ValidationExecutorFactory;
import org.apache.jackrabbit.vault.validation.ValidationViolation;
import org.apache.jackrabbit.vault.validation.spi.ValidationContext;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessageSeverity;
import org.apache.jackrabbit.vault.validation.spi.ValidatorSettings;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Base class for integration tests */
public abstract class AbstractValidationIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractValidationIT.class);

    public @NotNull Collection<ValidationViolation> validatePackageFolder(String resourceName) throws URISyntaxException, IOException, ConfigurationException {
        return validatePackageFolder(resourceName, Collections.emptyMap());
    }

    public @NotNull Collection<ValidationViolation> validatePackageFolder(String resourceName, Map<String, ? extends ValidatorSettings> validatorsSettings) throws URISyntaxException, IOException, ConfigurationException {
        URL resourceUrl = AbstractValidationIT.class.getResource(resourceName);
        if (resourceUrl == null) {
            throw new IllegalArgumentException("Could not find resource with name " + resourceName);
        }
        Path path = Paths.get(resourceUrl.toURI());
        Collection<ValidationViolation> violations = validatePackageFolder(path, validatorsSettings);
        violations = violations.stream().filter(v -> v.getSeverity().ordinal() > ValidationMessageSeverity.INFO.ordinal()).collect(Collectors.toList());
        violations.forEach(v -> LOGGER.info(v.toString()));
        return violations;
    }

    public @NotNull Collection<ValidationViolation> validatePackageFolder(Path rootPath) throws IOException, ConfigurationException {
        return validatePackageFolder(rootPath, Collections.emptyMap());
    }

    public @NotNull Collection<ValidationViolation> validatePackageFolder(Path rootPath, Map<String, ? extends ValidatorSettings> validatorsSettings) throws IOException, ConfigurationException {
        ValidationExecutorFactory executorFactory = new ValidationExecutorFactory(Thread.currentThread().getContextClassLoader());
        ValidationContext context = new PackageFolderValidationContext(rootPath);
        ValidationExecutor executor = executorFactory.createValidationExecutor(context, false, false, validatorsSettings);
        if (executor == null) {
            Assert.fail("No validator services found in current thread's context class loader");
        }
        return validatePackageFolder(executor, rootPath);
    }

    private static @NotNull Collection<ValidationViolation> validatePackageFolder(ValidationExecutor executor, Path rootPath) throws IOException {
        Collection<ValidationViolation> violations = new LinkedList<>();
        try (Stream<Path> files = Files.walk(rootPath).skip(1).sorted(new ParentAndDotContentXmlFirstComparator())) {
            files.forEach(file -> {
                try {
                    violations.addAll(validateFile(executor, rootPath, file));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
        violations.addAll(executor.done());
        return violations;
    }

    private static Collection<ValidationViolation> validateFile(ValidationExecutor executor, Path rootPath, Path file) throws IOException {
        int rootPathLevel = rootPath.getNameCount();
        if (file.getNameCount() < rootPathLevel + 2) {
            return Collections.emptyList();
        }
        String topLevelFolderName = file.getName(rootPathLevel).toString();
        final boolean isMetaInf;
        final Path basePath = file.subpath(0, rootPathLevel+1);
        final Path relativeFilePath = file.subpath(rootPathLevel + 1, file.getNameCount());
        if (topLevelFolderName.equals(Constants.META_INF)) {
            isMetaInf = true;
        } else if (topLevelFolderName.equals(Constants.ROOT_DIR)) {
            isMetaInf = false;
        } else {
            throw new IllegalArgumentException("Unexpected folder with name " + topLevelFolderName + " below " + rootPath);
        }
        if (Files.isDirectory(file)) {
            return validateStream(executor, isMetaInf, null, relativeFilePath, basePath);
        } else {
            try (InputStream inputStream = Files.newInputStream(file)) {
                return validateStream(executor, isMetaInf, inputStream, relativeFilePath, basePath);
            }
        }
    }

    private static @NotNull Collection<ValidationViolation> validateStream(ValidationExecutor executor, boolean isMetaInf, InputStream input, Path relativeFilePath, Path basePath) throws IOException {
        if (isMetaInf) {
            return executor.validateMetaInf(input, relativeFilePath, basePath);
        } else {
            return executor.validateJcrRoot(input, relativeFilePath, basePath);
        }
    }

    /** 
     * Comparator on file paths which makes sure that parent folders and files named {@code .content.xml} come first.
     */
    static final class ParentAndDotContentXmlFirstComparator implements Comparator<Path> {
        
        @Override
        public int compare(Path path1, Path path2) {
            if (path2.startsWith(path1)) {
                return -1;
            } else if (path1.startsWith(path2)) {
                return 1;
            } else if (path1.getParent().equals(path2.getParent())) {
                // in same branch?
                String s1 = path1.getFileName().toString();
                String s2 = path2.getFileName().toString();
                if (Constants.DOT_CONTENT_XML.equals(s1) && Constants.DOT_CONTENT_XML.equals(s2)) {
                    return 0;
                } else if (Constants.DOT_CONTENT_XML.equals(s1)) {
                    return -1;
                } else if (Constants.DOT_CONTENT_XML.equals(s2)) {
                    return 1;
                }
                // lexicographical order for sibling files
                return s1.compareTo(s2);
            } else {
                // in different branches of the folder tree
                return -1;
            }
        }
    }
}
