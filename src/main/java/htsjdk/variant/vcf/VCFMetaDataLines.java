package htsjdk.variant.vcf;

import htsjdk.samtools.util.Log;
import htsjdk.tribble.TribbleException;
import htsjdk.utils.ValidationUtils;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Class for managing a set of VCFHeaderLines for a VCFHeader.
 * This class does not mandate that the list it maintains always contains a fileformat line
 * (its VCFHeader's job to maintain that condition for the header).
 */
//Visible to allow disq Kryo registration for serialization
public final class VCFMetaDataLines implements Serializable {
    public static final long serialVersionUID = 1L;
    protected final static Log logger = Log.getInstance(VCFMetaDataLines.class);

    // Map of all header lines (including file format version lines)
    private final Map<HeaderLineMapKey, VCFHeaderLine> mMetaData = new LinkedHashMap<>();

    //TODO: should this cache the fileformat line so we don't have to go find it...

    /**
     * Add all metadata lines from Set. If a duplicate line is encountered (same key/ID pair for
     * structured lines, or duplicate content for unstructured lines with identical keys)
     * only the first line will be retained.
     *
     * @param newMetaData Set of lines to be added to the list.
     * @throws IllegalArgumentException if a fileformat line is added
     */
    public void addMetaDataLines(final Set<VCFHeaderLine> newMetaData) {
        newMetaData.forEach(this::addMetaDataLine);
    }

    /**
     * Add a metadata line to the list. If a duplicate line is encountered (same key/ID pair for
     * structured lines, or duplicate content for unstructured lines with identical keys), the new
     * line will replace the existing line.
     *
     * @param newMetaDataLine header line to attempt to add
     * @throws IllegalArgumentException if the line being added is a fileformat (VCF version) line
     * that has a different version that any version line that already exists in the metadata lines
     * maintained by this object, and any existing version line fails to validate against the new
     * version
     */
    public void addMetaDataLine(final VCFHeaderLine newMetaDataLine) {
        final HeaderLineMapKey newMapKey = makeKeyForLine(newMetaDataLine);
        if (VCFHeaderVersion.isFormatString(newMetaDataLine.getKey())) {
            // setVCFVersion calls addMetaDataLine back after removing the exiting key with a new version
            setVCFVersion(VCFHeaderVersion.toHeaderVersion(newMetaDataLine.getValue()));
        } else {
            final VCFHeaderLine existingLine = mMetaData.get(newMapKey);
            if (existingLine != null && !existingLine.equals(newMetaDataLine)) {
                // Previous htsjdk implementations would round trip lines with duplicate IDs by preserving
                // them in the master header line list maintained by VCFHeader, but would silently drop them
                // from the individual typed header line lists, so the duplicates would not be returned in
                // queries for typed lines (i.e. via getInfoHeaderLines()). This implementation doesn't retain
                // the duplicates (the 4.2/4.3 specs expressly forbid it), so they're dropped here.
                final String message = String.format(
                        "Attempt to add header a line (%s) that collides with an existing line header line (%s). " +
                                "The existing header line will be discarded.",
                        newMetaDataLine,
                        mMetaData.get(newMapKey));
                logger.warn(message);
            }
            mMetaData.put(newMapKey, newMetaDataLine);
        }
    }

    /**
     * Establish a new VCF Version for this line list. This requires validating any existing lines against
     * the new version, removing an existing version line if one exists, and then adding a new fileformat
     * line to the list.
     * @param newVCFVersion new VCF Version to use for this line list
     * @throws TribbleException if the new version is older than the previous version, or if any existing
     * line fails to validate against the new version
     */
    public void setVCFVersion(final VCFHeaderVersion newVCFVersion) {
        ValidationUtils.nonNull(newVCFVersion);

        final VCFHeaderLine currentVersionLine = getFileFormatLine();
        if (currentVersionLine != null) {
            final VCFHeaderVersion currentVCFVersion = VCFHeaderVersion.toHeaderVersion(currentVersionLine.getValue());
            final int compareTo = newVCFVersion.compareTo(currentVCFVersion);
            if (compareTo < 0) {
                throw new TribbleException(String.format(
                        "New header version %s must be >= existing version %s",
                        newVCFVersion,
                        currentVCFVersion));
            }
            // make sure we validate the meta data lines and throw if any line fails BEFORE we mutate anything
            validateMetaDataLines(newVCFVersion, false);
            removeMetaDataLine(currentVersionLine);
        }

        // add the line directly, don't call addMetaDataLine here to prevent mutual recursion back to this method
        final VCFHeaderLine newVersionLine = VCFHeader.makeHeaderVersionLine(newVCFVersion);
        mMetaData.put(makeKeyForLine(newVersionLine), newVersionLine);
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
     * Remove the headerline matching this headerline (determined by makeKeyForLine) if it exists. This
     * only removes a header line if it is an exact match of the line provided.
     *
     * @param headerLine the header line to remove
     * @return The headerline removed, or null of no matching headerline was found
     */
    public VCFHeaderLine removeMetaDataLine(final VCFHeaderLine headerLine) {
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
                .forEach(line -> line.validateForVersion(targetVersion));
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
        return Collections.unmodifiableSet(new LinkedHashSet<>(new TreeSet<>(mMetaData.values())));
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

    /**
     * The version/fileformat header line if one exists, otherwise null.
     * @return The version/fileformat header line if one exists, otherwise null.
     */
    public VCFHeaderLine getFileFormatLine() {
        // find any existing version line(s). since there are multiple possible keys that
        // represent version lines (old V3 specs used "format" instead of "fileformat")
        final List<VCFHeaderLine> existingVersionLines = mMetaData.values()
                .stream()
                .filter(line -> VCFHeaderVersion.isFormatString(line.getKey()))
                .collect(Collectors.toList());

        // This class doesn't mandate that the list it maintains always contains a fileformat line
        // (its VCFHeader's job to maintain that condition for the header).
        if (!existingVersionLines.isEmpty()) {
            if (existingVersionLines.size() > 1) {
                throw new IllegalStateException(
                        String.format("The metadata lines class contains more than one version line (%s)",
                                existingVersionLines.stream()
                                        .map(VCFHeaderLine::toString)
                                        .collect(Collectors.joining(","))));
            }
            return existingVersionLines.get(0);
        } else {
            return null;
        }
    }

    @Override
    public String toString() {
        final StringBuilder b = new StringBuilder();
        b.append("[VCFMetaDataLines:");
        for ( final VCFHeaderLine line : mMetaData.values() )
            b.append("\n\t").append(line);
        return b.append("\n]").toString();
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
     * Generate a unique key for a VCFHeaderLine. If the header line is a VCFStructuredHeaderLine, the key
     * is the concatenation of the VCFHeaderLine's key (i.e., the type of the VCFHeaderLine) and the ID for
     * that VCFHeaderLine (with a ":" separator). Otherwise, we use the concatenation of the OTHER_KEY, the
     * VCFHeaderLine's key, and a nonce value to ensure that unstructured lines never collide with structured
     * lines, and also can have duplicate identical instances.
     *
     * @param headerLine the {@link VCFHeaderLine} for which a key should be returned
     * @return the generated HeaderLineMapKey
     */
    private HeaderLineMapKey makeKeyForLine(final VCFHeaderLine headerLine) {
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
    private HeaderLineMapKey makeKey(final String nameSpace, final String id) { return new HeaderLineMapKey(nameSpace, id); }

    // composite keys used by the metadata lines map
    private static class HeaderLineMapKey implements Serializable {
        public static final long serialVersionUID = 1L;

        final String key;
        final String constraint;

        public HeaderLineMapKey(final String key, final String constraint) {
            this.key = key;
            this.constraint = constraint;
        }

        public final String getKey() { return key; }
        public final String getConstraint() { return constraint; }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            final HeaderLineMapKey that = (HeaderLineMapKey) o;

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

