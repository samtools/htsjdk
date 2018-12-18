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

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The block content types specified by Section 8.1 of the CRAM spec
 */
public enum BlockContentType {
    FILE_HEADER(0),
    COMPRESSION_HEADER(1),
    MAPPED_SLICE(2),
    RESERVED(3),
    EXTERNAL(4),
    CORE(5);

    private final int contentTypeId;

    /**
     * The block content types specified by Section 8.1 of the CRAM spec
     * @param id the ID assigned to each content type in the CRAM spec
     */
    BlockContentType(final int id) {
        contentTypeId = id;
    }

    /**
     * @return the ID assigned to each content type in the CRAM spec
     */
    public int getContentTypeId() {
        return contentTypeId;
    }

    /**
     * Return the BlockContentType specified by the ID
     *
     * @param id the number assigned to each block content type in the CRAM spec
     * @return the BlockContentType associated with the ID
     */
    public static BlockContentType byId(final int id) {
        return Optional.ofNullable(ID_MAP.get(id))
                .orElseThrow(() -> new CRAMException("Could not find BlockContentType for: " + id));
    }

    private static final Map<Integer, BlockContentType> ID_MAP =
            Collections.unmodifiableMap(Stream.of(BlockContentType.values())
                    .collect(Collectors.toMap(BlockContentType::getContentTypeId, Function.identity())));
}
