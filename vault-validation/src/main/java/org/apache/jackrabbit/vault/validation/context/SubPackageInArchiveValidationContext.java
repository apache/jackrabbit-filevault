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
package org.apache.jackrabbit.vault.validation.context;

import java.io.IOException;
import java.nio.file.Path;

import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.validation.spi.ValidationContext;
import org.jetbrains.annotations.NotNull;

/**
 * Implements a {@link ValidationContext} based on a sub package given through an {@link Archive}.
 */
public class SubPackageInArchiveValidationContext extends ArchiveValidationContext {

    private final ValidationContext containerPackageContext;

    public SubPackageInArchiveValidationContext(@NotNull ArchiveValidationContext containerPackageContext, @NotNull Archive archive, @NotNull Path archivePath, @NotNull DependencyResolver resolver) throws IOException {
        super(archive, archivePath, resolver);
        this.containerPackageContext = containerPackageContext;
    }

    @Override
    public ValidationContext getContainerValidationContext() {
        return containerPackageContext;
    }

}
