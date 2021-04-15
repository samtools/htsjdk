package htsjdk.variant.bcf2;

import htsjdk.tribble.TribbleException;
import htsjdk.variant.vcf.VCFConstants;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.variant.vcf.VCFHeaderLine;
import htsjdk.variant.vcf.VCFSimpleHeaderLine;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * Dictionary of strings or contigs for use with a BCF file.
 * <p>
 * Provides an Integer -> String map interface, but determines during construction whether
 * mapping can be stored as an array (if it can be stored as a dense array) or
 * it must be stored using a map.
 * <p>
 * This class validates that IDX fields are used as required by the BCF 2.2 spec, namely
 * that either all lines of a given dictionary type (contig or FORMAT/INFO/FILTER) have
 * IDX fields or none do.
 * <p>
 * The spec does not require a 1-to-1 IDX-to-string mapping, but logically a header with a
 * 1-to-n IDX-to-string mapping would be unparsable, and we reject such headers, while an
 * n-to-1 IDX-to-string mapping might result from tools that do not deduplicate IDXs, so
 * we accept them.
 */
public abstract class BCF2Dictionary extends AbstractMap<Integer, String> {

    /**
     * Create and return a BCF string dictionary
     * The dictionary is an ordered list of common VCF identifiers (FILTER, INFO, and FORMAT) fields.
     * <p>
     * Note that it's critical that the list be dedupped and sorted in a consistent manner each time,
     * as the BCF2 offsets are encoded relative to this dictionary, and if it isn't determined exactly
     * the same way as in the header each time it's very bad
     *
     * @param vcfHeader VCFHeader containing the strings to be stored
     * @param version   BCF version for which the dictionary will be used
     * @return BCF2Dictionary suitable for use with a BCF file
     */
    public static BCF2Dictionary makeBCF2StringDictionary(final VCFHeader vcfHeader, final BCFVersion version) {
        final List<VCFSimpleHeaderLine> headerLines = vcfHeader.getMetaDataInInputOrder().stream()
            .filter(BCF2Dictionary::isStringDictionaryDefining)
            .map(l -> (VCFSimpleHeaderLine) l)
            .collect(Collectors.toList());

        return BCF2Dictionary.makeDictionary(headerLines, version, true);
    }

    private static boolean isStringDictionaryDefining(final VCFHeaderLine line) {
        switch (line.getKey()) {
            case VCFConstants.INFO_HEADER_KEY:
            case VCFConstants.FORMAT_HEADER_KEY:
            case VCFConstants.FILTER_HEADER_KEY:
                return true;
            default:
                return false;
        }
    }

    /**
     * Create and return a BCF contig dictionary
     *
     * @param vcfHeader VCFHeader containing the contig header lines to be stored
     * @param version   BCF version for which the dictionary will be used
     * @return BCF2Dictionary suitable for use with a BCF file
     */
    public static BCF2Dictionary makeBCF2ContigDictionary(final VCFHeader vcfHeader, final BCFVersion version) {
        return BCF2Dictionary.makeDictionary(vcfHeader.getContigLines(), version, false);
    }

    private static BCF2Dictionary makeDictionary(
        final List<? extends VCFSimpleHeaderLine> headerLines,
        final BCFVersion version,
        final boolean stringDictionary
    ) {
        if (headerLines.isEmpty()) {
            return new BCF2DenseDictionary(Collections.emptyList());
        }

        // Note that we count FILTER/FORMAT/INFO header lines with the same ID but different key
        // (e.g. a FORMAT line and an INFO line both with ID "A") to define the same string
        // for the purposes of building the dictionary
        // c.f. https://github.com/samtools/hts-specs/issues/591#issuecomment-904487133
        final Set<String> seen = new HashSet<>(headerLines.size() + 1);

        if (stringDictionary) {
            // Special case the special PASS field which may not show up in the FILTER field definitions
            seen.add(VCFConstants.PASSES_FILTERS_v4);
        }

        // Check version and possibly peek at first value to see if lines should contain IDX fields or not
        final boolean shouldHaveIDX = version.getMinorVersion() > 1 &&
            headerLines.get(0).getGenericFieldValue(BCF2Codec.IDXField) != null;

        // Validate
        for (final VCFSimpleHeaderLine headerLine : headerLines) {
            final String idxString = headerLine.getGenericFieldValue(BCF2Codec.IDXField);
            if ((idxString == null) == shouldHaveIDX) {
                // If any line had an IDX then they all should
                throw new TribbleException.InvalidHeader(String.format(
                    "Inconsistent IDX field usage in BCF file %s header line %s, %s",
                    headerLine.getKey(),
                    headerLine.getID(),
                    shouldHaveIDX ? "did not find expected IDX field" : "unexpected IDX field"
                ));
            }
        }

        if (shouldHaveIDX) {
            final HashMap<Integer, String> strings = new HashMap<>(headerLines.size() + 1);
            int maxIDX = 0;
            if (stringDictionary) {
                strings.put(0, VCFConstants.PASSES_FILTERS_v4);
            }

            for (final VCFSimpleHeaderLine line : headerLines) {
                final String id = line.getID();
                final int IDX = Integer.parseUnsignedInt(line.getGenericFieldValue(BCF2Codec.IDXField));
                if (!seen.contains(id)) {
                    seen.add(id);
                    maxIDX = Math.max(maxIDX, IDX);
                    strings.put(IDX, line.getID());
                }

                // Have we seen this IDX before with a different string?
                if (strings.containsKey(IDX)) {
                    final String oldString = strings.get(IDX);
                    if (!oldString.equals(id)) {
                        throw new TribbleException.InvalidHeader(String.format(
                            "IDX %d associated with multiple dictionary defining strings: %s and %s",
                            IDX, oldString, id
                        ));
                    }
                }
            }
            if (maxIDX == seen.size() - 1) {
                // By the pigeonhole principle, if we have N unique non-negative IDXs numbered starting from 0
                // (possibly including 0 -> PASS implicitly) and (N - 1) is the highest IDX we have seen,
                // we have all the IDXs in [0, N), which we can represent as a length N dense array.
                // This check is useful because bcftools will always add IDX fields to headers even when not
                // strictly necessary, so we can avoid the cost of the hash map in many cases.
                final ArrayList<String> stringsList = new ArrayList<>(seen.size());
                strings.forEach(stringsList::add);
                return new BCF2DenseDictionary(stringsList);
            } else {
                return new BCF2SparseDictionary(strings);
            }
        } else {
            final ArrayList<String> strings = new ArrayList<>(headerLines.size() + 1);
            if (stringDictionary) {
                strings.add(VCFConstants.PASSES_FILTERS_v4);
            }

            for (final VCFSimpleHeaderLine line : headerLines) {
                final String id = line.getID();
                if (!seen.contains(id)) {
                    strings.add(line.getID());
                    seen.add(id);
                }
            }
            return new BCF2DenseDictionary(strings);
        }
    }

    /**
     * Additional method in interface to avoid boxing when indexing into a
     * dictionary backed by a List
     *
     * @param i index
     * @return the string associated with the index or null
     */
    public abstract String get(final int i);

    /**
     * BCF 2.2 dense sequence dictionary. Strings are assigned an index corresponding to its position in a 0-indexed
     * array. This dictionary is used if no IDX fields are present in the header, or they are present, but they
     * represent a set of indices that are of the form 0, 1, ..., n, that is, the set has no gaps and is numbered
     * starting at 0.
     */
    private static class BCF2DenseDictionary extends BCF2Dictionary {

        private final List<String> dictionary;

        private BCF2DenseDictionary(final List<String> dictionary) {
            this.dictionary = dictionary;
        }

        @Override
        public Set<Entry<Integer, String>> entrySet() {
            final Set<Entry<Integer, String>> set = new HashSet<>(dictionary.size());
            int i = 0;
            for (final String s : dictionary) {
                set.add(new AbstractMap.SimpleEntry<>(i, s));
                i++;
            }
            return set;
        }

        @Override
        public String get(final int i) {
            return i < 0 || i >= dictionary.size() ? null : dictionary.get(i);
        }

        @Override
        public String get(final Object key) {
            return dictionary.get((Integer) key);
        }

        @Override
        public int size() {
            return dictionary.size();
        }

        @Override
        public boolean isEmpty() {
            return dictionary.isEmpty();
        }

        @Override
        public void forEach(final BiConsumer<? super Integer, ? super String> action) {
            int i = 0;
            for (final String s : dictionary) {
                action.accept(i, s);
                i++;
            }
        }
    }

    /**
     * BCF 2.2 sparse dictionary. Strings are assigned an index corresponding to its line's IDX field.
     * This dictionary is used if IDX fields are present in the header, and they represent a set of
     * indices that is not of the form 0, 1, ..., n, that is, the set has gaps or is not numbered starting
     * at 0.
     */
    private static class BCF2SparseDictionary extends BCF2Dictionary {

        private final Map<Integer, String> dictionary;

        private BCF2SparseDictionary(final Map<Integer, String> dictionary) {
            this.dictionary = dictionary;
        }

        @Override
        public Set<Entry<Integer, String>> entrySet() {
            return dictionary.entrySet();
        }

        @Override
        public String get(final int i) {
            return dictionary.get(i);
        }

        @Override
        public String get(final Object key) {
            return dictionary.get(key);
        }

        @Override
        public int size() {
            return dictionary.size();
        }

        @Override
        public boolean isEmpty() {
            return dictionary.isEmpty();
        }

        @Override
        public void forEach(final BiConsumer<? super Integer, ? super String> action) {
            dictionary.forEach(action);
        }
    }
}
