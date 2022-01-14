package htsjdk.samtools.cram.compression.fqzcomp;

import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.compression.CompressionUtils;
import htsjdk.samtools.cram.compression.range.ByteModel;
import htsjdk.samtools.cram.compression.range.RangeCoder;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class FQZCompDecode {
    private static final int NUMBER_OF_SYMBOLS = 256;

    public static ByteBuffer uncompress( final ByteBuffer inBuffer) {
        final int bufferLength = CompressionUtils.readUint7(inBuffer);
        final int version = inBuffer.get() & 0xFF;
        if (version != 5) {
            throw new CRAMException("Invalid FQZComp format version number: " + version);
        }
        final FQZGlobalFlags globalFlags = new FQZGlobalFlags(inBuffer.get() & 0xFF);
        final int numParamBlock = globalFlags.isMultiParam()?inBuffer.get() : 1;
        int maxSelector = (numParamBlock > 1) ? (numParamBlock - 1) : 0;
        final int[] selectorTable = new int[NUMBER_OF_SYMBOLS];
        if (globalFlags.hasSelectorTable()) {
            maxSelector = inBuffer.get() & 0xFF;
            readArray(inBuffer, selectorTable, NUMBER_OF_SYMBOLS);
        } else {
            for (int i = 0; i < numParamBlock; i++) {
                selectorTable[i] = i;
            }
            for (int i = numParamBlock; i < NUMBER_OF_SYMBOLS; i++) {
                selectorTable[i] = numParamBlock - 1;
            }
        }
        final List<FQZParam> fqzParamList = new ArrayList<FQZParam>(numParamBlock);
        int maxSymbols = 0; // maximum number of distinct Quality values across all param sets
        for (int p=0; p < numParamBlock; p++){
            fqzParamList.add(p,decodeFQZSingleParam(inBuffer));
            if(maxSymbols < fqzParamList.get(p).getMaxSymbols()){
                maxSymbols = fqzParamList.get(p).getMaxSymbols();
            }
        }

        // main decode loop
        int i = 0;
        final FQZState fqzState = new FQZState();
        final RangeCoder rangeCoder = new RangeCoder();
        rangeCoder.rangeDecodeStart(inBuffer);
        final FQZModel model = fqzCreateModels(maxSymbols, maxSelector);
        final List<Integer> QualityLengths = new ArrayList<>();
        FQZParam params = null;
        int last = 0;
        final int[] rev = null;
        final ByteBuffer outBuffer = CompressionUtils.allocateByteBuffer(bufferLength);
        while (i<bufferLength){
            if (fqzState.getBases()==0) {
                decodeFQZNewRecord(
                        inBuffer,
                        rangeCoder,
                        model,
                        fqzState,
                        maxSelector,
                        globalFlags.doReverse(),
                        selectorTable,
                        fqzParamList,
                        rev);
                if (fqzState.getIsDuplicate() == true) {
                    if (model.getDuplicate().modelDecode(inBuffer, rangeCoder) == 0) {
                        for (int x = 0; x < fqzState.getRecordLength(); x++) {
                            outBuffer.put(i + x, outBuffer.get(i + x - fqzState.getRecordLength()));
                        }
                        i += fqzState.getRecordLength();
                        fqzState.setBases(0);
                    }
                }
                QualityLengths.add(fqzState.getRecordLength());
                params = fqzParamList.get(fqzState.getSelectorTable());
                last = params.getContext();
            }
            final int quality = model.getQuality()[last].modelDecode(inBuffer, rangeCoder);
            outBuffer.put(i++, (byte) params.getQualityMap()[quality]);
            last = fqzUpdateContext(params, fqzState, quality);
        }
        if (globalFlags.doReverse()){
            reverseQualities(outBuffer,bufferLength,rev,QualityLengths);
        }
        int outBufferIndex = 0;
        for (int recordLength:QualityLengths) {
            for (int recordIndex = 0; recordIndex < recordLength; recordIndex++) {
                outBuffer.put(outBufferIndex, (byte)((outBuffer.get(outBufferIndex)& 0xFF) + 33)); // Shift character codes by 33
                outBufferIndex += 1;
            }
        }
        outBuffer.rewind();
        return outBuffer;
    }

    public static void readArray(final ByteBuffer inBuffer, final int[] table, final int size) {
        int j = 0; // array value
        int z = 0; // array index: table[j]
        int last = -1;

        // Remove first level of run-length encoding
        final int[] rle = new int[1024]; // runs
        while (z < size) {
            final int run = inBuffer.get() & 0xFF;
            rle[j++] = run;
            z += run;

            if (run == last) {
                int copy = inBuffer.get() & 0xFF;
                z += run * copy;
                while (copy-- > 0)
                    rle[j++] = run;
            }
            last = run;
        }

        // Now expand runs in rle to table, noting 255 is max run
        int i = 0;
        j = 0;
        z = 0;
        int part;
        while (z < size) {
            int run_len = 0;
            do {
                part = rle[j++];
                run_len += part;
            } while (part == 255);

            while (run_len-- > 0)
                table[z++] = i;
            i++;
        }
    }

    public static FQZModel fqzCreateModels(final int maxSymbols, final int maxSelector){
        final FQZModel fqzModel = new FQZModel();
        fqzModel.setQuality(new ByteModel[1 << 16]);
        for (int i = 0; i < (1 << 16); i++) {
            fqzModel.getQuality()[i] = new ByteModel(maxSymbols + 1); // +1 as max value not num. values
        }
        fqzModel.setLength(new ByteModel[4]);
        for (int i = 0; i < 4; i++) {
            fqzModel.getLength()[i] = new ByteModel(NUMBER_OF_SYMBOLS);
        }
        fqzModel.setReverse(new ByteModel(2));
        fqzModel.setDuplicate(new ByteModel(2));
        if (maxSelector > 0) {
            fqzModel.setSelector(new ByteModel(maxSelector + 1));
        }
        return fqzModel;
    }

    // If duplicate returns 1, else 0
    public static void decodeFQZNewRecord(
            final ByteBuffer inBuffer,
            final RangeCoder rangeCoder,
            final FQZModel model,
            final FQZState state,
            final int maxSelector,
            final boolean doReverse,
            final int[] selectorTable,
            final List<FQZParam> fqzParamList,
            final int[] rev){

        // Parameter selector
        if (maxSelector > 0) {
            state.setSelector(model.getSelector().modelDecode(inBuffer, rangeCoder));
        } else {
            state.setSelector(0);
        }
        state.setSelectorTable(selectorTable[state.getSelector()]);
        final FQZParam params = fqzParamList.get(state.getSelectorTable());

        // Reset contexts at the start of each new record
        int len;
        if (params.getFixedLen() >= 0) {
            // Not fixed or fixed but first record
            len = model.getLength()[0].modelDecode(inBuffer, rangeCoder);
            len |= model.getLength()[1].modelDecode(inBuffer, rangeCoder) << 8;
            len |= model.getLength()[2].modelDecode(inBuffer, rangeCoder) << 16;
            len |= model.getLength()[3].modelDecode(inBuffer, rangeCoder) << 24;
            if (params.getFixedLen() > 0) {
                params.setFixedLen(-len);
            }
        } else {
            len = -params.getFixedLen();
        }
        state.setRecordLength(len);
        if (doReverse) {
            rev[state.getRecordNumber()] = model.getReverse().modelDecode(inBuffer, rangeCoder);
        }
        state.setIsDuplicate(false);
        if (params.isDoDedup()) {
            if (model.getDuplicate().modelDecode(inBuffer, rangeCoder) != 0) {
                state.setIsDuplicate(true);
            }
        }
        state.setBases(len);  // number of remaining bytes in this record
        state.setDelta(0);
        state.setQualityContext(0);
        state.setPreviousQuality(0);
        state.setRecordNumber(state.getRecordNumber() + 1);
    }

    public static int fqzUpdateContext(final FQZParam params,
                                       final FQZState state,
                                       final int quality){

        int last = params.getContext();
        state.setQualityContext(((state.getQualityContext() << params.getQualityContextShift()) + params.getQualityContextTable()[quality]) >>> 0);
        last += ((state.getQualityContext() & ((1 << params.getQualityContextBits()) - 1)) << params.getQualityContextLocation()) >>> 0;

        if (params.isDoPos())
            last += params.getPositionContextTable()[Math.min(state.getBases(), 1023)] << params.getPositionContextLocation();

        if (params.isDoDelta()) {
            last += params.getDeltaContextTable()[Math.min(state.getDelta(), 255)] << params.getDeltaContextLocation();
            state.setDelta(state.getDelta()+ ((state.getPreviousQuality() != quality) ? 1 : 0));
            state.setPreviousQuality(quality);
        }
        if (params.isDoSel())
            last += state.getSelector() << params.getSelectorContextLocation();
        state.setBases(state.getBases()-1);
        return last & 0xffff;
    }

    public static FQZParam decodeFQZSingleParam(ByteBuffer inBuffer) {
        final FQZParam param = new FQZParam();
        param.setContext((inBuffer.get() & 0xFF) | ((inBuffer.get() & 0xFF) << 8));
        param.setParameterFlags(inBuffer.get() & 0xFF);
        param.setMaxSymbols(inBuffer.get() & 0xFF);
        final int x = inBuffer.get() & 0xFF;
        param.setQualityContextBits(x >> 4);
        param.setQualityContextShift(x & 0x0F);
        final int y = inBuffer.get() & 0xFF;
        param.setQualityContextLocation(y >> 4);
        param.setSelectorContextLocation(y & 0x0F);
        final int z = inBuffer.get() & 0xFF;
        param.setPositionContextLocation(z >> 4);
        param.setDeltaContextLocation(z & 0x0F);

        // Read Quality Map. Example: "unbin" Illumina Qualities
        param.setQualityMap(new int[NUMBER_OF_SYMBOLS]);
        if (param.isDoQmap()) {
            for (int i = 0; i < param.getMaxSymbols(); i++) {
                param.getQualityMap()[i] = inBuffer.get() & 0xFF;
            }
        } else {
            for (int i = 0; i < NUMBER_OF_SYMBOLS; i++) {
                param.getQualityMap()[i] = i;
            }
        }

        // Read tables
        param.setQualityContextTable(new int[1024]);
        if (param.getQualityContextBits() > 0 && param.isDoQtab()) {
            readArray(inBuffer, param.getQualityContextTable(), NUMBER_OF_SYMBOLS);
        } else {
            for (int i = 0; i < NUMBER_OF_SYMBOLS; i++) {
                param.getQualityContextTable()[i] = i;  // NOP
            }
        }
        param.setPositionContextTable(new int[1024]);
        if (param.isDoPos()) {
            readArray(inBuffer, param.getPositionContextTable(), 1024);
        }
        param.setDeltaContextTable(new int[NUMBER_OF_SYMBOLS]);
        if (param.isDoDelta()) {
            readArray(inBuffer, param.getDeltaContextTable(), NUMBER_OF_SYMBOLS);
        }
        return param;
    }

    public static void reverseQualities(
            final ByteBuffer outBuffer,
            final int bufferLength,
            final int[] rev,
            final List<Integer> QualityLengths
            ){
        int rec = 0;
        int idx = 0;
        while (idx< bufferLength) {
            if (rev[rec]==1) {
                int j = 0;
                int k = QualityLengths.get(rec) - 1;
                while (j < k) {
                    byte tmp = outBuffer.get(idx + j);
                    outBuffer.put(idx + j,outBuffer.get(idx + k));
                    outBuffer.put(idx + k, tmp);
                    j++;
                    k--;
                }
            }
            idx += QualityLengths.get(rec++);
        }
    }

}