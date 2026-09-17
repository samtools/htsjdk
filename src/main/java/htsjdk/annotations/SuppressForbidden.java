package htsjdk.annotations;

import static java.lang.annotation.ElementType.*;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Exempts a class, method, constructor or field from the forbidden-API check (the {@code forbiddenApisMain}
 * Gradle task, whose banned signatures are listed in {@code gradle/forbidden-apis.txt}).
 *
 * <p>Use it only where a banned API is genuinely required, and say why in {@link #reason()}. Prefer the
 * narrowest element that covers the use.
 *
 * <p>Retention is {@code CLASS} because the check reads compiled class files; nothing needs the
 * annotation at runtime.
 */
@Target({CONSTRUCTOR, FIELD, METHOD, TYPE})
@Retention(RetentionPolicy.CLASS)
public @interface SuppressForbidden {
    /** Why the banned API is needed here. */
    String reason();
}
