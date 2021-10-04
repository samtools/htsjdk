package htsjdk.variant.vcf;

import htsjdk.samtools.util.Log;
import htsjdk.tribble.TribbleException;
import htsjdk.utils.ValidationUtils;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

//TODO:make sure this doesn't have issues with ALT lines (which have IDs with embedded ":")
/**
 * Class for managing a set of VCFHeaderLines for a VCFHeader.
 */
//Visible to allow disq Kryo registration for serialization
public final class VCFMetaDataLines implements Serializable {
    public static final long serialVersionUID = 1L;
    protected final static Log logger = Log.getInstance(VCFMetaDataLines.class);

    // Map of all header lines (including file format version lines)
    private final Map<String, VCFHeaderLine> mMetaData = new LinkedHashMap<>();

    private static final String KEY_SEPARATOR = ":";

    // Namespace key used for "other" (unstructured, non-ID) metadata lines. This string needs to be different from
    // any string in the set of legal structured line types in knownStructuredLineKeys.
    private static final String OTHER_KEY = "OTHER";

    /**
     * Add all metadata lines from Set. If a duplicate line is encountered (same key/ID pair for
     * structured lines, or duplicate content for unstructured lines with identical keys)
     * only the first line will be retained.
     *
     * @param newMetaData Set of lines to be added to the list.
     * @throws IllegalArgumentException if a fileformat line is added
     */
    public void addAllMetaDataLines(final Set<VCFHeaderLine> newMetaData) {
        newMetaData.forEach(this::addMetaDataLine);
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
        final String key = makeKeyForLine(headerLine);
        if (VCFHeaderVersion.isFormatString(headerLine.getKey())) {
            // In order to ensure that only one version line is retained, this needs to check for all possible
            // types of version lines (v3.2 used "format" instead of "fileformat")
            final Optional<VCFHeaderLine> fileFormatLine = getOtherHeaderLines().stream()
                    .filter(line -> VCFHeaderVersion.isFormatString(line.getKey()))
                    .findAny();
            if (fileFormatLine.isPresent() && !fileFormatLine.get().equals(headerLine)) {
                final String message = String.format(
                        "Attempt to add a version header line (%s) that collides with an existing line header line (%s). " +
                                "Use setVCFVersion to change the header version.",
                        headerLine,
                        mMetaData.get(key));
                throw new TribbleException(message);
            }
        } else {
            final VCFHeaderLine existingLine = mMetaData.get(key);
            if (existingLine != null && !existingLine.equals(headerLine) && VCFUtils.getVerboseVCFLogging()) {
                // Previous htsjdk implementations would round trip lines with duplicate IDs by preserving
                // them in the master header line list maintained by VCFHeader, but would silently drop them
                // from the individual typed header line lists, so the duplicates would not be included in
                // queries for typed line (i.e. via getInfoHeaderLines()). This implementation doesn't retain
                // the duplicates (the 4.2/4.3 specs expressly forbid it), so they're dropped here.
                //
                // TODO: Is it sufficient to log a warning for this case (duplicate key) ? Should we add code
                // TODO: here to throw only for v4.3+ ? Only if VCFUtils.getStrictVCFVersionValidation is set ? Or both ?
                final String message = String.format(
                        "Attempt to add header line (%s) collides with existing line header line (%s). " +
                                "The existing header line will be discarded.",
                        headerLine,
                        mMetaData.get(key));
                logger.warn(message);
            }
        }
        mMetaData.put(key, headerLine);
    }

    /**
     * Generate a unique key for a VCFHeaderLine. If the header line is a VCFStructuredHeaderLine, the key
     * is the concatenation of the VCFHeaderLine's key (i.e., the type of the VCFHeaderLine) and the ID for
     * that VCFHeaderLine (with a ":" separator). Otherwise, we use the concatenation of the OTHER_KEY, the
     * VCFHeaderLine's key, and a nonce value to ensure that unstructured lines never collide with structured
     * lines, and also can have duplicate identical instances.
     *
     * @param headerLine the {@link VCFHeaderLine} for which a key should be returned
     * @return the generated key
     */
    private String makeKeyForLine(final VCFHeaderLine headerLine) {
        if (headerLine.isIDHeaderLine()) { // required to have a unique ID
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
            // The previous implementation dropped duplicate keys for unstructured lines, but I don't think
            // the spec requires these to be unique. This is more permissive in that it allows duplicate lines such
            // as ##GATKCommandLines to accumulate if they have different values, but retains only one if it has
            // a unique value.
            return makeKey(OTHER_KEY, headerLine.getKey() + headerLine.hashCode());
        }
    }

    // Create a VCFHeaderLine hashmap key given a key and an id
    private String makeKey(final String nameSpace, final String id) { return nameSpace + KEY_SEPARATOR + id; }

    public void setVCFVersion(final VCFHeaderVersion newVCFVersion) {
        ValidationUtils.nonNull(newVCFVersion);

        // find any existing version line
        final List<VCFHeaderLine> allFormatLines = mMetaData.values()
                .stream()
                .filter(line -> VCFHeaderVersion.isFormatString(line.getKey()))
                .collect(Collectors.toList());
        // This class always does not mandate that the list it maintains contains a fileformat line
        // at all times (its VCFHeader's job to maintain that condition for the header object).
        // Therefore we can only check for the version going backwards when a ##fileformat actually
        // line exists. Either way, validate all existing lines.
        if (!allFormatLines.isEmpty()) {
             if (allFormatLines.size() > 1) {
                 throw new IllegalStateException(
                         String.format("The metadata lines class contains more than one version line (%s)",
                                 allFormatLines.stream()
                                         .map(VCFHeaderLine::toString)
                                         .collect(Collectors.joining(","))));
             }
            final VCFHeaderLine fileFormatLine = allFormatLines.get(0);
            final VCFHeaderVersion currentVersion = VCFHeaderVersion.toHeaderVersion(fileFormatLine.getValue());
            final int compareTo = newVCFVersion.compareTo(currentVersion);
                if (compareTo < 0) {
                    throw new IllegalStateException(String.format(
                            "New header version %s must be >= existing version %s",
                            newVCFVersion,
                            currentVersion));
                }
                removeHeaderLine(fileFormatLine);
        }

        // no matter what, call validateMetaDataLines to ensure the version transition is valid WRT any
        // existing lines
        validateMetaDataLines(newVCFVersion, false);
        addMetaDataLine(VCFHeader.getHeaderVersionLine(newVCFVersion));
    }

    /**
     * Return any existing line if the list already contains a header line of the same type/id. The line
     * need not satisfy equality for the queryLine for ID lines, only that it is of the same type/id.
     *
     * @param queryLine the source line to use to check for equivalents
     * @return The existing header line of the type/key provided, otherwise NULL.
     */
    public VCFHeaderLine hasEquivalentHeaderLine(final VCFHeaderLine queryLine) {
        return mMetaData.get(makeKeyForLine(queryLine));
    }

    /**
     * Remove the headerline matching this headerline (determined by makeKeyForLine) if it exists
     *
     * @param headerLine the header line to remove
     * @return The headerline removed, or null of no matching headerline was found
     */
    public VCFHeaderLine removeHeaderLine(final VCFHeaderLine headerLine) {
        return mMetaData.remove(makeKeyForLine(headerLine));
    }

    /**
     * Validate all metadata lines except the file format line against a target version.
     * If this returns true, the these lines can be upgraded to targetVersion. If false, the metadata lines
     * must be manually updated.
     * @param targetVersion the target version to validate against
     * @param includeFileFormatLine if true, fileformat lines will be included for validation validate. Using
     *                              false allows callers to test whether the metadata lines can be upgraded to
     *                              the proposed targetVersion.
     */
    //TODO: need to tell users how to resolve the case where this fails
    public void validateMetaDataLines(final VCFHeaderVersion targetVersion, final boolean includeFileFormatLine) {
        mMetaData.values()
                .stream()
                .filter(line -> !VCFHeaderVersion.isFormatString(line.getKey()) || includeFileFormatLine)
                .forEach(line -> validateMetaDataLine(targetVersion, line));
    }

    /**
     * Validate all metadata lines against the target version, including the case where the headerLine is
     * itself a fileformat line with a version, in case it clashes.
     *
     * @param targetVersion the version for which to validate
     * @param vcfHeaderLine the headerline to validate
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
        return Collections.unmodifiableSet(new LinkedHashSet<>(mMetaData.values()));
    }

    public Set<VCFHeaderLine> getMetaDataInSortedOrder() {
        return Collections.unmodifiableSet(new TreeSet<>(mMetaData.values()));
    }

    /**
     * @return all of the structured (ID) lines in their original file order, or an empty list if none were present
     */
    //TODO: does this correctly retain order ? does it return Contig header lines ? did it do so previously ?
    public List<VCFSimpleHeaderLine> getIDHeaderLines() {
        return mMetaData.values().stream()
                .filter(VCFHeaderLine::isIDHeaderLine)
                .map(hl -> (VCFSimpleHeaderLine) hl)
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
        final List<VCFContigHeaderLine> contigLines = new ArrayList<>();
        mMetaData.values().stream()
                .filter(hl -> hl.getKey().equals(VCFConstants.CONTIG_HEADER_KEY))
                .forEach(hl -> contigLines.add((VCFContigHeaderLine) hl));
        return Collections.unmodifiableList(contigLines);
    }

    /**
     * Get the VCFHeaderLine(s) whose key equals key.  Returns null if no such line exists
     * @param key the key to use to locate the headerline
     * @return collection of headerlines
     */
    public Collection<VCFHeaderLine> getMetaDataLines(final String key) {
        return mMetaData.values().stream()
                .filter(hl -> hl.getKey().equals(key)).collect(Collectors.toList());
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

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof VCFMetaDataLines)) return false;

        final VCFMetaDataLines that = (VCFMetaDataLines) o;

        return mMetaData.equals(that.mMetaData);
    }

    @Override
    public int hashCode() {
        return mMetaData.hashCode();
    }

    /**
     * @param id the id of the requested header line
     * @return the meta data line, or null if there is none
     */
    public VCFFilterHeaderLine getFilterHeaderLine(final String id) {
        return (VCFFilterHeaderLine) mMetaData.get(makeKey(VCFConstants.FILTER_HEADER_KEY, id));
    }

    /**
     * Returns the other VCFHeaderLines in their original ordering, where "other" means any
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
        b.append("[VCFMetaDataLines:");
        for ( final VCFHeaderLine line : mMetaData.values() )
            b.append("\n\t").append(line);
        return b.append("\n]").toString();
    }
}

