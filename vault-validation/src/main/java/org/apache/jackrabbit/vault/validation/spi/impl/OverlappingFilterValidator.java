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
package org.apache.jackrabbit.vault.validation.spi.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.jackrabbit.util.Text;
import org.apache.jackrabbit.vault.fs.api.FilterSet.Entry;
import org.apache.jackrabbit.vault.fs.api.PathFilter;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.filter.DefaultPathFilter;
import org.apache.jackrabbit.vault.validation.spi.FilterValidator;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessage;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessageSeverity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class OverlappingFilterValidator implements FilterValidator {

    static final String MESSAGE_OVERLAPPING_FILTER_ROOTS = "Filter root '%s' in package '%s' potentially overlapping with filter root '%s' in package '%s' (prior checking include/exclude patterns)";
    static final String MESSAGE_OVERLAPPING_FILTERS = "Filter root '%s' with include pattern %s in package '%s' potentially overlapping with filter '%s' in package '%s'";

    private static final Pattern PATTERN_OPTIONAL_GROUP = Pattern.compile("\\(.*\\)\\?");
    private static final Pattern PATTERN_ESCAPED_CHARACTER = Pattern.compile("\\\\([^\\(])");
    private static final String PATTERN_ALLOW_ALL = ".*";
    /** key = path of package (might be a nested subpackage) */
    private final NavigableMap<String, List<PathFilterSet>> filtersPerPackages = new TreeMap<>();
    private final ValidationMessageSeverity severityForOverlappingSingleNodePatterns;
    private final ValidationMessageSeverity defaultSeverity;

    public OverlappingFilterValidator(ValidationMessageSeverity defaultSeverity, ValidationMessageSeverity severityForOverlappingSingleNodePatterns) {
        this.defaultSeverity = defaultSeverity;
        this.severityForOverlappingSingleNodePatterns = severityForOverlappingSingleNodePatterns;
    }

    @Override
    public @Nullable Collection<ValidationMessage> done() {
        return validateFilters(filtersPerPackages);
    }

    private Collection<ValidationMessage> validateFilters(NavigableMap<String, List<PathFilterSet>> filtersPerPackages) {
        Collection<ValidationMessage> validationMessages = new ArrayList<>();
        // go through each package individually
        for (Map.Entry<String, List<PathFilterSet>> filtersPerPackage : filtersPerPackages.entrySet()) {
            String higherKey = filtersPerPackages.higherKey(filtersPerPackage.getKey());
            if (higherKey == null) {
                break;
            }
            // and compare with rest of packages (that covers all possible combinations)
            validationMessages.addAll(
                    validateFiltersOfSinglePackage(
                            filtersPerPackage.getValue(),
                            filtersPerPackage.getKey(),
                            filtersPerPackages.tailMap(higherKey)));
        }
        return validationMessages;
    }

    private Collection<ValidationMessage> validateFiltersOfSinglePackage(List<PathFilterSet> filtersOfPackage, String packagePath,
            SortedMap<String, List<PathFilterSet>> otherPackageFiltersPerPackages) {
        Collection<ValidationMessage> validationMessages = new ArrayList<>();
        int index = 0;
        while (index < filtersOfPackage.size()) {
            PathFilterSet set1 = filtersOfPackage.get(index);
            // go through all other packages
            for (Map.Entry<String, List<PathFilterSet>> otherPackageFiltersPerPackage : otherPackageFiltersPerPackages.entrySet()) {
                // find one that is nested
                Optional<PathFilterSet> set2 = otherPackageFiltersPerPackage.getValue().stream()
                        .filter(new NestedPathPredicate(set1))
                        .findAny();
                if (set2.isPresent()) {
                    validationMessages.add(
                            new ValidationMessage(ValidationMessageSeverity.DEBUG,
                                    String.format(MESSAGE_OVERLAPPING_FILTER_ROOTS,
                                            set1.getRoot(), packagePath, set2.get().getRoot(), otherPackageFiltersPerPackage.getKey())
                                            + " (prior checking includes/excludes)"));
                    // 2. only allow if the canonical includes are excludes in the other rule
                    validationMessages.addAll(validateFilterSets(set1, packagePath, set2.get(), otherPackageFiltersPerPackage.getKey()));
                }
            }
            index++;
        }
        return validationMessages;
    }

    /** Checks if the filter root path overlaps with a given root path */
    private static final class NestedPathPredicate implements Predicate<PathFilterSet> {

        private final String path;

        NestedPathPredicate(PathFilterSet filterSet) {
            this.path = filterSet.getRoot();
        }

        @Override
        public boolean test(PathFilterSet t) {
            String path2 = t.getRoot();
            return (Text.isDescendantOrEqual(path, path2) ||
                    Text.isDescendant(path2, path));
        }
    }

    /** This is just a heuristic which might emit false positives. It is impossible to come up with a better estimation without knowing the
     * importing repository and the package contents.
     * The order of the filter sets don't matter, i.e. this method is symmetric (but the there is only one validation message emitted)
     * 
     * @param filterSet1
     * @param filterSet2
     * @return {@code true} in case the filter sets are overlapping, {@code false} otherwise */
    private Collection<ValidationMessage> validateFilterSets(PathFilterSet filterSet1, String packagePathFilterSet1, PathFilterSet filterSet2, String packagePathFilterSet2) {
        Collection<ValidationMessage> messages = validateIncludesInFilterSet1AreExcludedInFilterSet2(filterSet1, packagePathFilterSet1, filterSet2, packagePathFilterSet2);
        // only check the other direction in case no overlap has been found yet
        if (messages.isEmpty()) {
            messages = validateIncludesInFilterSet1AreExcludedInFilterSet2(filterSet2, packagePathFilterSet2, filterSet1, packagePathFilterSet1);
        }
        return messages;
    }

    private Collection<ValidationMessage> validateIncludesInFilterSet1AreExcludedInFilterSet2(PathFilterSet includeFilterSet, String packagePathIncludeFilterSet, PathFilterSet excludeFilterSet, String packagePathExcludeFilterSet) {
        Collection<ValidationMessage> messages = new LinkedList<>();
        for (PathFilter includeFilter : getIncludeFilters(includeFilterSet.getEntries())) {
            String testPattern = getNormalizedPattern(includeFilter, includeFilterSet.getRoot());
            if (!isExcluded(testPattern, excludeFilterSet)) {
                boolean isSingleNodeFilterPattern = isSingleNodeFilterPattern(testPattern);
                ValidationMessageSeverity severity =  isSingleNodeFilterPattern ? severityForOverlappingSingleNodePatterns : defaultSeverity;
                messages.add(
                        new ValidationMessage(severity,
                                String.format(MESSAGE_OVERLAPPING_FILTERS,
                                        includeFilterSet.getRoot(), getPatternLabel(includeFilter), packagePathIncludeFilterSet, excludeFilterSet, packagePathExcludeFilterSet)));
            }
        }
        return messages;
    }

    static List<Entry<PathFilter>> getNormalizedEntries(List<Entry<PathFilter>> entries) {
        List<Entry<PathFilter>> normalizedPathFilter = new ArrayList<>();
        if (entries.isEmpty()) {
            return PathFilterSet.INCLUDE_ALL.getEntries();
        } else {
            if (entries.get(0).isInclude()) {
                normalizedPathFilter.add(PathFilterSet.EXCLUDE_ALL.getEntries().get(0));
            } else {
                normalizedPathFilter.add(PathFilterSet.INCLUDE_ALL.getEntries().get(0));
            }
            normalizedPathFilter.addAll(entries);
        }
        return normalizedPathFilter;
    }

    static List<PathFilter> getIncludeFilters(List<Entry<PathFilter>> entries) {
        if (entries.isEmpty()) {
            return Collections.singletonList(PathFilter.ALL);
        }
        return entries.stream().filter(Entry::isInclude).map(Entry::getFilter).collect(Collectors.toList());
    }

    static boolean isSingleNodeFilterPattern(String pattern) {
        return !pattern.endsWith(PATTERN_ALLOW_ALL);
    }

    /**
     * 
     * @param testPattern the normalized pattern to check against
     * @param filterSet the filter set containing the potential excludes
     * @return {@code true} in case the filter set excludes the test pattern
     */
    static boolean isExcluded(String testPattern, PathFilterSet filterSet) {
        List<Entry<PathFilter>> entries = getNormalizedEntries(filterSet.getEntries());
        Collections.reverse(entries);
        // go through all entries, first matching element wins (from reversed original list)
        Optional<Entry<PathFilter>> entry = entries.stream()
                .filter(new PatternMatchPredicate(testPattern, filterSet.getRoot()))
                .findFirst();
        return entry.map(e -> !e.isInclude()).orElse(true);
    }

    /** Predicate which returns true in case the given path filter pattern overlaps with the initial one.
     * Overlapping is not symmetric, i.e. the pattern given to the constructor must be descendant or same level as the test pattern.
     */
    static final class PatternMatchPredicate implements Predicate<Entry<PathFilter>> {

        private final String pattern;
        private final String otherRoot;

        public PatternMatchPredicate(String pattern, String otherRoot) {
            super();
            this.pattern = pattern;
            this.otherRoot = otherRoot;
        }

        @Override
        public boolean test(Entry<PathFilter> t) {
            // first try to match treating original pattern as string with only literals
            String testPath = getUnescapedPattern(pattern);
            if (testPath.startsWith(otherRoot) && t.getFilter().matches(testPath)) {
                return true;
            }
            String otherPattern = OverlappingFilterValidator.getNormalizedPattern(t.getFilter(), otherRoot);
            if (otherPattern.endsWith(PATTERN_ALLOW_ALL)) {
                // allow original pattern also to be descendant of the other pattern in this case
                otherPattern = otherPattern.substring(0, otherPattern.length() - PATTERN_ALLOW_ALL.length());
                return Text.isDescendantOrEqual(otherPattern, pattern);
            } else {
                return otherPattern.equals(pattern);
            }
        }
    }

    static String getUnescapedPattern(String pattern) {
        return PATTERN_ESCAPED_CHARACTER.matcher(pattern).replaceAll("$1");
    }

    static String getNormalizedPattern(PathFilter filter, String rootPath) {
        String pattern;
        if (filter instanceof DefaultPathFilter) {
            pattern = DefaultPathFilter.class.cast(filter).getPattern();
        } else if (filter == PathFilter.ALL) {
            pattern = Pattern.quote(rootPath.endsWith("/") ? rootPath : rootPath + "/");
            pattern += PATTERN_ALLOW_ALL;
            
        } else {
            // unsupported filter type
            throw new IllegalArgumentException("Unsupported filter type " + filter);
        }
        // remove optional groups
        Matcher matcher = PATTERN_OPTIONAL_GROUP.matcher(pattern);
        pattern = matcher.replaceAll("");
        // normalize allow all prefix
        if (pattern.startsWith(PATTERN_ALLOW_ALL)) {
            pattern = Pattern.quote(rootPath) + pattern.substring(PATTERN_ALLOW_ALL.length(), pattern.length());
        }
        // TODO: call Pattern.normalize() via reflection (but for now assume no complex unicode patterns)
        return removeQeQuoting(pattern);
    }

    static String getPatternLabel(PathFilter filter) {
        final StringBuilder pattern = new StringBuilder();
        if (filter instanceof DefaultPathFilter) {
            pattern.append("'").append(DefaultPathFilter.class.cast(filter).getPattern()).append("'");
        } else if (filter == PathFilter.ALL) {
            pattern.append("(implicit) ALL");
        } else {
            // unsupported filter type
            throw new IllegalArgumentException("Unsupported filter type " + filter);
        }
        return pattern.toString();
    }

    /** Replace string quoting using {@code /Q} and {@code /E} with the individual character quoting. The implementation is copied from the
     * private method {@code java.util.Pattern.RemoveQEQuoting()}.
     * 
     * @param pattern the regular expression pattern
     * @return the pattern with the QE quotes replaced */
    static String removeQeQuoting(String pattern) {
        final int pLen = pattern.length();
        int[] temp = pattern.codePoints().toArray();
        int i = 0;
        while (i < pLen - 1) {
            if (temp[i] != '\\')
                i += 1;
            else if (temp[i + 1] != 'Q')
                i += 2;
            else
                break;
        }
        if (i >= pLen - 1) // No \Q sequence found
            return pattern;
        int j = i;
        i += 2;
        int[] newtemp = new int[j + 3 * (pLen - i) + 2];
        System.arraycopy(temp, 0, newtemp, 0, j);

        boolean inQuote = true;
        boolean beginQuote = true;
        while (i < pLen) {
            int c = temp[i++];
            if (!ASCII.isAscii(c) || ASCII.isAlpha(c) || c == '/') {
                newtemp[j++] = c;
            } else if (ASCII.isDigit(c)) {
                if (beginQuote) {
                    /*
                     * A unicode escape \[0xu] could be before this quote, and we don't want this numeric char to processed as part of the
                     * escape.
                     */
                    newtemp[j++] = '\\';
                    newtemp[j++] = 'x';
                    newtemp[j++] = '3';
                }
                newtemp[j++] = c;
            } else if (c != '\\') {
                if (inQuote)
                    newtemp[j++] = '\\';
                newtemp[j++] = c;
            } else if (inQuote) {
                if (temp[i] == 'E') {
                    i++;
                    inQuote = false;
                } else {
                    newtemp[j++] = '\\';
                    newtemp[j++] = '\\';
                }
            } else {
                if (temp[i] == 'Q') {
                    i++;
                    inQuote = true;
                    beginQuote = true;
                    continue;
                } else {
                    newtemp[j++] = c;
                    if (i != pLen)
                        newtemp[j++] = temp[i++];
                }
            }

            beginQuote = false;
        }
        return new String(newtemp, 0, j); // double zero termination
    }

    /**
     * Copy from {@code java.util.regex.ASCII}, only used from {@link OverlappingFilterValidator#removeQeQuoting(String)}.
     *
     */
    static final class ASCII {

        static final int UPPER = 0x00000100;

        static final int LOWER = 0x00000200;

        static final int DIGIT = 0x00000400;

        static final int SPACE = 0x00000800;

        static final int PUNCT = 0x00001000;

        static final int CNTRL = 0x00002000;

        static final int BLANK = 0x00004000;

        static final int HEX = 0x00008000;

        static final int UNDER = 0x00010000;

        static final int ASCII = 0x0000FF00;

        static final int ALPHA = (UPPER | LOWER);

        static final int ALNUM = (UPPER | LOWER | DIGIT);

        static final int GRAPH = (PUNCT | UPPER | LOWER | DIGIT);

        static final int WORD = (UPPER | LOWER | UNDER | DIGIT);

        static final int XDIGIT = (HEX);

        private static final int[] ctype = new int[] {
                CNTRL, /* 00 (NUL) */
                CNTRL, /* 01 (SOH) */
                CNTRL, /* 02 (STX) */
                CNTRL, /* 03 (ETX) */
                CNTRL, /* 04 (EOT) */
                CNTRL, /* 05 (ENQ) */
                CNTRL, /* 06 (ACK) */
                CNTRL, /* 07 (BEL) */
                CNTRL, /* 08 (BS) */
                SPACE + CNTRL + BLANK, /* 09 (HT) */
                SPACE + CNTRL, /* 0A (LF) */
                SPACE + CNTRL, /* 0B (VT) */
                SPACE + CNTRL, /* 0C (FF) */
                SPACE + CNTRL, /* 0D (CR) */
                CNTRL, /* 0E (SI) */
                CNTRL, /* 0F (SO) */
                CNTRL, /* 10 (DLE) */
                CNTRL, /* 11 (DC1) */
                CNTRL, /* 12 (DC2) */
                CNTRL, /* 13 (DC3) */
                CNTRL, /* 14 (DC4) */
                CNTRL, /* 15 (NAK) */
                CNTRL, /* 16 (SYN) */
                CNTRL, /* 17 (ETB) */
                CNTRL, /* 18 (CAN) */
                CNTRL, /* 19 (EM) */
                CNTRL, /* 1A (SUB) */
                CNTRL, /* 1B (ESC) */
                CNTRL, /* 1C (FS) */
                CNTRL, /* 1D (GS) */
                CNTRL, /* 1E (RS) */
                CNTRL, /* 1F (US) */
                SPACE + BLANK, /* 20 SPACE */
                PUNCT, /* 21 ! */
                PUNCT, /* 22 " */
                PUNCT, /* 23 # */
                PUNCT, /* 24 $ */
                PUNCT, /* 25 % */
                PUNCT, /* 26 & */
                PUNCT, /* 27 ' */
                PUNCT, /* 28 ( */
                PUNCT, /* 29 ) */
                PUNCT, /* 2A * */
                PUNCT, /* 2B + */
                PUNCT, /* 2C , */
                PUNCT, /* 2D - */
                PUNCT, /* 2E . */
                PUNCT, /* 2F / */
                DIGIT + HEX + 0, /* 30 0 */
                DIGIT + HEX + 1, /* 31 1 */
                DIGIT + HEX + 2, /* 32 2 */
                DIGIT + HEX + 3, /* 33 3 */
                DIGIT + HEX + 4, /* 34 4 */
                DIGIT + HEX + 5, /* 35 5 */
                DIGIT + HEX + 6, /* 36 6 */
                DIGIT + HEX + 7, /* 37 7 */
                DIGIT + HEX + 8, /* 38 8 */
                DIGIT + HEX + 9, /* 39 9 */
                PUNCT, /* 3A : */
                PUNCT, /* 3B ; */
                PUNCT, /* 3C < */
                PUNCT, /* 3D = */
                PUNCT, /* 3E > */
                PUNCT, /* 3F ? */
                PUNCT, /* 40 @ */
                UPPER + HEX + 10, /* 41 A */
                UPPER + HEX + 11, /* 42 B */
                UPPER + HEX + 12, /* 43 C */
                UPPER + HEX + 13, /* 44 D */
                UPPER + HEX + 14, /* 45 E */
                UPPER + HEX + 15, /* 46 F */
                UPPER + 16, /* 47 G */
                UPPER + 17, /* 48 H */
                UPPER + 18, /* 49 I */
                UPPER + 19, /* 4A J */
                UPPER + 20, /* 4B K */
                UPPER + 21, /* 4C L */
                UPPER + 22, /* 4D M */
                UPPER + 23, /* 4E N */
                UPPER + 24, /* 4F O */
                UPPER + 25, /* 50 P */
                UPPER + 26, /* 51 Q */
                UPPER + 27, /* 52 R */
                UPPER + 28, /* 53 S */
                UPPER + 29, /* 54 T */
                UPPER + 30, /* 55 U */
                UPPER + 31, /* 56 V */
                UPPER + 32, /* 57 W */
                UPPER + 33, /* 58 X */
                UPPER + 34, /* 59 Y */
                UPPER + 35, /* 5A Z */
                PUNCT, /* 5B [ */
                PUNCT, /* 5C \ */
                PUNCT, /* 5D ] */
                PUNCT, /* 5E ^ */
                PUNCT | UNDER, /* 5F _ */
                PUNCT, /* 60 ` */
                LOWER + HEX + 10, /* 61 a */
                LOWER + HEX + 11, /* 62 b */
                LOWER + HEX + 12, /* 63 c */
                LOWER + HEX + 13, /* 64 d */
                LOWER + HEX + 14, /* 65 e */
                LOWER + HEX + 15, /* 66 f */
                LOWER + 16, /* 67 g */
                LOWER + 17, /* 68 h */
                LOWER + 18, /* 69 i */
                LOWER + 19, /* 6A j */
                LOWER + 20, /* 6B k */
                LOWER + 21, /* 6C l */
                LOWER + 22, /* 6D m */
                LOWER + 23, /* 6E n */
                LOWER + 24, /* 6F o */
                LOWER + 25, /* 70 p */
                LOWER + 26, /* 71 q */
                LOWER + 27, /* 72 r */
                LOWER + 28, /* 73 s */
                LOWER + 29, /* 74 t */
                LOWER + 30, /* 75 u */
                LOWER + 31, /* 76 v */
                LOWER + 32, /* 77 w */
                LOWER + 33, /* 78 x */
                LOWER + 34, /* 79 y */
                LOWER + 35, /* 7A z */
                PUNCT, /* 7B { */
                PUNCT, /* 7C | */
                PUNCT, /* 7D } */
                PUNCT, /* 7E ~ */
                CNTRL, /* 7F (DEL) */
        };

        static int getType(int ch) {
            return ((ch & 0xFFFFFF80) == 0 ? ctype[ch] : 0);
        }

        static boolean isType(int ch, int type) {
            return (getType(ch) & type) != 0;
        }

        static boolean isAscii(int ch) {
            return ((ch & 0xFFFFFF80) == 0);
        }

        static boolean isAlpha(int ch) {
            return isType(ch, ALPHA);
        }

        static boolean isDigit(int ch) {
            return ((ch - '0') | ('9' - ch)) >= 0;
        }
    }

    public void addFilter(@NotNull WorkspaceFilter filter, String pathInContainer) {
        filtersPerPackages.put(pathInContainer, filter.getFilterSets());
    }

    @Override
    public @Nullable Collection<ValidationMessage> validate(@NotNull WorkspaceFilter filter) {
        // nothing to do here, as filters are injected from outside (i.e. from factory via addFilter(...))
        return null;
    }

}
