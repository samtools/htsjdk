package htsjdk.variant.variantcontext;

import java.util.HashMap;

/**
 * Hybrid dynamic cache for genotype likelihood counts
 */

public class GenotypeNumLikelihoodsCache {

    private final static int N_ALLELES = 5;
    private final static int PLOIDY = 10;

    private final int[][] staticCache = new int[5][10];
    private final HashMap<Integer, HashMap<Integer, Integer>> dynamicCache = new HashMap<>();

    public void put(final int numAlleles, final int ploidy, final int numLikelihoods) {
        if(numAlleles < N_ALLELES && ploidy < PLOIDY) {
            staticCache[numAlleles][ploidy] = numLikelihoods;
        }
        else{
            final HashMap<Integer, Integer> cache = dynamicCache.containsKey(ploidy) ? dynamicCache.get(ploidy) : new HashMap<>();
            if(cache.isEmpty()){
                dynamicCache.put(ploidy, cache);
            }
            cache.put(numAlleles, numLikelihoods);
        }
    }

    public Integer get(final int numAlleles, final int ploidy) {
        if(numAlleles >= 0 && numAlleles < N_ALLELES && ploidy >= 0 && ploidy < PLOIDY && staticCache[numAlleles][ploidy] != 0){
            return staticCache[numAlleles][ploidy];
        }
        else{
            return dynamicCache.containsKey(ploidy) ? dynamicCache.get(ploidy).get(numAlleles) : null;
        }
    }

    public boolean containsKey(final int numAlleles, final int ploidy) {
        return (numAlleles < N_ALLELES && ploidy < PLOIDY && staticCache[numAlleles][ploidy] > 0)
                || (dynamicCache.containsKey(ploidy) && dynamicCache.get(ploidy).containsKey(numAlleles));
    }
}
