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
package org.apache.jackrabbit.vault.validation.spi.impl;

import org.apache.jackrabbit.vault.validation.spi.ValidationContext;
import org.apache.jackrabbit.vault.validation.spi.Validator;
import org.apache.jackrabbit.vault.validation.spi.ValidatorFactory;
import org.apache.jackrabbit.vault.validation.spi.ValidatorSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.kohsuke.MetaInfServices;

@MetaInfServices
public final class DuplicateUuidValidatorFactory implements ValidatorFactory {

    @Override
    public @Nullable Validator createValidator(@NotNull ValidationContext context, @NotNull ValidatorSettings settings) {
        // TODO: check cross-package duplicates
        return new DuplicateUuidValidator(settings.getDefaultSeverity(), context.getFilter());
    }

    @Override
    public boolean shouldValidateSubpackages() {
        return true;
    }

    @Override
    public @NotNull String getId() {
        return ValidatorFactory.ID_PREFIX_JACKRABBIT + "duplicateuuid";
    }

    @Override
    public int getServiceRanking() {
        return 0;
    }

    
}
