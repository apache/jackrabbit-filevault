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
package org.apache.jackrabbit.vault.util.console.util;

import java.util.ArrayList;

/**
 * <code>Text</code>...
 */
public class Text {

    public static String[] parseLine(String line) {
        ArrayList tokens = new ArrayList();
        StringBuffer buf = new StringBuffer();
        int quote = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (Character.isWhitespace(c)) {
                if (quote == 0) {
                    if (buf.length() > 0) {
                        tokens.add(buf.toString());
                        buf = new StringBuffer();
                    }
                } else {
                    buf.append(c);
                }
            } else if (c == '\'') {
                if (quote == 1) {
                    quote = 0;
                } else if (quote == 0) {
                    quote = 1;
                } else {
                    buf.append(c);
                }
            } else if (c == '\"') {
                if (quote == 2) {
                    quote = 0;
                } else if (quote == 0) {
                    quote = 2;
                } else {
                    buf.append(c);
                }
            } else {
                buf.append(c);
            }
        }
        if (buf.length() > 0) {
            tokens.add(buf.toString());
        }
        return (String[]) tokens.toArray(new String[tokens.size()]);
    }
}