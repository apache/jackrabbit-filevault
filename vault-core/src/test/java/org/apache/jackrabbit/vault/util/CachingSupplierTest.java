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

import java.util.function.Supplier;

import org.junit.Test;
import org.mockito.AdditionalAnswers;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class CachingSupplierTest {

    @Test
    public void testLazyResolution() {
        Supplier<String> s = spyLambda(() -> "test");
        CachingSupplier<String> lazy = CachingSupplier.of(s);
        verify(s, times(0)).get();
        assertEquals("test", lazy.get());
        // ensure that it was resolved just once
        assertEquals("test", lazy.get());
        verify(s, times(1)).get();
    }

    @Test
    public void testLazyResolutionWithNull() {
        Supplier<Object> s = spyLambda(() -> null);
        CachingSupplier<Object> lazy = CachingSupplier.of(s);
        assertEquals(null, lazy.get());
        assertEquals(null, lazy.get());
        // resolution is done twice, as null is returned
        verify(s, times(2)).get();
    }

    // Spying on lambdas is not possible; see https://stackoverflow.com/questions/54328867/spying-a-lambda-with-mockito
    @SuppressWarnings("unchecked")
    static <T, P extends T> P spyLambda(P lambda) {
        Class<?>[] interfaces = lambda.getClass().getInterfaces();
        return (P) Mockito.mock((Class<T>) interfaces[0], AdditionalAnswers.delegatesTo(lambda));
    }
}
