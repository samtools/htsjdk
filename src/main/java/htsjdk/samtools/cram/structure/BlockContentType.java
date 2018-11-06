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
package htsjdk.samtools.cram.structure;

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

    public int getContentTypeId() {
        return contentTypeId;
    }
}
