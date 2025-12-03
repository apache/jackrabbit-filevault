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
package org.apache.jackrabbit.vault.validation.context;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.jackrabbit.vault.validation.spi.ValidationContext;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractValidationContext implements ValidationContext {

    @NotNull
    protected final Map<String, Object> attributes;

    protected AbstractValidationContext() {
        this.attributes = new HashMap<>();
    }

    @Override
    public Object setAttribute(String name, Object value) {
        return attributes.put(name, value);
    }

    @Override
    public Object getAttribute(String name) {
        return attributes.get(name);
    }

    @Override
    public Set<String> getAttributeNames() {
        return attributes.keySet();
    }
}
