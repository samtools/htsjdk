package htsjdk.variant.vcf;

import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceDictionaryUtils;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.tribble.TribbleException;
import htsjdk.utils.ValidationUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Class used to produce a set of header lines resulting from the merger of one or more input VCFHeaders.
 * <p>
 * The resulting lines have a version line matching the highest version of any of the input headers.
 * <p>
 * The headers to be merged must conform to certain requirements:
 * Some headers sets cannot be merged, and will result in an exception being thrown:
 * <ul>
 * <li> Headers must have a version that is at least VCF v4.2. Headers from older versions may not be merged (note
 * that older headers that are read from input files are automatically "converted" to VCF v4.2 by VCFCodec. See
 * {@link AbstractVCFCodec#setVCFHeader(VCFHeader).}</li>
 * <li> any header that contains a header line that doesn't conform to the resulting (highest )version of any
 * header in the merge list </li>
 * <li> any header that has a sequence dictionary that is incompatible with any other merged header's
 * sequence dictionary. All headers must either share a common sequence dictionary, or have a sequence dictionary
 * that is a subset of the common sequence dictionary that is taken from the remaining headers. </li>
 * </ul>
 */
public class VCFHeaderMerger {

    /**
     * Merge all header lines in a set of headers into a single set of header lines. The resulting set includes
     * all unique lines that appeared in any header; duplicates of lines are excluded from the result set. Equivalent
     * header lines are reduced to a single representative header line. The resulting set contains a ##fileformat
     * version line for the newest version seen in any of the headers provided in the input header collection,
     * and all lines in the merged set are compatible with that version.
     *
     * @param headers the headers to merge
     * @param emitWarnings true if warnings should be emitted
     * @return a set of merged VCFHeaderLines
     * @throws TribbleException if any header has a version < VCFv4.2, or if any header line in any
     * input header is not compatible the newest version selected from amongst all headers provided, or if any
     * header has a sequence dictionary that is incompatible with any other header's sequence dictionary
     */
    public static Set<VCFHeaderLine> getMergedHeaderLines(final Collection<VCFHeader> headers, final boolean emitWarnings) {
        ValidationUtils.nonNull(headers, "headers");
        ValidationUtils.validateArg(!headers.isEmpty(), "headers collection must be non empty");

        // use a VCFMetaDataLines object to accumulate header lines
        final VCFMetaDataLines mergedMetaData = new VCFMetaDataLines();
        final HeaderMergeConflictWarnings conflictWarner = new HeaderMergeConflictWarnings(emitWarnings);

        final VCFHeaderVersion newestVersion = getNewestHeaderVersion(headers);
        final SAMSequenceDictionary commonSequenceDictionary = getCommonSequenceDictionaryOrThrow(headers, conflictWarner);

        for (final VCFHeader sourceHeader : headers) {
            for (final VCFHeaderLine line : sourceHeader.getMetaDataInSortedOrder()) {
                final String key = line.getKey();
                if (VCFHeaderVersion.isFormatString(key) || key.equals(VCFHeader.CONTIG_KEY)) {
                    // drop all version and contig lines, and at the end we'll set the version and
                    // commonSequenceDictionary
                    continue;
                }

                // Structured header lines are only considered equal if they have identical key, id, and
                // attribute/value pairs, but for merging we need to reduce lines that have the same key/id pairs
                // but different attributes to a single line. So use the more permissive "findEquivalentHeaderLine"
                // to detect equivalent lines, and delegate to the individual header line implementations to do the
                // smart reconciliation.
                final VCFHeaderLine other = mergedMetaData.findEquivalentHeaderLine(line);
                if (other != null && !line.equals(other)) {
                    if (key.equals(VCFConstants.FORMAT_HEADER_KEY)) {
                        // Delegate to the FORMAT line resolver
                        mergedMetaData.addMetaDataLine(
                                VCFFormatHeaderLine.getMergedFormatHeaderLine(
                                    (VCFFormatHeaderLine) line,
                                    (VCFFormatHeaderLine) other,
                                    conflictWarner)
                        );
                    } else if (key.equals(VCFConstants.INFO_HEADER_KEY)) {
                        // Delegate to the INFO line resolver
                        mergedMetaData.addMetaDataLine(
                                VCFInfoHeaderLine.getMergedInfoHeaderLine(
                                    (VCFInfoHeaderLine) line,
                                    (VCFInfoHeaderLine) other,
                                    conflictWarner)
                        );
                    } else if (line.isIDHeaderLine()) {
                        // equivalent ID header line, but not a compound(format/info) line, and also not strictly equal
                        // to the existing line: preserve the existing line (this *may* drop attributes/values if the
                        // dropped line has additional attributes)
                        conflictWarner.warn(
                                String.format("Dropping duplicate header line %s during header merge, retaining equivalent line %s",
                                        line,
                                        other));
                    } else {
                        // a non-structured line with a duplicate key of an existing line, but a different value,
                        // retain the new line in addition to the old one
                        mergedMetaData.addMetaDataLine(line);
                    }
                } else {
                    mergedMetaData.addMetaDataLine(line);
                }
            }
        }
        return makeMergedMetaDataSet(mergedMetaData, newestVersion, commonSequenceDictionary, conflictWarner);
    }

    // Create the final set of all of our merged header lines. Start with the version line for the new
    // version, add in the lines from the merged set, use the resulting list to create a header, add the common
    // sequence dictionary to that, and then extract and return the resulting set of lines in sorted order
    private static Set<VCFHeaderLine> makeMergedMetaDataSet(
            final VCFMetaDataLines mergedMetaData,
            final VCFHeaderVersion newestVersion,
            final SAMSequenceDictionary commonSequenceDictionary,
            final HeaderMergeConflictWarnings conflictWarner) {

        if (conflictWarner.emitWarnings) {
            //TODO: any header contains a line that fails version validation, then the merge should fail...just like
            // a version upgrade would fail for that same header. We can't honor the fallback policy i.e., fallback to
            // the old version) here because that would require knowing how to back-version the OTHER headers being merged
            mergedMetaData.getVersionValidationFailures(newestVersion)
                    .forEach(validationError -> conflictWarner.warn(validationError.getFailureMessage()));
        }

        final Set<VCFHeaderLine> mergedLines = VCFHeader.makeHeaderVersionLineSet(newestVersion);
        mergedLines.addAll(mergedMetaData.getMetaDataInInputOrder());
        final VCFHeader mergedHeader = new VCFHeader(mergedLines, Collections.emptySet());
        if (commonSequenceDictionary != null) {
            mergedHeader.setSequenceDictionary(commonSequenceDictionary);
        } else {
            conflictWarner.warn(
                    "The header lines resulting from a header merge contain no contig lines because none " +
                            "of the input headers contains a sequence dictionary.");
        }

        return new LinkedHashSet<>(mergedHeader.getMetaDataInSortedOrder());
    }

    // Find the newest version af any header in the input set, and return that to use as the target
    // version for the merged lines.
    private static VCFHeaderVersion getNewestHeaderVersion(final Collection<VCFHeader> vcfHeaders) {
        VCFHeaderVersion newestVersion = null;
        for (final VCFHeader header : vcfHeaders) {
            final VCFHeaderVersion vcfVersion = header.getVCFHeaderVersion();
            if (!vcfVersion.isAtLeastAsRecentAs(VCFHeaderVersion.VCF4_2)) {
                throw new TribbleException(String.format(
                        "Cannot merge a VCFHeader with version (%s) that is older than version %s",
                        header.getVCFHeaderVersion(), VCFHeaderVersion.VCF4_2));
            }
            if (newestVersion == null || (vcfVersion.ordinal() > newestVersion.ordinal())) {
                newestVersion = vcfVersion;
            }
        }
        return newestVersion;
    }

    // Create a common sequence dictionary from the set of dictionaries in VCFHeaders. The headers must
    // either have identical dictionaries, or contain a common superset dictionary where individual dictionaries
    // contain a dictionary that is subset of that common superset. Otherwise throw.
    private static SAMSequenceDictionary getCommonSequenceDictionaryOrThrow(
            final Collection<VCFHeader> headers,
            final HeaderMergeConflictWarnings conflictWarner) {
        SAMSequenceDictionary candidateDictionary = null;

        // Because we're doing pairwise comparisons and always selecting the best dictionary as
        // our running candidate, we need to visit the headers in order of dictionary size
        // (largest first). This prevents a premature failure where an individual pairwise
        // comparison erroneously fails because the source is pairwise incompatible with the
        // running candidate, and the common superset exists but we just haven't seen it yet.
        final List<VCFHeader> headersByDictionarySize = new ArrayList<>(headers);
        headersByDictionarySize.sort(((Comparator<VCFHeader>)
                (hdr1, hdr2) -> Integer.compare(getDictionarySize(hdr1), getDictionarySize(hdr2))).reversed());

        for ( final VCFHeader sourceHeader : headersByDictionarySize ) {
            final SAMSequenceDictionary sourceDictionary = sourceHeader.getSequenceDictionary();
            if (sourceDictionary != null) {
                if (candidateDictionary == null) {
                    candidateDictionary = sourceDictionary;
                } else {
                    // first, compare with checkContigOrdering on
                    final SAMSequenceDictionaryUtils.SequenceDictionaryCompatibility compatibility =
                            SAMSequenceDictionaryUtils.compareDictionaries(
                                    candidateDictionary,
                                    sourceDictionary,
                                    true);
                    switch (compatibility) {
                        case IDENTICAL: // existing candidateDictionary is identical to sourceDictionary, so keep it
                        case SUPERSET:  // existing candidateDictionary is a superset of sourceDictionary, so keep it
                            break;

                        case COMMON_SUBSET: // fall through
                        case DIFFERENT_INDICES:
                            // There exists a common subset of contigs, but for merging purposes we have a slightly
                            // stricter requirement, that one dictionary is a superset of the other. So try the
                            // comparison again with checkContigOrdering off, in both directions. If one is a
                            // superset of the other, retain the superset.
                            if (SAMSequenceDictionaryUtils.SequenceDictionaryCompatibility.SUPERSET ==
                                    SAMSequenceDictionaryUtils.compareDictionaries(
                                            candidateDictionary,
                                            sourceDictionary,
                                            false)) {
                                break; // keep our candidate
                            } else if (SAMSequenceDictionaryUtils.SequenceDictionaryCompatibility.SUPERSET ==
                                    SAMSequenceDictionaryUtils.compareDictionaries(
                                            sourceDictionary,
                                            candidateDictionary,
                                            false)) {
                                candidateDictionary = sourceDictionary; // take the sourceDictionary as the new candidate
                            } else {
                                // dictionaries are disjoint, and we have no basis to choose a merge order for the
                                // non-common contigs, so give up
                                throw new TribbleException(
                                        createHeaderDictionaryFailureMessage(
                                                candidateDictionary, sourceHeader, sourceDictionary, compatibility));
                            }
                            break;

                        case NO_COMMON_CONTIGS:              // no overlap between dictionaries
                        case UNEQUAL_COMMON_CONTIGS:         // common subset has contigs that have the same name but different lengths
                        case NON_CANONICAL_HUMAN_ORDER:      // human reference detected but the order of the contigs is non-standard (lexicographic, for example)
                        case OUT_OF_ORDER:                   // the two dictionaries overlap but the overlapping contigs occur in different
                        default:
                            throw new TribbleException(
                                    createHeaderDictionaryFailureMessage(
                                            candidateDictionary, sourceHeader, sourceDictionary, compatibility));
                    }
                }
            } else {
                conflictWarner.warn(
                        String.format(
                                "Merging header with no sequence dictionary: %s",
                                getHeaderFragmentForDisplay(sourceHeader)));
            }
        }
        return candidateDictionary;
    }

    private static Integer getDictionarySize(final VCFHeader hdr) {
        final SAMSequenceDictionary dictionary = hdr.getSequenceDictionary();
        return dictionary == null ? 0 : dictionary.size();
    }

    private static String createHeaderDictionaryFailureMessage(
            final SAMSequenceDictionary commonSequenceDictionary,
            final VCFHeader sourceHeader,
            final SAMSequenceDictionary sourceSequenceDictionary,
            final SAMSequenceDictionaryUtils.SequenceDictionaryCompatibility failureReason) {
        // return a nice long message that includes as much of the offending context as is reasonable,
        // without printing the entire context, since the headers and sequence dictionaries can have
        // thousands of entries
        return String.format(
                "Can't merge VCF headers with incompatible sequence dictionaries, merge failed due to %s:" +
                        "\n\nHeader dictionary:\n\n%1.2000s\n\nis incompatible with the common dictionary:\n\n%1.2000s\n\n merging VCF header:\n\n%1.2000s\n",
                failureReason,
                sourceSequenceDictionary.getSequences().stream().map(SAMSequenceRecord::toString).collect(Collectors.joining("\n")),
                commonSequenceDictionary.getSequences().stream().map(SAMSequenceRecord::toString).collect(Collectors.joining("\n")),
                getHeaderFragmentForDisplay(sourceHeader));
    }

    private static String getHeaderFragmentForDisplay(final VCFHeader sourceHeader) {
        return sourceHeader.getContigLines().stream().map(VCFContigHeaderLine::toString).collect(Collectors.joining("\n"));
    }

    /** Only displays a warning if warnings are enabled and an identical warning hasn't been already issued */
    static final class HeaderMergeConflictWarnings {
        boolean emitWarnings;
        final Set<String> alreadyIssued = new HashSet<>();

        protected HeaderMergeConflictWarnings(final boolean emitWarnings ) {
            this.emitWarnings = emitWarnings;
        }

        public void warn(final String msg) {
            if ( emitWarnings && ! alreadyIssued.contains(msg) ) {
                alreadyIssued.add(msg);
                VCFHeader.logger.warn(msg);
            }
        }
    }
}
