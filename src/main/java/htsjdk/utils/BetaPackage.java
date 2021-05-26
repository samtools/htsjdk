package htsjdk.utils;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation indicating that an entire package is release level "BETA", is not yet part of the published API,
 * and is subject to change.
 */
@Target(ElementType.PACKAGE)
@Retention(RetentionPolicy.SOURCE)
@Inherited
@Documented
public @interface BetaPackage {
}
