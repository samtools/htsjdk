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

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The block compression methods specified by Section 8 of the CRAM spec.
 */
public enum BlockCompressionMethod {
    RAW(0),
    GZIP(1),
    BZIP2(2),
    LZMA(3),
    RANS(4),
    RANGE(5);

    private final int methodId;

    /**
     * The block compression methods specified by Section 8 of the CRAM spec
     * @param id the number assigned to each block compression method in the CRAM spec
     */
    BlockCompressionMethod(final int id) {
        methodId = id;
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