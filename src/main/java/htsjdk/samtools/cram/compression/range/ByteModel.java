package htsjdk.samtools.cram.compression.range;

import java.nio.ByteBuffer;

public class ByteModel {
    // spec: To encode any symbol the entropy encoder needs to know
    // the frequency of the symbol to encode,
    // the cumulative frequencies of all symbols prior to this symbol,
    // and the total of all frequencies.
    public int totalFrequency;
    public int maxSymbol;
    public final int[] symbols;
    public final int[] frequencies;

    public ByteModel(final int numSymbols) {
        // Spec: ModelCreate method
        this.totalFrequency = numSymbols;
        this.maxSymbol = numSymbols - 1;
        frequencies = new int[maxSymbol+1];
        symbols = new int[maxSymbol+1];
        for (int i = 0; i <= maxSymbol; i++) {
            this.symbols[i] = i;
            this.frequencies[i] = 1;
        }
    }

    public  void modelRenormalize(){
        totalFrequency = 0;
        for (int i=0; i < maxSymbol; i++){
            frequencies[i] -= Math.floorDiv(frequencies[i],2);
            totalFrequency += frequencies[i];
        }
    }

    public void modelEncode(final ByteBuffer outBuffer, RangeCoder rangeCoder, int symbol){

        // find cumulative frequency
        int acc = 0;
        int i;
        for( i = 0; symbols[i] != symbol; i++){
            acc += frequencies[i];
        }

        // Encode
        rangeCoder.rangeEncode(outBuffer, acc, frequencies[i],totalFrequency);

        // Update Model
        frequencies[i] += Constants.STEP;
        totalFrequency += Constants.STEP;
        if (totalFrequency > Constants.MAX_FREQ){
            modelRenormalize(); // How are we ensuring freq of symbol is never 0
        }

        // Keep symbols approximately frequency sorted (ascending order)
        symbol = symbols[i];
        if (i > 0 && frequencies[i] > frequencies[i-1]){
            // swap frequencies
            int tmp = frequencies[i];
            frequencies[i] = frequencies[i-1];
            frequencies[i-1]=tmp;

            // swap symbols
            tmp = symbols[i];
            symbols[i] = symbols[i-1];
            symbols[i-1] = tmp;
        }
    }

}