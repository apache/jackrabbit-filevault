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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

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

    private final File logFile;

    public SyncLog(File logFile) {
        this.logFile = logFile;
    }

    public void log(String fmt, Object ... args) {
        String msg = String.format(Locale.ENGLISH, fmt, args);
        log.info("{}", msg);

        StringBuilder line = new StringBuilder();
        line.append(DateTimeFormatter.ISO_DATE_TIME.format(OffsetDateTime.now(ZoneOffset.UTC)));
        line.append(msg);
        line.append("\n");
        
        try (FileOutputStream fileOutputStream = new FileOutputStream(logFile, true);
             OutputStreamWriter writer = new OutputStreamWriter(fileOutputStream, StandardCharsets.US_ASCII)) {
            writer.write(line.toString());
        } catch (IOException e) {
            log.error("Unable to update log file: {}", logFile, e);
        }
    }
}