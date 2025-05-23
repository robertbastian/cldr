package org.unicode.cldr.unittest;

import com.google.common.base.Objects;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;
import com.ibm.icu.impl.Row.R2;
import com.ibm.icu.text.UnicodeSet;
import com.ibm.icu.util.ULocale;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.unicode.cldr.util.CLDRConfig;
import org.unicode.cldr.util.CLDRPaths;
import org.unicode.cldr.util.CldrUtility;
import org.unicode.cldr.util.LanguageTagCanonicalizer;
import org.unicode.cldr.util.LanguageTagParser;
import org.unicode.cldr.util.LocaleNames;
import org.unicode.cldr.util.StandardCodes;
import org.unicode.cldr.util.StandardCodes.LstrField;
import org.unicode.cldr.util.StandardCodes.LstrType;
import org.unicode.cldr.util.TestCLDRPaths;
import org.unicode.cldr.util.TransliteratorUtilities;
import org.unicode.cldr.util.Units;
import org.unicode.cldr.util.Validity;
import org.unicode.cldr.util.Validity.Status;

public class TestValidity extends TestFmwkPlus {

    private boolean DEBUG = false;

    public static void main(String[] args) {
        new TestValidity().run(args);
    }

    Validity validity = Validity.getInstance();

    public void TestBasicValidity() {
        Object[][] tests = {
            {LstrType.language, Validity.Status.regular, true, "aa", "en"},
            {LstrType.language, null, false, "eng"}, // null means never found under any status
            {LstrType.language, null, false, LocaleNames.ROOT},
            {LstrType.language, Validity.Status.special, true, LocaleNames.MUL},
            {LstrType.language, Validity.Status.deprecated, true, "aju"},
            {LstrType.language, Validity.Status.reserved, true, "qaa", "qfy"},
            {LstrType.language, Validity.Status.private_use, true, "qfz"},
            {LstrType.language, Validity.Status.unknown, true, LocaleNames.UND},
            {LstrType.script, Validity.Status.reserved, true, "Qaaa", "Qaap"},
            {LstrType.script, Validity.Status.private_use, true, "Qaaq", "Qabx"},
            {LstrType.script, Validity.Status.special, true, "Zinh"},
            {LstrType.script, Validity.Status.special, true, "Zmth"},
            {LstrType.script, Validity.Status.special, true, "Zsye"},
            {LstrType.script, Validity.Status.special, true, "Zsym"},
            {LstrType.script, Validity.Status.special, true, "Zxxx"},
            {LstrType.script, Validity.Status.special, true, "Zyyy"},
            {LstrType.script, Validity.Status.unknown, true, "Zzzz"},
            {LstrType.region, Validity.Status.deprecated, true, "QU"},
            {LstrType.region, Validity.Status.macroregion, true, "EU"},
            {LstrType.region, Validity.Status.regular, true, "XK"},
            {LstrType.region, Validity.Status.macroregion, true, "001"},
            {LstrType.region, Validity.Status.reserved, true, "AA", "QM", "QZ"},
            {LstrType.region, Validity.Status.private_use, true, "XC", "XZ"},
            {LstrType.region, Validity.Status.unknown, true, "ZZ"},
            {LstrType.subdivision, Validity.Status.unknown, true, "kzzzzz"},
            {LstrType.subdivision, Validity.Status.regular, true, "usca"},
            {LstrType.subdivision, Validity.Status.deprecated, true, "albr"},
            {LstrType.currency, Validity.Status.regular, true, "USD"},
            {LstrType.currency, Validity.Status.unknown, true, "XXX"},
            {LstrType.currency, Validity.Status.deprecated, true, "ADP"},
            {LstrType.unit, Validity.Status.regular, true, "area-acre"},
        };
        for (Object[] test : tests) {
            LstrType lstr = (LstrType) test[0];
            Validity.Status subtypeRaw = (Validity.Status) test[1];
            Boolean desired = (Boolean) test[2];
            for (int i = 3; i < test.length; ++i) {
                String code = (String) test[i];
                List<Status> subtypes =
                        subtypeRaw == null
                                ? Arrays.asList(Status.values())
                                : Collections.singletonList(subtypeRaw);
                for (Status subtype : subtypes) {
                    Set<String> actual = validity.getStatusToCodes(lstr).get(subtype);
                    if (!assertRelation(
                            "Validity",
                            desired,
                            CldrUtility.ifNull(actual, Collections.EMPTY_SET),
                            TestFmwkPlus.CONTAINS,
                            code)) {
                        int debug = 0;
                    }
                }
            }
        }
        if (isVerbose()) {

            for (LstrType lstrType : LstrType.values()) {
                logln(lstrType.toString());
                final Map<Status, Set<String>> statusToCodes = validity.getStatusToCodes(lstrType);
                for (Entry<Validity.Status, Set<String>> entry2 : statusToCodes.entrySet()) {
                    logln("\t" + entry2.getKey());
                    logln("\t\t" + entry2.getValue());
                }
            }
        }
    }

    static final Set<String> ALLOWED_UNDELETIONS =
            ImmutableSet.of(
                    "ug331",
                    "nlbq1",
                    "nlbq2",
                    "nlbq3",
                    "no21",
                    "no22",
                    // 2022
                    "no",
                    "escn",
                    "gbeng",
                    "gbnir",
                    "gbsct",
                    "gbwls",
                    "itgo",
                    "itpn",
                    "itts",
                    "itud",
                    "SLE",
                    // 2024
                    "dzd",
                    "knn",
                    // 2025
                    "mnk");
    static final Set<String> ALLOWED_MISSING =
            ImmutableSet.of(LocaleNames.ROOT, "POSIX", "REVISED", "SAAHO");
    static final Set<String> ALLOWED_REGULAR_TO_SPECIAL = ImmutableSet.of("Zanb", "Zinh", "Zyyy");

    public void TestCompatibility() {
        if (!TestCLDRPaths.canUseArchiveDirectory()) {
            return; // skipped
        }

        Set<String> messages = new HashSet<>();
        File archive = new File(CLDRPaths.ARCHIVE_DIRECTORY);
        for (File cldrArchive : archive.listFiles()) {
            if (!cldrArchive.getName().startsWith("cldr-")) {
                continue;
            }
            File oldValidityLocation =
                    new File(
                            cldrArchive,
                            File.separator
                                    + "common"
                                    + File.separator
                                    + "validity"
                                    + File.separator);
            if (!oldValidityLocation.exists()) {
                logln("Skipping " + oldValidityLocation);
                continue;
            }
            logln("Checking " + oldValidityLocation.toString());
            //            final String oldValidityLocation = CLDRPaths.ARCHIVE_DIRECTORY + "cldr-" +
            // ToolConstants.PREVIOUS_CHART_VERSION +
            //                File.separator + "common" + File.separator + "validity" +
            // File.separator;
            Validity oldValidity =
                    Validity.getInstance(oldValidityLocation.toString() + File.separator);

            for (LstrType type : LstrType.values()) {
                final Map<Status, Set<String>> statusToCodes = oldValidity.getStatusToCodes(type);
                if (statusToCodes == null) {
                    logln("validity data unavailable: " + type);
                    continue;
                }
                for (Entry<Status, Set<String>> e2 : statusToCodes.entrySet()) {
                    Status oldStatus = e2.getKey();
                    for (String code : e2.getValue()) {
                        Status newStatus = getNewStatus(type, code);
                        if (oldStatus == newStatus) {
                            continue;
                        }

                        if (newStatus == null) {
                            if (ALLOWED_MISSING.contains(code)) {
                                continue;
                            }
                            errln(
                                    messages,
                                    type
                                            + ":"
                                            + code
                                            + ":"
                                            + oldStatus
                                            + " => "
                                            + newStatus
                                            + " — missing in new data vs. "
                                            + cldrArchive.getName());
                        }

                        if (oldStatus == Status.private_use && newStatus == Status.special) {
                            logln(
                                    messages,
                                    "OK: " + type + ":" + code + " was " + oldStatus + " => "
                                            + newStatus);
                            continue;
                        }
                        if (oldStatus == Status.special && newStatus == Status.unknown) {
                            if (type == LstrType.subdivision && code.endsWith("zzzz")) {
                                continue;
                            }
                            logln(
                                    messages,
                                    "OK: " + type + ":" + code + " was " + oldStatus + " => "
                                            + newStatus);
                            continue;
                        }
                        if (oldStatus == Status.regular) {
                            if (newStatus == Status.deprecated) {
                                //                                logln(messages, "OK: " + type +
                                // ":" + code + " was " + oldStatus + " => " + newStatus);
                                continue;
                            } else if (newStatus == Status.special
                                    && ALLOWED_REGULAR_TO_SPECIAL.contains(code)) {
                                //                              logln(messages, "OK: " + type + ":"
                                // + code + " was " + oldStatus + " => " + newStatus);
                                continue;
                            }
                            errln(
                                    messages,
                                    type
                                            + ":"
                                            + code
                                            + ":"
                                            + oldStatus
                                            + " => "
                                            + newStatus
                                            + " — regular item changed, and didn't become deprecated");
                        }
                        if (oldStatus == Status.deprecated) {
                            if (ALLOWED_UNDELETIONS.contains(code)) {
                                continue;
                            }
                            errln(
                                    messages,
                                    type
                                            + ":"
                                            + code
                                            + ":"
                                            + oldStatus
                                            + " => "
                                            + newStatus
                                            + " // add to exception list (ALLOWED_UNDELETIONS) if really un-deprecated");
                        } else if (oldStatus == Status.private_use && newStatus == Status.regular) {
                            //                          logln(messages, "OK: " + type + ":" + code +
                            // " was " + oldStatus + " => " + newStatus);
                        } else if (oldStatus == Status.deprecated) {
                            errln(
                                    messages,
                                    type + ":" + code + " was " + oldStatus + " => " + newStatus);
                        }
                    }
                }
            }
        }
    }

    private void logln(Set<String> messages, String string) {
        if (!messages.contains(string)) {
            logln(string);
            messages.add(string);
        }
    }

    private void errln(Set<String> messages, String string) {
        if (!messages.contains(string)) {
            errln(string);
            messages.add(string);
        }
    }

    private Status getNewStatus(LstrType type, String code) {
        Map<Status, Set<String>> info = validity.getStatusToCodes(type);
        for (Entry<Status, Set<String>> e : info.entrySet()) {
            if (e.getValue().contains(code)) {
                return e.getKey();
            }
        }
        return null;
    }

    public void TestBothDirections() {
        for (LstrType type : LstrType.values()) {
            Map<Status, Set<String>> statusToCodes = validity.getStatusToCodes(type);
            Map<String, Status> codeToStatus = validity.getCodeToStatus(type);
            assertEquals("null at same time", statusToCodes == null, codeToStatus == null);
            if (statusToCodes == null) {
                logln("validity data unavailable: " + type);
                continue;
            }
            for (Entry<Status, Set<String>> entry : statusToCodes.entrySet()) {
                Status status = entry.getKey();
                for (String code : entry.getValue()) {
                    assertEquals("Forward works", status, codeToStatus.get(code));
                }
            }
            for (Entry<String, Status> entry : codeToStatus.entrySet()) {
                final String code = entry.getKey();
                final Status status = entry.getValue();
                assertTrue("Reverse works: " + status, statusToCodes.get(status).contains(code));
            }
        }
    }

    public void TestValidityUniqueness() {
        Splitter HYPHEN_SPLITTER = Splitter.on('-');
        UnicodeSet allowed = new UnicodeSet("[a-z0-9A-Z]").freeze();
        Validity validity = Validity.getInstance();
        for (Entry<LstrType, Map<Status, Set<String>>> e1 : validity.getData().entrySet()) {
            final LstrType lstrType = e1.getKey();
            final boolean lstrTypeUnit = lstrType == LstrType.unit;

            // Try truncating every key to 8 letters, to ensure that it is unique

            Multimap<String, String> truncatedToFull = TreeMultimap.create();
            for (Entry<Status, Set<String>> e2 : e1.getValue().entrySet()) {
                final Status status = e2.getKey();
                final boolean statusDeprecated = status != Status.deprecated;
                for (String codeLong : e2.getValue()) {
                    String code = codeLong;
                    String shortCode = code;
                    if (lstrTypeUnit) {
                        shortCode = Units.getShort(codeLong);
                        if (shortCode == null) {
                            if (statusDeprecated) {
                                errln("No short form of " + codeLong);
                            }
                            continue;
                        }
                    }
                    for (String subcode : HYPHEN_SPLITTER.split(code)) {
                        truncatedToFull.put(
                                subcode.length() <= 8 ? subcode : subcode.substring(0, 8), subcode);
                        if (!allowed.containsAll(subcode)) {
                            errln("subcode has illegal character: " + subcode + ", in " + code);
                        }
                    }
                }
            }

            checkTruncationStatus(truncatedToFull);
        }
    }

    public void checkTruncationStatus(Multimap<String, String> truncatedToFull) {
        for (Entry<String, Collection<String>> entry : truncatedToFull.asMap().entrySet()) {
            final String truncated = entry.getKey();
            final Collection<String> longForms = entry.getValue();
            if (longForms.size() > 1) {
                errln("Ambiguous subkey: " + entry);
            } else if (isVerbose()) {
                if (!longForms.contains(truncated)) {
                    logln(entry.toString());
                }
            }
        }
    }

    public void TestLanguageTagParser() {
        String[][] tests = {
            {
                "en-cyrl_ru_variant2_variant1",
                "en_Cyrl_RU_VARIANT1_VARIANT2",
                "en-Cyrl-RU-variant1-variant2"
            },
            // Hold off, since ICU doesn't canonicalize: doesn't correctly interpret
            // en@co=PHONEBK;em=EMOJI;t=RU
            // { "EN-U-CO-PHONEBK-EM-EMOJI-T_RU", "en@T=RU;CO=PHONEBK;EM=EMOJI",
            // "en-t-ru-u-co-phonebk-em-emoji" },
        };
        LanguageTagParser ltp = new LanguageTagParser();
        for (String[] test : tests) {
            String source = test[0];
            String expectedLanguageSubtagParserIcu = test[1];
            String expectedLanguageSubtagParserBCP = test[2];

            // check that field 2 is the same as ICU
            ULocale icuFromICU = new ULocale(expectedLanguageSubtagParserIcu);
            ULocale icuFromBCP = ULocale.forLanguageTag(expectedLanguageSubtagParserBCP);
            assertEquals("ICU from BCP47 => ICU from legacy:\t" + source, icuFromBCP, icuFromICU);

            ltp.set(source);
            String actualLanguageSubtagParserIcu = ltp.toString();
            assertEquals(
                    "Language subtag (ICU) for " + source,
                    expectedLanguageSubtagParserIcu,
                    actualLanguageSubtagParserIcu);
            String actualLanguageSubtagParserBCP =
                    ltp.toString(LanguageTagParser.OutputOption.BCP47);
            assertEquals(
                    "Language subtag (BCP47) for " + source,
                    expectedLanguageSubtagParserBCP,
                    actualLanguageSubtagParserBCP);
        }
    }

    public void TestLanguageTagCanonicalizer() {
        String[][] tests = {
            {"dE-foniPa", "de_fonipa"},
            //            { "el-1901-polytoni-aaland", "el_AX_1901_polyton" }, // doesn't yet handle
            // polyton
            //            { "en-POLYTONI-WHATEVER-ANYTHING-AALAND",
            // "en_AX_anything_polyton_whatever" }, // doesn't yet handle polyton
            {"eng-840", "en"},
            {"sh_ba", "sr_Latn_BA"},
            {"iw-arab-010", "he_Arab_AQ"},
            {LocaleNames.UND, LocaleNames.UND},
            {"und_us", "und_US"},
            {"und_su", "und_RU"},
        };
        LanguageTagCanonicalizer canon = new LanguageTagCanonicalizer();
        for (String[] inputExpected : tests) {
            assertEquals("Canonicalize", inputExpected[1], canon.transform(inputExpected[0]));
        }
    }

    final Map<LstrType, Map<String, Map<LstrField, String>>> lstr = StandardCodes.getEnumLstreg();
    final Map<String, Map<String, R2<List<String>, String>>> typeToCodeToReplacement =
            CLDRConfig.getInstance().getSupplementalDataInfo().getLocaleAliasInfo();

    public void TestLstrConsistency() {
        // get the alias info, and process
        // eg "language" -> "sh" -> <{"sr_Latn"}, reason>

        // quick consistency check of lstr
        // hack for extlang.
        Map<String, Map<LstrField, String>> extlangItems = lstr.get(LstrType.extlang);
        Map<String, Map<LstrField, String>> languageItems = lstr.get(LstrType.language);
        if (!languageItems.keySet().containsAll(extlangItems.keySet())) {
            errln(
                    "extlang not subset of language: "
                            + setDifference(extlangItems.keySet(), languageItems.keySet()));
        }

        ImmutableSet<LstrType> LstrTypesToSkip =
                ImmutableSet.of(LstrType.extlang, LstrType.legacy, LstrType.redundant);
        Set<LstrType> lstrTypesToTest = EnumSet.allOf(LstrType.class);
        lstrTypesToTest.removeAll(LstrTypesToSkip);
        Set<String> missingAliases = new LinkedHashSet<>();
        Map<String, String> changedAliases = new LinkedHashMap<>();

        for (LstrType lstrType : lstrTypesToTest) {
            Map<String, Map<LstrField, String>> lstrValue = lstr.get(lstrType);
            if (lstrValue == null) {
                continue;
            }
            Map<String, R2<List<String>, String>> codeToReplacement =
                    typeToCodeToReplacement.get(lstrType.toCompatString());

            Set<String> lstrDeprecated = new TreeSet<>();
            Set<String> aliased = new TreeSet<>();
            Map<String, String> lstrPreferred = new TreeMap<>();
            Map<String, String> aliasPreferred = new TreeMap<>();

            for (Entry<String, Map<LstrField, String>> codeToData : lstrValue.entrySet()) {
                String code = codeToData.getKey();
                Map<LstrField, String> data = codeToData.getValue();
                boolean deprecated = data.get(LstrField.Deprecated) != null;
                if (deprecated) {
                    lstrDeprecated.add(code);
                }
                String preferred = data.get(LstrField.Preferred_Value);
                if (preferred != null) {
                    lstrPreferred.put(code, preferred);
                }
                if (codeToReplacement != null) {
                    R2<List<String>, String> replacement = codeToReplacement.get(code);
                    if (replacement != null) {
                        aliased.add(code);
                        List<String> replacementList = replacement.get0();
                        aliasPreferred.put(code, CldrUtility.join(replacementList, " "));
                    }
                }
            }
            Set<String> diff = setDifference(aliased, lstrDeprecated);
            logln(lstrType + ": aliased and not deprecated in lstr: " + diff);
            lstrDeprecated.addAll(diff);

            //            // special exceptions
            //            switch(lstrType) {
            //            case script: lstrDeprecated.add("Qaai"); break;
            //            case region: lstrDeprecated.add("QU"); break;
            //            default: break;
            //            }

            Map<Status, Set<String>> statusToCodes = validity.getStatusToCodes(lstrType);
            Set<String> validityDeprecated =
                    statusToCodes == null ? null : statusToCodes.get(Status.deprecated);

            if (!Objects.equal(lstrDeprecated, validityDeprecated)) {
                showMinus(
                        "Deprecated lstr - validity", lstrType, lstrDeprecated, validityDeprecated);
                showMinus(
                        "Deprecated validity - lstr", lstrType, validityDeprecated, lstrDeprecated);
            }

            if (!Objects.equal(lstrPreferred, aliasPreferred)) {
                // showMinus("Preferred lstr - alias", lstrType, lstrPreferred.entrySet(),
                // aliasPreferred.entrySet());
                for (Entry<String, String> entry : lstrPreferred.entrySet()) {
                    String code = entry.getKey();
                    String lstrReplacement = entry.getValue();
                    String aliasValue = aliasPreferred.get(code);
                    if (lstrReplacement.equals(aliasValue)) {
                        continue;
                    }
                    String newAlias = makeAliasXml(lstrType, code, lstrReplacement, "deprecated");
                    if (aliasValue == null) {
                        missingAliases.add(newAlias);
                    } else {
                        changedAliases.put(
                                newAlias, makeAliasXml(lstrType, code, aliasValue, "deprecated"));
                    }
                }
            }
        }
        if (!missingAliases.isEmpty()) {
            errln("Missing aliases for supplementalMetadata: " + missingAliases.size());
            for (String s : missingAliases) {
                System.out.println(s);
            }
        }
        if (!changedAliases.isEmpty()) {
            warnln("Changed aliases from LSTR, just double-check: " + changedAliases.size());
            for (Entry<String, String> entry : changedAliases.entrySet()) {
                getLogger().fine("\tcurrent=" + entry.getValue() + "\n\tlstr=" + entry.getKey());
            }
        }
    }

    // <languageAlias type="art_lojban" replacement="jbo" reason="deprecated"/> <!-- Lojban -->
    // <scriptAlias type="Qaai" replacement="Zinh" reason="deprecated"/>
    // <territoryAlias type="AAA" replacement="AA" reason="overlong"/> <!-- null -->
    // <variantAlias type="AALAND" replacement="AX" reason="deprecated"/>
    private String makeAliasXml(
            LstrType lstrType, String code, String lstrReplacement, String reason) {
        return "<"
                + lstrType.toCompatString()
                + "Alias"
                + " type=\""
                + code
                + "\""
                + " replacement=\""
                + lstrReplacement
                + "\""
                + " reason=\""
                + reason
                + "\"/>"
                + " <!-- "
                + TransliteratorUtilities.toXML.transform(
                        CLDRConfig.getInstance()
                                        .getEnglish()
                                        .nameGetter()
                                        .getNameFromIdentifier(code)
                                + " ⇒ "
                                + CLDRConfig.getInstance()
                                        .getEnglish()
                                        .nameGetter()
                                        .getNameFromIdentifier(lstrReplacement))
                + " -->";
    }

    private <T, U extends Collection<T>> Set<T> setDifference(U a, U b) {
        Set<T> diff = new LinkedHashSet<>(a);
        diff.removeAll(b);
        return diff;
    }

    // Shows the items in set A that are not in set B
    private <T> Set<T> showMinus(String title, LstrType lstrType, Set<T> a, Set<T> b) {
        if (a == null) {
            a = Collections.emptySet();
        }
        if (b == null) {
            b = Collections.emptySet();
        }

        Set<T> diff = setDifference(a, b);
        if (!diff.isEmpty()) {
            T first = diff.iterator().next();
            if (first instanceof String) {
                List<String> names =
                        diff.stream()
                                .map(
                                        code ->
                                                "\n\t\t"
                                                        + code
                                                        + " ⇒\t"
                                                        + lstr.get(lstrType).get(code)
                                                        + "\n\t\tvalid.⇒\t"
                                                        + CldrUtility.ifNull(
                                                                        validity.getCodeToStatus(
                                                                                lstrType),
                                                                        Collections.emptyMap())
                                                                .get(code)
                                                        + "\n\t\talias⇒\t"
                                                        + CldrUtility.ifNull(
                                                                        typeToCodeToReplacement.get(
                                                                                lstrType
                                                                                        .toCompatString()),
                                                                        Collections.emptyMap())
                                                                .get(code))
                                .collect(Collectors.toList());
                errln(
                        title
                                + "\n\tLstrType=\t"
                                + lstrType
                                + "\n\tsize=\t"
                                + diff.size()
                                + "\n\tcodes=\t"
                                + diff
                                + "\n\tnames=\t"
                                + names);
            } else {
                errln(
                        title
                                + "\n\tLstrType=\t"
                                + lstrType
                                + "\n\tsize=\t"
                                + diff.size()
                                + "\n\tcodes=\t"
                                + diff);
            }
        }
        return diff;
    }

    final Set<LstrType> EXPECTED_NULL =
            ImmutableSet.of(
                    LstrType.extlang,
                    LstrType.legacy,
                    LstrType.redundant,
                    LstrType.usage,
                    LstrType.zone,
                    LstrType.extension);
    final Set<LstrType> EXPECTED_UNSORTED = ImmutableSet.of(LstrType.unit);

    public void testOrder() {
        for (LstrType type : LstrType.values()) {
            Map<Status, Set<String>> statusToCodes = validity.getStatusToCodes(type);
            assertEquals(type + " is null", EXPECTED_NULL.contains(type), statusToCodes == null);
            if (statusToCodes == null) {
                continue;
            }
            for (Entry<Status, Set<String>> entry : statusToCodes.entrySet()) {
                Status status = entry.getKey();
                Set<String> codes = entry.getValue();
                List<String> list = new ArrayList<>(codes);
                List<String> sorted = new ArrayList<>(new TreeSet<>(codes));
                boolean isSorted = list.equals(sorted);
                assertEquals(
                        type + ":" + status + " is sorted",
                        EXPECTED_UNSORTED.contains(type),
                        !isSorted);
            }
        }
    }
}
