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

package org.apache.jackrabbit.vault.cli;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.nodetype.PropertyDefinition;

import org.apache.commons.cli2.CommandLine;
import org.apache.commons.cli2.Option;
import org.apache.commons.cli2.builder.CommandBuilder;
import org.apache.commons.cli2.builder.DefaultOptionBuilder;
import org.apache.commons.cli2.builder.GroupBuilder;
import org.apache.commons.cli2.option.Command;
import org.apache.jackrabbit.vault.util.console.ConsoleFile;
import org.apache.jackrabbit.vault.util.console.ExecutionException;
import org.apache.jackrabbit.vault.util.console.util.Table;

/**
 * Implements the 'ls' command.
 *
 */
public class CmdLsRepo extends AbstractCmdLs {

    private static final int F_FLAG_NT = 0x01;
    private static final int F_FLAG_UUID = 0x02;
    private static final int F_FLAG_LONG = 0x03;

    private Option optLong;

    private Option optNodeType;

    private Option optUUID;


    protected int getFormatFlags(VaultFsConsoleExecutionContext ctx, CommandLine cl) {
        int fmtFlag = 0;
        fmtFlag |= cl.hasOption(optNodeType) ? F_FLAG_NT : 0;
        fmtFlag |= cl.hasOption(optUUID) ? F_FLAG_UUID : 0;
        fmtFlag |= cl.hasOption(optLong) ? F_FLAG_LONG : 0;
        return fmtFlag;
    }

    protected Command createCommand() {
        return new CommandBuilder()
                .withName("ls")
                .withDescription(getShortDescription())
                .withChildren(new GroupBuilder()
                        .withName("Options:")
                        .withOption(optNodeType = new DefaultOptionBuilder()
                                .withShortName("n")
                                .withDescription("display the node type of the nodes")
                                .create())
                        .withOption(optUUID = new DefaultOptionBuilder()
                                .withShortName("u")
                                .withDescription("display the uuid of the referenceable nodes")
                                .create())
                        .withOption(optLong = new DefaultOptionBuilder()
                                .withShortName("l")
                                .withDescription("combines the flags 'n' and 'u'")
                                .create())
                        .withOption(argPath)
                        .create())
                .create();
    }


    protected void formatFile(ConsoleFile file, Table.Row row, int flags) {
        Object item = file.unwrap();
        if (item instanceof Node) {
            format((Node) item, row, flags, 0);
        } else if (item instanceof Property) {
            format((Property) item, row, flags, 0);
        } else {
            throw new ExecutionException("Illegal argument: " + item.getClass().getName());
        }
    }

    private void format(Node node, Table.Row r, int flags, int indent) {
        if ((flags & F_FLAG_UUID) > 0) {
            try {
                r.addCol(node.getUUID());
            } catch (RepositoryException e) {
                r.addCol("");
            }
        }
        if ((flags & F_FLAG_NT) > 0) {
            try {
                r.addCol(node.getPrimaryNodeType().getName());
            } catch (RepositoryException e) {
                r.addCol("");
            }
        }
        String mode = " + ";
        if (node.isModified()) {
            mode = "*+ ";
        } else if (node.isNew()) {
            mode = "++ ";
        }
        mode = Table.Col.SPACES.substring(0, indent * 2) + mode;
        try {
            if (node.getName().equals("")) {
                mode += "/";
            }
            r.addCol(mode + node.getName());
        } catch (RepositoryException e) {
            r.addCol("???");
        }
    }

    private void format(Property prop, Table.Row r, int flags, int indent) {
        PropertyDefinition def;
        try {
            def = prop.getDefinition();
        } catch (RepositoryException e) {
            throw new ExecutionException(e);
        }
        /*
        if ((flags & LS_FLAG_DEF) > 0) {
            StringBuffer buf = new StringBuffer();
            buf.append(def.isAutoCreated() ? "a" : "-");
            buf.append(def.isMandatory() ? "m" : "-");
            buf.append(def.isProtected() ? "p  " : "-  ");
            buf.append(OnParentVersionAction.nameFromValue(def.getOnParentVersion()).substring(0, 3));
            r.addCol(buf.toString());
        }
        */
        if ((flags & F_FLAG_UUID) > 0) {
            r.addCol("");
        }
        if ((flags & F_FLAG_NT) > 0) {
            try {
                r.addCol(PropertyType.nameFromValue(prop.getType()) + (def.isMultiple() ? "[]" : ""));
            } catch (RepositoryException e) {
                r.addCol("");
            }
        }
        String mode = " - ";
        if (prop.isModified()) {
            mode = "*- ";
        } else if (prop.isNew()) {
            mode = "-- ";
        }
        mode = Table.Col.SPACES.substring(0, indent * 2) + mode;
        try {
            r.addCol(mode + prop.getName());
        } catch (RepositoryException e) {
            r.addCol("");
        }
        /*
        if ((flags & LS_FLAG_VALUES) > 0) {
            if (def.isMultiple()) {
                Value[] vals = prop.getValues();
                StringBuffer buf = new StringBuffer();
                buf.append("{");
                for (int i=0; i<vals.length; i++) {
                    if (i>0) {
                        buf.append(", ");
                    }
                    buf.append(AbstractConsole.formatValue(vals[i], 140));
                }
                buf.append("}");
                r.addCol(buf.toString());
            } else {
                r.addCol(AbstractConsole.formatValue(prop.getValue(), 140));
            }
        }
        */
    }

}