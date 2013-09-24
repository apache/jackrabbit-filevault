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

package org.apache.jackrabbit.vault.util;

/**
 * Implements a repository to platform name formatter. 
 * 
 * <p>Illegal characters a
 * generally escaped using the url escaping format, i.e. replacing the char
 * by a '%' hex(char) sequence. special treatment is used for the ':' char
 * since it's used quite often as namespace prefix separator. the
 * PREFIX ':' NAME sequence is replaced by '_' PREFIX '_' NAME. item names
 * that would generate the same pattern are escaped with an extra leading '_'.
 * 
 * <p>Examples:
 * 
 * <pre>
 * +-------------------+----------------------+----+----+
 * | repository name   | platform name        | pp | sp |
 * +-------------------+----------------------+----+----+
 * | test.jpg          | test.jpg             | -1 | -1 |
 * | cq:content        | _cq_content          |  2 | -1 |
 * | cq:test_image.jpg | _cq_test_image.jpg   |  2 |  7 |
 * | test_image.jpg    | test_image.jpg       | -1 |  4 |
 * | _testimage.jpg    | _testimage.jpg       | -1 |  0 |
 * | _test_image.jpg   | __test_image.jpg     | -1 |  0 |
 * +-------------------+----------------------+----+----+
 * | cq:test:image.jpg | _cq_test%3aimage.jpg |  2 | -1 |
 * | _cq_:test.jpg     | __cq_%3atest.jpg     |  4 |  0 |
 * | _cq:test.jpg      | __cq%3atest.jpg      |  3 |  0 |
 * | cq_:test.jpg      | cq_%3atest.jpg       |  3 |  2 |
 * +-------------------+----------------------+----+----+
 * </pre>
 * 
 * note for the 2nd set of examples the cases are very rare and justify the
 * ugly '%' escaping.
 *
 */
public class PlatformNameFormat {

    /**
     * Returns the platform name for a given repository name. Unsupported
     * characters are URL escaped (i.e. %xx).
     *
     * Note: Forward slashes '/' are not escaped since they never occur in a
     * jcr name. so this method can also be used to encode paths.
     *
     * @param repositoryName the repository name
     * @return the (escaped) platform name.
     */
    public static String getPlatformName(String repositoryName) {
        StringBuffer buf = new StringBuffer("_");
        boolean escapeColon = false;
        boolean useUnderscore = false;
        int numUnderscore = 0;
        for (int i=0; i<repositoryName.length(); i++) {
            char c = repositoryName.charAt(i);
            switch (c) {
                 case':':
                     if (!escapeColon && i>0) {
                         // pure prefix
                         escapeColon = true;
                         useUnderscore = true;
                         numUnderscore = 2;
                         buf.append('_');
                     } else {
                         buf.append("%3a");
                     }
                     break;
                 case '_':
                     if (i==0) {
                         useUnderscore = true;
                     }
                     numUnderscore++;
                     escapeColon=true;
                     buf.append(c);
                     break;
                 case'\\':
                 case'<':
                 case'>':
                 case'|':
                 case'\"':
                 case '/':
                 case'?':
                 case'%':
                     buf.append('%');
                     buf.append(Character.forDigit(c / 16, 16));
                     buf.append(Character.forDigit(c % 16, 16));
                     break;
                 default:
                     buf.append(c);
             }
        }
        if (useUnderscore && numUnderscore > 1) {
            return buf.toString();
        } else {
            return buf.substring(1);
        }
    }

    /**
     * Returns the platform path for the given repository one.
     * @param repoPath the repository path
     * @return the platform path
     */
    public static String getPlatformPath(String repoPath) {
        String[] elems = Text.explode(repoPath, '/', true);
        for (int i=0; i<elems.length; i++) {
            if (elems[i].length() > 0) {
                elems[i] = getPlatformName(elems[i]);
            }
        }
        return Text.implode(elems, "/");
    }

    /**
     * Returns the repository name for a given platform name.
     *
     * @param platformName the platform name
     * @return the (unescaped) repository name.
     */
    public static String getRepositoryName(String platformName) {
        StringBuffer buffer = new StringBuffer("_");
        boolean firstUnderscore = false;
        for (int i=0; i<platformName.length(); i++) {
            char c = platformName.charAt(i);
            if (c == '%') {
                if (platformName.length() > i+2) {
                    int a = Character.digit(platformName.charAt(++i), 16);
                    int b = Character.digit(platformName.charAt(++i), 16);
                    c = (char) (a * 16 + b);
                }
            } else if (c == '_') {
                if (i==0) {
                    firstUnderscore = true;
                    if (platformName.length()>1) {
                        c = platformName.charAt(++i);
                        if (c == '_') {
                            buffer.append('_');
                            firstUnderscore = false;
                        } else {
                            buffer.append(c);
                        }
                    }
                    continue;
                } else if (firstUnderscore) {
                    c = ':';
                    firstUnderscore = false;
                }
            }
            buffer.append(c);
        }
        if (firstUnderscore) {
            // pending underscore
            return buffer.toString();
        } else {
            return buffer.substring(1);
        }
    }

    /**
     * Returns the repository path for the given platform one.
     * @param path the platform path
     * @return the repository path
     */
    public static String getRepositoryPath(String path) {
        String[] elems = Text.explode(path, '/', true);
        for (int i=0; i<elems.length; i++) {
            if (elems[i].length() > 0) {
                elems[i] = getRepositoryName(elems[i]);
            }
        }
        return Text.implode(elems, "/");
    }

    /**
     * Returns the repository path for the given platform one.
     * @param path the platform path
     * @param respectDotDir if <code>true</code>, all ".dir" are removed.
     * @return the repository path
     */
    public static String getRepositoryPath(String path, boolean respectDotDir) {
        String[] elems = Text.explode(path, '/', true);
        for (int i=0; i<elems.length; i++) {
            if (elems[i].length() > 0) {
                if (respectDotDir && elems[i].endsWith(".dir")) {
                    elems[i] = getRepositoryName(elems[i].substring(0, elems[i].length() - 4));
                } else {
                    elems[i] = getRepositoryName(elems[i]);
                }
            }
        }
        return Text.implode(elems, "/");
    }


}