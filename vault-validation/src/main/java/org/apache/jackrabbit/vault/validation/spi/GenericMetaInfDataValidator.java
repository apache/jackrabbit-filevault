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

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import org.osgi.annotation.versioning.ProviderType;

/**
 * Low-level validator interface for all files below META-INF (including {@code vault/filter.xml} and {@code vault/properties.xml}).
 * 
 * For validating properties and filter rather use the high-level validators {@link PropertiesValidator} or {@link FilterValidator}.
 * For validators only considering the file name use {@link MetaInfPathValidator} instead.
 
 */
@ProviderType
public interface GenericMetaInfDataValidator extends Validator {

    /**
     * Called for each file below META-INF.
     * Only called in case {@link #shouldValidateMetaInfData(Path)} returned true for the given path.
     *
     * @param input the input stream of the META-INF file located at filePath
     * @param filePath file path relative to the META-INF directory (i.e. does not start with {@code META-INF})
     * @return a collection of validation messages or {@code null}
     * @throws IOException in case the input stream could not be accessed
     */
    @CheckForNull Collection<ValidationMessage> validateMetaInfData(@Nonnull InputStream input, @Nonnull Path filePath) throws IOException;

    /**
     * Called for each file below META-INF.
     * 
     * @param filePath file path relative to the META-INF directory (i.e. does not start with {@code META-INF})
     * @return {@code true} in case the file should be validated, otherwise {@code false}
     */
    boolean shouldValidateMetaInfData(@Nonnull Path filePath);

    
}
