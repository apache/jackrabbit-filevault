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
package org.apache.jackrabbit.vault.util;

import javax.jcr.ValueFormatException;

/**
 * Wraps a {@link ValueFormatException} with an unchecked exception.
 * Useful in {@link FunctionalInterface} methods/lambda expressions which must not throw checked exceptions.
 * @since 3.7.0
 *
 */
public class UncheckedValueFormatException extends RuntimeException {

    private static final long serialVersionUID = 7179774059211440453L;

    public UncheckedValueFormatException(ValueFormatException e) {
        super(e);
    }

    @Override
    public synchronized ValueFormatException getCause() {
        return (ValueFormatException) super.getCause();
    }
}
