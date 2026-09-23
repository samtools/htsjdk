/**
 * ****************************************************************************
 * Copyright 2013 EMBL-EBI
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ****************************************************************************
 */
package htsjdk.samtools.cram.structure.block;

import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.common.CRAMVersion;
import htsjdk.samtools.cram.common.CramVersions;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The block compression methods specified by Section 8 of the CRAM spec.
 */
public enum BlockCompressionMethod {
    RAW(0, CramVersions.CRAM_v2_1),
    GZIP(1, CramVersions.CRAM_v2_1),
    BZIP2(2, CramVersions.CRAM_v2_1),
    LZMA(3, CramVersions.CRAM_v2_1),
    RANS(4, CramVersions.CRAM_v3), // rAns 4x8
    RANSNx16(5, CramVersions.CRAM_v3_1),
    ADAPTIVE_ARITHMETIC(6, CramVersions.CRAM_v3_1),
    FQZCOMP(7, CramVersions.CRAM_v3_1),
    NAME_TOKENISER(8, CramVersions.CRAM_v3_1);

    private final int methodId;
    private final CRAMVersion minimumCramVersion;

    /**
     * The block compression methods specified by Section 8 of the CRAM spec
     * @param id the number assigned to each block compression method in the CRAM spec
     * @param minimumCramVersion the earliest CRAM version (of those htsjdk supports) that specifies the method
     */
    BlockCompressionMethod(final int id, final CRAMVersion minimumCramVersion) {
        methodId = id;
        this.minimumCramVersion = minimumCramVersion;
    }

    /**
     * @return the earliest CRAM version (of those htsjdk supports) whose specification includes this method; a file
     * of an earlier version cannot contain it
     */
    public CRAMVersion getMinimumCramVersion() {
        return minimumCramVersion;
    }

    /**
     * @param cramVersion a CRAM version
     * @return true if a file of the given CRAM version may contain blocks compressed with this method
     */
    public boolean isAvailableIn(final CRAMVersion cramVersion) {
        return cramVersion.compatibleWith(minimumCramVersion);
    }

    /**
     * @return the number assigned to each block compression method in the CRAM spec
     */
    public int getMethodId() {
        return methodId;
    }

    /**
     * Return the BlockCompressionMethod specified by the ID
     *
     * @param id the number assigned to each block compression method in the CRAM spec
     * @return the BlockCompressionMethod associated with the ID
     */
    public static BlockCompressionMethod byId(final int id) {
        return Optional.ofNullable(ID_MAP.get(id))
                .orElseThrow(() -> new CRAMException("Could not find BlockCompressionMethod for: " + id));
    }

    private static final Map<Integer, BlockCompressionMethod> ID_MAP =
            Collections.unmodifiableMap(Stream.of(BlockCompressionMethod.values())
                    .collect(Collectors.toMap(BlockCompressionMethod::getMethodId, Function.identity())));
}
