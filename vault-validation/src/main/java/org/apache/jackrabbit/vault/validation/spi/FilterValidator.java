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

import java.util.Collection;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.osgi.annotation.versioning.ProviderType;

/**
 * Validator interface for package filters. Called for each {@code META-INF/vault/filter.xml} being found.
 *
 */
@ProviderType
public interface FilterValidator extends Validator {
    /**
     * Called when a filter is validated.
     * 
     * @param filter the deserialized filter
     * @return validation messages or {@code null}
     */
    @CheckForNull Collection<ValidationMessage> validate(@Nonnull WorkspaceFilter filter);
}
