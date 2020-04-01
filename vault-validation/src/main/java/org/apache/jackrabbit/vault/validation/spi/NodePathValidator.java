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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.annotation.versioning.ProviderType;


/**
 * Validator interface for validating node paths.
 * For validators interested in the actual properties use either {@link DocumentViewXmlValidator} or {@link GenericJcrDataValidator}.
 * In contrast to {@link JcrPathValidator} might be called multiple times per file in case it covers multiple nodes.
 */
@ProviderType
public interface NodePathValidator extends Validator {
    /**
     * Called for each node being found in a package.
     * Empty node elements (in DocView files) are not included as they are only used for ordering purposes.
     * 
     * @param nodePath the absolute node path
     * @return validation messages or {@code null}
     * @deprecated Use {@link #validate(NodeContext)} instead!
     */
    @Deprecated
    default @Nullable Collection<ValidationMessage> validate(@NotNull String nodePath) { 
        throw new UnsupportedOperationException(); 
    }
    
    /**
     * Called for each node being found in a package.
     * Empty node elements (in DocView files) are not included as they are only used for ordering purposes.
     * 
     * @param nodeContext the information about the node context
     * @return validation messages or {@code null}
     */
    default @Nullable Collection<ValidationMessage> validate(@NotNull NodeContext nodeContext) {
        return validate(nodeContext.getNodePath());
    }
}
