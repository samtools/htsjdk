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

import htsjdk.samtools.cram.io.ByteBufferUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;

public class SliceIO {

	public void readSliceHeadBlock(Slice s, InputStream is) throws IOException {
		s.headerBlock = new Block(is, true, true);
		parseSliceHeaderBlock(s);
	}

	public void parseSliceHeaderBlock(Slice s) throws IOException {
		InputStream is = new ByteArrayInputStream(s.headerBlock.getRawContent());
		// is = new DebuggingInputStream (is) ;

		s.sequenceId = ByteBufferUtils.readUnsignedITF8(is);
		s.alignmentStart = ByteBufferUtils.readUnsignedITF8(is);
		s.alignmentSpan = ByteBufferUtils.readUnsignedITF8(is);
		s.nofRecords = ByteBufferUtils.readUnsignedITF8(is);
		s.globalRecordCounter = ByteBufferUtils.readUnsignedLTF8(is);
		s.nofBlocks = ByteBufferUtils.readUnsignedITF8(is);

		s.contentIDs = ByteBufferUtils.array(is);
		s.embeddedRefBlockContentID = ByteBufferUtils.readUnsignedITF8(is);
		s.refMD5 = new byte[16];
		ByteBufferUtils.readFully(s.refMD5, is);
	}

	public byte[] createSliceHeaderBlockContent(Slice s) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ByteBufferUtils.writeUnsignedITF8(s.sequenceId, baos);
		ByteBufferUtils.writeUnsignedITF8(s.alignmentStart, baos);
		ByteBufferUtils.writeUnsignedITF8(s.alignmentSpan, baos);
		ByteBufferUtils.writeUnsignedITF8(s.nofRecords, baos);
		ByteBufferUtils.writeUnsignedLTF8(s.globalRecordCounter, baos);
		ByteBufferUtils.writeUnsignedITF8(s.nofBlocks, baos);

		s.contentIDs = new int[s.external.size()];
		int i = 0;
		for (int id : s.external.keySet())
			s.contentIDs[i++] = id;
		ByteBufferUtils.write(s.contentIDs, baos);
		ByteBufferUtils.writeUnsignedITF8(s.embeddedRefBlockContentID, baos);
		baos.write(s.refMD5 == null ? new byte[16]: s.refMD5);
		ByteBufferUtils.writeUnsignedITF8(s.sequenceId, baos);
		ByteBufferUtils.writeUnsignedITF8(s.sequenceId, baos);
		ByteBufferUtils.writeUnsignedITF8(s.sequenceId, baos);

		return baos.toByteArray();
	}

	public void createSliceHeaderBlock(Slice s) throws IOException {
		byte[] rawContent = createSliceHeaderBlockContent(s);
		s.headerBlock = new Block(BlockCompressionMethod.RAW,
				BlockContentType.MAPPED_SLICE, 0, rawContent, null);
	}

	public void readSliceBlocks(Slice s, boolean uncompressBlocks,
			InputStream is) throws IOException {
		s.external = new HashMap<Integer, Block>();
		for (int i = 0; i < s.nofBlocks; i++) {
			Block b1 = new Block(is, true, uncompressBlocks);

			switch (b1.contentType) {
			case CORE:
				s.coreBlock = b1;
				break;
			case EXTERNAL:
				if (s.embeddedRefBlockContentID == b1.contentId)
					s.embeddedRefBlock = b1;
				s.external.put(b1.contentId, b1);
				break;

			default:
				throw new RuntimeException(
						"Not a slice block, content type id "
								+ b1.contentType.name());
			}
		}
	}

	public void write(Slice s, OutputStream os) throws IOException {

		s.nofBlocks = 1 + s.external.size() + (s.embeddedRefBlock == null ? 0
				: 1);

		{
			s.contentIDs = new int[s.external.size()];
			int i = 0;
			for (int id : s.external.keySet())
				s.contentIDs[i] = id;
		}

		createSliceHeaderBlock(s);

		s.headerBlock.write(os);
		s.coreBlock.write(os);
		for (Block e : s.external.values())
			e.write(os);
	}

	public void read(Slice s, InputStream is) throws IOException {
		readSliceHeadBlock(s, is);
		readSliceBlocks(s, true, is);
	}
}
