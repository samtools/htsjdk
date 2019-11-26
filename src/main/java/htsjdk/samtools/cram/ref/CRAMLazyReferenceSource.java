/*
 * Copyright (c) 2019 The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
 * THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package htsjdk.samtools.cram.ref;

import htsjdk.samtools.SAMSequenceRecord;

/**
 * A lazy CRAMReferenceSource implementation, for use when no explicit reference source has been provided
 * by the user. This allows client code to have a CRAMReferenceSource to thread through the CRAM code and to
 * access containers, slices, and un-normalized CRAM records and otherwise perform operations such as indexing
 * that do not require a reference to be resolved. If a reference sequence is actually requested, throws an
 * exception.
 */
public class CRAMLazyReferenceSource implements CRAMReferenceSource {

    @Override
    public byte[] getReferenceBases(final SAMSequenceRecord sequenceRecord, final boolean tryNameVariants) {
        throw new IllegalArgumentException(
                String.format("A reference must be supplied that includes the reference sequence for %s).",
                        sequenceRecord.getSequenceName()));
    }

}
