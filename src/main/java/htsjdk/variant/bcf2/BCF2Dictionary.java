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
 * Provides an Integer -> String map interface, but determines during construction
 * whether mapping can be stored as an array (if there are no IDX fields)
 * or it must be stored using a map.
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
     * @return BCFDictionary suitable for use with a BCF file
     */
    public static BCF2Dictionary makeBCF2StringDictionary(final VCFHeader vcfHeader, final BCFVersion version) {
        // TODO this excludes all lines but FILTER, INFO and FORMAT, this is probably what is intended by the spec.
        //  The spec does not disallow other line types (e.g. PEDIGREE) from coming between lines of these types
        //  or specify whether they should be counted towards the implicit index or allowed to have IDX fields.
        final List<VCFSimpleHeaderLine> headerLines = vcfHeader.getIDHeaderLines().stream()
            // TODO: this needs to be changed if VCFHeaderLine::shouldBeAddedToDictionary is removed during refactoring
            .filter(VCFHeaderLine::shouldBeAddedToDictionary)
            .collect(Collectors.toList());

        return BCF2Dictionary.makeDictionary(headerLines, version, true);
    }

    /**
     * Create and return a BCF contig dictionary
     *
     * @param vcfHeader VCFHeader containing the contig header lines to be stored
     * @param version   BCF version for which the dictionary will be used
     * @return BCFDictionary suitable for use with a BCF file
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
            return new BCF2OrdinalSequenceDictionary(Collections.emptyList());
        }

        // TODO this also dedups contig lines, while the original implementation in cn_bcf2 did not
        final Set<String> seen = new HashSet<>();

        if (stringDictionary) {
            // Special case the special PASS field which may not show up in the FILTER field definitions
            seen.add(VCFConstants.PASSES_FILTERS_v4);
        }

        // Check version and possibly peek at first value to see if lines should contain IDX fields or not
        final boolean shouldHaveIDX = version.getMinorVersion() > 1 && headerLines.get(0).getGenericFieldValue(BCF2Codec.IDXField) != null;

        // Validate
        for (final VCFSimpleHeaderLine headerLine : headerLines) {
            final String idxString = headerLine.getGenericFieldValue(BCF2Codec.IDXField);
            if ((idxString == null) == shouldHaveIDX) {
                // If any line had an IDX then they all should
                throw new TribbleException(String.format(
                    "Inconsistent IDX field usage in BCF file %s header lines, %s",
                    headerLine.getKey(),
                    shouldHaveIDX ? "unexpected IDX field" : "did not find expected IDX field"
                ));
            }
        }

        if (shouldHaveIDX) {
            final HashMap<Integer, String> strings = new HashMap<>(headerLines.size() + 1);
            if (stringDictionary) {
                strings.put(0, VCFConstants.PASSES_FILTERS_v4);
            }

            for (final VCFSimpleHeaderLine line : headerLines) {
                final String id = line.getID();
                if (!seen.contains(id)) {
                    seen.add(id);
                    strings.put(
                        Integer.parseInt(line.getGenericFieldValue(BCF2Codec.IDXField)),
                        line.getID()
                    );
                }
            }
            return new BCF2IndexedSequenceDictionary(strings);
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
            return new BCF2OrdinalSequenceDictionary(strings);
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
     * BCF2.2 ordinal sequence dictionary (no IDX values). Values are stored in the order
     * in which they are discovered, and indexed by their ordinal position.
     */
    private static class BCF2OrdinalSequenceDictionary extends BCF2Dictionary {

        private final List<String> dictionary;

        private BCF2OrdinalSequenceDictionary(final List<String> dictionary) {
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
            return dictionary.get(i);
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
     * BCF2.2 indexed dictionary. Values are assigned an index via
     * a value embedded in the header line's IDX field.
     */
    private static class BCF2IndexedSequenceDictionary extends BCF2Dictionary {

        private final Map<Integer, String> dictionary;

        private BCF2IndexedSequenceDictionary(final Map<Integer, String> dictionary) {
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
