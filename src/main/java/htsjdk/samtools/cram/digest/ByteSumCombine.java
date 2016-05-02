package htsjdk.samtools.cram.digest;

class ByteSumCombine implements Combine<byte[]> {

    @Override
    public byte[] combine(final byte[] state, final byte[] update) {
        for (int i = 0; i < state.length; i++)
            state[i] += update[i];
        return state;
    }

}
