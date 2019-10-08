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
package org.apache.jackrabbit.vault.validation;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.vault.validation.impl.util.ValidatorSettingsImpl;
import org.apache.jackrabbit.vault.validation.spi.ValidationContext;
import org.apache.jackrabbit.vault.validation.spi.Validator;
import org.apache.jackrabbit.vault.validation.spi.ValidatorFactory;
import org.apache.jackrabbit.vault.validation.spi.ValidatorSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates {@link ValidationExecutor}s. Holds a number of {@link ValidatorFactory} instances.
 */
public class ValidationExecutorFactory {

    /** All registered ValidatorFactories in the correct order (sorted by their ranking) */
    final List<ValidatorFactory> validatorFactories;

    /**
     * the default logger
     */
    private static final Logger log = LoggerFactory.getLogger(ValidationExecutorFactory.class);

    /** Creates a new instance with {@code ValidatorFactory} instances being created via the {@link ServiceLoader} being used with the given classloader.
     * 
     * @param classLoader the class loader to be used with the service loader
     */
    public ValidationExecutorFactory(ClassLoader classLoader) {
        this(ServiceLoader.load(ValidatorFactory.class, classLoader));
    }

    ValidationExecutorFactory(Iterable<ValidatorFactory> validatorFactoriesUnsorted) {
        // sort by service
        Comparator<ValidatorFactory> rankingComparator = Comparator.comparingInt(ValidatorFactory::getServiceRanking).reversed();
        this.validatorFactories = new LinkedList<>();
        for (ValidatorFactory factory : validatorFactoriesUnsorted) {
            this.validatorFactories.add(factory);
        }
        Collections.sort(validatorFactories, rankingComparator);
    }

    
    /** 
     * Creates a {@link ValidationExecutor} for the given context.
     * 
     * @param context the validation context given to the validators
     * @param isSubPackage {@code true} in case this is a subpackage, otherwise {@code false}
     * @param enforceSubpackageValidation {@code true} in case all validators should be also applied in any case to the sub package
     *            (independent of their {@link ValidatorFactory#shouldValidateSubpackages()} return value)
     * @param validatorSettingsById a map of {@link ValidatorSettings}. The key is the validator id. May be {@code null}.
     * @return either {@code null} or an executor (if at least one validator is registered)
     */
    public @CheckForNull ValidationExecutor createValidationExecutor(@Nonnull ValidationContext context, boolean isSubPackage, boolean enforceSubpackageValidation, Map<String, ? extends ValidatorSettings> validatorSettingsById) {
        Map<String, Validator> validatorsById = createValidators(context, isSubPackage, enforceSubpackageValidation, validatorSettingsById != null ? validatorSettingsById : Collections.emptyMap());
        if (validatorsById.isEmpty()) {
            return null;
        }
        return new ValidationExecutor(validatorsById);
    }

    private @Nonnull Map<String, Validator> createValidators(@Nonnull ValidationContext context, boolean isSubPackage, boolean enforceSubpackageValidation, Map<String, ? extends ValidatorSettings> validatorSettingsById) {
        Map<String, Validator> validatorsById = new LinkedHashMap<>();
        Set<String> validatorSettingsIds = new HashSet<>(validatorSettingsById.keySet());
        for (ValidatorFactory validatorFactory : validatorFactories) {
            if (!isSubPackage || enforceSubpackageValidation || validatorFactory.shouldValidateSubpackages()) {
                String validatorId = validatorFactory.getId();
                validatorSettingsIds.remove(validatorId);
                ValidatorSettings settings = validatorSettingsById.get(validatorId);
                if (settings == null) {
                    settings = new ValidatorSettingsImpl();
                }
                if (!settings.isDisabled()) {
                    Validator validator = validatorFactory.createValidator(context, settings);
                    if (validator != null) {
                        Validator oldValidator = validatorsById.putIfAbsent(validatorId, validator);
                        if (oldValidator != null) {
                            log.error("Found validators with duplicate id " + validatorId + ": " + oldValidator.getClass().getName() + " and " + validator.getClass().getName() + "(Duplicate, not considered)");
                        }
                    }
                } else {
                    log.debug("Skip disabled validator " + validatorId);
                }
            }
        }
        if (!validatorSettingsIds.isEmpty()) {
            log.warn("There are validator settings bound to invalid ids " + StringUtils.join(validatorSettingsIds, ", "));
        }
        return validatorsById;
    }

}
