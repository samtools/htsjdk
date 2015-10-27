package htsjdk.samtools.cram.digest;

class IntegerSumCombine implements Combine<Integer> {

    @Override
    public Integer combine(final Integer state, final Integer update) {
        return state + update;
    }

}
