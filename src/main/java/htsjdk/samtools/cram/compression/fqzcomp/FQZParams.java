package htsjdk.samtools.cram.compression.fqzcomp;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class FQZParams {
    private static final int NUMBER_OF_SYMBOLS = 256;

    final FQZGlobalFlags fqzFlags;
    final List<FQZParam> fqzParamList;
    final int maxSelector;
    final int maxSymbol;
    final int[] selectorTable = new int[256];

    public FQZParams(final ByteBuffer inBuffer) {
        fqzFlags = new FQZGlobalFlags(inBuffer);

        final int numParamBlock = fqzFlags.isMultiParam() ? inBuffer.get() : 1;
        int maxSelector = numParamBlock > 1 ?
                numParamBlock :
                0;
        if (fqzFlags.hasSelectorTable()) {
            maxSelector = inBuffer.get() & 0xFF;
            FQZUtils.readArray(inBuffer, selectorTable, 256);
        } else {
            for (int i = 0; i < numParamBlock; i++) {
                selectorTable[i] = i;
            }
            for (int i = numParamBlock; i < 256; i++) {
                selectorTable[i] = numParamBlock - 1;
            }
        }

        int maxSymbol = 0;
        fqzParamList = new ArrayList<>(numParamBlock);
        for (int p = 0; p < numParamBlock; p++) {
            final FQZParam fqzparam = new FQZParam(inBuffer, NUMBER_OF_SYMBOLS);
            fqzParamList.add(p, fqzparam);
            if (maxSymbol < fqzparam.getMaxSymbols()){
                maxSymbol = fqzparam.getMaxSymbols();
            }
        }

        this.maxSelector = maxSelector;
        this.maxSymbol = maxSymbol;
    }

    public FQZGlobalFlags getFQZFlags() {
        return fqzFlags;
    }

    public List<FQZParam> getFQZParamList() {
        return fqzParamList;
    }

    public int getMaxSelector() {
        return maxSelector;
    }

    public int getMaxSymbol() {
        return maxSymbol;
    }

    public int[] getSelectorTable() {
        return selectorTable;
    }

}
