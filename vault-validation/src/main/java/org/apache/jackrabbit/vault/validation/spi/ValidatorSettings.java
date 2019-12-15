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

import java.util.Map;

import javax.annotation.Nonnull;

import org.osgi.annotation.versioning.ProviderType;

/**
 * Settings relevant for one {@link ValidatorFactory}.
 */
@ProviderType
public interface ValidatorSettings {

    /**
     * Returns the default severity.
     * 
     * @return the default severity for most {@link ValidationMessage}s being returned by the validator.
     */
    @Nonnull ValidationMessageSeverity getDefaultSeverity();

    /**
     * Returns the additional options.
     * 
     * @return list of options relevant for this validator
     */
    @Nonnull Map<String, String> getOptions();

    /**
     * Returns whether the validator is disabled.
     * 
     * @return {@code true} in case validator is disabled otherwise {@code false}
     */
    @Nonnull boolean isDisabled();

}