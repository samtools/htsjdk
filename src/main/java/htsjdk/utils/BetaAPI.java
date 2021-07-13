package htsjdk.utils;

import java.lang.annotation.Documented;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;

/**
 * Annotation indicating that a package, class, method, or type is release level "BETA", and is not part
 * of the public API. BETA APIs are published for evaluation, and may be changed or removed without a
 * deprecation warning.
 */
@Target({CONSTRUCTOR, FIELD, LOCAL_VARIABLE, METHOD, PACKAGE, PARAMETER, TYPE})
@Retention(RetentionPolicy.SOURCE)
@Inherited
@Documented
public @interface BetaAPI {
}
