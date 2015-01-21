/*******************************************************************************
 * Copyright 2013 EMBL-EBI
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package htsjdk.samtools.cram.structure;

import java.io.IOException;
import java.io.InputStream;

public class CompressionHeaderBLock extends Block {
    private CompressionHeader compressionHeader;

    public CompressionHeader getCompressionHeader() {
        return compressionHeader;
    }

    public CompressionHeaderBLock(CompressionHeader header) {
        super();
        this.compressionHeader = header;
        contentType = BlockContentType.COMPRESSION_HEADER;
        contentId = 0;
        method = BlockCompressionMethod.RAW;
        byte[] bytes;
        try {
            bytes = header.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("This should have never happend.");
        }
        setRawContent(bytes);
    }

    public CompressionHeaderBLock(InputStream is) throws IOException {
        super(is, true, true);

        if (contentType != BlockContentType.COMPRESSION_HEADER)
            throw new RuntimeException("Content type does not match: "
                    + contentType.name());

        compressionHeader = new CompressionHeader();
        compressionHeader.read(getRawContent());
    }
}
