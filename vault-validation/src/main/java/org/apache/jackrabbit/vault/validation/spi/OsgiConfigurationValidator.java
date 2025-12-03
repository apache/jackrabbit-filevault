/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.jackrabbit.vault.validation.spi;

import java.util.Collection;
import java.util.Dictionary;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Validator interface for OSGi configurations in any of the serialization formats supported by
 * <a href="https://sling.apache.org/documentation/bundles/configuration-installer-factory.html">Sling Configuration Installer Factory</a>.
 * @since 3.7.0
 */
public interface OsgiConfigurationValidator extends Validator {

    /**
     * Called for each serialized OSGi configuration found in the content package.
     * @param config the deserialized configuration properties as defined in <a href="https://docs.osgi.org/specification/osgi.cmpn/7.0.0/service.cm.html#service.cm.configuration.properties">
     * OSGi Config Admin Service Specification, $104.4.3</a> (but as {@link Map} instead of as {@link Dictionary})
     * @param pid either the PID or the factory PID of the configuration properties
     * @param subname either null (for regular PID) or the alias (for factory PID configurations)
     * @param nodePath the repository path of the node which contains the given OSGi configuration
     * @return validation messages or {@code null}
     */
    @Nullable
    Collection<ValidationMessage> validateConfig(
            @NotNull Map<String, Object> config,
            @NotNull String pid,
            @Nullable String subname,
            @NotNull String nodePath);
}
