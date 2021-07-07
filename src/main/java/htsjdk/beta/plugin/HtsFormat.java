package htsjdk.beta.plugin;

import java.util.Optional;

/**
 * Base interface implemented for all file format enums used with {@link HtsCodec}, {@link HtsEncoder}
 * and {@link HtsDecoder}.
 *
 * @param <F> a file format enum for use with used with {@link HtsCodec}, {@link HtsEncoder}
 * and {@link HtsDecoder}.
 */
public interface HtsFormat<F extends Enum<F>> {
    /**
     * A method that accepts a format string and returns the matching instance of the file format enum
     * from {@link F}
     *
     * @param formatString the format String to be converted
     * @return an instance of the file format enum {@link F} that corresponds to {@code format}, or
     * Optional.empty if there is no match
     */
    Optional<F> formatStringToEnum(final String formatString);
}
