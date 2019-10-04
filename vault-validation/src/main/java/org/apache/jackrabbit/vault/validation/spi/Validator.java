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

import org.osgi.annotation.versioning.ProviderType;

/**
 * A validator is created per Maven module individually and only used from a single thread.
 * Also the instance is only used for one package at most (i.e. subpackages get another instance)
 * Instead of implementing this generic interface each validator should rather implement one of the
 * sub interfaces.
 *
 */
@ProviderType
public interface Validator {

    /**
     * Called when the validation is done for one {@link ValidationContext} (i.e. this instance is no longer needed)
     * @return validation messages or {@code null}
     */
     @CheckForNull Collection<ValidationMessage> done();


}
