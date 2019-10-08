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
package org.apache.jackrabbit.vault.validation.spi;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;

import javax.annotation.Nonnull;

import org.osgi.annotation.versioning.ProviderType;

/**
 * Low-level validator interface for all files below jcr_root.
 * For Document View XML files rather use {@link DocumentViewXmlValidator}.
 * For validators only considering file name or node name use {@link JcrPathValidator} or {@link NodePathValidator} instead.
 */
@ProviderType
public interface GenericJcrDataValidator extends Validator {

    /**
     * Called for each file below jcr_root.
     * Only called in case {@link #shouldValidateJcrData(Path)} returned {@code true} for the given path.
     * 
     * @param input the input stream of the file which ends up below jcr_root in the package located at filePath
     * @param filePath file path relative to the jcr_root directory (i.e. does not start with {@code jcr_root})
     * @param nodePathsAndLineNumbers a map which should be filled with all node path and their according line numbers if nodes are detected in the given input
     * @return a collection of validation messages or {@code null}
     * @throws IOException in case the input stream could not be accessed
     */
    Collection<ValidationMessage> validateJcrData(@Nonnull InputStream input, @Nonnull Path filePath, @Nonnull Map<String, Integer> nodePathsAndLineNumbers) throws IOException;

    /**
     * Called for each file below jcr_root.
     * 
     * @param filePath file path relative to the jcr_root directory (i.e. does not start with {@code jcr_root})
     * @return {@code true} in case the file should be validated, otherwise {@code false}
     */
    boolean shouldValidateJcrData(@Nonnull Path filePath);
}
