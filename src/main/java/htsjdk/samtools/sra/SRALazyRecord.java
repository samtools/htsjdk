/*===========================================================================
*
*                            PUBLIC DOMAIN NOTICE
*               National Center for Biotechnology Information
*
*  This software/database is a "United States Government Work" under the
*  terms of the United States Copyright Act.  It was written as part of
*  the author's official duties as a United States Government employee and
*  thus cannot be copyrighted.  This software/database is freely available
*  to the public for use. The National Library of Medicine and the U.S.
*  Government have not placed any restriction on its use or reproduction.
*
*  Although all reasonable efforts have been taken to ensure the accuracy
*  and reliability of the software and data, the NLM and the U.S.
*  Government do not and cannot warrant the performance or results that
*  may be obtained by using this software or data. The NLM and the U.S.
*  Government disclaim all warranties, express or implied, including
*  warranties of performance, merchantability or fitness for any particular
*  purpose.
*
*  Please cite the author in any work or product based on this material.
*
* ===========================================================================
*
*/

package htsjdk.samtools.sra;

import gov.nih.nlm.ncbi.ngs.NGS;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMTagUtil;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.Cigar;
import htsjdk.samtools.SAMBinaryTagAndValue;
import htsjdk.samtools.SAMUtils;
import htsjdk.samtools.SAMValidationError;
import htsjdk.samtools.util.Log;
import ngs.ReadCollection;
import ngs.AlignmentIterator;
import ngs.Alignment;
import ngs.ReadIterator;
import ngs.Read;
import ngs.Fragment;
import ngs.ErrorMsg;

import java.io.IOException;
import java.util.EnumSet;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Extends SAMRecord so that any of the fields will be loaded only when needed.
 * Since SRA is a column oriented database, it is very inefficient to load all the fields at once.
 * However, loading only set of actually needed fields will be even faster than in row oriented databases.
 *
 * Because of that we are providing lazy loading of fields, flags and attributes.
 *
 * Created by andrii.nikitiuk on 8/25/15.
 */
public class SRALazyRecord extends SAMRecord {
    private static final Log log = Log.getInstance(SRALazyRecord.class);

    private SRAAccession accession;
    private boolean isAligned;
    private transient ReadCollection run;
    private transient Alignment alignmentIterator;
    private transient Read unalignmentIterator;
    private String sraReadId;
    private String sraAlignmentId;
    private int unalignedReadFragmentIndex = -1;


    private Set<LazyField> initializedFields = EnumSet.noneOf(LazyField.class);
    private Set<LazyFlag> initializedFlags = EnumSet.noneOf(LazyFlag.class);
    private Set<LazyAttribute> initializedAttributes = EnumSet.noneOf(LazyAttribute.class);

    private enum LazyField {
        ALIGNMENT_START {
            @Override
            public void loadValue(SRALazyRecord self) {
                self.getAlignmentStart();
            }
        },
        MAPPING_QUALITY {
            @Override
            public void loadValue(SRALazyRecord self) {
                self.getMappingQuality();
            }
        },
        REFERENCE_NAME {
            @Override
            public void loadValue(SRALazyRecord self) {
                self.getReferenceName();
            }
        },
        CIGAR_STRING {
            @Override
            public void loadValue(SRALazyRecord self) {
                self.getCigarString();
            }
        },
        BASES {
            @Override
            public void loadValue(SRALazyRecord self) {
                self.getReadBases();
            }
        },
        QUALS {
            @Override
            public void loadValue(SRALazyRecord self) {
                self.getBaseQualities();
            }
        },
        MATE_ALIGNMENT_START {
            @Override
            public void loadValue(SRALazyRecord self) {
                self.getMateAlignmentStart();
            }
        },
        MATE_REFERENCE_NAME {
            @Override
            public void loadValue(SRALazyRecord self) {
                self.getMateReferenceName();
            }
        },
        INFERRED_INSERT_SIZE {
            @Override
            public void loadValue(SRALazyRecord self) {
                self.getInferredInsertSize();
            }
        };

        public abstract void loadValue(SRALazyRecord self);
    }

    private enum LazyFlag {
        READ_NEGATIVE_STRAND(true) {
            @Override
            public boolean getFlag(SRALazyRecord self) {
                return self.getReadNegativeStrandFlag();
            }
        },
        READ_PAIRED(true) {
            @Override
            public boolean getFlag(SRALazyRecord self) {
                return self.getReadPairedFlag();
            }
        },
        PROPER_PAIR(false) {
            @Override
            public boolean getFlag(SRALazyRecord self) {
                return self.getProperPairFlag();
            }
        },
        NOT_PRIMARY_ALIGNMENT(true) {
            @Override
            public boolean getFlag(SRALazyRecord self) {
                return self.getNotPrimaryAlignmentFlag();
            }
        },
        MATE_NEGATIVE_STRAND(false) {
            @Override
            public boolean getFlag(SRALazyRecord self) {
                return self.getMateNegativeStrandFlag();
            }
        },
        MATE_UNMAPPED(false) {
            @Override
            public boolean getFlag(SRALazyRecord self) {
                return self.getMateUnmappedFlag();
            }
        },
        FIRST_OF_PAIR(false) {
            @Override
            public boolean getFlag(SRALazyRecord self) {
                return self.getFirstOfPairFlag();
            }
        },
        SECOND_OF_PAIR(false) {
            @Override
            public boolean getFlag(SRALazyRecord self) {
                return self.getSecondOfPairFlag();
            }
        };

        private final boolean canCallOnNotPaired;

        LazyFlag(final boolean canCallOnNotPaired) {
            this.canCallOnNotPaired = canCallOnNotPaired;
        }

        public boolean canCallOnNotPaired() { return canCallOnNotPaired; }

        public abstract boolean getFlag(SRALazyRecord self);
    }

    private enum LazyAttribute {
        RG {
            @Override
            public String getAttribute(SRALazyRecord self) {
                return self.getAttributeGroupNameImpl();
            }
        };

        public abstract String getAttribute(SRALazyRecord self);
    }

    private static Map<Short, LazyAttribute> lazyAttributeTags;
    static
    {
        lazyAttributeTags = new HashMap<Short, LazyAttribute>();
        lazyAttributeTags.put(SAMTagUtil.getSingleton().RG, LazyAttribute.RG);
    }

    public SRALazyRecord(final SAMFileHeader header, SRAAccession accession, ReadCollection run, AlignmentIterator alignmentIterator, String readId, String alignmentId) {
        this(header, accession, readId, alignmentId);

        this.run = run;
        this.alignmentIterator = alignmentIterator;
    }

    public SRALazyRecord(final SAMFileHeader header, SRAAccession accession, ReadCollection run, ReadIterator unalignmentIterator, String readId, int unalignedReadFragmentIndex) {
        this(header, accession, readId, unalignedReadFragmentIndex);

        this.run = run;
        this.unalignmentIterator = unalignmentIterator;
    }

    protected SRALazyRecord(final SAMFileHeader header, SRAAccession accession, String readId, String alignmentId) {
        this(header, accession, readId, true);

        this.sraAlignmentId = alignmentId;
    }

    protected SRALazyRecord(final SAMFileHeader header, SRAAccession accession, String readId, int unalignedReadFragmentIndex) {
        this(header, accession, readId, false);

        this.unalignedReadFragmentIndex = unalignedReadFragmentIndex;
    }

    private SRALazyRecord(final SAMFileHeader header, SRAAccession accession, String readId, boolean isAligned) {
        super(header);

        this.accession = accession;
        this.isAligned = isAligned;
        this.sraReadId = readId;
        setReadName(readId);
        setReadUnmappedFlag(!isAligned);
    }

    /**
     * Is being called when original NGS iterator is being moved to the next object.
     * Later, if any of uninitialized fields is requested, either Read object or Alignment has to be retrieved from
     * ReadCollection
     */
    public void detachFromIterator() {
        alignmentIterator = null;
        unalignmentIterator = null;
    }

    // ===== fields =====

    @Override
    public int getAlignmentStart() {
        if (!initializedFields.contains(LazyField.ALIGNMENT_START)) {
            setAlignmentStart(getAlignmentStartImpl());
        }
        return super.getAlignmentStart();
    }

    @Override
    public void setAlignmentStart(final int value) {
        if (!initializedFields.contains(LazyField.ALIGNMENT_START)) {
            initializedFields.add(LazyField.ALIGNMENT_START);
        }
        super.setAlignmentStart(value);
    }

    @Override
    public int getMappingQuality() {
        if (!initializedFields.contains(LazyField.MAPPING_QUALITY)) {
            setMappingQuality(getMappingQualityImpl());
        }
        return super.getMappingQuality();
    }

    @Override
    public void setMappingQuality(final int value) {
        if (!initializedFields.contains(LazyField.MAPPING_QUALITY)) {
            initializedFields.add(LazyField.MAPPING_QUALITY);
        }
        super.setMappingQuality(value);
    }

    @Override
    public String getReferenceName() {
        if (!initializedFields.contains(LazyField.REFERENCE_NAME)) {
            setReferenceName(getReferenceNameImpl());
        }
        return super.getReferenceName();
    }

    @Override
    public void setReferenceName(final String value) {
        if (!initializedFields.contains(LazyField.REFERENCE_NAME)) {
            initializedFields.add(LazyField.REFERENCE_NAME);
        }
        super.setReferenceName(value);
    }

    @Override
    public Integer getReferenceIndex() {
        if (!initializedFields.contains(LazyField.REFERENCE_NAME)) {
            setReferenceName(getReferenceNameImpl());
        }
        return super.getReferenceIndex();
    }

    @Override
    public void setReferenceIndex(final int value) {
        if (!initializedFields.contains(LazyField.REFERENCE_NAME)) {
            initializedFields.add(LazyField.REFERENCE_NAME);
        }
        super.setReferenceIndex(value);
    }

    @Override
    public String getCigarString() {
        if (!initializedFields.contains(LazyField.CIGAR_STRING)) {
            setCigarString(getCigarStringImpl());
        }
        return super.getCigarString();
    }

    @Override
    public void setCigarString(final String value) {
        if (!initializedFields.contains(LazyField.CIGAR_STRING)) {
            initializedFields.add(LazyField.CIGAR_STRING);
        }
        super.setCigarString(value);
    }

    @Override
    public Cigar getCigar() {
        if (!initializedFields.contains(LazyField.CIGAR_STRING)) {
            setCigarString(getCigarStringImpl());
        }
        return super.getCigar();
    }

    @Override
    public void setCigar(final Cigar value) {
        if (!initializedFields.contains(LazyField.CIGAR_STRING)) {
            initializedFields.add(LazyField.CIGAR_STRING);
        }
        super.setCigar(value);
    }

    @Override
    public byte[] getReadBases() {
        if (!initializedFields.contains(LazyField.BASES)) {
            setReadBases(getReadBasesImpl());
        }
        return super.getReadBases();
    }

    @Override
    public void setReadBases(final byte[] value) {
        if (!initializedFields.contains(LazyField.BASES)) {
            initializedFields.add(LazyField.BASES);
        }
        super.setReadBases(value);
    }

    @Override
    public byte[] getBaseQualities() {
        if (!initializedFields.contains(LazyField.QUALS)) {
            setBaseQualities(getBaseQualitiesImpl());
        }
        return super.getBaseQualities();
    }

    @Override
    public void setBaseQualities(final byte[] value) {
        if (!initializedFields.contains(LazyField.QUALS)) {
            initializedFields.add(LazyField.QUALS);
        }
        super.setBaseQualities(value);
    }

    @Override
    public int getMateAlignmentStart() {
        if (!initializedFields.contains(LazyField.MATE_ALIGNMENT_START)) {
            setMateAlignmentStart(getMateAlignmentStartImpl());
        }
        return super.getMateAlignmentStart();
    }

    @Override
    public void setMateAlignmentStart(final int value) {
        if (!initializedFields.contains(LazyField.MATE_ALIGNMENT_START)) {
            initializedFields.add(LazyField.MATE_ALIGNMENT_START);
        }
        super.setMateAlignmentStart(value);
    }

    @Override
    public String getMateReferenceName() {
        if (!initializedFields.contains(LazyField.MATE_REFERENCE_NAME)) {
            setMateReferenceName(getMateReferenceNameImpl());
        }
        return super.getMateReferenceName();
    }

    @Override
    public void setMateReferenceName(final String value) {
        if (!initializedFields.contains(LazyField.MATE_REFERENCE_NAME)) {
            initializedFields.add(LazyField.MATE_REFERENCE_NAME);
        }
        super.setMateReferenceName(value);
    }

    @Override
    public Integer getMateReferenceIndex() {
        if (!initializedFields.contains(LazyField.MATE_REFERENCE_NAME)) {
            setMateReferenceName(getMateReferenceNameImpl());
        }
        return super.getMateReferenceIndex();
    }

    @Override
    public void setMateReferenceIndex(final int value) {
        if (!initializedFields.contains(LazyField.MATE_REFERENCE_NAME)) {
            initializedFields.add(LazyField.MATE_REFERENCE_NAME);
        }
        super.setMateReferenceIndex(value);
    }

    @Override
    public int getInferredInsertSize() {
        if (!initializedFields.contains(LazyField.INFERRED_INSERT_SIZE)) {
            setInferredInsertSize(getInferredInsertSizeImpl());
        }
        return super.getInferredInsertSize();
    }

    @Override
    public void setInferredInsertSize(final int value) {
        if (!initializedFields.contains(LazyField.INFERRED_INSERT_SIZE)) {
            initializedFields.add(LazyField.INFERRED_INSERT_SIZE);
        }
        super.setInferredInsertSize(value);
    }

    // ===== flags =====

    @Override
    public int getFlags() {
        for (LazyFlag flag : LazyFlag.values()) {
            if (initializedFlags.contains(flag)) {
                continue;
            }

            if (flag.canCallOnNotPaired() || getReadPairedFlag()) {
                flag.getFlag(this);
            }
        }

        return super.getFlags();
    }

    @Override
    public void setFlags(final int value) {
        for (LazyFlag flag : LazyFlag.values()) {
            if (!initializedFlags.contains(flag)) {
                initializedFlags.add(flag);
            }
        }
        super.setFlags(value);
    }

    @Override
    public boolean getReadNegativeStrandFlag() {
        if (!initializedFlags.contains(LazyFlag.READ_NEGATIVE_STRAND)) {
            setReadNegativeStrandFlag(getReadNegativeStrandFlagImpl());
        }
        return super.getReadNegativeStrandFlag();
    }

    @Override
    public void setReadNegativeStrandFlag(final boolean flag) {
        if (!initializedFlags.contains(LazyFlag.READ_NEGATIVE_STRAND)) {
            initializedFlags.add(LazyFlag.READ_NEGATIVE_STRAND);
        }
        super.setReadNegativeStrandFlag(flag);
    }

    @Override
    public boolean getReadPairedFlag() {
        if (!initializedFlags.contains(LazyFlag.READ_PAIRED)) {
            setReadPairedFlag(getReadPairedFlagImpl());
        }
        return super.getReadPairedFlag();
    }

    @Override
    public void setReadPairedFlag(final boolean flag) {
        if (!initializedFlags.contains(LazyFlag.READ_PAIRED)) {
            initializedFlags.add(LazyFlag.READ_PAIRED);
        }
        super.setReadPairedFlag(flag);
    }

    @Override
    public boolean getProperPairFlag() {
        if (!initializedFlags.contains(LazyFlag.PROPER_PAIR)) {
            setProperPairFlag(getProperPairFlagImpl());
        }
        return super.getProperPairFlag();
    }

    @Override
    public void setProperPairFlag(final boolean flag) {
        if (!initializedFlags.contains(LazyFlag.PROPER_PAIR)) {
            initializedFlags.add(LazyFlag.PROPER_PAIR);
        }
        super.setProperPairFlag(flag);
    }

    @Override
    public boolean getNotPrimaryAlignmentFlag() {
        if (!initializedFlags.contains(LazyFlag.NOT_PRIMARY_ALIGNMENT)) {
            setNotPrimaryAlignmentFlag(getNotPrimaryAlignmentFlagImpl());
        }
        return super.getNotPrimaryAlignmentFlag();
    }

    @Override
    public void setNotPrimaryAlignmentFlag(final boolean flag) {
        if (!initializedFlags.contains(LazyFlag.NOT_PRIMARY_ALIGNMENT)) {
            initializedFlags.add(LazyFlag.NOT_PRIMARY_ALIGNMENT);
        }
        super.setNotPrimaryAlignmentFlag(flag);
    }

    @Override
    public boolean getMateNegativeStrandFlag() {
        if (!initializedFlags.contains(LazyFlag.MATE_NEGATIVE_STRAND)) {
            setMateNegativeStrandFlag(getMateNegativeStrandFlagImpl());
        }
        return super.getMateNegativeStrandFlag();
    }

    @Override
    public void setMateNegativeStrandFlag(final boolean flag) {
        if (!initializedFlags.contains(LazyFlag.MATE_NEGATIVE_STRAND)) {
            initializedFlags.add(LazyFlag.MATE_NEGATIVE_STRAND);
        }
        super.setMateNegativeStrandFlag(flag);
    }

    @Override
    public boolean getMateUnmappedFlag() {
        if (!initializedFlags.contains(LazyFlag.MATE_UNMAPPED)) {
            setMateUnmappedFlag(getMateUnmappedFlagImpl());
        }
        return super.getMateUnmappedFlag();
    }

    @Override
    public void setMateUnmappedFlag(final boolean flag) {
        if (!initializedFlags.contains(LazyFlag.MATE_UNMAPPED)) {
            initializedFlags.add(LazyFlag.MATE_UNMAPPED);
        }
        super.setMateUnmappedFlag(flag);
    }

    @Override
    public boolean getFirstOfPairFlag() {
        if (!initializedFlags.contains(LazyFlag.FIRST_OF_PAIR)) {
            setFirstOfPairFlag(getFirstOfPairFlagImpl());
        }
        return super.getFirstOfPairFlag();
    }

    @Override
    public void setFirstOfPairFlag(final boolean flag) {
        if (!initializedFlags.contains(LazyFlag.FIRST_OF_PAIR)) {
            initializedFlags.add(LazyFlag.FIRST_OF_PAIR);
        }
        super.setFirstOfPairFlag(flag);
    }

    @Override
    public boolean getSecondOfPairFlag() {
        if (!initializedFlags.contains(LazyFlag.SECOND_OF_PAIR)) {
            setSecondOfPairFlag(getSecondOfPairFlagImpl());
        }
        return super.getSecondOfPairFlag();
    }

    @Override
    public void setSecondOfPairFlag(final boolean flag) {
        if (!initializedFlags.contains(LazyFlag.SECOND_OF_PAIR)) {
            initializedFlags.add(LazyFlag.SECOND_OF_PAIR);
        }
        super.setSecondOfPairFlag(flag);
    }


    // ===== attributes =====

    @Override
    public Object getAttribute(final short tag) {
        LazyAttribute attr = lazyAttributeTags.get(tag);
        if (attr != null) {
            if (!initializedAttributes.contains(attr)) {
                setAttribute(tag, attr.getAttribute(this));
            }
        }
        return super.getAttribute(tag);
    }

    @Override
    public void setAttribute(final short tag, final Object value) {
        LazyAttribute attr = lazyAttributeTags.get(tag);
        if (attr != null && !initializedAttributes.contains(attr)) {
            initializedAttributes.add(attr);
        }
        super.setAttribute(tag, value);
    }

    @Override
    protected void setAttribute(final short tag, final Object value, final boolean isUnsignedArray) {
        LazyAttribute attr = lazyAttributeTags.get(tag);
        if (attr != null && !initializedAttributes.contains(attr)) {
            initializedAttributes.add(attr);
        }
        super.setAttribute(tag, value, isUnsignedArray);
    }

    @Override
    public void clearAttributes() {
        for (LazyAttribute lazyAttribute : LazyAttribute.values()) {
            if (!initializedAttributes.contains(lazyAttribute)) {
                initializedAttributes.add(lazyAttribute);
            }
        }
        super.clearAttributes();
    }

    @Override
    protected void setAttributes(final SAMBinaryTagAndValue attributes) {
        for (LazyAttribute lazyAttribute : LazyAttribute.values()) {
            if (!initializedAttributes.contains(lazyAttribute)) {
                initializedAttributes.add(lazyAttribute);
            }
        }
        super.setAttributes(attributes);
    }

    @Override
    protected SAMBinaryTagAndValue getBinaryAttributes() {
        for (Map.Entry<Short, LazyAttribute> info : lazyAttributeTags.entrySet()) {
            if (!initializedAttributes.contains(info.getValue())) {
                getAttribute(info.getKey());
            }
        }

        return super.getBinaryAttributes();
    }

    public boolean isUnsignedArrayAttribute(final String tag) {
        Short binaryTag = SAMTagUtil.getSingleton().makeBinaryTag(tag);
        LazyAttribute attr = lazyAttributeTags.get(binaryTag);
        if (attr != null && !initializedAttributes.contains(attr)) {
            getAttribute(binaryTag);
        }

        return super.isUnsignedArrayAttribute(tag);
    }

    // ===== misc ====

    /**
     * For records equality, we should only compare read id, reference and position on the reference.
     * Since read id is a constructor parameter, we only need to make sure that reference info is loaded.
     * @param o other
     * @return comparison result
     */
    @Override
    public boolean equals(final Object o) {
        if (o instanceof SRALazyRecord) {
            SRALazyRecord otherRecord = (SRALazyRecord)o;
            otherRecord.getReferenceIndex();
            otherRecord.getAlignmentStart();
        }

        getReferenceIndex();
        getAlignmentStart();

        return super.equals(o);
    }

    /**
     * The same approach as with 'equals' method. We only load reference and position.
     */
    @Override
    public int hashCode() {
        getReferenceIndex();
        getAlignmentStart();

        return super.hashCode();
    }

    /**
     * Performs a deep copy of the SAMRecord and detaches a copy from NGS iterator
     * @return new object
     * @throws CloneNotSupportedException
     */
    @Override
    public Object clone() throws CloneNotSupportedException {
        SRALazyRecord newObject = (SRALazyRecord)super.clone();
        newObject.initializedFields = EnumSet.copyOf(this.initializedFields);
        newObject.initializedFlags = EnumSet.copyOf(this.initializedFlags);
        newObject.initializedAttributes = EnumSet.copyOf(this.initializedAttributes);
        newObject.detachFromIterator();

        return newObject;
    }

    @Override
    public String format() {
        if (!initializedAttributes.contains(LazyAttribute.RG)) {
            getAttribute("RG");
        }
        return super.format();
    }

    @Override
    public List<SAMValidationError> isValid(final boolean firstOnly) {
        loadFields();
        getFlags();
        getBinaryAttributes();

        return super.isValid(firstOnly);
    }

    // =============================== Implementation ========================================

    private ReadCollection getReadCollection() {
        if (run != null) {
            return run;
        }

        log.debug("Recovering SRA read collection. Accession: " + accession);
        try {
            return run = NGS.openReadCollection(accession.toString());
        } catch (ErrorMsg e) {
            throw new RuntimeException(e);
        }
    }

    private Alignment getCurrentAlignment() throws ErrorMsg {
        if (!isAligned) {
            throw new RuntimeException("Should be called for aligned records only");
        }

        if (alignmentIterator == null) {
            log.debug("Recovering SAM record after detaching from iterator. Alignment id: " + sraAlignmentId);
            if (sraAlignmentId == null) {
                throw new RuntimeException("Cannot recover SAM object after detaching from iterator: no alignment id");
            }

            alignmentIterator = getReadCollection().getAlignment(sraAlignmentId);
        }
        return alignmentIterator;
    }

    private Read getCurrentUnalignedRead() throws ErrorMsg {
        if (isAligned) {
            throw new RuntimeException("Should be called for unaligned records only");
        }

        if (unalignmentIterator == null) {
            log.debug("Recovering SAM record after detaching from iterator. Read id: " + sraReadId + ", fragment index: " + unalignedReadFragmentIndex);
            if (sraReadId == null) {
                throw new RuntimeException("Cannot recover SAM object after detaching from iterator: no read id");
            }

            Read read = getReadCollection().getRead(sraReadId);
            for (int i = 0; i < unalignedReadFragmentIndex + 1; i++) {
                read.nextFragment();
            }

            unalignmentIterator = read;
        }
        return unalignmentIterator;
    }

    // ===== fields =====

    private void loadFields() {
        for (LazyField field : LazyField.values()) {
            if (initializedFields.contains(field)) {
                continue;
            }

            field.loadValue(this);
        }
    }

    private int getAlignmentStartImpl() {
        try {
            if (isAligned) {
                return (int) getCurrentAlignment().getAlignmentPosition() + 1;
            }
        } catch (ErrorMsg e) {
            throw new RuntimeException(e);
        }
        return SAMRecord.NO_ALIGNMENT_START;
    }

    private int getMappingQualityImpl() {
        try {
            if (isAligned) {
                return getCurrentAlignment().getMappingQuality();
            }
        } catch (ErrorMsg e) {
            throw new RuntimeException(e);
        }
        return SAMRecord.NO_MAPPING_QUALITY;
    }

    private String getReferenceNameImpl() {
        try {
            if (isAligned) {
                return getCurrentAlignment().getReferenceSpec();
            }
        } catch (ErrorMsg e) {
            throw new RuntimeException(e);
        }
        return SAMRecord.NO_ALIGNMENT_REFERENCE_NAME;
    }

    private String getCigarStringImpl() {
        try {
            if (isAligned) {
                return getCurrentAlignment().getShortCigar(false);
            }
        } catch (ErrorMsg e) {
            throw new RuntimeException(e);
        }
        return SAMRecord.NO_ALIGNMENT_CIGAR;
    }

    private byte[] getReadBasesImpl() {
        try {
            if (isAligned) {
                return getCurrentAlignment().getAlignedFragmentBases().getBytes();
            } else {
                return getCurrentUnalignedRead().getFragmentBases().getBytes();
            }
        } catch (ErrorMsg e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] getBaseQualitiesImpl() {
        try {
            Fragment fragment;
            if (isAligned) {
                fragment = getCurrentAlignment();
            } else {
                fragment = getCurrentUnalignedRead();
            }

            // quals are being taken from PRIMARY_ALIGNMENT.SAM_QUALITY column which reverse automatically them if needed
            return SAMUtils.fastqToPhred(fragment.getFragmentQualities());
        } catch (ErrorMsg e) {
            throw new RuntimeException(e);
        }
    }

    private int getMateAlignmentStartImpl() {
        try {
            if (isAligned && getReadPairedFlag() && !getMateUnmappedFlag()) {
                Alignment mate = getCurrentAlignment().getMateAlignment();
                return (int) mate.getAlignmentPosition() + 1;
            }
        } catch (ErrorMsg e) {
            throw new RuntimeException(e);
        }
        return SAMRecord.NO_ALIGNMENT_START;
    }

    private String getMateReferenceNameImpl() {
        try {
            if (isAligned && getReadPairedFlag() && !getMateUnmappedFlag()) {
                return getCurrentAlignment().getMateReferenceSpec();
            }
        } catch (ErrorMsg e) {
            throw new RuntimeException(e);
        }
        return SAMRecord.NO_ALIGNMENT_REFERENCE_NAME;
    }

    private int getInferredInsertSizeImpl() {
        try {
            if (isAligned) {
                return (int) getCurrentAlignment().getTemplateLength();
            }
        } catch (ErrorMsg e) {
            throw new RuntimeException(e);
        }
        return 0;
    }

    // ===== flags =====

    private boolean getReadNegativeStrandFlagImpl() {
        try {
            if (isAligned) {
                return getCurrentAlignment().getIsReversedOrientation();
            }
        } catch (ErrorMsg e) {
            throw new RuntimeException(e);
        }
        return false;
    }

    private boolean getReadPairedFlagImpl() {
        try {
            if (isAligned) {
                return getCurrentAlignment().isPaired();
            } else {
                return getCurrentUnalignedRead().getNumFragments() > 1;
            }
        } catch (ErrorMsg e) {
            throw new RuntimeException(e);
        }
    }

    private boolean getProperPairFlagImpl() {
        return isAligned && getReadPairedFlag() && !getMateUnmappedFlag();
    }

    private boolean getNotPrimaryAlignmentFlagImpl() {
        try {
            if (isAligned) {
                return getCurrentAlignment().getAlignmentCategory() == Alignment.secondaryAlignment;
            }
        } catch (ErrorMsg e) {
            throw new RuntimeException(e);
        }

        return false;
    }

    private boolean getMateNegativeStrandFlagImpl() {
        try {
            if (isAligned && getReadPairedFlag() && !getMateUnmappedFlag()) {
                Alignment mate = getCurrentAlignment().getMateAlignment();
                return mate.getIsReversedOrientation();
            }
        } catch (ErrorMsg e) {
            throw new RuntimeException(e);
        }

        return false;
    }

    private boolean getMateUnmappedFlagImpl() {
        try {
            if (isAligned) {
                return !getCurrentAlignment().hasMate();
            } else {
                Read unalignedRead = getCurrentUnalignedRead();
                int numFragments = unalignedRead.getNumFragments();
                int nextFragmentIdx = unalignedReadFragmentIndex + 1;
                if (nextFragmentIdx == numFragments) {
                    nextFragmentIdx = 0;
                }

                return unalignedRead.fragmentIsAligned(nextFragmentIdx);
            }
        } catch (ErrorMsg e) {
            throw new RuntimeException(e);
        }
    }

    private boolean getFirstOfPairFlagImpl() {
        if (!getReadPairedFlag()) {
            return false;
        }
        try {
            if (isAligned) {
                String fragmentId = getCurrentAlignment().getFragmentId();
                if (!fragmentId.contains(".FA")) {
                    throw new RuntimeException("Invalid fragment id: " + fragmentId);
                }

                return fragmentId.contains(".FA0.");
            } else {
                return unalignedReadFragmentIndex == 0;
            }
        } catch (ErrorMsg e) {
            throw new RuntimeException(e);
        }
    }

    private boolean getSecondOfPairFlagImpl() {
        if (!getReadPairedFlag()) {
            return false;
        }
        try {
            if (isAligned) {
                String fragmentId = getCurrentAlignment().getFragmentId();
                if (!fragmentId.contains(".FA")) {
                    throw new RuntimeException("Invalid fragment id: " + fragmentId);
                }

                return !fragmentId.contains(".FA0.");
            } else {
                return unalignedReadFragmentIndex != 0;
            }
        } catch (ErrorMsg e) {
            throw new RuntimeException(e);
        }
    }

    // ===== attributes =====

    private String getAttributeGroupNameImpl() {
        try {
            String readGroupName;
            if (isAligned) {
                readGroupName = getCurrentAlignment().getReadGroup();
            } else {
                readGroupName = getCurrentUnalignedRead().getReadGroup();
            }

            if (!readGroupName.isEmpty()) {
                return readGroupName;
            }
            return getReadCollection().getName();
        } catch (ErrorMsg e) {
            throw new RuntimeException(e);
        }
    }
}
