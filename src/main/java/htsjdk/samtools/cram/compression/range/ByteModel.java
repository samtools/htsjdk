package htsjdk.samtools.cram.compression.range;

import java.nio.ByteBuffer;

public class ByteModel {
    // spec: To encode any symbol the entropy encoder needs to know
    // the frequency of the symbol to encode,
    // the cumulative frequencies of all symbols prior to this symbol,
    // and the total of all frequencies.
    public int totalFrequency;
    public final int maxSymbol;
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

    public int modelDecode(final ByteBuffer inBuffer, final RangeCoder rangeCoder){

        // decodes one symbol
        final int freq = rangeCoder.rangeGetFrequency(totalFrequency);
        int cumulativeFrequency = 0;
        int x = 0;
        while (cumulativeFrequency + frequencies[x] <= freq){
            cumulativeFrequency += frequencies[x++];
        }

        // update rangecoder
        rangeCoder.rangeDecode(inBuffer,cumulativeFrequency,frequencies[x]);

        // update model frequencies
        frequencies[x] += Constants.STEP;
        totalFrequency += Constants.STEP;
        if (totalFrequency > Constants.MAX_FREQ){
            // if totalFrequency is too high, the frequencies are halved, making
            // sure to avoid any zero frequencies being created.
            modelRenormalize();
        }

        // keep symbols approximately frequency sorted
        final int symbol = symbols[x];
        if (x > 0 && frequencies[x] > frequencies[x-1]){
            // Swap frequencies[x], frequencies[x-1]
            int tmp = frequencies[x];
            frequencies[x] = frequencies[x-1];
            frequencies[x-1] = tmp;

            // Swap symbols[x], symbols[x-1]
            tmp = symbols[x];
            symbols[x] = symbols[x-1];
            symbols[x-1] = tmp;
        }
        return symbol;
    }

    public  void modelRenormalize(){
        // frequencies are halved
        totalFrequency = 0;
        for (int i=0; i <= maxSymbol; i++){
            frequencies[i] -= Math.floorDiv(frequencies[i],2);
            totalFrequency += frequencies[i];
        }
    }

    public void modelEncode(final ByteBuffer outBuffer, final RangeCoder rangeCoder, final int symbol){

        // encodes one input symbol
        int cumulativeFrequency = 0;
        int i;
        for( i = 0; symbols[i] != symbol; i++){
            cumulativeFrequency += frequencies[i];
        }

        // Encode
        rangeCoder.rangeEncode(outBuffer, cumulativeFrequency, frequencies[i],totalFrequency);

        // Update Model
        frequencies[i] += Constants.STEP;
        totalFrequency += Constants.STEP;
        if (totalFrequency > Constants.MAX_FREQ){
            modelRenormalize();
        }

        // Keep symbols approximately frequency sorted (ascending order)
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