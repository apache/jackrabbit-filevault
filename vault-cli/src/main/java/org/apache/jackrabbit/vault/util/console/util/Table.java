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

import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * <code>Table</code>...
 */
public class Table {
    private int numCols;
    private int[] lengths;
    private LinkedList rows = new LinkedList();

    public Table(int numCols) {
        this.numCols = numCols;
        lengths = new int[numCols];
    }

    public Row createRow() {
        return new Row(numCols);
    }

    public void addRow(String c1, String c2) {
        Row r = createRow();
        r.addCol(c1);
        r.addCol(c2);
        addRow(r);
    }

    public void addRow(Row row) {
        rows.add(row);
        for (int i=0; i<row.cols.length; i++) {
            if (row.cols[i] != null) {
                int l = row.cols[i].value.length();
                if (l > lengths[i]) {
                   lengths[i] = l;
                }
            }
        }
    }

    public void print() {
        Iterator iter = rows.iterator();
        while (iter.hasNext()) {
            Row r = (Row) iter.next();
            StringBuffer buf = new StringBuffer();
            r.print(buf, lengths);
            System.out.println(buf);
        }
    }

    public void sort(final int col) {
        Collections.sort(rows, new Comparator() {
            public int compare(Object o1, Object o2) {
                Row r1 = (Row) o1;
                Row r2 = (Row) o2;
                return r1.cols[col].value.compareTo(r2.cols[col].value);
            }
        });
    }

    public static class Row {
        private Table.Col[] cols;

        private int pos = 0;
        public Row(int numCols) {
            cols = new Table.Col[numCols];
        }

        public void addCol(String value) {
            addCol(value, false);
        }

        public void addCol(String value, boolean align) {
            cols[pos++] = new Table.Col(value, align);
        }

        public void print(StringBuffer buf, int[] lengths) {
            for (int i=0; i<cols.length; i++) {
                if (cols[i] != null) {
                    cols[i].print(buf, lengths[i]);
                }
            }
        }
    }

    public static class Col {
        public static final String SPACES = "                                "+
                "                                                             "+
                "                                                             "+
                "                                                             "+
                "                                                             "+
                "                                                             "+
                "                                                             ";
        String value="";

        private boolean alignRight;

        public Col(String value) {
            this.value = value;
        }

        public Col(String value, boolean align) {
            this.value = value;
            this.alignRight = align;
        }

        public void print(StringBuffer buf, int maxLength) {
            if (value.length()>maxLength) {
                buf.append(value.substring(0, maxLength));
            } else {
                if (alignRight) {
                    buf.append(SPACES.substring(0, maxLength - value.length()));
                }
                buf.append(value);
                if (!alignRight) {
                    buf.append(SPACES.substring(0, maxLength - value.length()));
                }
            }
            buf.append(" ");
        }
    }
}