/*
 * The MIT License
 *
 * Copyright (c) 2009 The Broad Institute
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
package htsjdk.samtools;

import java.util.List;

/**
 * A canonical alignment in a SAMRecord, stored in the 'SA' attribute of a {@link SAMRecord}
 *  <cite>Other canonical alignments in a chimeric alignment, in the format of:( rname,pos,strand,CIGAR,mapQ,NM;)+.</cite>
 * @author Pierre Lindenbaum   @yokofakun
 *
 */
public interface OtherCanonicalAlignment {
	/** get the owner */
	public SAMRecord getSAMRecord();
	/** get the reference name */
	public String getReferenceName();	
	/** get the reference index in the sam header */
	public int getReferenceIndex();
	/** @return 1-based inclusive leftmost position of the clipped sequence, or 0 if there is no position. */
	public int getAlignmentStart();
	/**  strand of the query (false for forward; true for reverse strand). */
	public boolean getReadNegativeStrandFlag();
	/** @return the CIGAR string of the alignment */
	public String getCigarString();
	/** @return Cigar object for the read */
	public Cigar getCigar();
	/** @return phred scaled mapping quality */
	public int getMappingQuality();
	/** @return Edit distance to the reference, including ambiguous bases but excluding clipping */
	public int getNM();
	 /** @return the alignment start (1-based, inclusive) adjusted for clipped bases. */
	public int getUnclippedStart();
	/**  @return the alignment end (1-based, inclusive) adjusted for clipped bases. */
	public int getUnclippedEnd();
 	/** @return 1-based inclusive rightmost position of the clipped sequence, or 0 read if unmapped. */
	public int getAlignmentEnd();
	/** shortcut to getCigar().getCigarElements(); */
	public List<CigarElement> getCigarElements();
	}
