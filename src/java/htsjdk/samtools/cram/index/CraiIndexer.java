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
package htsjdk.samtools.cram.index;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.cram.build.CramIO;
import htsjdk.samtools.cram.io.CountingInputStream;
import htsjdk.samtools.cram.structure.Container;
import htsjdk.samtools.cram.structure.CramHeader;
import htsjdk.samtools.util.Log;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPOutputStream;

class CraiIndexer {
	private static Log log = Log.getInstance(CraiIndexer.class);

	private CountingInputStream is;
	private SAMFileHeader samFileHeader;
	private CramIndex index;

	public CraiIndexer(InputStream is, File output)
			throws FileNotFoundException, IOException {
		this.is = new CountingInputStream(is);
		CramHeader cramHeader = CramIO.readCramHeader(this.is);
		samFileHeader = cramHeader.getSamFileHeader();

		index = new CramIndex(new GZIPOutputStream(new BufferedOutputStream(
				new FileOutputStream(output))));

	}

	private boolean nextContainer() throws IOException {
		long offset = is.getCount();
		Container c = CramIO.readContainer(is);
		if (c == null)
			return false;
		c.offset = offset;
		index.addContainer(c);
		log.info("INDEXED: " + c.toString());
		return true;
	}

	private void index() throws IOException {
		while (true) {
			if (!nextContainer())
				break;
		}
	}

	public void run() throws IOException {
		index();
		index.close();
	}
}
