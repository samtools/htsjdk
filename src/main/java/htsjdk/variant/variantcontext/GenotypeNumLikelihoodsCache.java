package htsjdk.variant.variantcontext;

import java.util.HashMap;

/**
 * Hybrid dynamic cache for genotype likelihood counts
 */

class GenotypeNumLikelihoodsCache {

    private final static int DEFAULT_N_ALLELES = 5;
    private final static int DEFAULT_PLOIDY = 10;

    private final int[][] staticCache;
    private final HashMap<CacheKey, Integer> dynamicCache = new HashMap<>();

    /**
     * Initializes cache with default values, {@value #DEFAULT_N_ALLELES} alleles and {@value #DEFAULT_PLOIDY} ploidy
     */
    GenotypeNumLikelihoodsCache(){
        this(DEFAULT_N_ALLELES, DEFAULT_PLOIDY);
    }

    GenotypeNumLikelihoodsCache(int numAlleles, int ploidy){
        staticCache = new int[numAlleles][ploidy];

        fillCache();
    }

    private void fillCache(){
        for ( int numAlleles = 0; numAlleles < staticCache.length; numAlleles++ ) {
            for ( int ploidy = 0; ploidy < staticCache[numAlleles].length; ploidy++ ) {
                staticCache[numAlleles][ploidy] = GenotypeLikelihoods.calcNumLikelihoods(numAlleles+1, ploidy+1);
            }
        }
    }

    private synchronized void put(final int numAlleles, final int ploidy, final int numLikelihoods) {
        dynamicCache.put(new CacheKey(numAlleles, ploidy), numLikelihoods);
    }

    /**
     * Returns the number of likelihoods for the specified allele count and ploidy
     * Values not present yet in the cache will be calculated and cached when get is called
     * @param numAlleles
     * @param ploidy
     * @return number of likelihoods
     */
    synchronized int get(final int numAlleles, final int ploidy) {
        if(numAlleles <= 0 || ploidy <= 0){
            throw new IllegalArgumentException("numAlleles and ploidy must both exceed 0, but they are numAlleles: " + numAlleles + ", ploidy: " + ploidy);
        }
        if(numAlleles < staticCache.length && ploidy < staticCache[numAlleles].length){
            return staticCache[numAlleles-1][ploidy-1];
        }
        else{
            final Integer cachedValue = dynamicCache.get(new CacheKey(numAlleles, ploidy));
            if(cachedValue == null){
                final int newValue = GenotypeLikelihoods.calcNumLikelihoods(numAlleles, ploidy);
                put(numAlleles, ploidy, newValue);
                return newValue;
            }
            else{
                return cachedValue;
            }
        }
    }

    /**
     * Key for the hash map, made up of numAlleles and ploidy
     */
    private class CacheKey{
        private final int numAlleles;
        private final int ploidy;

        CacheKey(final int numAlleles, final int ploidy){
            this.numAlleles = numAlleles;
            this.ploidy = ploidy;
        }

        @Override
        public boolean equals(Object object){
            if(object != null && object instanceof CacheKey){
                final CacheKey c = (CacheKey)object;
                return this.numAlleles == c.numAlleles && this.ploidy == c.ploidy;
            }
            return false;
        }

        @Override
        public int hashCode(){
            return numAlleles * 31 + ploidy;
        }
    }
}
