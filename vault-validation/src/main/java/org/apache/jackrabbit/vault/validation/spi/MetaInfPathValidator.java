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

import java.nio.file.Path;
import java.util.Collection;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.annotation.versioning.ProviderType;


/**
 * Validator interface for validating file paths for files and folders
 * below META-INF.
 */
@ProviderType
public interface MetaInfPathValidator extends Validator {
    /**
     * Called for each file/folder below META-INF.
     * 
     * @param filePath the relative file/folder path to the META-INF directory
     * @return validation messages or {@code null}
     * @deprecated Use {@link #validateMetaInfPath(Path, Path, boolean)} instead.
     */
    @Deprecated 
    default @Nullable Collection<ValidationMessage> validateMetaInfPath(@NotNull Path filePath) { 
        throw new UnsupportedOperationException();
    }
    
    /**
     * Called for each file/folder below META-INF.
     * 
     * @param filePath the relative file/folder path to the META-INF directory (given in {@code basePath})
     * @param basePath the absolute path to the META-INF directory to which {@code filePath} is relative
     * @param isFolder {@code true} in case it is a folder, otherwise {@code false}
     * @return validation messages or {@code null}
     */
    default @Nullable Collection<ValidationMessage> validateMetaInfPath(@NotNull Path filePath, @NotNull Path basePath, boolean isFolder) { return validateMetaInfPath(filePath); }
   
   
}
