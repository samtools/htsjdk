package htsjdk.samtools.cram.compression.fqzcomp;

import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.compression.CompressionUtils;
import htsjdk.samtools.cram.compression.range.RangeCoder;
import java.nio.ByteBuffer;

/**
 * Decoder for the CRAM 3.1 FQZComp codec, used for compressing quality scores. Allows the use of previous quality
 * values, position within the read sequence, a sum of the quality differences from the previous quality, and a generic
 * model selector to generate a context model.
 *
 * Uses the range (adaptive arithmetic) codec internally for compression.
 */
public class FQZCompDecode {
    private static final int NUMBER_OF_SYMBOLS = 256;
    private static int SUPPORTED_FQZCOMP_VERSION = 5; // 5 because the spec says so

    /**
     * Decompress a FQZComp-compressed quality score block. Reads the uncompressed size, version,
     * parameters, and then decodes quality symbols using adaptive arithmetic coding with the
     * context model defined by the parameters.
     *
     * @param inBuffer the compressed FQZComp data (consumed by this call)
     * @return a rewound ByteBuffer containing the decompressed quality scores
     */
    public static ByteBuffer uncompress(final ByteBuffer inBuffer) {
        final int outBufferLength = CompressionUtils.readUint7(inBuffer);
        final int version = inBuffer.get() & 0xFF;
        if (version != SUPPORTED_FQZCOMP_VERSION) {
            throw new CRAMException("Invalid FQZComp format version number: " + version);
        }

        final FQZParams fqzParams = new FQZParams(inBuffer);
        final FQZModels fqzModels = new FQZModels(fqzParams);
        final FQZState fqzState = new FQZState();
        final RangeCoder rangeCoder = new RangeCoder();
        rangeCoder.rangeDecodeStart(inBuffer);

        final ByteBuffer outBuffer = CompressionUtils.allocateByteBuffer(outBufferLength);
        FQZParam fqzParam = null;
        for (int i = 0, last = 0; i < outBufferLength; ) {
            fqzState.resizeArrays(fqzState.getReadOrdinal());
            if (fqzState.getBases() == 0) {
                fqzState.setContext(0);
                decodeFQZNewRecord(inBuffer, rangeCoder, fqzModels, fqzParams, fqzState);
                if (fqzState.getIsDuplicate() == true) {
                    for (int j = 0; j < fqzState.getRecordLength(); j++) {
                        outBuffer.put(i + j, outBuffer.get(i + j - fqzState.getRecordLength()));
                    }
                    i += fqzState.getRecordLength();
                    last = 0;
                    fqzState.setBases(0);
                    fqzState.setContext(0);
                    fqzState.setIsDuplicate(false);
                    fqzState.getQualityLengthArray()[fqzState.getReadOrdinal() - 1] = fqzState.getRecordLength();
                    continue;
                }
                fqzState.resizeArrays(fqzState.getReadOrdinal());
                fqzState.getQualityLengthArray()[fqzState.getReadOrdinal() - 1] = fqzState.getRecordLength();
                fqzParam = fqzParams.getFQZParamList().get(fqzState.getSelectorTable());
                last = fqzState.getContext();
            }
            final int quality = fqzModels.getQuality()[last].modelDecode(inBuffer, rangeCoder);
            if (fqzParam.isDoQmap()) {
                outBuffer.put(i++, (byte) fqzParam.getQualityMap()[quality]);
            } else {
                outBuffer.put(i++, (byte) quality);
            }
            last = fqzUpdateContext(fqzParam, fqzState, quality);
            fqzState.setContext(last);
        }
        if (fqzParams.getFQZFlags().doReverse()) {
            reverseQualities(outBuffer, outBufferLength, fqzState);
        }

        outBuffer.rewind();
        return outBuffer;
    }

    /**
     * Decode the header for a new record: selector, length, reverse flag, and duplicate flag.
     * Updates the FQZ state with the decoded record metadata.
     */
    public static void decodeFQZNewRecord(
            final ByteBuffer inBuffer,
            final RangeCoder rangeCoder,
            final FQZModels model,
            final FQZParams fqzParams,
            final FQZState state) {
        // Parameter selector
        if (fqzParams.getMaxSelector() > 0) {
            state.setSelector(model.getSelector().modelDecode(inBuffer, rangeCoder));
        } else {
            state.setSelector(0);
        }
        state.setSelectorTable(fqzParams.getSelectorTable()[state.getSelector()]);
        final FQZParam params = fqzParams.getFQZParamList().get(state.getSelectorTable());

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
        if (fqzParams.getFQZFlags().doReverse()) {
            state.getReverseArray()[state.getReadOrdinal()] =
                    model.getReverse().modelDecode(inBuffer, rangeCoder) == 0 ? false : true;
        }
        state.setIsDuplicate(false);
        if (params.isDoDedup()) {
            if (model.getDuplicate().modelDecode(inBuffer, rangeCoder) != 0) {
                state.setIsDuplicate(true);
            }
        }
        state.setBases(len); // number of remaining bytes in this record
        state.setDelta(0);
        state.setQualityContext(0);
        state.setPreviousQuality(0);
        state.setReadOrdinal(state.getReadOrdinal() + 1);
    }

    /**
     * Update the 16-bit context value after encoding/decoding a quality score. Incorporates
     * quality history, position, delta, and selector into the context based on the parameter
     * configuration. Also decrements the remaining bases counter.
     *
     * @param params the parameter block controlling context bit allocation
     * @param state the mutable encoder/decoder state
     * @param quality the quality value just encoded/decoded
     * @return the new 16-bit context value
     */
    public static int fqzUpdateContext(final FQZParam params, final FQZState state, final int quality) {

        int last = params.getContext();
        state.setQualityContext(((state.getQualityContext() << params.getQualityContextShift())
                        + params.getQualityContextTable()[quality])
                >>> 0);
        last += ((state.getQualityContext() & ((1 << params.getQualityContextBits()) - 1))
                        << params.getQualityContextLocation())
                >>> 0;

        if (params.isDoPos()) {
            last += params.getPositionContextTable()[Math.min(state.getBases(), 1023)]
                    << params.getPositionContextLocation();
        }
        if (params.isDoDelta()) {
            last += params.getDeltaContextTable()[Math.min(state.getDelta(), 255)] << params.getDeltaContextLocation();
            state.setDelta(state.getDelta() + ((state.getPreviousQuality() != quality) ? 1 : 0));
            state.setPreviousQuality(quality);
        }
        if (params.isDoSel()) {
            last += state.getSelector() << params.getSelectorContextLocation();
        }
        state.setBases(state.getBases() - 1);
        return last & 0xffff;
    }

    /**
     * Reverse quality score arrays for records that were flagged for reversal during encoding.
     * Called after all quality scores have been decoded when the global DO_REVERSE flag is set.
     */
    public static void reverseQualities(
            final ByteBuffer outBuffer, final int outBufferLength, final FQZState fqzState) {
        final boolean[] toReverse = fqzState.getReverseArray();
        final int[] qualityLengths = fqzState.getQualityLengthArray();
        final int nRecs = fqzState.getReadOrdinal();

        for (int rec = 0, idx = 0; idx < outBufferLength && rec != nRecs; ) {
            if (toReverse[rec]) {
                int j = 0;
                int k = qualityLengths[rec] - 1;
                while (j < k) {
                    byte tmp = outBuffer.get(idx + j);
                    outBuffer.put(idx + j, outBuffer.get(idx + k));
                    outBuffer.put(idx + k, tmp);
                    j++;
                    k--;
                }
            }
            idx += qualityLengths[rec++];
        }
    }
}
