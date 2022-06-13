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

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

class ReturnNodeAndLineNumberAnswer<T extends Collection<?>> implements Answer<T> {

    private final String nodePath;
    private final int lineNumber;
    private final T returnValue;

    public ReturnNodeAndLineNumberAnswer(String nodePath, int lineNumber) {
        this(nodePath, lineNumber, (T) Collections.emptyList());
    }

    public ReturnNodeAndLineNumberAnswer(String nodePath, int lineNumber, T returnValue) {
        this.nodePath = nodePath;
        this.lineNumber = lineNumber;
        this.returnValue = returnValue;
    }

    @Override
    public T answer(InvocationOnMock invocationOnMock) {
        Map nodePathsAndLineNumbers = invocationOnMock.getArgument(3, Map.class);
        nodePathsAndLineNumbers.put(nodePath, lineNumber);
        return returnValue;
    }
}
