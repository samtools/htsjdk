package htsjdk.utils;

import java.lang.annotation.Documented;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;

/**
 * Annotation indicating that a package, class, method, or type is release level "private", even if the
 * access modifier is "public". {@link PrivateAPI} types are intended to be for internal use only, and
 * should not be used by external consumers. Classes and methods with this annotation are subject to
 * modification or removal in future releases without a deprecation warning.
 */
@Target({CONSTRUCTOR, FIELD, LOCAL_VARIABLE, METHOD, PACKAGE, PARAMETER, TYPE})
@Retention(RetentionPolicy.SOURCE)
@Inherited
@Documented
public @interface PrivateAPI {
}
