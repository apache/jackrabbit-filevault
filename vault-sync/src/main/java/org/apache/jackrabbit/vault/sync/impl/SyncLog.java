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
package org.apache.jackrabbit.vault.sync.impl;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code SyncLog}...
 */
public class SyncLog {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(SyncLog.class);

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss ");

    private final File logFile;

    public SyncLog(File logFile) {
        this.logFile = logFile;
    }

    public void log(String fmt, Object ... args) {
        String msg = String.format(fmt, args);
        log.info("{}", msg);

        StringBuilder line = new StringBuilder();
        line.append(DATE_FMT.format(Instant.now()));
        line.append(msg);
        line.append("\n");
        try (FileWriter writer = new FileWriter(logFile, true)) {
            writer.write(line.toString());
        } catch (IOException e) {
            log.error("Unable to update log file: {}", logFile, e);
        }
    }
}