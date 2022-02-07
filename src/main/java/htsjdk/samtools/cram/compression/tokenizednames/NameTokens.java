package htsjdk.samtools.cram.compression.tokenizednames;

import htsjdk.samtools.cram.CRAMException;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum NameTokens {
    TYPE(0),
    STRING(1),
    CHAR(2),
    DIGITS0(3),
    DZLEN(4),
    DUP(5),
    DIFF(6),
    DIGITS(7),
    DELTA(8),
    DELTA0(9),
    MATCH(10),
    NOP(11),
    END(12);

    private final int typeId;

    NameTokens(final int id) {
        typeId = id;
    }

    /**
     * @return the type id for this token type
     */
    public int getNameTokenId() {
        return typeId;
    }

    private static final Map<Integer, NameTokens> ID_MAP =
            Collections.unmodifiableMap(Stream.of(NameTokens.values())
                    .collect(Collectors.toMap(NameTokens::getNameTokenId, Function.identity())));
    /**
     * Return the NAME_TOKEN specified by the ID
     *
     * @param id the id of the requested token
     * @return the NAME_TOKEN associated with the ID
     */
    public static NameTokens byId(final int id) {
        return Optional.ofNullable(ID_MAP.get(id))
                .orElseThrow(() -> new CRAMException("Could not find NAME_TOKEN for: " + id));
    }
}