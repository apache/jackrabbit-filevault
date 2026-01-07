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
package org.apache.jackrabbit.vault.util.console;

import java.util.List;
import java.util.Map;

import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;
import org.jline.reader.impl.completer.StringsCompleter;

public class ContextAwareCompleter implements Completer {

    private final Map<String, Completer> contextCompleters;

    public ContextAwareCompleter(Map<String, ConsoleExecutionContext> contexts) {
        if (contexts == null || contexts.isEmpty()) {
            throw new IllegalArgumentException("No contexts provided for ContextAwareCompleter");
        }
        contextCompleters = new java.util.HashMap<>();
        for (Map.Entry<String, ConsoleExecutionContext> entry : contexts.entrySet()) {
            Iterable<String> commands = entry.getValue().getCommandsGroup().getTriggers();
            contextCompleters.put(entry.getKey(), new StringsCompleter(commands));
        }
    }

    @Override
    public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        // Get current context from reader variables
        String context = (String) reader.getVariable(Console.VARIABLE_CONTEXT_NAME);
        if (context == null) {
            throw new IllegalStateException("No context set in reader variables");
        }
        // Use the appropriate completer for this context
        Completer contextCompleter = contextCompleters.get(context);
        // If no completer found for context, fallback to a default behavior
        if (contextCompleter == null) {
            throw new IllegalStateException("No completer found for context: " + context);
        }
        contextCompleter.complete(reader, line, candidates);
    }
}
