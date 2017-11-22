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

package org.apache.jackrabbit.vault.packaging.impl;

import java.util.LinkedList;
import java.util.List;

import org.apache.jackrabbit.vault.fs.io.AbstractExporter;
import org.apache.jackrabbit.vault.packaging.ExportPostProcessor;

/**
 * Helper class that allows to chain post processors.
 */
public class CompositeExportProcessor implements ExportPostProcessor {

    private List<ExportPostProcessor> processors = new LinkedList<>();

    public void addProcessor(ExportPostProcessor p) {
        processors.add(p);
    }

    @Override
    public void process(AbstractExporter exporter) {
        for (ExportPostProcessor p: processors) {
            p.process(exporter);
        }
    }
}
