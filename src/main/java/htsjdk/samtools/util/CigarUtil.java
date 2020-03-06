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
import htsjdk.samtools.SAMTag;
import htsjdk.samtools.SAMValidationError;
import htsjdk.samtools.TextCigarCodec;
import htsjdk.utils.ValidationUtils;

import java.util.*;

/**
 * @author alecw@broadinstitute.org
 */
public class CigarUtil {
        private static final Log log = Log.getInstance(CigarUtil.class);

    /** Adjust the cigar based on adapter clipping.
     * TODO: If there is hard clipping at the end of the input CIGAR, it is lost.  It should not be. 
     * *
     * @param clipFrom           1-based position where the clipping starts
     * @param oldCigar           The existing unclipped cigar
     * @param clippingOperator   Type of clipping to use, either soft or hard.  If non-clipping operator is used an exception is thrown
     * @return                   New adjusted list of cigar elements
     */
    public static List<CigarElement> clipEndOfRead(final int clipFrom, final List<CigarElement> oldCigar, final CigarOperator clippingOperator) {
        ValidationUtils.validateArg(clippingOperator.isClipping(), () -> "Clipping operator should be SOFT or HARD clip, found " + clippingOperator.toString());
        final int clippedBases = (int)CoordMath.getLength(clipFrom, Cigar.getReadLength(oldCigar));
        List<CigarElement> newCigar = new LinkedList<CigarElement>();
        int pos = 1;
        final CigarElement oldCigarFinalElement = oldCigar.get(oldCigar.size() - 1);
        final int trailingHardClipBases = oldCigarFinalElement.getOperator() == CigarOperator.HARD_CLIP? oldCigarFinalElement.getLength() : 0;

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
                mergeClippingCigarElement(newCigar, c,
                        (clipFrom - 1) - (pos - 1) , clippedBases, clippingOperator, trailingHardClipBases);
                break;
            }

            pos = endPos + 1;      // update pos for next iteration
        } // end loop over cigar elements
        return newCigar;
    }

    /** Adjust the cigar based on adapter clipping
     * @param clipFrom           1-based position where the clipping starts
     * @param oldCigar           The existing unclipped cigar
     * @return                   New adjusted list of cigar elements
     */
    public static List<CigarElement> softClipEndOfRead(final int clipFrom, final List<CigarElement> oldCigar) {
        return clipEndOfRead(clipFrom, oldCigar, CigarOperator.SOFT_CLIP);
    }

    /**
     * Merge clipping cigar element into end of cigar
     * @param newCigar the list of cigar elements to which the merged elements are to be added (modified in place)
     * @param originalElement the cigar element of the original cigar which first overlaps with bases to be clipped
     * @param relativeClippedPosition number of bases in originalElement after which clipping element is to be merged
     * @param clippedBases total number of clipping bases to be merged
     * @param newClippingOperator clipping operator to be merged
     * @param trailingHardClippedBases number of hardClippedBases which were on the end of the original cigar
     */
    static private void mergeClippingCigarElement(List<CigarElement> newCigar, CigarElement originalElement,
                                                int relativeClippedPosition,
                                                int clippedBases, final CigarOperator newClippingOperator,
                                                final int trailingHardClippedBases) {
        ValidationUtils.validateArg(newClippingOperator.isClipping(), () -> "Clipping operator should be SOFT or HARD clip, found " + newClippingOperator.toString());

        final CigarOperator originalOperator = originalElement.getOperator();
        int clipAmount = clippedBases;
        if (newClippingOperator == CigarOperator.HARD_CLIP) {
            clipAmount += trailingHardClippedBases;
        }
        if (originalOperator.consumesReadBases()){
            if ((originalOperator.consumesReferenceBases() || newClippingOperator == CigarOperator.HARD_CLIP ) && relativeClippedPosition > 0){
                newCigar.add(new CigarElement(relativeClippedPosition, originalOperator));
            }
            if (!(originalOperator.consumesReferenceBases() || newClippingOperator == CigarOperator.HARD_CLIP ) || originalOperator == newClippingOperator) {
                clipAmount = clippedBases + relativeClippedPosition;
            }
        } else if (relativeClippedPosition != 0){
            throw new SAMException("Unexpected non-0 relativeClippedPosition " + relativeClippedPosition);
        }
        newCigar.add(new CigarElement(clipAmount, newClippingOperator));  // add clipping operator
        if(newClippingOperator == CigarOperator.SOFT_CLIP && trailingHardClippedBases > 0) {
            newCigar.add(new CigarElement(trailingHardClippedBases, CigarOperator.HARD_CLIP)); //add in trailing hard-clipped bases
        }
    }

    /** Adjust the cigar of <code>rec</code> based on adapter clipping using soft-clipping
     * @param clipFrom           1-based position where the soft-clipping starts
     */
    public static void softClip3PrimeEndOfRead(SAMRecord rec, final int clipFrom) {
        clip3PrimeEndOfRead(rec, clipFrom, CigarOperator.SOFT_CLIP);
    }

    /**
     * Adds a soft- or hard-clip, based on <code>clipFrom</code> and <code>clippingOperator</code>, to the SAM record's existing cigar
     * and, for negative strands, also adjusts the SAM record's start position.  If clipping changes the number of unclipped bases,
     * the the NM, MD, and UQ tags will be invalidated.
     * Clips the end of the read as the read came off the sequencer.
     * @param rec               SAMRecord to clip
     * @param clipFrom          Position to clip from
     * @param clippingOperator  Type of clipping to use, either soft or hard.  If non-clipping operator is used an exception is thrown
     */
    public static void clip3PrimeEndOfRead(SAMRecord rec, final int clipFrom, final CigarOperator clippingOperator) {
        ValidationUtils.validateArg(clippingOperator.isClipping(), () -> "Clipping operator should be SOFT or HARD clip, found " + clippingOperator.toString());

        final Cigar cigar = rec.getCigar();
        // we don't worry about SEED_REGION_LENGTH in clipFrom
        final boolean negativeStrand = rec.getReadNegativeStrandFlag();
        List<CigarElement> oldCigar = cigar.getCigarElements();

        if (!isValidCigar(rec, cigar, true)){
            return; // log message already issued
        }

        final int originalReadLength = rec.getReadLength();
        final int originalReferenceLength = cigar.getReferenceLength();
        if (negativeStrand){
            // Can't just use Collections.reverse() here because oldCigar is unmodifiable
            oldCigar = new ArrayList<CigarElement>(oldCigar);
            Collections.reverse(oldCigar);
        }
        List<CigarElement> newCigarElems = CigarUtil.clipEndOfRead(clipFrom, oldCigar, clippingOperator);

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

        // If hard-clipping, remove the hard-clipped bases from the read
        if(clippingOperator == CigarOperator.HARD_CLIP) {
            final byte[] bases = rec.getReadBases();
            final byte[] baseQualities = rec.getBaseQualities();

            if (originalReadLength != bases.length) {
                throw new SAMException("length of bases array (" + bases.length + ") does not match length expected based on cigar (" + cigar+ ")");
            }

            if (originalReadLength != baseQualities.length) {
                throw new SAMException("length of baseQualities array (" + baseQualities.length + ") does not match length expected based on cigar (" + cigar+ ")");
            }
            if(rec.getReadNegativeStrandFlag()) {
                rec.setReadBases(Arrays.copyOfRange(bases, bases.length - clipFrom + 1, originalReadLength));
                rec.setBaseQualities(Arrays.copyOfRange(baseQualities, baseQualities.length - clipFrom + 1, originalReadLength));
            } else {
                rec.setReadBases(Arrays.copyOf(bases, clipFrom - 1));
                rec.setBaseQualities(Arrays.copyOf(baseQualities, clipFrom - 1));
            }
        }

        // Check that the end result is not a read without any aligned bases
        boolean hasMappedBases = false;
        for (final CigarElement elem : newCigar.getCigarElements()) {
            final CigarOperator op = elem.getOperator();
            if (op.consumesReferenceBases() && op.consumesReadBases()) {
                hasMappedBases = true;
                break;
            }
        }

        if (newCigar.getReferenceLength() != originalReferenceLength) {
            //invalidate NM, UQ, MD tags if we have changed the length of the read.
            rec.setAttribute(SAMTag.NM.name(), null);
            rec.setAttribute(SAMTag.MD.name(), null);
            rec.setAttribute(SAMTag.UQ.name(), null);
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
        else if (rec.getReadLength() != newCigar.getReadLength()) {
            throw new IllegalStateException("new Cigar: " + newCigar + " implies different read base than record (" + rec.getReadLength() +")");
        }
        else if (rec.getReadBases().length != rec.getBaseQualities().length) {
            throw new IllegalStateException("new read bases have different length (" + rec.getReadBases().length + ") than new base qualities (" + rec.getBaseQualities() + ")");
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
     * @param clippingOperator  Type of clipping to use, either soft or hard.  If non-clipping operator is used an exception is thrown
     */
    public static Cigar addClippedBasesToEndsOfCigar(final Cigar cigar, final boolean negativeStrand,
                                                         final int threePrimeEnd, final int fivePrimeEnd, final CigarOperator clippingOperator) {
        ValidationUtils.validateArg(clippingOperator.isClipping(), () -> "Clipping operator should be SOFT or HARD clip, found " + clippingOperator.toString());
        List<CigarElement> newCigar = new ArrayList<CigarElement>(cigar.getCigarElements());
        if (negativeStrand) {
            Collections.reverse(newCigar);
        }

        if (threePrimeEnd > 0) {
            int last = newCigar.size()-1;
            int bases = threePrimeEnd;
            if(newCigar.get(last).getOperator() == clippingOperator) {
                final CigarElement oldClip = newCigar.remove(last);
                bases += oldClip.getLength();
            }
            newCigar.add(new CigarElement(bases, clippingOperator));
        }

        if (fivePrimeEnd > 0) {
            int bases = fivePrimeEnd;
            if (newCigar.get(0).getOperator().isClipping()) {
                final CigarElement oldClip = newCigar.remove(0);
                bases += oldClip.getLength();
            }
            newCigar.add(0, new CigarElement(bases, clippingOperator));
        }

        if (negativeStrand) {
            Collections.reverse(newCigar);
        }
        return new Cigar(newCigar);
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
     *
     * @return                  New cigar with additional soft-clipped bases
     */
    public static Cigar addSoftClippedBasesToEndsOfCigar(final Cigar cigar, final boolean negativeStrand,
                                                         final int threePrimeEnd, final int fivePrimeEnd) {
        return addClippedBasesToEndsOfCigar(cigar, negativeStrand, threePrimeEnd, fivePrimeEnd, CigarOperator.SOFT_CLIP);
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
