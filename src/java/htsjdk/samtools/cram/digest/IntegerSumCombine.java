package htsjdk.samtools.cram.digest;

class IntegerSumCombine implements Combine<Integer> {

    @Override
    public Integer combine(Integer state, Integer update) {
        return state + update;
    }

}
