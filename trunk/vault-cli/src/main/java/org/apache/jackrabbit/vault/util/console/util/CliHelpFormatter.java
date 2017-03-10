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

import java.io.PrintWriter;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.cli2.DisplaySetting;
import org.apache.commons.cli2.HelpLine;
import org.apache.commons.cli2.Option;
import org.apache.commons.cli2.builder.GroupBuilder;
import org.apache.commons.cli2.util.HelpFormatter;
import org.apache.jackrabbit.vault.util.console.CliCommand;

/**
 * Highly customized help formatter to work with {@link CliCommand}s.
 */
public class CliHelpFormatter extends HelpFormatter {

    public static final String SYS_PROP_TERM_WIDTH = "env.term.width";
    
    private String description;

    private String example;

    private CliCommand cmd;

    private boolean showUsage = true;

    private boolean skipToplevel = false;


    public CliHelpFormatter(final String gutterLeft, final String gutterCenter, final String gutterRight, final int fullWidth) {
        super(gutterLeft, gutterCenter, gutterRight, fullWidth);
    }

    public static CliHelpFormatter create() {
        return new CliHelpFormatter(HelpFormatter.DEFAULT_GUTTER_LEFT,
                HelpFormatter.DEFAULT_GUTTER_CENTER,
                HelpFormatter.DEFAULT_GUTTER_RIGHT,
                getDefaultWidth());
    }

    private static int getDefaultWidth() {
        int w = Integer.getInteger(SYS_PROP_TERM_WIDTH, HelpFormatter.DEFAULT_FULL_WIDTH).intValue();
        return Math.max(w, HelpFormatter.DEFAULT_FULL_WIDTH);
    }

    public CliCommand getCmd() {
        return cmd;
    }

    public void setCmd(CliCommand cmd) {
        this.cmd = cmd;
        setDescription(cmd.getLongDescription());
        setExample(cmd.getExample());
        //setShellCommand(cmd.getName());
        // we need a fake group for the command
        setGroup(new GroupBuilder().withOption(cmd.getCommand()).create());
        getFullUsageSettings().remove(DisplaySetting.DISPLAY_OPTIONAL);
        setSkipToplevel(true);
        //getDisplaySettings().remove(DisplaySetting.DISPLAY_PARENT_ARGUMENT);
    }

    public void printUsage() {
        if (showUsage) {
            super.printUsage();
        }
    }

    public boolean isSkipToplevel() {
        return skipToplevel;
    }

    public void setSkipToplevel(boolean skipToplevel) {
        this.skipToplevel = skipToplevel;
    }

    public boolean isShowUsage() {
        return showUsage;
    }

    public void setShowUsage(boolean showUsage) {
        this.showUsage = showUsage;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getExample() {
        return example;
    }

    public void setExample(String example) {
        this.example = example;
    }

    /**
     * Prints detailed help per option.
     */
    public void printHelp() {
        printDivider();

        printDescription();

        final Option option;
        final PrintWriter out = getPrintWriter();

        if ((getException() != null) && (getException().getOption() != null)) {
            option = getException().getOption();
        } else if (cmd != null) {
            option = cmd.getCommand();
        } else {
            option = getGroup();
        }

        // grab the HelpLines to display
        final List helpLines = option.helpLines(
                skipToplevel ? -1 : 0, getDisplaySettings(), getComparator());
        if (skipToplevel) {
            helpLines.remove(0);
        }
        
        // calculate the maximum width of the usage strings
        int usageWidth = 0;

        for (final Iterator i = helpLines.iterator(); i.hasNext();) {
            final HelpLine helpLine = (HelpLine) i.next();
            final String usage = helpLine.usage(getLineUsageSettings(), getComparator());
            usageWidth = Math.max(usageWidth, usage.length());
        }

        // build a blank string to pad wrapped descriptions
        final StringBuffer blankBuffer = new StringBuffer();

        for (int i = 0; i < usageWidth; i++) {
            blankBuffer.append(' ');
        }

        // print a blank line
        out.println();
        
        // determine the width available for descriptions
        final int descriptionWidth = Math.max(1, getPageWidth() - getGutterCenter().length() - usageWidth);

        // display each HelpLine
        for (final Iterator i = helpLines.iterator(); i.hasNext();) {
            // grab the HelpLine
            final HelpLine helpLine = (HelpLine) i.next();

            // wrap the description
            final List descList = wrap(helpLine.getDescription(), descriptionWidth);
            final Iterator descriptionIterator = descList.iterator();

            // display usage + first line of description
            printGutterLeft();
            pad(helpLine.usage(getLineUsageSettings(), getComparator()), usageWidth, out);
            out.print(getGutterCenter());
            pad((String) descriptionIterator.next(), descriptionWidth, out);
            printGutterRight();
            out.println();

            // display padding + remaining lines of description
            while (descriptionIterator.hasNext()) {
                printGutterLeft();

                //pad(helpLine.getUsage(),usageWidth,out);
                out.print(blankBuffer);
                out.print(getGutterCenter());
                pad((String) descriptionIterator.next(), descriptionWidth, out);
                printGutterRight();
                out.println();
            }
        }
        printExample();
        
        printDivider();
    }

    public void printDescription() {
        if (description != null) {
            getPrintWriter().println();
            getPrintWriter().println("Description:");
            for (final Iterator i = wrap(description, getPageWidth() - 2).iterator(); i.hasNext();) {
                printGutterLeft();
                getPrintWriter().print("  ");
                pad((String) i.next(), getPageWidth()-2, getPrintWriter());
                printGutterRight();
                getPrintWriter().println();
            }
        }
    }

    public void printExample() {
        if (example != null) {
            getPrintWriter().println();
            getPrintWriter().println("Example:");
            for (final Iterator i = wrap(example, getPageWidth() - 2).iterator(); i.hasNext();) {
                printGutterLeft();
                getPrintWriter().print("  ");
                pad((String) i.next(), getPageWidth()-2, getPrintWriter());
                printGutterRight();
                getPrintWriter().println();
            }
        }
    }
}