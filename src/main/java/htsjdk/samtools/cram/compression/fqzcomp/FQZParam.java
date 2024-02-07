package htsjdk.samtools.cram.compression.fqzcomp;

public class FQZParam {
    private int context;
    private int parameterFlags; // Per-parameter block bit-flags
    // TODO: rename - follow names from spec. These flags should be set using parameterFlags value
    private boolean doDedup;
    private int fixedLen;
    private boolean doSel;
    private boolean doQmap;
    private boolean doPos;
    private boolean doDelta;
    private boolean doQtab;

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

    private static final int DEDUP_FLAG_MASK = 0x02;
    private static final int FIXED_LEN_FLAG_MASK = 0x04;
    private static final int SEL_FLAG_MASK = 0x08;
    private static final int QMAP_FLAG_MASK = 0x10;
    private static final int PTAB_FLAG_MASK = 0x20;
    private static final int DTAB_FLAG_MASK = 0x40;
    private static final int QTAB_FLAG_MASK = 0x80;

    public FQZParam() {
    }

    public int getContext() {
        return context;
    }

    public int getParameterFlags() {
        return parameterFlags;
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

    public void setContext(int context) {
        this.context = context;
    }

    public void setParameterFlags(int parameterFlags) {
        this.parameterFlags = parameterFlags;
        setDoDedup((parameterFlags & DEDUP_FLAG_MASK) != 0);
        setFixedLen(parameterFlags & FIXED_LEN_FLAG_MASK);
        setDoSel((parameterFlags & SEL_FLAG_MASK) != 0);
        setDoQmap((parameterFlags & QMAP_FLAG_MASK) != 0);
        setDoPos((parameterFlags & PTAB_FLAG_MASK) != 0);
        setDoDelta((parameterFlags & DTAB_FLAG_MASK) != 0);
        setDoQtab((parameterFlags & QTAB_FLAG_MASK) != 0);
    }

    public void setDoDedup(boolean doDedup) {
        this.doDedup = doDedup;
    }

    public void setFixedLen(int fixedLen) {
        this.fixedLen = fixedLen;
    }

    public void setDoSel(boolean doSel) {
        this.doSel = doSel;
    }

    public void setDoQmap(boolean doQmap) {
        this.doQmap = doQmap;
    }

    public void setDoPos(boolean doPos) {
        this.doPos = doPos;
    }

    public void setDoDelta(boolean doDelta) {
        this.doDelta = doDelta;
    }

    public void setDoQtab(boolean doQtab) {
        this.doQtab = doQtab;
    }

    public void setMaxSymbols(int maxSymbols) {
        this.maxSymbols = maxSymbols;
    }

    public void setQualityContextBits(int qualityContextBits) {
        this.qualityContextBits = qualityContextBits;
    }

    public void setQualityContextShift(int qualityContextShift) {
        this.qualityContextShift = qualityContextShift;
    }

    public void setQualityContextLocation(int qualityContextLocation) {
        this.qualityContextLocation = qualityContextLocation;
    }

    public void setSelectorContextLocation(int selectorContextLocation) {
        this.selectorContextLocation = selectorContextLocation;
    }

    public void setPositionContextLocation(int positionContextLocation) {
        this.positionContextLocation = positionContextLocation;
    }

    public void setDeltaContextLocation(int deltaContextLocation) {
        this.deltaContextLocation = deltaContextLocation;
    }

    public void setQualityMap(int[] qualityMap) {
        this.qualityMap = qualityMap;
    }

    public void setQualityContextTable(int[] qualityContextTable) {
        this.qualityContextTable = qualityContextTable;
    }

    public void setPositionContextTable(int[] positionContextTable) {
        this.positionContextTable = positionContextTable;
    }

    public void setDeltaContextTable(int[] deltaContextTable) {
        this.deltaContextTable = deltaContextTable;
    }

}