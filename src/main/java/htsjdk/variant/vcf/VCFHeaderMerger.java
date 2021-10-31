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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Class used to produce a set of header lines representing the merger of one or more input VCFHeaders.
 * <p>
 * The resulting lines have a version line matching the highest version of any of the input headers.
 * <p>
 * Some headers sets cannot be merged:
 * <ul>
 * <li> any header that is less than VCFv4.2 </li>
 * <li> any header that has a line that doesn't conform to the resulting merged version </li>
 * <li> any header that has a dictionary that can't be merged with any other header's dictionary </li>
 * </ul>
 */
public class VCFHeaderMerger {

    /**
     * Merge all header lines in a set of headers into a single set of header lines. The resulting set includes
     * all unique lines that appeared in any header; duplicates of lines are excluded from the result set. Equivalent
     * header lines are reduced to a single representative header line. The resulting set is compatible with (and
     * contains a ##fileformat version line for) the newest version seen in any of the headers provided in the
     * input collection.
     *
     * @param headers the headers to merge
     * @param emitWarnings true if warnings should be emitted
     * @return a set of merged VCFHeaderLines
     * @throws TribbleException if any header has a version < VCFv4.2, or if any header line in any
     * of the input headers is not compatible the newest version amongst all headers provided, or if any
     * header has a sequence dictionary that is incompatible with any other header's sequence dictionary
     */
    public static Set<VCFHeaderLine> getMergedHeaderLines(final Collection<VCFHeader> headers, final boolean emitWarnings) {
        ValidationUtils.nonNull(headers, "headers");
        ValidationUtils.validateArg(!headers.isEmpty(), "headers collection must be non empty");

        // use a VCFMetaDataLines object to accumulate header lines
        final VCFMetaDataLines mergedMetaData = new VCFMetaDataLines();
        final VCFHeader.HeaderConflictWarner conflictWarner = new VCFHeader.HeaderConflictWarner(emitWarnings);

        final VCFHeaderVersion newestVersion = getNewestHeaderVersion(headers);
        final SAMSequenceDictionary commonSequenceDictionary = getCommonSequenceDictionaryOrThrow(headers, conflictWarner);

        for (final VCFHeader sourceHeader : headers) {
            for (final VCFHeaderLine line : sourceHeader.getMetaDataInSortedOrder()) {
                final String key = line.getKey();
                if (VCFHeaderVersion.isFormatString(key) || key.equals(VCFHeader.CONTIG_KEY)) {
                    // drop all version and contig lines and set the version and commonSequenceDictionary at the end
                    continue;
                }

                // Structured header lines are only considered equal if they have identical key, id, and
                // attribute/value pairs, but for merging we need to reduce duplicate key/id pairs to a
                // single line, so we use a more discriminating "hasEquivalentHeaderLine" to detect logical
                // duplicates, and delegate to the individual header line implementations to do a smart
                // reconciliation.
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

    private static Set<VCFHeaderLine> makeMergedMetaDataSet(
            final VCFMetaDataLines mergedMetaData,
            final VCFHeaderVersion newestVersion,
            final SAMSequenceDictionary commonSequenceDictionary,
            final VCFHeader.HeaderConflictWarner conflictWarner) {
        // create a set of all of our merged header lines, starting with the new version, adding
        // in the merged lines, use the resulting list to create a header, add the common sequence
        // dictionary, and extract the resulting set of lines
        final Set<VCFHeaderLine> mergedLines = VCFHeader.makeHeaderVersionLineSet(newestVersion);
        mergedLines.addAll(mergedMetaData.getMetaDataInInputOrder());
        final VCFHeader mergedHeader = new VCFHeader(
                mergedLines,
                Collections.emptySet()
        );
        if (commonSequenceDictionary != null) {
            mergedHeader.setSequenceDictionary(commonSequenceDictionary);
        } else {
            conflictWarner.warn(
                    "The header lines resulting from a header merge have contain no contig lines because none " +
                            "of the input headers contains a sequence dictionary.");
        }

        return new LinkedHashSet<>(mergedHeader.getMetaDataInSortedOrder());
    }

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

    // Create a common sequence dictionary from the set of dictionaries in VCFHeaders, as long as
    // the headers either have identical dictionaries, or each dictionary is a subset of a common
    // superset that has all of the contigs ordered w/r/t each other. Otherwise throw.
    private static SAMSequenceDictionary getCommonSequenceDictionaryOrThrow(
            final Collection<VCFHeader> headers,
            final VCFHeader.HeaderConflictWarner conflictWarner) {
        SAMSequenceDictionary candidateDictionary = null;

        // Because we're doing pairwise comparisons and always selecting the best dictionary as
        // our running candidate, we need to visit the headers in order of dictionary size
        // (largest first). This prevents a premature failure where an individual pairwise
        // comparison erroneously fails because the source is pairwise incompatible with the
        // running candidate, and the common superset exists but we just haven't seen it yet.
        final List<VCFHeader> headersByDictionarySize = new ArrayList<>(headers);
        Collections.sort(
                headersByDictionarySize,
                new Comparator<VCFHeader>() {
                    @Override
                    public int compare(final VCFHeader hdr1, final VCFHeader hdr2) {
                        return Integer.compare(getDictionarySize(hdr1), getDictionarySize(hdr2));
                    }
                }.reversed());

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
                        "\n\nHeader dictionary:\n\n%1.2000s\n\nis incompatible with the common dictionary:\n\n%1.2000s\n\nOffending VCF header:\n\n%1.2000s\n",
                failureReason,
                sourceSequenceDictionary.getSequences().stream().map(SAMSequenceRecord::toString).collect(Collectors.joining("\n")),
                commonSequenceDictionary.getSequences().stream().map(SAMSequenceRecord::toString).collect(Collectors.joining("\n")),
                getHeaderFragmentForDisplay(sourceHeader));
    }

    private static String getHeaderFragmentForDisplay(final VCFHeader sourceHeader) {
        return sourceHeader.getContigLines().stream().map(VCFContigHeaderLine::toString).collect(Collectors.joining("\n"));
    }
}
