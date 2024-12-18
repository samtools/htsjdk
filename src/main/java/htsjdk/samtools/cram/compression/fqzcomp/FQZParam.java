package htsjdk.samtools.cram.compression.fqzcomp;

import java.nio.ByteBuffer;

public class FQZParam {
    private static final int DEDUP_FLAG_MASK = 0x02;
    private static final int FIXED_LEN_FLAG_MASK = 0x04;
    private static final int SEL_FLAG_MASK = 0x08;
    private static final int QMAP_FLAG_MASK = 0x10;
    private static final int PTAB_FLAG_MASK = 0x20;
    private static final int DTAB_FLAG_MASK = 0x40;
    private static final int QTAB_FLAG_MASK = 0x80;

    private int context;
    private int maxSymbols; // Total number of distinct quality values
    private int qualityContextBits; // Total number of bits for Quality context
    private int qualityContextShift; // Left bit shift per successive quality in quality context
    private int qualityContextLocation; // Bit position of quality context
    private int selectorContextLocation; // Bit position of selector context
    private int positionContextLocation; // Bit position of position context
    private int deltaContextLocation; // Bit position of delta context
    private int[] qualityMap; // Map for unbinning quality values.
    private int[] qualityContextTable; // Quality context lookup table
    private int[] positionContextTable; // Position context lookup table
    private int[] deltaContextTable; // Delta context lookup table

    // cached parameter flags
    private boolean doDedup;
    private int fixedLen;
    private boolean doSel;
    private boolean doQmap;
    private boolean doPos;
    private boolean doDelta;
    private boolean doQtab;

    public FQZParam(final ByteBuffer inBuffer, final int numberOfSymbols) {
        this.context = (inBuffer.get() & 0xFF) | ((inBuffer.get() & 0xFF) << 8);
        cacheParameterFlags(inBuffer.get() & 0xFF);
        this.maxSymbols = (inBuffer.get() & 0xFF);
        final int x = inBuffer.get() & 0xFF;
        this.qualityContextBits = x >> 4;
        this.qualityContextShift = x & 0x0F;
        final int y = inBuffer.get() & 0xFF;
        this.qualityContextLocation = y >> 4;
        this.selectorContextLocation = y & 0x0F;
        final int z = inBuffer.get() & 0xFF;
        this.positionContextLocation = z >> 4;
        this.deltaContextLocation = z & 0x0F;

        // Read Quality Map. Example: "unbin" Illumina Qualities
        qualityMap = new int[numberOfSymbols];
        if (isDoQmap()) {
            for (int i = 0; i < getMaxSymbols(); i++) {
                qualityMap[i] = inBuffer.get() & 0xFF;
            }
        } else {
            for (int i = 0; i < numberOfSymbols; i++) {
                qualityMap[i] = i;
            }
        }

        // Read tables
        qualityContextTable = new int[1024];
        if (getQualityContextBits() > 0 && isDoQtab()) {
            FQZUtils.readArray(inBuffer, qualityContextTable, numberOfSymbols);
        } else {
            for (int i = 0; i < numberOfSymbols; i++) {
                qualityContextTable[i] = i;  // NOP
            }
        }
        if (isDoPos()) {
            this.positionContextTable = new int[1024];
            FQZUtils.readArray(inBuffer, positionContextTable, 1024);
        }
        if (isDoDelta()) {
            this.deltaContextTable = new int[numberOfSymbols];
            FQZUtils.readArray(inBuffer, deltaContextTable, numberOfSymbols);
        }
    }

    public int getContext() {
        return context;
    }

    public boolean isDoDedup() {
        return doDedup;
    }

    public int getFixedLen() {
        return fixedLen;
    }

    public boolean isDoSel() {
        return doSel;
    }

    public boolean isDoQmap() {
        return doQmap;
    }

    public boolean isDoPos() {
        return doPos;
    }

    public boolean isDoDelta() {
        return doDelta;
    }

    public boolean isDoQtab() {
        return doQtab;
    }

    public int getMaxSymbols() {
        return maxSymbols;
    }

    public int getQualityContextBits() {
        return qualityContextBits;
    }

    public int getQualityContextShift() {
        return qualityContextShift;
    }

    public int getQualityContextLocation() {
        return qualityContextLocation;
    }

    public int getSelectorContextLocation() {
        return selectorContextLocation;
    }

    public int getPositionContextLocation() {
        return positionContextLocation;
    }

    public int getDeltaContextLocation() {
        return deltaContextLocation;
    }

    public int[] getQualityMap() {
        return qualityMap;
    }

    public int[] getQualityContextTable() {
        return qualityContextTable;
    }

    public int[] getPositionContextTable() {
        return positionContextTable;
    }

    public int[] getDeltaContextTable() {
        return deltaContextTable;
    }

    public void setFixedLen(int fixedLen) {
        this.fixedLen = fixedLen;
    }

    private void cacheParameterFlags(int parameterFlags) {
        this.doDedup = (parameterFlags & DEDUP_FLAG_MASK) != 0;
        setFixedLen(parameterFlags & FIXED_LEN_FLAG_MASK);  //TODO: f'd up - is this a flag or an int ?
        this.doSel = (parameterFlags & SEL_FLAG_MASK) != 0;
        this.doQmap = (parameterFlags & QMAP_FLAG_MASK) != 0;
        this.doPos = (parameterFlags & PTAB_FLAG_MASK) != 0;
        this.doDelta = (parameterFlags & DTAB_FLAG_MASK) != 0;
        this.doQtab = (parameterFlags & QTAB_FLAG_MASK) != 0;
    }

}