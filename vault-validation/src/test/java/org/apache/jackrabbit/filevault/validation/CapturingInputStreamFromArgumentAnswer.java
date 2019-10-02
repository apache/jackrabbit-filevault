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
package org.apache.jackrabbit.filevault.validation;

import java.io.InputStream;
import java.nio.charset.Charset;

import org.apache.commons.io.IOUtils;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/** Captures an input stream from the argument with the given index and exposes that via
 * {@link CapturingInputStreamFromArgumentAnswer#getValue()} as String.
 * In addition returns the given value.
 * It does not close the given input stream nor reset it.
 *
 * @param <T> */
public final class CapturingInputStreamFromArgumentAnswer<T> implements Answer<T> {
    private String value;
    private final int argumentPositionOfInputStream;
    private final Charset encoding;
    private final T returnValue;

    public CapturingInputStreamFromArgumentAnswer(Charset encoding, int argumentPositionOfInputStream, T returnValue) {
        this.encoding = encoding;
        this.argumentPositionOfInputStream = argumentPositionOfInputStream;
        this.returnValue = returnValue;
    }

    @Override
    public T answer(InvocationOnMock invocation) throws Throwable {
        InputStream input = InputStream.class.cast(invocation.getArguments()[argumentPositionOfInputStream]);
        value = IOUtils.toString(input, encoding);
        return returnValue;
    }

    public String getValue() {
        return value;
    }
}