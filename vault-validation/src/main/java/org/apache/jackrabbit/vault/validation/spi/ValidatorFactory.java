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

import java.util.ServiceLoader;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import org.osgi.annotation.versioning.ProviderType;

/**
 * Each {@link Validator} is created via the according factory.
 * The factories should be registered via the {@link ServiceLoader} mechanism
 */
@ProviderType
public interface ValidatorFactory {
    /**
     * Reserved prefix for all validator ids integrated in this JAR. No custom validator ids must start with this prefix.
     */
    public static final String ID_PREFIX_JACKRABBIT = "jackrabbit-";
    /**
     * Creates a new validator for the given context.
     * 
     * @param context the validation context
     * @param settings the validator settings
     * @return a new validator instance (lifecycle bound to the package outlined in context) or {@code null} in case there is no validation relevant for the given context
     */
    @CheckForNull Validator createValidator(@Nonnull ValidationContext context, @Nonnull ValidatorSettings settings);

    /**
     * Returns whether the validator should by default also called for subpackages.
     * 
     * @return {@code true} in case the validation with this validator should also happen for subpackages (recursively), otherwise {@code false}
     */
    boolean shouldValidateSubpackages();

    /**
     * Returns the validator ID. It should be unique i.e. not overlap between any two validators. To achieve that
     * use the format {@code <prefix>-<name>} for every ID. Reserved prefixes are "jackrabbit" (used by all OOTB validators), "aem" and "sling".
     * For custom validators use a company name as prefix. The name should not contain the string "validator".
     * The id should only contain lower case characters.
     * @return the id of the validator returned by {@link #createValidator(ValidationContext, ValidatorSettings)}
     */
    @Nonnull String getId();
    
    /**
     * The service ranking will influence the order in which the validators will be called.
     * In general:
     * The higher the ranking the earlier it will be executed.
     * @return the service ranking
     */
    int getServiceRanking();
}
