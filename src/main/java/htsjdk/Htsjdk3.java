package htsjdk;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker interface to indicate the status of the class in the HTSJDK3 development.
 *
 * @author Daniel Gomez-Sanchez (magicDGS)
 */
@Retention(RetentionPolicy.CLASS)
@Target({
        ElementType.ANNOTATION_TYPE,
        ElementType.CONSTRUCTOR,
        ElementType.FIELD,
        ElementType.METHOD,
        ElementType.TYPE
})
@Documented
public @interface Htsjdk3 {

    /** Indicates the name of the new package if changed; if not changed, returns the empty String. */
    public String newPackage();

    /** Returns {@code true} if the port is backwards compatible; {@code false} otherwise. */
    public boolean backwardsCompatible();

}
