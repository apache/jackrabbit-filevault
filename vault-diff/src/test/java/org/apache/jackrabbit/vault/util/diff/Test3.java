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
public class Test3 {

    public static void main(String[] args) throws Exception {
        FileDocumentSource ds1 = new FileDocumentSource((new File(args[0])));
        FileDocumentSource ds2 = new FileDocumentSource((new File(args[1])));
        FileDocumentSource ds3 = new FileDocumentSource((new File(args[2])));

        Document d1 = new Document(ds1, LineElementsFactory.create(ds1, false, "utf-8"));
        Document d2 = new Document(ds2, LineElementsFactory.create(ds2, false, "utf-8"));
        Document d3 = new Document(ds3, LineElementsFactory.create(ds3, false, "utf-8"));

        //DocumentDiff diff = new DocumentDiff(d1, d2);
        DocumentDiff3 diff = new DocumentDiff3(d1, d2, d3);
        for (Hunk3 hunk  = diff.getHunks(); hunk != null; hunk = hunk.next()) {
            System.out.print(hunk);
        }
        System.out.println("---------------------------- final ------------------");
        StringBuffer buf = new StringBuffer();
        diff.write(buf, DiffWriter.LS_UNIX, true);
        System.out.print(buf);

    }
}