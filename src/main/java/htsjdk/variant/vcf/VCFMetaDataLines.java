package htsjdk.variant.vcf;

import htsjdk.samtools.util.Log;
import htsjdk.tribble.TribbleException;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

//TODO: should this class be public so consumers can use in place of Set<VCFHeaderLine>

/**
 * Class for managing a set of VCFHeaderLines for a VCFHeader.
 */
class VCFMetaDataLines implements Serializable {
    public static final long serialVersionUID = 1L;
    protected final static Log logger = Log.getInstance(VCFMetaDataLines.class);

    // Require a unique key for each ID line
    // Allow duplicate non-ID keys, unless fileformat, reference ?

    // TODO: Should we reject attempts to add two contig header lines with the same contigIndex ?
    // TODO: GATK VcfUtilsUnitTest.createHeaderLines test creates headers with contig lines with identical (0) indices
    private final Map<String, VCFHeaderLine> mMetaData = new LinkedHashMap<>();

    private static String KEY_SEPARATOR = ":";

    // Namespace key used for "other" (unstructured, non-ID) metadata lines. This string needs to be different from
    // any string in the set of legal structured line types in knownStructuredLineKeys.
    private static String OTHER_KEY = "OTHER";

    /**
     * Add all metadata lines from Set. If a duplicate line is encountered (same key/ID pair for
     * structured lines, or duplicate content for unstructured lines with identical keys)
     * only the first line will be retained.
     *
     * @param newMetaData Set of lines to be added to the list.
     * @throws IllegalArgumentException if a fileformat line is added
     */
    public void addAllMetaDataLines(Set<VCFHeaderLine> newMetaData) {
        newMetaData.stream().forEach(hl -> addMetaDataLine(hl));
    }

    /**
     * Add a metadata line to the list. If a duplicate line is encountered (same key/ID pair for
     * structured lines, or duplicate content for unstructured lines with identical keys)
     * only the first line will be retained.
     *
     * @param headerLine header line to attempt to add
     * @throws IllegalArgumentException if a fileformat line is added
     */
    public void addMetaDataLine(final VCFHeaderLine headerLine) {
        String key = makeKeyForLine(headerLine);
        if ( mMetaData.get(key) != null ) {
            final String message = String.format(
                    "Attempt to add header line (%s) collides with existing line header line (%s). " +
                            "The existing line will be retained",
                    mMetaData.get(key),
                    headerLine);
            //TODO: should this also detect/reject multiple "assembly" or "reference" lines ?
            if (VCFHeaderVersion.isFormatString(headerLine.getKey())) { // Throw if there is more than one fileformat line
                throw new TribbleException(message);
            } else if (VCFUtils.getVerboseVCFLogging()) {
                // TODO: The previous header implementation would round trip lines with duplicate IDs by preserving
                // TODO: them in the master header line list maintained by VCFHeader, but would silently drop them
                // TODO: from the typed header line lists, so the duplicates would not surface in queries (i.e. via
                // TODO: getInfoHeaderLines()).
                //
                // TODO: This implementation doesn't allow the duplicates to be preserved (the 4.2/4.3 specs expressly
                // TODO: forbid it), so they're dropped here. There are several Picard and GATK test files around with
                // TODO: duplicate ID lines, which will have to be modified in order for the tests to pass.
                //
                // TODO: Is it sufficient to log a warning for this case (duplicate key) ? Should we add code
                // TODO: here to throw only for v4.3+ ? Only if VCFUtils.getStrictVCFVersionValidation is set ? Or both ?
                logger.warn(message);
          }
        } else {
            mMetaData.put(key, headerLine);
        }
    }

    /**
     * Generate a unique key for a VCFHeaderLine. If the header line is a VCFStructuredHeaderLine, the key is the
     * concatenation of the VCFHeaderLine's key (i.e., the type of the VCFHeaderLine) and the ID for that
     * VCFHeaderLine (with a ":" separator). Otherwise, we use the concatenation of the OTHER_KEY, the VCFHeaderLine's
     * key, and a nonce value to ensure that unstructured lines never collide with structured lines, and also can
     * have duplicate identical instances.
     *
     * @param headerLine
     * @return
     */
    private String makeKeyForLine(final VCFHeaderLine headerLine) {
        if (headerLine.isStructuredHeaderLine()) { // required to have a unique ID
            // use the line type as the namespace, to ensure unique key/id combination
            return makeKey(headerLine.getKey(), headerLine.getID());
        } else {
            // Allow duplicate unstructured "other" keys, as long as they have different values. Prepend
            // the string "OTHER" to prevent a non-structured line from having a key that overlaps a key
            // key for a "known" structured line type, such as:
            //
            //  ##FORMAT:bar=...
            //
            // which would overlap with the key generated for a real FORMAT line with id=bar.
            //
            // TODO: The previous implementation dropped duplicate keys for unstructured lines, but I don't think
            // the spec requires these to be unique. This is more permissive in that it allows duplicate lines such
            //  as ##GATKCommandLines to accumulate if they have different values, but retains only one if it has
            // a unique value.
            return makeKey(OTHER_KEY, headerLine.getKey() + Integer.toString(headerLine.hashCode()));
        }
    }

    // Create a VCFHeaderLine hashmap key given a key and an id
    private String makeKey(final String nameSpace, final String id) { return nameSpace + KEY_SEPARATOR + id; }

    /**
     * Return the existing line if the list already contains a header line of the same type/id.
     * @param line
     * @return The eixsting header line of this type/key, otherwise NULL.
     */
    public VCFHeaderLine hasEquivalentHeaderLine(final VCFHeaderLine line) {
        return mMetaData.get(makeKeyForLine(line));
    }

    /**
     * Remove the headerline matching this headerline (determined by makeKeyForLine) if it exists
     * @param headerLine
     * @return The headerline removed, or null of no headerline with a matching was found
     */
    public VCFHeaderLine removeHeaderLine(final VCFHeaderLine headerLine) {
        return mMetaData.remove(makeKeyForLine(headerLine));
    }

    /**
     * Starting at version 4.3, validate all metadata lines against a target version.
     * @param targetVersion
     */
    public void validateMetaDataLines(final VCFHeaderVersion targetVersion) {
        mMetaData.values().forEach(line -> validateMetaDataLine(targetVersion, line));
    }

    /**
     * Starting at version 4.3, validate all metadata lines against the target version,
     * including the case where the headerLine is itself a fileformat line with a version, in case
     * it clashes.
     *
     * @param targetVersion
     * @param vcfHeaderLine
     */
    public void validateMetaDataLine(final VCFHeaderVersion targetVersion, final VCFHeaderLine vcfHeaderLine) {
        vcfHeaderLine.validateForVersion(targetVersion);
    }

    /**
     * get the meta data, associated with this header, in sorted order
     *
     * @return a set of the meta data
     */
    public Set<VCFHeaderLine> getMetaDataInInputOrder() {
        return Collections.unmodifiableSet(new LinkedHashSet(mMetaData.values()));
    }

    public Set<VCFHeaderLine> getMetaDataInSortedOrder() {
        return Collections.unmodifiableSet(new TreeSet<>(mMetaData.values()));
    }

    /**
     * @return all of the structured (ID) lines in their original file order, or an empty list if none were present
     */
    public List<VCFHeaderLine> getStructuredHeaderLines() {
        return mMetaData.values().stream()
                .filter(hl -> hl.isStructuredHeaderLine())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * @return all of the VCF FILTER lines in their original file order, or an empty list if none were present
     */
    public List<VCFFilterHeaderLine> getFilterLines() {
        return mMetaData.values().stream()
                .filter(hl -> hl.getKey().equals(VCFConstants.FILTER_HEADER_KEY))
                .map(hl -> (VCFFilterHeaderLine) hl)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * @return all of the VCF header lines of the ##contig form in order, or an empty list if none were present
     */
    public List<VCFContigHeaderLine> getContigLines() {
        List<VCFContigHeaderLine> contigLines = new ArrayList<>();
        mMetaData.values().stream()
                .filter(hl -> hl.getKey().equals(VCFConstants.CONTIG_HEADER_KEY))
                .forEach(hl -> contigLines.add((VCFContigHeaderLine) hl));
        return Collections.unmodifiableList(contigLines);
    }

    //TODO: Is this useful ? It returns the first match for the given key, no matter how many
    //TODO: there are. Should we deprecate it (and add a new one that returns a collection)
    //TODO: or just change this one to return a collection ?
    /**
     * Get the VCFHeaderLine whose key equals key.  Returns null if no such line exists
     * @param key
     * @return
     */
    public VCFHeaderLine getMetaDataLine(final String key) {
        return mMetaData.values().stream()
                .filter(hl -> hl.getKey().equals(key))
                .findFirst()
                .orElseGet(()->null);
    }

    /**
     * Returns the INFO HeaderLines in their original ordering
     */
    public Collection<VCFInfoHeaderLine> getInfoHeaderLines() {
        return mMetaData.values().stream()
                .filter(hl -> hl.getKey().equals(VCFConstants.INFO_HEADER_KEY))
                .map(hl -> (VCFInfoHeaderLine) hl)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * Returns the FORMAT HeaderLines in their original ordering
     */
    public Collection<VCFFormatHeaderLine> getFormatHeaderLines() {
        return mMetaData.values().stream()
                .filter(hl -> hl.getKey().equals(VCFConstants.FORMAT_HEADER_KEY))
                .map(hl -> (VCFFormatHeaderLine) hl)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * @param id the id of the requested header line
     * @return the meta data line, or null if there is none
     */
    public VCFInfoHeaderLine getInfoHeaderLine(final String id) {
        return (VCFInfoHeaderLine) mMetaData.get(makeKey(VCFConstants.INFO_HEADER_KEY, id));
    }

    /**
     * @param id  the id of the requested header line
     * @return the meta data line, or null if there is none
     */
    public VCFFormatHeaderLine getFormatHeaderLine(final String id) {
        return (VCFFormatHeaderLine) mMetaData.get(makeKey(VCFConstants.FORMAT_HEADER_KEY, id));
    }

    /**
     * @param id the id of the requested header line
     * @return the meta data line, or null if there is none
     */
    public VCFFilterHeaderLine getFilterHeaderLine(final String id) {
        return (VCFFilterHeaderLine) mMetaData.get(makeKey(VCFConstants.FILTER_HEADER_KEY, id));
    }

    //TODO: Is this useful ? It returns the first match for the given key, no matter how many
    //TODO: there are. Should we deprecate it (and add a new one that returns a collection)
    //TODO: or just change this one to return a collection ?
    /**
     * @param key the of the requested other header line
     * @return the meta data line, or null if there is none
     */
    public VCFHeaderLine getOtherHeaderLine(final String key) {
        Iterator<VCFHeaderLine> it = getOtherHeaderLines().iterator();
        while (it.hasNext()) {
            VCFHeaderLine next = it.next();
            if (next.getKey().equals(key)) {
                return next;
            }
        }
        return null;
    }

    /**
     * Returns the other HeaderLines in their original ordering, where "other" means any
     * VCFHeaderLine that is not a contig, info, format or filter header line.
     */
    public Collection<VCFHeaderLine> getOtherHeaderLines() {
        return mMetaData.values().stream().filter(
            hl ->
                !hl.getKey().equals(VCFConstants.CONTIG_HEADER_KEY) &&
                !hl.getKey().equals(VCFConstants.INFO_HEADER_KEY) &&
                !hl.getKey().equals(VCFConstants.FILTER_HEADER_KEY) &&
                !hl.getKey().equals(VCFConstants.FORMAT_HEADER_KEY)
        )
        .collect(Collectors.toCollection(ArrayList::new));
    }

    @Override
    public String toString() {
        final StringBuilder b = new StringBuilder();
        b.append("[VCFHeader:");
        for ( final VCFHeaderLine line : mMetaData.values() )
            b.append("\n\t").append(line);
        return b.append("\n]").toString();
    }
}

