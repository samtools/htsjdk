package htsjdk.variant.vcf;

import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceDictionaryUtils;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.tribble.TribbleException;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Class used to produce a set of merged header lines representing the merger of one or more input headers.
 * <p>
 * The version of the resulting lines is the highest version of any of the input headers.
 * <p>
 * Some headers sets cannot be merged:
 * <ul>
 * <li> any header that is < vcfV4.2 </li>
 * <li> any header that has a line that doesn't conform to the resulting version </li>
 * <li> any header that has a dictionary that can't be merged with any other header's dictionary </li>
 * </ul>
 */
public class VCFHeaderMerger {

    /**
     * Merge the header lines from all of the header lines in a set of header. The resulting set includes
     * all unique lines that appeared in any header. Lines that are duplicated are removed from the result
     * set. The resulting set is compatible with (and contains a ##fileformat version line for) the highest
     * version seen in any of the headers provided in the input collection.
     *
     * @param headers the headers to merge
     * @param emitWarnings true of warnings should be emitted
     * @return a set of merged VCFHeaderLines
     * @throws TribbleException if any header has a version < vcfV4.2, or if any header line in any
     * of the input headers is not compatible the newest version amongst all headers provided
     */
    public static Set<VCFHeaderLine> getMergedHeaderLines(final Collection<VCFHeader> headers, final boolean emitWarnings) {
        final VCFMetaDataLines mergedMetaData = new VCFMetaDataLines();
        final VCFHeader.HeaderConflictWarner conflictWarner = new VCFHeader.HeaderConflictWarner(emitWarnings);
        final Set<VCFHeaderVersion> vcfVersions = new HashSet<>(headers.size());

        final SAMSequenceDictionary commonSequenceDictionary = getCommonSequenceDictionaryOrThrow(headers);

        VCFHeaderVersion newestVersion = null;
        for ( final VCFHeader sourceHeader : headers ) {
            final VCFHeaderVersion sourceHeaderVersion = sourceHeader.getVCFHeaderVersion();
            if (!sourceHeaderVersion.isAtLeastAsRecentAs(VCFHeaderVersion.VCF4_2)) {
                throw new TribbleException(String.format("Cannot merge a VCFHeader with version (%s) that is older than version %s",
                        sourceHeaderVersion, VCFHeaderVersion.VCF4_2));
            }
            vcfVersions.add(sourceHeaderVersion);
            for ( final VCFHeaderLine line : sourceHeader.getMetaDataInSortedOrder()) {
                final String key = line.getKey();
                if (VCFHeaderVersion.isFormatString(key)) {
                    if (newestVersion == null || (sourceHeader.getVCFHeaderVersion().ordinal() > newestVersion.ordinal())) {
                        newestVersion = sourceHeaderVersion;
                    }
                    // don't add a version line yet; wait until the end and we'll add the highest version,
                    // and then validate all lines against that
                    continue;
                } else if (key.equals(VCFHeader.CONTIG_KEY)) {
                    //drop contig lines entirely, and add the commonSequenceDictionary at the end
                    continue;
                }

                // NOTE: Structured header lines are only equal if they have identical attributes
                // and values (which is different from the previous implementation for some line types, like
                // compound header lines). So we use a more discriminating "hasEquivalentHeaderLine" to determine
                // equivalence, and delegate to the actual lines and to do a smart reconciliation.
                final VCFHeaderLine other = mergedMetaData.hasEquivalentHeaderLine(line);
                if (other != null && !line.equals(other) ) {
                    if (!key.equals(other.getKey())) {
                        throw new IllegalArgumentException(
                                String.format("Attempt to merge incompatible header lines %s/%s", line.getKey(), other.getKey()));
                    } else if (key.equals(VCFConstants.FORMAT_HEADER_KEY)) {
                        // Delegate to the format line resolver
                        mergedMetaData.addMetaDataLine(VCFFormatHeaderLine.getMergedFormatHeaderLine(
                                (VCFFormatHeaderLine) line,
                                (VCFFormatHeaderLine) other,
                                conflictWarner)
                        );
                    } else if (key.equals(VCFConstants.INFO_HEADER_KEY)) {
                        // Delegate to the info line resolver
                        mergedMetaData.addMetaDataLine(VCFInfoHeaderLine.getMergedInfoHeaderLine(
                                (VCFInfoHeaderLine) line,
                                (VCFInfoHeaderLine) other,
                                conflictWarner)
                        );
                    } else {
                        // same type of header line, but not compound(format/info), and not equal,
                        // so preserve the existing line; this may drop attributes/values.
                        // note that for contig lines, since the new one has the same ID, we don't need
                        // to add a new line, just retaining the existing one is fine
                        conflictWarner.warn(line, "Ignoring header line already in map: this header line = " +
                                line + " already present header = " + other);
                    }
                } else {
                    mergedMetaData.addMetaDataLine(line);
                }
            }
        }

        // create a set of all of our merged header lines, add in the new version, create
        // a header, add the sequence dictionary, and return the resulting lines
        final Set<VCFHeaderLine> mergedLines = VCFHeader.makeHeaderVersionLineSet(newestVersion);
        mergedLines.addAll(mergedMetaData.getMetaDataInInputOrder());
        final VCFHeader mergedHeader = new VCFHeader(
                mergedLines,
                Collections.emptySet()
        );
        if (commonSequenceDictionary != null) {
            mergedHeader.setSequenceDictionary(commonSequenceDictionary);
        }

        return new LinkedHashSet<>(mergedHeader.getMetaDataInSortedOrder());
    }

    // Create a common sequence dictionary from a set of dictionaries in VCFHeaders, as long as the headers
    // either have identical dictionaries, or dictionaries where one dictionary is a subset of another. It
    // cannot merge any dictionary that has a (disjoint) contig that is not already ordered wrt/all other
    // common contigs.
    private static SAMSequenceDictionary getCommonSequenceDictionaryOrThrow(final Collection<VCFHeader> headers) {
        SAMSequenceDictionary candidateDictionary = null;
        for ( final VCFHeader sourceHeader : headers ) {
            final VCFHeaderVersion sourceHeaderVersion = sourceHeader.getVCFHeaderVersion();
            if (!sourceHeaderVersion.isAtLeastAsRecentAs(VCFHeaderVersion.VCF4_2)) {
                throw new TribbleException(String.format(
                        "Cannot merge a VCFHeader (with version %s) that is is older than version %s",
                        sourceHeaderVersion,
                        VCFHeaderVersion.VCF4_2));
            }

            final SAMSequenceDictionary sourceDictionary = sourceHeader.getSequenceDictionary();
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

                    case COMMON_SUBSET:
                        // There exists a common subset of equivalent contigs, but that isn't sufficient for
                        // merging purposes, unless one is a superset of the other. So, try again, in both comparison
                        // directions, with checkContigOrdering off, to see if one is a superset of the other, and
                        // if so retain the superset.
                    case DIFFERENT_INDICES:
                        // The dictionaries have at least some common contigs, but with different relative indices.
                        // For merging purposes, this is ok, as long as one dictionary is strict superset of the other,
                        // since the superset has all of the contigs already ordered. So, try again, in both comparison
                        // directions, with checkContigOrdering off, to see if one is a superset of the other, and
                        // if so retain the superset.
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
                                    getHeaderDictionaryFailureMessage(
                                            candidateDictionary, sourceHeader, sourceDictionary, compatibility));
                        }
                        break;

                    case NO_COMMON_CONTIGS:              // no overlap between dictionaries
                    case UNEQUAL_COMMON_CONTIGS:         // common subset has contigs that have the same name but different lengths
                    case NON_CANONICAL_HUMAN_ORDER:      // human reference detected but the order of the contigs is non-standard (lexicographic, for example)
                    case OUT_OF_ORDER:                   // the two dictionaries overlap but the overlapping contigs occur in different
                    default:
                        throw new TribbleException(
                                getHeaderDictionaryFailureMessage(
                                        candidateDictionary, sourceHeader, sourceDictionary, compatibility));
                }
            }
        }
        return candidateDictionary;
    }

    private static String getHeaderDictionaryFailureMessage(
            final SAMSequenceDictionary commonSequenceDictionary,
            final VCFHeader sourceHeader,
            final SAMSequenceDictionary sourceSequenceDictionary,
            final SAMSequenceDictionaryUtils.SequenceDictionaryCompatibility failureReason) {
        // return a nice long message that attempts to print out as much of the offending context as is reasonable,
        // without printing the entire context, since the headers and sequence dictionaries can have thousands of entries
        return String.format(
                "Attempt to merge a VCFHeader containing a sequence dictionary that is incompatible with other merged headers, failed due to %s:" +
                        "\n\nHeader Dictionary:\n\n%1.2000s\n\nCommon Dictionary:\n\n%1.2000s\n\nSource Header:\n\n%1.2000s",
                failureReason,
                sourceSequenceDictionary.getSequences().stream().map(SAMSequenceRecord::toString).collect(Collectors.joining("\n")),
                commonSequenceDictionary.getSequences().stream().map(SAMSequenceRecord::toString).collect(Collectors.joining("\n")),
                sourceHeader.getContigLines().stream().map(VCFContigHeaderLine::toString).collect(Collectors.joining("\n")));
    }

}
