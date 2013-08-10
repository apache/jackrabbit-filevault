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
package org.apache.jackrabbit.vault.util.diff;

import java.io.File;


/**
 * <code>Test</code>...
 */
public class Test {

    public static void main(String[] args) throws Exception {
        FileDocumentSource ds1 = new FileDocumentSource((new File(args[0])));
        FileDocumentSource ds2 = new FileDocumentSource((new File(args[1])));

        Document d1 = new Document(ds1, LineElementsFactory.create(ds1, false, "utf-8"));
        Document d2 = new Document(ds2, LineElementsFactory.create(ds2, false, "utf-8"));

        DocumentDiff diff = d1.diff(d2);
        StringBuffer buf = new StringBuffer();
        diff.write(buf, DiffWriter.LS_UNIX, 1);
        System.out.println(buf);
    }
}
