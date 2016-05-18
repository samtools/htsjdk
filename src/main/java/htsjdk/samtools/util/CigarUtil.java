/*
 * The MIT License
 *
 * Copyright (c) 2010 The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package htsjdk.samtools.util;

import htsjdk.samtools.Cigar;
import htsjdk.samtools.CigarElement;
import htsjdk.samtools.CigarOperator;
import htsjdk.samtools.SAMException;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMValidationError;
import htsjdk.samtools.TextCigarCodec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * @author alecw@broadinstitute.org
 */
public class CigarUtil {
        private static final Log log = Log.getInstance(CigarUtil.class);

    /** adjust the cigar based on adapter clipping.
     * TODO: If there is hard clipping at the end of the input CIGAR, it is lost.  It should not be. 
     * *
     * @param clipFrom       1-based position where the clipping starts
     * @param oldCigar       The existing unclipped cigar
     * @return               New adjusted list of cigar elements
     */
    // package visible so can be unit-tested
    public static List<CigarElement> softClipEndOfRead(final int clipFrom, final List<CigarElement> oldCigar) {
        final int clippedBases = (int)CoordMath.getLength(clipFrom, Cigar.getReadLength(oldCigar));
        List<CigarElement> newCigar = new LinkedList<CigarElement>();
        int pos = 1;

        for (CigarElement c : oldCigar) {
            // Distinguish two cases:
            //	c occurs before the clipped region
            //	c is adjacent to or straddles the boundary between clipped and unclipped region.
            //  c never occurs after the clipped region; clipped region is always at the end

            final CigarOperator op = c.getOperator();
            final int length = op.consumesReadBases()? c.getLength() : 0;
            final int endPos = pos + length - 1;  // same as pos on next iteration

            if (endPos < (clipFrom - 1)) {
                // handle elements before clip position (just copy them)
                newCigar.add(c);

            } else if (endPos >= (clipFrom - 1)) {
                // handle adjacent or straddling element
                elementStraddlesClippedRead(newCigar, c,
                        (clipFrom -1) - (pos -1) , clippedBases);
                break;
            }

            pos = endPos + 1;      // update pos for next iteration
        } // end loop over cigar elements
        return newCigar;
    }

    // a cigar element occurs in the middle of an adapter clipping
    static private void elementStraddlesClippedRead(List<CigarElement> newCigar, CigarElement c,
                                                    int relativeClippedPosition,
                                                    int clippedBases){
        final CigarOperator op = c.getOperator();
        int clipAmount = clippedBases;
        if (op.consumesReadBases()){
            if (op.consumesReferenceBases() & relativeClippedPosition > 0){
               newCigar.add(new CigarElement(relativeClippedPosition, op));
            }
            if (!op.consumesReferenceBases()){
                clipAmount = clippedBases + relativeClippedPosition;
            }
        } else if (relativeClippedPosition != 0){
                throw new SAMException("Unexpected non-0 relativeClippedPosition " + relativeClippedPosition);
        }
        newCigar.add(new CigarElement(clipAmount, CigarOperator.S));  // S is always last element
    }

    /**
     * Adds a soft-clip, based on <code>clipFrom</code>, to the SAM record's existing cigar
     * and, for negative strands, also adjusts the SAM record's start position.
     * Soft clips the end of the read as the read came off the sequencer.
     */
    public static void softClip3PrimeEndOfRead(SAMRecord rec, final int clipFrom) {

        final Cigar cigar = rec.getCigar();
        // we don't worry about SEED_REGION_LENGTH in clipFrom
        final boolean negativeStrand = rec.getReadNegativeStrandFlag();
        List<CigarElement> oldCigar = cigar.getCigarElements();

        if (!isValidCigar(rec, cigar, true)){
            return; // log message already issued
        }
        if (negativeStrand){
            // Can't just use Collections.reverse() here because oldCigar is unmodifiable
            oldCigar = new ArrayList<CigarElement>(oldCigar);
            Collections.reverse(oldCigar);
        }
        List<CigarElement> newCigarElems = CigarUtil.softClipEndOfRead(clipFrom, oldCigar);

        if (negativeStrand) {
            Collections.reverse(newCigarElems);
        }

        final Cigar newCigar = new Cigar(newCigarElems);
        if (negativeStrand){
            int oldLength = cigar.getReferenceLength();
            int newLength = newCigar.getReferenceLength();
            int sizeChange = oldLength - newLength;
            if (sizeChange > 0){
                rec.setAlignmentStart(rec.getAlignmentStart() + sizeChange);
            } else if (sizeChange < 0){
                throw new SAMException("The clipped length " + newLength +
                        " is longer than the old unclipped length " + oldLength);
            }
        }
        rec.setCigar(newCigar);

        // Check that the end result is not a read without any aligned bases
        boolean hasMappedBases = false;
        for (final CigarElement elem : newCigar.getCigarElements()) {
            final CigarOperator op = elem.getOperator();
            if (op.consumesReferenceBases() && op.consumesReadBases()) {
                hasMappedBases = true;
                break;
            }
        }

        if (!hasMappedBases) {
            rec.setReadUnmappedFlag(true);
            rec.setCigarString(SAMRecord.NO_ALIGNMENT_CIGAR);
            rec.setReferenceIndex(SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX);
            rec.setAlignmentStart(SAMRecord.NO_ALIGNMENT_START);
            rec.setMappingQuality(SAMRecord.NO_MAPPING_QUALITY);
            rec.setInferredInsertSize(0);
        }
        else if (!isValidCigar(rec, newCigar, false)){
            // log message already issued
            throw new IllegalStateException("Invalid new Cigar: " + newCigar  + " (" + oldCigar + ") for " +
                    rec.getReadName());
        }

    }

    private static boolean isValidCigar(SAMRecord rec, Cigar cigar, boolean isOldCigar) {
        if (cigar == null || cigar.getCigarElements() == null || cigar.getCigarElements().isEmpty()) {
            if (isOldCigar) {
                if (rec.getReadUnmappedFlag()) {
                    // don't bother to warn since this does occur for PE reads
                } else {
                    log.warn("Cigar is empty for read " + rec);
                }
            } else {
                log.error("Empty new cigar");
            }
            return false;
        }

        if (rec.getReadUnmappedFlag()){
            log.info("Unmapped read with cigar: " + rec.getReadName() + " (" + rec.getCigarString() + "/" + cigar.toString()  + ")");

        }
        final List<SAMValidationError> validationErrors = cigar.isValid(rec.getReadName(), -1);
        if (validationErrors != null && !validationErrors.isEmpty()) {
            log.error("Invalid cigar for read " + rec +
                (isOldCigar ? " " : " for new cigar with clipped adapter ") +
                 " (" + rec.getCigarString() + "/" + cigar.toString()  + ") " +
                validationErrors);
            return false;
        }
    
        if (rec.getReadLength() != cigar.getReadLength()){
            // throw new SAMException(
            log.error( rec.getReadLength() +
               " read length does not = cigar length " + cigar.getReferenceLength() +
               (isOldCigar? " oldCigar " : " ") +
               rec + " cigar:" + cigar);
            return false;
        }
        return true;
    }

    /**
     * Adds additional soft-clipped bases at the 3' and/or 5' end of the cigar.  Does not
     * change the existing cigar except to merge the newly added soft-clipped bases if the
     * element at the end of the cigar being modified is also a soft-clip.
     *
     * @param cigar             The cigar on which to base the new cigar
     * @param negativeStrand    Whether the read is on the negative strand
     * @param threePrimeEnd     number of soft-clipped bases to add to the 3' end of the read
     * @param fivePrimeEnd      number of soft-clipped bases to add to the 5' end of the read
     */
    public static Cigar addSoftClippedBasesToEndsOfCigar(Cigar cigar, boolean negativeStrand,
                                                         final int threePrimeEnd, final int fivePrimeEnd) {

        List<CigarElement> newCigar = new ArrayList<CigarElement>(cigar.getCigarElements());
        if (negativeStrand) {
            Collections.reverse(newCigar);
        }

        if (threePrimeEnd > 0) {
            int last = newCigar.size()-1;
            int bases = threePrimeEnd;
            if (newCigar.get(last).getOperator() == CigarOperator.SOFT_CLIP) {
                CigarElement oldSoftClip = newCigar.remove(last);
                bases += oldSoftClip.getLength();
            }
            newCigar.add(new CigarElement(bases, CigarOperator.SOFT_CLIP));
        }

        if (fivePrimeEnd > 0) {
            int bases = fivePrimeEnd;
            if (newCigar.get(0).getOperator() == CigarOperator.SOFT_CLIP) {
                CigarElement oldSoftClip = newCigar.remove(0);
                bases += oldSoftClip.getLength();
            }
            newCigar.add(0, new CigarElement(bases, CigarOperator.SOFT_CLIP));
        }

        if (negativeStrand) {
            Collections.reverse(newCigar);
        }
        return new Cigar(newCigar);
    }

    // unpack a cigar string into an array of cigarOperators
    // to facilitate sequence manipulation
    public static char[] cigarArrayFromElements(List<CigarElement> cigar){
        int pos = 0;
        int length = 0;
        for (CigarElement e : cigar){
            length += e.getLength();
        }
        char[] result = new char[length];
        for (CigarElement e : cigar){
            for (int i = 0; i < e.getLength(); i++){
                CigarOperator o = e.getOperator();
                result[i+pos] = (char) CigarOperator.enumToCharacter(o);
            }
            pos += e.getLength();
        }
        return result;
    }

    // unpack a cigar string into an array of cigarOperators
    // to facilitate sequence manipulation
    public static char[] cigarArrayFromString(String cigar){
          return cigarArrayFromElements(TextCigarCodec.decode(cigar).getCigarElements());
    }

    // construct a cigar string from an array of cigarOperators.
    public static String cigarStringFromArray(final char[] cigar){
        String result = "";
        int length = cigar.length;
        char lastOp = 0;  int lastLen = 0;
        for (int i=0; i < length; i++){
             if (cigar[i] == lastOp){
                 lastLen++;
             } else if (cigar[i] == '-'){
                 ; // nothing - just ignore '-'
             } else {
                 if (lastOp != 0)
                     result = result + Integer.toString(lastLen) + Character.toString(lastOp);
                 lastLen = 1;
                 lastOp = cigar[i];
             }
        }
        return result + Integer.toString(lastLen) + Character.toString(lastOp);
    }
}
