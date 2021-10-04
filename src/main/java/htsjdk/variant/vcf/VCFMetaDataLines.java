package htsjdk.variant.vcf;

import htsjdk.samtools.util.Log;
import htsjdk.tribble.TribbleException;
import htsjdk.utils.ValidationUtils;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Class for managing a set of VCFHeaderLines for a VCFHeader.
 */
//Visible to allow disq Kryo registration for serialization
public final class VCFMetaDataLines implements Serializable {
    public static final long serialVersionUID = 1L;
    protected final static Log logger = Log.getInstance(VCFMetaDataLines.class);

    // Map of all header lines (including file format version lines)
    private final Map<HeaderLineKey, VCFHeaderLine> mMetaData = new LinkedHashMap<>();

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
        final HeaderLineKey key = makeKeyForLine(headerLine);
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
                        fileFormatLine.get());
                throw new TribbleException(message);
            }
        } else {
            final VCFHeaderLine existingLine = mMetaData.get(key);
            if (existingLine != null && !existingLine.equals(headerLine)) {
                // Previous htsjdk implementations would round trip lines with duplicate IDs by preserving
                // them in the master header line list maintained by VCFHeader, but would silently drop them
                // from the individual typed header line lists, so the duplicates would not be included in
                // queries for typed line (i.e. via getInfoHeaderLines()). This implementation doesn't retain
                // the duplicates (the 4.2/4.3 specs expressly forbid it), so they're dropped here.
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

    public void setVCFVersion(final VCFHeaderVersion newVCFVersion) {
        ValidationUtils.nonNull(newVCFVersion);

        // before we mutate anything, call validateMetaDataLines to ensure the version transition is
        // going to be valid for any existing lines
        validateMetaDataLines(newVCFVersion, false);

        // extract any existing version line
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
    //TODO: we need to tell users how to resolve the case where this fails due to version validation
    //i.e, use a custom upgrade tool
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

    /**
     * Generate a unique key for a VCFHeaderLine. If the header line is a VCFStructuredHeaderLine, the key
     * is the concatenation of the VCFHeaderLine's key (i.e., the type of the VCFHeaderLine) and the ID for
     * that VCFHeaderLine (with a ":" separator). Otherwise, we use the concatenation of the OTHER_KEY, the
     * VCFHeaderLine's key, and a nonce value to ensure that unstructured lines never collide with structured
     * lines, and also can have duplicate identical instances.
     *
     * @param headerLine the {@link VCFHeaderLine} for which a key should be returned
     * @return the generated HeaderLineKey
     */
    private HeaderLineKey makeKeyForLine(final VCFHeaderLine headerLine) {
        if (headerLine.isIDHeaderLine()) {
            // these are required to have a unique ID, so use the line key as the key, and the id as the constraint
            return makeKey(headerLine.getKey(), headerLine.getID());
        } else {
            // Allow duplicate unstructured "other" keys, as long as they have different values. Use
            // the line key as the key, and the line hashcode as the constraint.
            //
            // The previous implementation dropped duplicate keys for unstructured lines, but the spec doesn't
            // require these to be unique (only to have unique values). This implementation is more permissive in
            // that it allows lines with duplicate keys to accumulate as long as they have different values, but
            // retains only one with a unique value.
            return makeKey(headerLine.getKey(), Integer.toString(headerLine.hashCode()));
        }
    }

    // Create a VCFHeaderLine hashmap key given a key and an id
    private HeaderLineKey makeKey(final String nameSpace, final String id) { return new HeaderLineKey(nameSpace, id); }

    // composite keys used by the metadata lines map
    private static class HeaderLineKey implements Serializable {
        public static final long serialVersionUID = 1L;

        final String key;
        final String constraint;

        public HeaderLineKey(final String key, final String constraint) {
            this.key = key;
            this.constraint = constraint;
        }

        public final String getKey() { return key; }
        public final String getConstraint() { return constraint; }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            final HeaderLineKey that = (HeaderLineKey) o;

            if (!key.equals(that.key)) return false;
            return constraint.equals(that.constraint);
        }

        @Override
        public int hashCode() {
            int result = key.hashCode();
            result = 31 * result + constraint.hashCode();
            return result;
        }
    }
}

