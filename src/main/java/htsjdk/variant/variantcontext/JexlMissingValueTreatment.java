package htsjdk.variant.variantcontext;

import java.util.function.Supplier;

/**
 * How to treat values that appear in a jexl expression but are missing in the context it's applied to
 */
public enum JexlMissingValueTreatment {
    /**
     * Treat expressions with a missing value as a mismatch and evaluate to false
     */
    TREAT_AS_MISMATCH(() -> false),

    /**
     * Treat expressions with a missing value as a match and evaluate to true
     */
    TREAT_AS_MATCH(() -> true),

    /**
     * Treat expressions with a missing value as an error and throw an {@link IllegalArgumentException}
     */
    THROW(() -> {throw new IllegalArgumentException("Jexl Expression couldn't be evaluated because there was a missing value.");});

    private final Supplier<Boolean> resultSupplier;

    JexlMissingValueTreatment(final Supplier<Boolean> resultSupplier){
        this.resultSupplier = resultSupplier;
    }

    /**
     * get the missing value that corresponds to this option or throw an exception
     * @return the value that should be used in case of a missing value
     * @throws IllegalArgumentException if this should be treated as an error
     */
    boolean getMissingValueOrExplode(){
        return resultSupplier.get();
    }

}
