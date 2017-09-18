/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017 Daniel Gomez-Sanchez
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package htsjdk.samtools.read;

import htsjdk.samtools.Cigar;
import htsjdk.samtools.SAMException;
import htsjdk.samtools.SAMFlag;
import htsjdk.samtools.SAMUtils;
import htsjdk.samtools.util.Locatable;
import htsjdk.samtools.util.StringUtil;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Interface for read, which is a sequence of bases aligned to a reference.
 *
 * <p>This interface is based on the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
 *
 * @author Daniel Gomez-Sanchez (magicDGS)
 */
public interface Read extends Locatable {

    ///////////////////////////////////////////////////////////
    // MANDATORY FIELDS - getters/setters
    ///////////////////////////////////////////////////////////

    /**
     * Gets the name of the read.
     *
     * <p>Equivalent to <b>QNAME</b> in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * @return the read name or {@code null} if the read has no name.
     */
    String getName();

    /**
     * Sets the name of the read.
     *
     * <p>Equivalent to <b>QNAME</b> in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * @param name new name for the read; {@code null} if the read has no name.
     */
    void setName(final String name);

    /**
     * Gets the bitwise flag.
     *
     * <p>Equivalent to <b>FLAG</b> in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * <p>Note: it is preferable to use the methods for testing if specific flags are set (using {@link #isSet(SAMFlag)} or {@link #isUnset(SAMFlag)}),
     * get all the set flags with {@link #getSetFlags()} or testing specific flags with the shortcuts.
     *
     * @return the bitwise flag as an integer.
     */
    int getFlag();

    /**
     * Gets the {@link SAMFlag} that are set.
     *
     * <p>Equivalent to <b>FLAG</b> in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * <p>Default implementation calls {@code SAMFlag.getFlags(getFlag())}.
     *
     * @return a set of the flags that are set for the read.
     */
    default Set<SAMFlag> getSetFlags() {
        return SAMFlag.getFlags(getFlag());
    }

    /**
     * Checks if the flag is set.
     *
     * <p>Default implementation calls {@code flag.isSet(getFlag())}.
     *
     * @param flag the flag to test.
     * @return {@code true} if the flag is set; {@code false} otherwise.
     *
     * @throws IllegalArgumentException if the flag is {@code null}.
     */
    default boolean isSet(final SAMFlag flag) {
        return flag.isSet(getFlag());
    }

    /**
     * Checks if the flag is unset.
     *
     * <p>Default implementation calls {@code flag.isUnset(getFlag())}.
     *
     * @param flag the flag to test.
     * @return {@code true} if the flag is unset; {@code false} otherwise.
     *
     * @throws IllegalArgumentException if the flag is {@code null}.
     */
    default boolean isUnset(final SAMFlag flag) {
        return flag.isUnset(getFlag());
    }

    /**
     * Sets the bitwise flag.
     *
     * <p>Equivalent to <b>FLAG</b> in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * <p>Note: it is preferable to use the methods for set/unset specific flags are set (using {@link #setFlag(SAMFlag)} or {@link #unsetFlag(SAMFlag)}),
     * clear the flags using {@link #clearFlag()}, set/unset the specific flags (using {@link #setFlags(Set) and {@link #unsetFlags(Set)}},
     * or use the shortcuts.
     *
     * @param flag bitwise flag to assign to the read.
     */
    void setFlag(final int flag);

    /**
     * Clears the bitwise flag, un-setting all the bits.
     *
     * <p>Equivalent to <b>FLAG</b> in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * <p>Default implementation calls {@code setFlag(0)}.
     */
    default void clearFlag() {
        setFlag(0);
    }

    /**
     * Adds the flag, either setting it or unsetting it.
     *
     * <p>Equivalent to <b>FLAG</b> in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * <p>Deafault implementation modifies the {@link SAMFlag} bit of the {@link #getFlag()}, setting it afterwards with {@link #setFlag(int)} .
     *
     * @param flag flag to be set or unset.
     * @param set {@code true} if the flag should be set; {@code false} otherwise.
     *
     * @throws IllegalArgumentException if the flag is {@code null}.
     */
    default void addFlag(final SAMFlag flag, final boolean set) {
        if (flag == null) {
            throw new IllegalArgumentException("null flag");
        }
        int bitwiseFlag = getFlag();
        if (set) {
            bitwiseFlag |= flag.intValue();
        } else {
            bitwiseFlag &= ~flag.intValue();
        }
        setFlag(bitwiseFlag);
    }

    /**
     * Sets the bit of the provided flag.
     *
     * <p>Equivalent to <b>FLAG</b> in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * <p>Default implementation calls {@code addFlag(flag, true)}.
     *
     * @param flag flag to be set.
     *
     * @throws IllegalArgumentException if the flag is {@code null}.
     */
    default void setFlag(final SAMFlag flag) {
        addFlag(flag, true);
    }

    /**
     * Unsets the bit of the provided flag.
     *
     * <p>Equivalent to <b>FLAG</b> in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * <p>Default implementation calls {@code addFlag(flag, false)}.
     *
     * @param flag flag to be unset.
     */
    default void unsetFlag(final SAMFlag flag) {
        addFlag(flag, false);
    }

    /**
     * Sets all the provided flags.
     *
     * <p>Equivalent to <b>FLAG</b> in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * <p>Default implementation calls {@link #setFlag(SAMFlag)} for each of the elements in the set.
     *
     * @param flags flags to be set.
     */
    default void setFlags(final Set<SAMFlag> flags) {
        flags.forEach(this::setFlag);
    }

    /**
     * Unsets all the provided flags.
     *
     * <p>Equivalent to <b>FLAG</b> in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * <p>Default implementation calls {@link #unsetFlag(SAMFlag)}} for each of the elements in the set.
     *
     * @param flags flags to be unset.
     */
    default void unsetFlags(final Set<SAMFlag> flags) {
        flags.forEach(this::unsetFlag);
    }

    /**
     * Gets the contig name where the read is mapped to. May return null if there is no unique mapping.
     *
     * <p>Equivalent to <b>RNAME</b> in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * @return name of the contig this read is mapped to; {@link ReadConstants#NO_ALIGNMENT_REFERENCE_NAME} if unset (e.g. for unmapped reads).
     */
    @Override
    String getContig();

    /**
     * Gets the 1-based leftmost mapping POSition of the first CIGAR operation that “consumes” a reference base.
     *
     * <p>Equivalent to <b>POS</b> in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * @return 1-based start position; {@link ReadConstants#NO_ALIGNMENT_START} if there is no position (e.g. for unmapped read).
     */
    @Override
    int getStart();

    /**
     * Gets the 1-based inclusive rightmost mapping POSition of the first CIGAR operation that “consumes” a reference base.
     *
     * @return 1-based closed-ended position; {@link ReadConstants#NO_ALIGNMENT_START} if there is no position (e.g. for unmapped read).
     */
    @Override
    int getEnd();

    /**
     * Sets the alignment position.
     *
     * <p>Equivalent to <b>RNAME</b> and <b>POS</b> in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * @param contig name of the contig this read is mapped to; {@link ReadConstants#NO_ALIGNMENT_REFERENCE_NAME} if unset (e.g. for unmapped reads).
     * @param start 1-based start position; {@link ReadConstants#NO_ALIGNMENT_START} if there is no position (e.g. for unmapped read).
     *
     * @throws IllegalArgumentException if the contig is {@code null}.
     */
    void setAlignmentPosition(final String contig, final int start);

    /**
     * Sets the alignment position.
     *
     * <p>Equivalent to <b>POS</b> and <b>POS</b> in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * <p>Default implementation uses the {@link #setAlignmentPosition(String, int)}, ignoring the {@link Locatable#getEnd()}.
     *
     * @param position location this read is mapped to.
     *
     * @throws IllegalArgumentException if the locatable is {@code null}.
     */
    default void setAlignmentPosition(final Locatable position) {
        if (position == null) {
            throw new IllegalArgumentException("null position");
        }
        setAlignmentPosition(position.getContig(), position.getStart());
    }

    /**
     * Gets the read length.
     *
     * <p>Note: this is not necessarily the same as the number of reference bases the read is aligned to.
     *
     * @return The number of bases in the read-sequence.
     */
    int getLength();

    /**
     * Checks if the read has bases.
     *
     * <p>Default implementation returns {@code getLength() == 0}.
     *
     * @return {@code true} if the read has no bases; {@code false} otherwise.
     */
    default boolean isEmpty() {
        return getLength() == 0;
    }

    /**
     * Gets the phred scaled mapping quality.
     *
     * <p>The {@link ReadConstants#UNKNOWN_MAPPING_QUALITY} implies valid mapping, but hard to compute quality.
     *
     * <p>Equivalent to <b>MAPQ</b> in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * @return mapping quality.
     */
    int getMappingQuality();

    /**
     * Sets the phred scaled mapping quality.
     *
     * <p>Equivalent to <b>MAPQ</b> in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * @param mapq mapping quality.
     */
    void setMappingQuality(final int mapq);

    /**
     * Gets the {@link Cigar} object describing how the read aligns to the reference.
     *
     * <p>Equivalent to <b>CIGAR</b> in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * <p>This method should make a defensive copy of the {@link Cigar} before returning it to allow
     * modification of the returned {@link Cigar} without effects on the {@link Read}.
     *
     * @return Cigar object for the read; empty cigar if there is none.
     */
    Cigar getCigar();

    /**
     * Gets the number of cigar elements (number + operator) in the cigar string.
     *
     * <p>Equivalent to <b>CIGAR</b> in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * <p>Default implementation returns {@code getCigar().numCigarElements()}.
     * Subclasses may override to provide more efficient implementations.
     *
     * @return number of cigar elements (number + operator) in the cigar string.
     */
    default int getCigarLength() {
        return getCigar().numCigarElements();
    }

    /**
     * Sets the {@link Cigar} object describing how the read aligns to the reference.
     *
     * <p>Equivalent to <b>CIGAR</b> in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * @param cigar Cigar object for the read; empty Cigar if there is none.
     *
     * @throws IllegalArgumentException if the cigar is {@code null}.
     */
    void setCigar(final Cigar cigar);

    /**
     * Gets the contig name where the read's mate is mapped to. May return null if there is no unique mapping.
     *
     * <p>Equivalent to <b>RNEXT</b> in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * @return name of the contig the read's mate is mapped to; {@link ReadConstants#NO_ALIGNMENT_REFERENCE_NAME} if unset (e.g. for unmapped reads).
     */
    String getMateContig();

    /**
     * Gets the 1-based inclusive leftmost position of the sequence remaining after clipping for the read's mate.
     *
     * <p>Equivalent to <b>PNEXT</b> in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * @return 1-based start position; {@link ReadConstants#NO_ALIGNMENT_START} if there is no position (e.g. for unmapped read).
     */
    int getMateStart();

    /**
     * Sets the alignment position for the read's mate.
     *
     * <p>Equivalent to <b>RNEXT</b> and <b>PNEXT</b> in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * @param contig name of the contig the read's mate is mapped to; {@link ReadConstants#NO_ALIGNMENT_REFERENCE_NAME} if unset (e.g. for unmapped reads).
     * @param start 1-based start position; {@link ReadConstants#NO_ALIGNMENT_START} if there is no position (e.g. for unmapped read).
     *
     * @throws IllegalArgumentException if the contig is {@code null}.
     */
    void setMateAlignmentPosition(final String contig, int start);

    /**
     * Sets the alignment position for the read's mate.
     *
     * <p>Equivalent to <b>RNEXT</b> and <b>PNEXT</b> in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * <p>Default implementation uses the {@link #setMateAlignmentPosition(String, int)}, ignoring the {@link Locatable#getEnd()}.
     *
     * @param position location the read's mate is mapped to.
     *
     * @throws IllegalArgumentException if the locatable is {@code null}.
     */
    default void setMateAlignmentPosition(final Locatable position) {
        setMateAlignmentPosition(position.getContig(), position.getStart());
    }

    /**
     * Gets the signed observed template length. If  all  segments  are  mapped  to  the  same  reference,
     * the unsigned observed template length equals the number of bases from the leftmost mapped base to the
     * rightmost mapped base.  The leftmost segment has a plus sign and the rightmost has a minus sign.
     * The sign of segments in the middle is undefined. It is set as 0 for single-segment template or when the
     * information is unavailable.
     *
     * <p>Equivalent to <b>TLEN</b> in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * @return insert size if possible to compute; {@code 0} otherwise.
     */
    int getInferredInsertSize();

    /**
     * Sets the signed observed template length. If  all  segments  are  mapped  to  the  same  reference,
     * the unsigned observed template length equals the number of bases from the leftmost mapped base to the
     * rightmost mapped base.  The leftmost segment has a plus sign and the rightmost has a minus sign.
     * The sign of segments in the middle is undefined. It is set as 0 for single-segment template or when the
     * information is unavailable.
     *
     * <p>Equivalent to <b>TLEN</b> in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * @param insertSize insert size if possible to compute; {@code 0} otherwise.
     */
    void setInsertSize(final int insertSize);

    /**
     * Gets the read sequence as ASCII bytes.
     *
     * <p>Equivalent to <b>SEQ</b> in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * <p>This method should make a defensive copy of the bases array before returning it to allow
     * modification of the returned array without effects on the {@link Read}.
     *
     * @return the read sequence; {@link ReadConstants#NULL_SEQUENCE} if no sequence is present.
     */
    byte[] getBases();

    /**
     * Gets the base at position i.
     *
     * <p>Equivalent to <b>SEQ</b> in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * <p>Default implementation returns {@code getBases()[i]}.
     * Subclasses may override to provide a more efficient implementation.
     *
     * @return base at index i (0-based).
     *
     * @throws IndexOutOfBoundsException if i is negative or of i is not smaller than the number
     * of bases (as reported by {@link #getLength()}. In particular, if no sequence is present.
     */
    default byte getBase(final int i){
        return getBases()[i];
    }

    /**
     * Gets all the bases in the read as an ASCII String.
     *
     * <p>Equivalent to <b>SEQ</b> in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * <p>Default implementation uses {@code getBases()}.
     * Subclasses may override to provide a more efficient implementation.
     *
     * @return read sequence as a string ; {@link ReadConstants#NULL_SEQUENCE_STRING} if the read is empty.
     */
    default String getBasesString() {
        return isEmpty() ? ReadConstants.NULL_SEQUENCE_STRING : StringUtil.bytesToString(getBases());
    }

    /**
     * Sets the sequence bases.
     *
     * <p>Equivalent to <b>SEQ</b> in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * @param bases read sequence as ASCII bytes; {@link ReadConstants#NULL_SEQUENCE} if no sequence is present.
     *
     * @throws IllegalArgumentException if the bases are null.
     */
    void setBases(final byte[] bases);

    /**
     * Gets the base qualities, as binary phred scores (not ASCII).
     *
     * <p>Equivalent to <b>QUAL</b> in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * <p>This method should make a defensive copy of the bases array before returning it to allow
     * modification of the returned array without effects on the {@link Read}.
     *
     * @return base qualities; {@link ReadConstants#NULL_QUALS} if base qualities are not present.
     */
    byte[] getBaseQualities();

    /**
     * Gets the number of base qualities in the read sequence.
     *
     * <p>Equivalent to <b>QUAL</b> in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * <p>Default implementation calls {@code getBaseQualities().length}.
     * Subclasses may override to provide a more efficient implementation.
     *
     * @return number of base qualities in the read sequence.
     */
    default int getBaseQualityLength(){
        return getBaseQualities().length;
    }

    /**
     * Checks if the read has qualities.
     *
     * <p>Default implementation returns {@code getBaseQualityLength() != 0}.
     *
     * @return {@code true} if the read has qualities; {@code false} otherwise.
     */
    default boolean hasQualities() {
        return getBaseQualityLength() != 0;
    }

    /**
     * Gets the base quality at position i.
     *
     * <p>Default implementation returns {@code getBaseQualities()[i]}.
     * Subclasses may override to provide a more efficient implementation.
     *
     * @return The base quality at index i (0-based).
     *
     * @throws IndexOutOfBoundsException if i is negative or of i is not smaller than the number
     * of base qualities (as reported by {@link #getBaseQualityLength()}.
     */
    default byte getBaseQuality(final int i){
        return getBaseQualities()[i];
    }

    /**
     * Gets all the base qualities in the read as an ASCII String.
     *
     * <p>Equivalent to <b>QUAL</b> in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * @return base qualities as a string ; {@link ReadConstants#NULL_QUALS_STRING} if the read does not have qualities.
     */
    default String getBaseQualityString() {
        return hasQualities() ? SAMUtils.phredToFastq(getBaseQualities()) : ReadConstants.NULL_QUALS_STRING;
    }

    /**
     * Set the base qualities.
     *
     * <p>Equivalent to <b>QUAL</b> in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * @param baseQualities base qualities as binary phred scores (not ASCII); {@link ReadConstants#NULL_QUALS} if no base qualities are present.
     *
     * @throws IllegalArgumentException if the base qualities are null or an invalid (negative) base quality is provided.
     */
    void setBaseQualities(final byte[] baseQualities);

    ///////////////////////////////////////////////////////////
    // OPTIONAL FIELDS - untyped getters/setters
    ///////////////////////////////////////////////////////////

    /**
     * Gets the list of optional fields for the read. These are encoded as tag/value pairs, where
     * the tag is a 2-character String and the value is from a concrete type.
     *
     * @return immutable list of attributes.
     */
    List<ReadAttribute<?>> getAttributes();

    /**
     * Gets the attribute value associated with the attribute tag provided.
     *
     * <p>Default implementation search for the tag value is the {@link #getAttributes()} list
     * and returns the first one matching the tag.
     * Subclasses may override to provide more efficient implementations.
     *
     * @param tag attribute tag (two character string).
     *
     * @return optional value associated with the tag; may be empty if the attribute is not present.
     *
     * @throws SAMException if the tag is invalid following the contract of {@link ReadAttribute#isValidTag(String)}.
     */
    default Optional<Object> getAttribute(final String tag) {
        if (ReadAttribute.isValidTag(tag)) {
            throw new SAMException("String tag does not have length() == 2: " + tag);
        }
        return getAttributes().stream().filter(attr -> attr.getTag().equals(tag))
                .map(attr -> (Object) attr.getValue())
                .findFirst();
    }

    /**
     * Checks if the read has set the attribute tag provided.
     *
     * <p>Default implementation returns {@code getAttribute(tag).isPresent()}.
     *
     * @param tag attribute tag (two character string).
     * @return {@code true} if the attribute was set; {@code false} otherwise.
     *
     * @throws SAMException if the tag is invalid following the contract of {@link ReadAttribute#isValidTag(String)}.
     */
    default boolean hasAttribute(final String tag) {
        return getAttribute(tag).isPresent();
    }

    /**
     * Sets the attribute value associated with the attribute tag.
     *
     * <p>Note: it is preferable to use setters for typed objects.
     *
     * @param tag attribute tag (two character string).
     * @param value object value.
     *
     * @throws SAMException if the tag is invalid following the contract of {@link ReadAttribute#isValidTag(String)}.
     *
     * @throws IllegalArgumentException if the value is {@code null}. For clear an attribute value,
     * use {@link #clearAttribute(String)} instead.
     *
     * @throws SAMException if the tag is invalid following the contract of {@link ReadAttribute#isValidTag(String)}.
     * @throws SAMException if the value is invalid following the contract of {@link ReadAttribute#isAllowedAttributeValue(Object)}.
     */
    void setAttribute(final String tag, final Object value);

    /**
     * Clear an individual attribute on the read.
     *
     * @param tag attribute tag (two character string).
     *
     * @throws SAMException if the tag is invalid following the contract of {@link ReadAttribute#isValidTag(String)}.
     */
    void clearAttribute(final String tag);

    /**
     * Clear all attributes on the read.
     */
    void clearAttributes();


    ///////////////////////////////////////////////////////////
    // FLAG SHORTCUT METHODS
    ///////////////////////////////////////////////////////////


    /**
     * Checks if the read is paired.
     *
     * <p>Equivalent to the 0x1 <b>FLAG</b> in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * <p>Default implementation calls {@link #isSet(SAMFlag)} for {@link SAMFlag#READ_PAIRED}.
     *
     * @return {@code true} if this read is paired (e.g. has a mate); {@code false} otherwise.
     */
    default boolean isPaired() {
        return isSet(SAMFlag.READ_PAIRED);
    }

    /**
     * Mark the read as paired (having a mate) or not paired.
     *
     * <p>Equivalent to the 0x1 <b>FLAG</b> in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * <p>Default implementation calls the {@code addFlag(SAMFlag.READ_PAIRED, paired)}.
     *
     * @param paired {@code true} if the read is paired; {@code false} otherwise.
     */
    default void setPaired(final boolean paired) {
        addFlag(SAMFlag.READ_PAIRED, paired);
    }

    /**
     * Checks if the read is mapped in a proper pair (depends on the protocol, normally inferred during alignment).
     *
     * <p>Equivalent to the 0x2 <b>FLAG</b> in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * <p>Default implementation calls {@link #isSet(SAMFlag)} for {@link SAMFlag#PROPER_PAIR}.
     *
     * @return {@code true} if this read is properly paired ; {@code false} otherwise.
     */
    default boolean isProperlyPaired() {
        return isSet(SAMFlag.PROPER_PAIR);
    }

    /**
     * Mark the read as properly paired or not.
     *
     * <p>Equivalent to the 0x2 <b>FLAG</b> in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * <p>Default implementation calls the {@code addFlag(SAMFlag.PROPER_PAIR, properlyPaired)}.
     *
     * @param properlyPaired {@code true} if the read is properly paired; {@code false} otherwise.
     */
    default void setProperlyPaired(final boolean properlyPaired) {
        addFlag(SAMFlag.PROPER_PAIR, properlyPaired);
    }

    /**
     * Checks if the read is unmapped.
     *
     * <p>Equivalent to the 0x4 <b>FLAG</b> in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * <p>Default implementation calls {@link #isSet(SAMFlag)} for {@link SAMFlag#READ_UNMAPPED}.
     *
     * @return {@code true} if this read is unmapped; {@code false} otherwise.
     */
    // TODO (before merging) - add method for get/set the reverse
    default boolean isUnmapped() {
        return isSet(SAMFlag.READ_UNMAPPED);
    }

    /**
     * Mark the read as unmapped or not.
     *
     * <p>Equivalent to the 0x4 <b>FLAG</b> in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * <p>Default implementation calls the {@code addFlag(SAMFlag.READ_UNMAPPED, unmapped)}.
     *
     * @param unmapped {@code true} if this read is unmapped; {@code false} otherwise.
     */
    // TODO (before merging) - add method for get/set the reverse
    default void setUnmapped(final boolean unmapped) {
        addFlag(SAMFlag.READ_UNMAPPED, unmapped);
    }

    /**
     * Checks if the read's mate is unmapped.
     *
     * <p>Equivalent to the 0x8 <b>FLAG</b> in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * <p>Default implementation calls {@link #isSet(SAMFlag)} for {@link SAMFlag#MATE_UNMAPPED}.
     *
     * @return {@code true} if this read's mate is unmapped; {@code false} otherwise.
     */
    // TODO (before merging) - add method for get/set the reverse
    default boolean isMateUnmapped() {
        return isSet(SAMFlag.MATE_UNMAPPED);
    }

    /**
     * Mark the read's mate as unmapped or not.
     *
     * <p>Equivalent to the 0x8 <b>FLAG</b> in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * <p>Default implementation calls the {@code addFlag(SAMFlag.MATE_UNMAPPED, mateUnmapped)}.
     *
     * @param mateUnmapped {@code true} if this read's mate is unmapped; {@code false} otherwise.
     */
    // TODO (before merging) - add method for get/set the reverse
    default void setMateUnmapped(final boolean mateUnmapped) {
        addFlag(SAMFlag.MATE_UNMAPPED, mateUnmapped);
    }

    /**
     * Checks if the read is in the reverse strand.
     *
     * <p>Equivalent to the 0x10 <b>FLAG</b> in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * <p>Default implementation calls {@link #isSet(SAMFlag)} for {@link SAMFlag#READ_REVERSE_STRAND}.
     *
     * @return {@code true} if the read is in the reverse strand; {@code false} otherwise.
     */
    // TODO (before merging) - add method for get/set the reverse
    default boolean isReverseStrand() {
        return isSet(SAMFlag.READ_REVERSE_STRAND);
    }

    /**
     * Marks the read as being in the reverse strand or not.
     *
     * <p>Equivalent to the 0x10 <b>FLAG</b> in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * <p>Default implementation calls the {@code addFlag(SAMFlag.READ_REVERSE_STRAND, reverseStrand)}.
     *
     * @param reverseStrand {@code true} if the read is in the reverse strand; {@code false} otherwise.
     */
    // TODO (before merging) - add method for get/set the reverse
    default void setReverseStrand(final boolean reverseStrand) {
        addFlag(SAMFlag.READ_REVERSE_STRAND, reverseStrand);
    }

    /**
     * Checks if the read's mate is in the reverse strand.
     *
     * <p>Equivalent to the 0x20 <b>FLAG</b> in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * <p>Default implementation calls {@link #isSet(SAMFlag)} for {@link SAMFlag#MATE_REVERSE_STRAND}.
     *
     * @return {@code true} if the read's mate is in the reverse strand; {@code false} otherwise.
     */
    // TODO (before merging) - add method for get/set the reverse
    default boolean isMateReverseStrand() {
        return isSet(SAMFlag.MATE_REVERSE_STRAND);
    }

    /**
     * Marks the read's mate as being in the reverse strand or not.
     *
     * <p>Equivalent to the 0x20 <b>FLAG</b> in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * <p>Default implementation calls the {@code addFlag(SAMFlag.MATE_REVERSE_STRAND, mateReverseStrand)}.
     *
     * @param mateReverseStrand {@code true} if the read's mate is in the reverse strand; {@code false} otherwise.
     */
    // TODO (before merging) - add method for get/set the reverse
    default void setMateReverseStrand(final boolean mateReverseStrand) {
        addFlag(SAMFlag.MATE_REVERSE_STRAND, mateReverseStrand);
    }

    /**
     * Checks if the read is the first of a pair.
     *
     * <p>Equivalent to the 0x40 <b>FLAG</b> in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * <p>Default implementation calls {@link #isSet(SAMFlag)} for {@link SAMFlag#FIRST_OF_PAIR}.
     *
     * @return {@code true} if the read is in the first of a pair; {@code false} otherwise.
     */
    default boolean isFirstOfPair() {
        return isSet(SAMFlag.FIRST_OF_PAIR);
    }

    /**
     * Marks the read as being the first of the pair or not.
     *
     * <p>Equivalent to the 0x40 <b>FLAG</b> in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * <p>Default implementation calls the {@code addFlag(SAMFlag.FIRST_OF_PAIR, firstOfPair)}.
     *
     * @param firstOfPair {@code true} if the read is in the first of a pair; {@code false} otherwise.
     */
    default void setFirstOfPair(final boolean firstOfPair) {
        addFlag(SAMFlag.FIRST_OF_PAIR, firstOfPair);
    }

    /**
     * Checks if the read is the second of a pair.
     *
     * <p>Equivalent to the 0x80 <b>FLAG</b> in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * <p>Default implementation calls {@link #isSet(SAMFlag)} for {@link SAMFlag#SECOND_OF_PAIR}.
     *
     * @return {@code true} if the read is in the second of a pair; {@code false} otherwise.
     */
    default boolean isSecondOfPair() {
        return isSet(SAMFlag.SECOND_OF_PAIR);
    }

    /**
     * Marks the read as being the second of a pair or not.
     *
     * <p>Equivalent to the 0x80 <b>FLAG</b> in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * <p>Default implementation calls the {@code addFlag(SAMFlag.SECOND_OF_PAIR, secondOfPair)}.
     *
     * @param secondOfPair {@code true} if the read is in the second of a pair; {@code false} otherwise.
     */
    default void setSecondOfPair(final boolean secondOfPair) {
        addFlag(SAMFlag.SECOND_OF_PAIR, secondOfPair);
    }

    /**
     * Checks if the read is a secondary alignment.
     *
     * <p>Equivalent to the 0x100 <b>FLAG</b> in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * <p>Default implementation calls {@link #isSet(SAMFlag)} for {@link SAMFlag#NOT_PRIMARY_ALIGNMENT}.
     *
     * @return {@code true} if the read is a secondary alignment; {@code false} otherwise.
     */
    default boolean isSecondaryAlignment() {
        return isSet(SAMFlag.NOT_PRIMARY_ALIGNMENT);
    }

    /**
     * Marks the read as being a secondary alignment or not.
     *
     * <p>Equivalent to the 0x100 <b>FLAG</b> in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * <p>Default implementation calls the {@code addFlag(SAMFlag.NOT_PRIMARY_ALIGNMENT, secondaryAlignment)}.
     *
     * @param secondaryAlignment {@code true} if the read is a secondary alignment; {@code false} otherwise.
     */
    default void setSecondaryAlignment(final boolean secondaryAlignment) {
        addFlag(SAMFlag.NOT_PRIMARY_ALIGNMENT, secondaryAlignment);
    }

    /**
     * Checks if the read fails the quality vendor check.
     *
     * <p>Equivalent to the 0x200 <b>FLAG</b> in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * <p>Default implementation calls {@link #isSet(SAMFlag)} for {@link SAMFlag#READ_FAILS_VENDOR_QUALITY_CHECK}.
     *
     * @return {@code true} if the read fails the quality vendor check; {@code false} otherwise.
     */
    // TODO (before merging) - add method for get/set the reverse
    default boolean failsQualityVendorCheck() {
        return isSet(SAMFlag.READ_FAILS_VENDOR_QUALITY_CHECK);
    }

    /**
     * Marks the read as failing the quality vendor check or not.
     *
     * <p>Equivalent to the 0x200 <b>FLAG</b> in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * <p>Default implementation calls the {@code addFlag(SAMFlag.READ_FAILS_VENDOR_QUALITY_CHECK, failsQualityVendorCheck)}.
     *
     * @param failsQualityVendorCheck {@code true} if the read fails the quality vendor check; {@code false} otherwise.
     */
    // TODO (before merging) - add method for get/set the reverse
    default void setFailsQualityVendorCheck(final boolean failsQualityVendorCheck) {
        addFlag(SAMFlag.READ_FAILS_VENDOR_QUALITY_CHECK, failsQualityVendorCheck);
    }

    /**
     * Checks if the read is a duplicate.
     *
     * <p>Equivalent to the 0x400 <b>FLAG</b> in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * <p>Default implementation calls {@link #isSet(SAMFlag)} for {@link SAMFlag#DUPLICATE_READ}.
     *
     * @return {@code true} if the read is a duplicate; {@code false} otherwise.
     */
    default boolean isDuplicate() {
        return isSet(SAMFlag.DUPLICATE_READ);
    }

    /**
     * Marks the read as a duplicate or not.
     *
     * <p>Equivalent to the 0x400 <b>FLAG</b> in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * <p>Default implementation calls the {@code addFlag(SAMFlag.DUPLICATE_READ, duplicate)}.
     *
     * @param duplicate {@code true} if the read is a duplicate; {@code false} otherwise.
     */
    default void setDuplicate(final boolean duplicate) {
        addFlag(SAMFlag.DUPLICATE_READ, duplicate);
    }

    /**
     * Checks if the read is a supplementary alignment.
     *
     * <p>Equivalent to the 0x800 <b>FLAG</b> in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * <p>Default implementation calls {@link #isSet(SAMFlag)} for {@link SAMFlag#SUPPLEMENTARY_ALIGNMENT}.
     *
     * @return {@code true} if the read is a supplementary alignment; {@code false} otherwise.
     */
    default boolean isSupplementaryAlignment() {
        return isSet(SAMFlag.SUPPLEMENTARY_ALIGNMENT);
    }

    /**
     * Marks the read as being a supplementary alignment.
     *
     * <p>Equivalent to the 0x800 <b>FLAG</b> in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * <p>Default implementation calls the {@code addFlag(SAMFlag.SUPPLEMENTARY_ALIGNMENT, supplementaryAlignment)}.
     *
     * @param supplementaryAlignment {@code true} if the read is a supplementary alignment; {@code false} otherwise.
     */
    // TODO (before merging) - add method for get the reverse
    default void setSupplementaryAlignment(final boolean supplementaryAlignment) {
        addFlag(SAMFlag.SUPPLEMENTARY_ALIGNMENT, supplementaryAlignment);
    }

    /**
     * Checks if the read is a secondary or supplementary alignment.
     *
     * <p>Equivalent to the 0x100 or 0x800 <b>FLAG</b> in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * <p>Default implementation calls {@code isSecondaryAlignment() || isSupplementaryAlignment()}.
     *
     * @return {@code true} if the read is secondary or supplementary; {@code false} otherwise.
     */
    default boolean isSecondaryOrSupplementary() {
        return isSecondaryAlignment() || isSupplementaryAlignment();
    }

    ///////////////////////////////////////////////////////////
    // OPTIONAL FIELDS - typed getters/setters
    ///////////////////////////////////////////////////////////

    /**
     * Gets the character value associated with the tag.
     *
     * <p>Equivalent to an attribute with <b>TYPE A</b> in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * <p>Default implementation gets the attribute with {@link #getAttribute(String)}, checking if it is a {@link Character}.
     *
     * @param tag attribute tag (two character string).
     *
     * @return the character value.
     *
     * @throws SAMException if the tag is invalid following the contract of {@link ReadAttribute#isValidTag(String)}.
     * @throws SAMException if the attribute value is not a character.
     */
    default Optional<Character> getCharacterAttribute(final String tag) {
        final Optional<Object> attr = getAttribute(tag);
        if (!attr.isPresent()) {
            return Optional.empty();
        }
        final Object val = attr.get();

        if (val instanceof Character) {
            return Optional.of((Character)val);
        }
        throw new SAMException("Value for tag " + tag + " is not a Character: " + val.getClass());
    }

    /**
     * Gets the signed Integer value associated with the tag.
     *
     * <p>Equivalent to an attribute with <b>TYPE i</b> (signed or unsigned) in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * <p>Default implementation gets the attribute with {@link #getAttribute(String)}, checking if it is a numeric value within the integer range.
     *
     * @param tag attribute tag (two character string).
     *
     * @return the Integer value.
     *
     * @throws SAMException if the tag is invalid following the contract of {@link ReadAttribute#isValidTag(String)}.
     * @throws SAMException if the attribute value is not an Integer.
     */
    default Optional<Integer> getIntegerAttribute(final String tag) {
        final Optional<Object> attr = getAttribute(tag);
        if (!attr.isPresent()) {
            return Optional.empty();
        }
        final Object val = attr.get();

        if (!(val instanceof Number)) {
            throw new SAMException("Value for tag " + tag + " is not Number: " + val.getClass());
        }
        final long longVal = ((Number)val).longValue();
        if (longVal < Integer.MIN_VALUE || longVal > Integer.MAX_VALUE) {
            throw new SAMException("Value for tag " + tag + " is not in Integer range: " + longVal);
        }
        return Optional.of((int) longVal);
    }

    /**
     * Gets the Float value associated with the tag.
     *
     * <p>Equivalent to an attribute with <b>TYPE f</b> in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * <p>Default implementation gets the attribute with {@link #getAttribute(String)}, checking if it is a {@link Float}.
     *
     * @param tag attribute tag (two character string).
     *
     * @return the Float value.
     *
     * @throws SAMException if the tag is invalid following the contract of {@link ReadAttribute#isValidTag(String)}.
     * @throws SAMException if the attribute is not a Float.
     */
    default Optional<Float> getFloatAttribute(final String tag) {
        final Optional<Object> attr = getAttribute(tag);
        if (!attr.isPresent()) {
            return Optional.empty();
        }
        final Object val = attr.get();
        if (val instanceof Float) {
            return Optional.of((Float) val);
        }
        throw new SAMException("Value for tag " + tag + " is not a Float: " + val.getClass());
    }

    /**
     * Gets the String associated with the tag.
     *
     * <p>Equivalent to an attribute with <b>TYPE Z</b> in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * <p>Default implementation gets the attribute with {@link #getAttribute(String)}, checking if it is a {@link String}.
     *
     * @param tag attribute tag (two character string).
     *
     * @return the String value.
     *
     * @throws SAMException if the tag is invalid following the contract of {@link ReadAttribute#isValidTag(String)}.
     * @throws SAMException if the attribute is not a String.
     */
    default Optional<String> getStringAttribute(final String tag) {
        final Optional<Object> attr = getAttribute(tag);
        if (!attr.isPresent()) {
            return Optional.empty();
        }
        final Object val = attr.get();
        if (val instanceof String) {
            return Optional.of((String) val);
        }
        throw new SAMException("Value for tag " + tag + " is not a String: " + val.getClass());
    }

    /**
     * Gets the Byte associated with the tag.
     *
     * <p>Equivalent to an attribute with <b>TYPE B</b> byte (signed or unsigned) in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * <p>Default implementation gets the attribute with {@link #getAttribute(String)}, checking if it is a numeric within the Byte range.
     *
     * @param tag attribute tag (two character string).
     *
     * @return the Byte value.
     *
     * @throws SAMException if the tag is invalid following the contract of {@link ReadAttribute#isValidTag(String)}.
     * @throws SAMException if the attribute is not a Byte.
     */
    default Optional<Byte> getByteAttribute(final String tag) {
        final Optional<Object> attr = getAttribute(tag);
        if (!attr.isPresent()) {
            return Optional.empty();
        }
        final Object val = attr.get();
        if (val instanceof Byte) {
            return Optional.of((Byte)val);
        }
        if (!(val instanceof Number)) {
            throw new SAMException("Value for tag " + tag + " is not Number: " + val.getClass());
        }
        final long longVal = ((Number)val).longValue();
        if (longVal < Byte.MIN_VALUE || longVal > Byte.MAX_VALUE) {
            throw new SAMException("Value for tag " + tag + " is not in Short range: " + longVal);
        }
        return Optional.of((byte)longVal);
    }

    /**
     * Gets the Short associated with the tag.
     *
     * <p>Equivalent to an attribute with <b>TYPE B</b> short (signed or unsigned) in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * <p>Default implementation gets the attribute with {@link #getAttribute(String)}, checking if it is a numeric within the Short range.
     *
     * @param tag attribute tag (two character string).
     *
     * @return the Short value.
     *
     * @throws SAMException if the tag is invalid following the contract of {@link ReadAttribute#isValidTag(String)}.
     * @throws SAMException if the attribute is not a Short.
     */
    default Optional<Short> getShortAttribute(final String tag) {
        final Optional<Object> attr = getAttribute(tag);
        if (!attr.isPresent()) {
            return Optional.empty();
        }
        final Object val = attr.get();
        if (val instanceof Short) {
            return Optional.of((Short) val);
        }
        if (!(val instanceof Number)) {
            throw new SAMException("Value for tag " + tag + " is not Number: " + val.getClass());
        }
        final long longVal = ((Number) val).longValue();
        if (longVal < Short.MIN_VALUE || longVal > Short.MAX_VALUE) {
            throw new SAMException("Value for tag " + tag + " is not in Short range: " + longVal);
        }
        return Optional.of((short) longVal);
    }

    /**
     * Gets the integer array associated with the tag.
     *
     * <p>Equivalent to an attribute with <b>TYPE B</b> integer array (signed or unsigned) in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * <p>Default implementation gets the attribute with {@link #getAttribute(String)}, checking if it is a {@code int[]}.
     *
     * @param tag attribute tag (two character string).
     *
     * @return the int array value.
     *
     * @throws SAMException if the tag is invalid following the contract of {@link ReadAttribute#isValidTag(String)}.
     * @throws SAMException if the attribute is not a {@code int[]}.
     */
    default Optional<int[]> getIntegerArrayAttribute(final String tag) {
        final Optional<Object> attr = getAttribute(tag);
        if (!attr.isPresent()) {
            return Optional.empty();
        }
        final Object val = attr.get();
        if (val instanceof int[]) {
            return Optional.of((int[])val);
        }
        throw new SAMException("Value for tag " + tag + " is not a int[]: " + val.getClass());
    }

    /**
     * Gets the float array associated with the tag.
     *
     * <p>Equivalent to an attribute with <b>TYPE B</b> float array in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * <p>Default implementation gets the attribute with {@link #getAttribute(String)}, checking if it is a {@code float[]}.
     *
     * @param tag attribute tag (two character string).
     *
     * @return the float array value.
     *
     * @throws SAMException if the tag is invalid following the contract of {@link ReadAttribute#isValidTag(String)}.
     * @throws SAMException if the attribute is not a {@code float[]}.
     */
    default Optional<float[]> getFloatArrayAttribute(final String tag) {
        final Optional<Object> attr = getAttribute(tag);
        if (!attr.isPresent()) {
            return Optional.empty();
        }
        final Object val = attr.get();
        if (val instanceof float[]) {
            return Optional.of((float[])val);
        }
        throw new SAMException("Value for tag " + tag + " is not a float[]: " + val.getClass());
    }

    /**
     * Gets the byte array associated with the tag.
     *
     * <p>Equivalent to an attribute with <b>TYPE H</b> and any <b>TYPE B</b> byte array (signed or unsigned) in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * <p>Default implementation gets the attribute with {@link #getAttribute(String)}, checking if it is a {@code byte[]}.
     *
     * @param tag attribute tag (two character string).
     *
     * @return the byte array value.
     *
     * @throws SAMException if the tag is invalid following the contract of {@link ReadAttribute#isValidTag(String)}.
     * @throws SAMException if the attribute is not a {@code byte[]}.
     */
    default Optional<byte[]> getByteArrayAttribute(final String tag) {
        final Optional<Object> attr = getAttribute(tag);
        if (!attr.isPresent()) {
            return Optional.empty();
        }
        final Object val = attr.get();
        if (val instanceof byte[]) {
            return Optional.of((byte[])val);
        }
        throw new SAMException("Value for tag " + tag + " is not a byte[]: " + val.getClass());
    }

    /**
     * Gets the short array associated with the tag.
     *
     * <p>Equivalent to an attribute with <b>TYPE B</b> short array (signed or unsigned) in the <a href="http://samtools.github.io/hts-specs/SAMv1.pdf">SAM specifications</a>.
     *
     * <p>Default implementation gets the attribute with {@link #getAttribute(String)}, checking if it is a {@code short[]}.
     *
     * @param tag attribute tag (two character string).
     *
     * @return the short array value.
     *
     * @throws SAMException if the tag is invalid following the contract of {@link ReadAttribute#isValidTag(String)}.
     * @throws SAMException if the attribute is not a {@code short[]}.
     */
    default Optional<short[]> getShortArrayAttribute(final String tag) {
        final Optional<Object> attr = getAttribute(tag);
        if (!attr.isPresent()) {
            return Optional.empty();
        }
        final Object val = attr.get();
        if (val instanceof short[]) {
            return Optional.of((short[])val);
        }
        throw new SAMException("Value for tag " + tag + " is not a short[]: " + val.getClass());
    }

    /**
     * Sets a character attribute.
     *
     * <p>Default implementation calls {@link #setAttribute(String, Object)} casting the value.
     *
     * @param tag attribute tag (two character string).
     * @param value the character value.
     *
     * @throws SAMException if the tag is invalid following the contract of {@link ReadAttribute#isValidTag(String)}.
     */
    default void setAttribute(final String tag, final char value) {
        setAttribute(tag, (Object) value);
    }

    /**
     * Sets an integer attribute.
     *
     * <p>Default implementation calls {@link #setAttribute(String, Object)} casting the value.
     *
     * @param tag attribute tag (two character string).
     * @param value the integer value.
     *
     * @throws SAMException if the tag is invalid following the contract of {@link ReadAttribute#isValidTag(String)}.
     */
    default void setAttribute(final String tag, final int value) {
        setAttribute(tag, (Object) value);
    }

    /**
     * Sets a float attribute.
     *
     * <p>Default implementation calls {@link #setAttribute(String, Object)} casting the value.
     *
     * @param tag attribute tag (two character string).
     * @param value the float value.
     *
     * @throws SAMException if the tag is invalid following the contract of {@link ReadAttribute#isValidTag(String)}.
     */
    default void setAttribute(final String tag, final float value) {
        setAttribute(tag, (Object) value);
    }

    /**
     * Sets a String attribute.
     *
     * <p>Default implementation calls {@link #setAttribute(String, Object)} casting the value.
     *
     * @param tag attribute tag (two character string).
     * @param value the string value.
     *
     * @throws SAMException if the tag is invalid following the contract of {@link ReadAttribute#isValidTag(String)}.
     */
    default void setAttribute(final String tag, final String value) {
        setAttribute(tag, (Object) value);
    }

    /**
     * Sets a byte attribute.
     *
     * <p>Default implementation calls {@link #setAttribute(String, Object)} casting the value.
     *
     * @param tag attribute tag (two character string).
     * @param value the byte value.
     *
     * @throws SAMException if the tag is invalid following the contract of {@link ReadAttribute#isValidTag(String)}.
     */
    default void setAttribute(final String tag, final byte value) {
        setAttribute(tag, (Object) value);
    }

    /**
     * Sets a short attribute.
     *
     * <p>Default implementation calls {@link #setAttribute(String, Object)} casting the value.
     *
     * @param tag attribute tag (two character string).
     * @param value the short value.
     *
     * @throws SAMException if the tag is invalid following the contract of {@link ReadAttribute#isValidTag(String)}.
     */
    default void setAttribute(final String tag, final short value) {
        setAttribute(tag, (Object) value);
    }

    /**
     * Sets a integer array attribute.
     *
     * <p>Default implementation calls {@link #setAttribute(String, Object)} casting the value.
     *
     * @param tag attribute tag (two character string).
     * @param value the integer value.
     *
     * @throws SAMException if the tag is invalid following the contract of {@link ReadAttribute#isValidTag(String)}.
     */
    default void setAttribute(final String tag, final int[] value) {
        setAttribute(tag, (Object) value);
    }

    /**
     * Sets a float array attribute.
     *
     * <p>Default implementation calls {@link #setAttribute(String, Object)} casting the value.
     *
     * @param tag attribute tag (two character string).
     * @param value the float array value.
     *
     * @throws SAMException if the tag is invalid following the contract of {@link ReadAttribute#isValidTag(String)}.
     */
    default void setAttribute(final String tag, final float[] value) {
        setAttribute(tag, (Object) value);
    }

    /**
     * Sets a byte array attribute.
     *
     * <p>Default implementation calls {@link #setAttribute(String, Object)} casting the value.
     *
     * @param tag attribute tag (two character string).
     * @param value the byte array value.
     *
     * @throws SAMException if the tag is invalid following the contract of {@link ReadAttribute#isValidTag(String)}.
     */
    default void setAttribute(final String tag, final byte[] value) {
        setAttribute(tag, (Object) value);
    }

    /**
     * Sets a short array attribute.
     *
     * <p>Default implementation calls {@link #setAttribute(String, Object)} casting the value.
     *
     * @param tag attribute tag (two character string).
     * @param value the short array value.
     *
     * @throws SAMException if the tag is invalid following the contract of {@link ReadAttribute#isValidTag(String)}.
     */
    default void setAttribute(final String tag, final short[] value) {
        setAttribute(tag, (Object) value);
    }

    ///////////////////////////////////////////////////////////
    // OTHER METHODS
    ///////////////////////////////////////////////////////////

    /**
     * Returns the record in the SAM line-based text format. Fields are separated by '\t' characters,
     * and the String is terminated by '\n'.
     *
     * @return SAM-formatted String
     */
    // TODO: add default implementation - requires SAMTestWriter to handle reads
    String getSAMString();

    /**
     * Return a copy of this read.
     *
     * <p>Note: the copy will not necessarily be a true deep copy. The fields encapsulated by the read
     * itself may be shallow copied. It should be safe to use in general if the return objects are
     * defensive copies of the read.
     *
     * @return a copy of this read.
     */
    Read copy();

    /**
     * Return a deep copy of this read, where any downstream modification would not be applied to this object.
     *
     * @return a true deep copy of this read.
     */
    Read deepCopy();

    ///////////////////////////////////////////////////////////
    // HELPER METHODS FOR SUBCLASSES
    ///////////////////////////////////////////////////////////

    /**
     * Generates a common String to use in {@link #toString()} for subclasses.
     *
     * <p>Note: this method should be used only for developer consumption.
     *
     * @return a common String representation or the read.
     */
    default String commonToString() {
        final StringBuilder builder = new StringBuilder(64);
        builder.append(getName());
        if (isPaired()) {
            if (isFirstOfPair()) {
                builder.append(" 1/2");
            }
            else {
                builder.append(" 2/2");
            }
        }

        builder.append(' ')
                .append(String.valueOf(getLength()))
                .append('b');

        if (isUnmapped()) {
            builder.append(" unmapped read.");
        }
        else {
            builder.append(" aligned read.");
        }

        return builder.toString();
    }

}
