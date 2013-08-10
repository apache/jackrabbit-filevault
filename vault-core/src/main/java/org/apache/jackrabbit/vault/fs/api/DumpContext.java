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

package org.apache.jackrabbit.vault.fs.api;

import java.io.PrintWriter;
import java.util.LinkedList;

/**
 * <code>DumpHandler</code>...
 *
 */
public class DumpContext {

    private final PrintWriter out;

    private LinkedList<String> stack = new LinkedList<String>();

    public DumpContext(PrintWriter out) {
        this.out = out;
        stack.add("");
    }

    public void println(String str) {
        out.println(str);
    }
    
    public void println(boolean isLast, String str) {
        out.print(stack.getLast());
        out.print(isLast ? "`-- " : "|-- ");
        out.println(str);
    }

    public void printf(boolean isLast, String format, Object ... args) {
        println(isLast, String.format(format, args));
    }
    
    public void indent(boolean isLast) {
        String ind = stack.getLast() + (isLast ? "    " : "|   ");
        stack.addLast(ind);
    }

    public void outdent() {
        stack.removeLast();
    }

    public void flush() {
        out.flush();
    }

}