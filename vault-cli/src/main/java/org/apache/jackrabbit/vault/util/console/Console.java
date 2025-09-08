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
package org.apache.jackrabbit.vault.util.console;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.jackrabbit.vault.util.console.util.Table;
import org.jline.reader.History;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.impl.history.DefaultHistory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * {@code Console}...
 */
public class Console {

    static final String VARIABLE_CONTEXT_NAME = "CONTEXT";

    /**
     * the default logger
     */
    static final Logger log = LoggerFactory.getLogger(Console.class);

    private ConsoleExecutionContext currentCtx;

    private final Map<String, ConsoleExecutionContext> contexts = new HashMap<>();

    private final AbstractApplication app;

    private LineReader reader;

    /**
     * indicates console is running
     */
    private boolean running = false;


    public Console(AbstractApplication app) {
        this.app = app;
    }

    public void addContext(ConsoleExecutionContext ctx) {
        if (contexts.containsKey(ctx.getName())) {
            throw new IllegalArgumentException("Context with name '" + ctx.getName() + "' already registered.");
        }
        contexts.put(ctx.getName(), ctx);
        ctx.attach(this);
        switchContext(ctx.getName());
    }

    public void removeContext(ConsoleExecutionContext ctx) {
        contexts.remove(ctx.getName());
    }

    public void switchContext(ConsoleExecutionContext ctx) {
        if (!contexts.containsValue(ctx)) {
            throw new IllegalArgumentException("Context not installed: " + ctx.getName());
        }
        switchContext(ctx.getName());
    }

    public void switchContext(String name) {
        if (name == null) { 
            // lists all contexts
            Iterator iter = contexts.keySet().iterator();
            Table t = new Table(2);
            while (iter.hasNext()) {
                name = (String) iter.next();
                ConsoleExecutionContext c = (ConsoleExecutionContext) contexts.get(name);
                if (c == currentCtx) {
                    name = "*" + name;
                } else {
                    name = " " + name;
                }
                String path = c.getProperty(AbstractApplication.KEY_PATH);
                t.addRow(name, path);
            }
            t.print();

        } else {
            if (!contexts.containsKey(name)) {
                throw new ExecutionException("No such context: " + name);
            }
            currentCtx = contexts.get(name);
            if (reader != null) {
                reader.setVariable(VARIABLE_CONTEXT_NAME, name);
            }
            log.info("Switched to context '{}'", name);
        }
    }

    protected void setup() {
    }

    protected AbstractApplication getApplication() {
        return app;
    }

    protected LineReader createJLineReader() {
        History history = new DefaultHistory();
        return LineReaderBuilder.builder()
            .history(history)
            .completer(new ContextAwareCompleter(contexts))
            // set current context
            .variable(VARIABLE_CONTEXT_NAME, currentCtx.getName())
            .build();
    }

    public void run() throws IOException {

        reader = createJLineReader();
        // setup and start
        setup();

        running = true;
        while (running) {
            try {
                String line = reader.readLine(getPrompt());
                if (line == null) {
                    running = false;
                } else {
                    line = line.trim();
                    if (line.length() > 0) {
                        if (line.startsWith("!")) {
                            // re-execute event from history
                            String oldLine;
                            try {
                                int historyIndex = Integer.parseInt(line.substring(1).trim());
                                oldLine = reader.getHistory().get(historyIndex - 1);
                            } catch (Exception e) {
                                System.out.println("  " + line + ": event not found");
                                continue;
                            }
                            reader.getHistory().add(oldLine);
                            System.out.println("Executing '" + oldLine + "'");
                            line = oldLine;
                        }
                        long now = System.currentTimeMillis();
                        currentCtx.execute(line);
                        long time = System.currentTimeMillis() - now;
                        System.out.println("Command completed in " + time + "ms");
                    }
                }
            } catch (Exception e) {
                log.error("There was a unexpected problem while processing the line.", e);
            }
        }
        close();
    }

    public LineReader getReader() {
        return reader;
    }

    public boolean isRunning() {
        return running;
    }

    public void quit() {
        running = false;
    }

    private String getPrompt() {
        String p = app.getProperty(AbstractApplication.KEY_PROMPT);
        StringBuffer out = new StringBuffer();
        for (int i = 0; i < p.length(); i++) {
            char c = p.charAt(i);
            if (c == '$') {
                c = p.charAt(++i);
                if (c == '{') {
                    int j = p.indexOf('}', i);
                    String key = p.substring(i + 1, j);
                    String prop = currentCtx.getProperty(key);
                    if (prop != null && prop.length() > 40) {
                        prop = "..." + prop.substring(prop.length() - 37);
                    }
                    out.append(prop);
                    i = j;
                } else {
                    out.append('$');
                    out.append(c);
                }
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    protected boolean close() {
        return true;
    }

}