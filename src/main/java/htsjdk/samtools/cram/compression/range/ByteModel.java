package htsjdk.samtools.cram.compression.range;

import com.sun.org.apache.xalan.internal.xsltc.compiler.*;

import java.nio.*;

public class ByteModel {
    // Is this analogous to Arithmetic Decoder in rans

    public int totalFrequency;
    public int maxSymbol;
    public final int[] symbols =new int[Constants.NUMBER_OF_SYMBOLS];
    public final int[] frequencies = new int[Constants.NUMBER_OF_SYMBOLS];

    public ByteModel(final int numSymbols) {
        // Spec: ModelCreate method
        this.totalFrequency = numSymbols;
        this.maxSymbol = numSymbols - 1;
        for (int i = 0; i < maxSymbol; i++) {
            this.symbols[i] = 0;
            this.frequencies[i] = 0;
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