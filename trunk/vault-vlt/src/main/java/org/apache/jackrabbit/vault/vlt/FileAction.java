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
package org.apache.jackrabbit.vault.vlt;

/**
 * The file action represents an action that was performed on a file.
 *
 */
public enum FileAction {

    ADDED ("A"),
    UPDATED ("U"),
    MERGED ("G"),
    CONFLICTED ("C"),
    DELETED ("D"),
    IGNORED ("I"),
    VOID (" "),
    REVERTED ("R");

    /**
     * A one letter name of the action
     */
    public final String letter;

    private FileAction(String shortName) {
        this.letter = shortName;
    }

    public String toString() {
        return name().toLowerCase() + " (" + letter + ")";
    }

}