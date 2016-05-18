/*
 * The MIT License
 *
 * Copyright (c) 2013 The Broad Institute
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
package htsjdk.tribble.index;

/**
 * Represents a contiguous block of bytes in a file, defined by a start position and size (in bytes)
*/
public class Block {

    private final long startPosition;
    private long size;

    /**
     * @param startPosition  in bytes
     * @param size in bytes
     */
    public Block(final long startPosition, final long size) {
        this.startPosition = startPosition;
        this.size = size;
    }

    /**
     * @return the startPosition
     */
    public long getStartPosition() {
        return startPosition;
    }

    public long getEndPosition() {
        return startPosition + size;
    }

    /**
     * This method is used to aid in consolidating blocks.
     * Sets the size based on the provided {@code endPosition}
     * @param endPosition Where the block ends, in bytes
     */
    public void setEndPosition(final long endPosition) {
        if(endPosition < startPosition)
            throw new IllegalArgumentException("Attempting to set block end position to " +
                                                                           endPosition + " which is before the start of " + startPosition);
        size = endPosition - startPosition;

    }

    /**
     * @return the # of bytes in this block.  Note that for block-compressed files, this is not truly the
     * size of the block in the file.  getEndPosition should be used to determine the virtual file offset
     * of the end of the region of interest.
     */
    public long getSize() {
        return size;
    }

    public boolean equals(final Object obj) {
        if ( this == obj ) return true;
        if ( ! (obj instanceof Block) ) return false;
        final Block otherBlock = (Block)obj;
        return this.startPosition == otherBlock.startPosition && this.size == otherBlock.size;
    }
}
