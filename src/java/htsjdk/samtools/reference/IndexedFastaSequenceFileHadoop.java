/*
 * The MIT License
 *
 * Copyright (c) 2009 The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package htsjdk.samtools.reference;

import htsjdk.samtools.SAMException;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.hdfs.FileOperate;
import htsjdk.samtools.util.hdfs.RandomFileInt;
import htsjdk.samtools.util.hdfs.RandomFileInt.RandomFileFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Iterator;

/**
 * A fasta file driven by an index for fast, concurrent lookups. Supports two
 * interfaces: the ReferenceSequenceFile for old-style, stateful lookups and a
 * direct getter.
 */
public class IndexedFastaSequenceFileHadoop extends AbstractFastaSequenceFile {
	/**
	 * The interface facilitating direct access to the fasta.
	 */
	// private final FileChannel channel;
	RandomFileInt randomChrFileInt;
	/**
	 * A representation of the sequence index, stored alongside the fasta in a
	 * .fasta.fai file.
	 */
	private final FastaSequenceIndex index;

	/**
	 * An iterator into the fasta index, for traversing iteratively across the
	 * fasta.
	 */
	private Iterator<FastaSequenceIndexEntry> indexIterator;

	/**
	 * Open the given indexed fasta sequence file. Throw an exception if the
	 * file cannot be opened.
	 * 
	 * @param file
	 *            The file to open.
	 * @param index
	 *            Pre-built FastaSequenceIndex, for the case in which one does
	 *            not exist on disk.
	 * @throws FileNotFoundException
	 *             If the fasta or any of its supporting files cannot be found.
	 */
	public IndexedFastaSequenceFileHadoop(final File file, final FastaSequenceIndex index) {
		super(file);
		if (index == null) throw new IllegalArgumentException("Null index for fasta " + file);
		this.index = index;
		IOUtil.assertFileIsReadable(file);
		randomChrFileInt = RandomFileFactory.createInstance(file);
		reset();

		if (getSequenceDictionary() != null) sanityCheckDictionaryAgainstIndex(file.getAbsolutePath(), sequenceDictionary, index);
	}

	/**
	 * Open the given indexed fasta sequence file. Throw an exception if the
	 * file cannot be opened.
	 * 
	 * @param file
	 *            The file to open.
	 * @throws FileNotFoundException
	 *             If the fasta or any of its supporting files cannot be found.
	 */
	public IndexedFastaSequenceFileHadoop(final File file) throws FileNotFoundException {
		this(file, new FastaSequenceIndex((IndexedFastaSequenceFile.findRequiredFastaIndexFile(file))));
	}

	public boolean isIndexed() {
		return true;
	}

	/**
	 * Do some basic checking to make sure the dictionary and the index match.
	 * 
	 * @param fastaFile
	 *            Used for error reporting only.
	 * @param sequenceDictionary
	 *            sequence dictionary to check against the index.
	 * @param index
	 *            index file to check against the dictionary.
	 */
	protected static void sanityCheckDictionaryAgainstIndex(final String fastaFile, final SAMSequenceDictionary sequenceDictionary,
			final FastaSequenceIndex index) {
		// Make sure dictionary and index are the same size.
		if (sequenceDictionary.getSequences().size() != index.size())
			throw new SAMException("Sequence dictionary and index contain different numbers of contigs");

		Iterator<SAMSequenceRecord> sequenceIterator = sequenceDictionary.getSequences().iterator();
		Iterator<FastaSequenceIndexEntry> indexIterator = index.iterator();

		while (sequenceIterator.hasNext() && indexIterator.hasNext()) {
			SAMSequenceRecord sequenceEntry = sequenceIterator.next();
			FastaSequenceIndexEntry indexEntry = indexIterator.next();

			if (!sequenceEntry.getSequenceName().equals(indexEntry.getContig())) {
				throw new SAMException(String.format("Mismatch between sequence dictionary fasta index for %s, sequence '%s' != '%s'.", fastaFile,
						sequenceEntry.getSequenceName(), indexEntry.getContig()));
			}

			// Make sure sequence length matches index length.
			if (sequenceEntry.getSequenceLength() != indexEntry.getSize())
				throw new SAMException("Index length does not match dictionary length for contig: " + sequenceEntry.getSequenceName());
		}
	}

	/**
	 * Retrieves the sequence dictionary for the fasta file.
	 * 
	 * @return sequence dictionary of the fasta.
	 */
	public SAMSequenceDictionary getSequenceDictionary() {
		return sequenceDictionary;
	}

	/**
	 * Retrieves the complete sequence described by this contig.
	 * 
	 * @param contig
	 *            contig whose data should be returned.
	 * @return The full sequence associated with this contig.
	 */
	public ReferenceSequence getSequence(String contig) {
		return getSubsequenceAt(contig, 1, (int) index.getIndexEntry(contig).getSize());
	}

	/**
	 * Gets the subsequence of the contig in the range [start,stop]
	 * 
	 * @param contig
	 *            Contig whose subsequence to retrieve.
	 * @param start
	 *            inclusive, 1-based start of region.
	 * @param stop
	 *            inclusive, 1-based stop of region.
	 * @return The partial reference sequence associated with this range.
	 */
	public ReferenceSequence getSubsequenceAt(String contig, long start, long stop) {
		if (start > stop + 1) throw new SAMException(String.format("Malformed query; start point %d lies after end point %d", start, stop));

		FastaSequenceIndexEntry indexEntry = index.getIndexEntry(contig);

		if (stop > indexEntry.getSize()) throw new SAMException("Query asks for data past end of contig");
		if (randomChrFileInt == null) {
			throw new SAMException("no file exist: " + file.getName());
		}
		int length = (int) (stop - start + 1);

		byte[] target = new byte[length];

		final int basesPerLine = indexEntry.getBasesPerLine();
		final int bytesPerLine = indexEntry.getBytesPerLine();
		final int terminatorLength = bytesPerLine - basesPerLine;

		long[] startEndReal = getStartEndReal(indexEntry, start, stop);
		long startReal = startEndReal[0];
		long endReal = startEndReal[1];

		byte[] readInfo = new byte[(int) (endReal - startReal)];
		try {
			randomChrFileInt.seek(startReal);
			randomChrFileInt.read(readInfo);
		} catch (IOException e) {
			throw new SAMException("read error: " + file.getName());
		}

		int num = 0;
		int lineNum = getBias(start, basesPerLine);
		int byteNum = 1;
		boolean isBase = true;
		for (int i = 0; i < readInfo.length; i++) {
			if (isBase) {
				byte c = readInfo[i];
				target[num++] = readInfo[i];
				if (lineNum == basesPerLine) {
					lineNum = 1;
					isBase = false;
				} else {
					lineNum++;
				}
			} else {
				if (byteNum == terminatorLength) {
					byteNum = 1;
					isBase = true;
				} else {
					byteNum++;
				}
			}
		}
		return new ReferenceSequence(contig, indexEntry.getSequenceIndex(), target);
	}

	/**
	 * 修正输入的start和end的坐标为随机文本的实际坐标
	 * 
	 * @return null表示失败
	 * @throws IOException
	 */
	private long[] getStartEndReal(FastaSequenceIndexEntry indexEntry, long start, long end) {

		long chrLength = indexEntry.getSize();
		if (start <= 0) start = 1;
		if (end <= 0 || end > chrLength) end = chrLength;
		start--;

		long startChr = indexEntry.getLocation();
		int lengthRow = indexEntry.getBasesPerLine();
		int lenRowEnter = indexEntry.getBytesPerLine();
		try {
			long startReal = getRealSite(startChr, start, lengthRow, lenRowEnter);
			long endReal = getRealSite(startChr, end, lengthRow, lenRowEnter);
			return new long[] { startReal, endReal };
		} catch (Exception e) {
			throw new RuntimeException("extract seq error: " + indexEntry.getContig() + " " + start + " " + end, e);
		}
	}

	/**
	 * @param chrStart
	 *            该染色体起点在文本中的位置
	 * @param site
	 *            想转换的某个染色体坐标
	 * @param rowLen
	 *            每行有多少碱基
	 * @return
	 * @throws IOException
	 */
	private long getRealSite(long chrStart, long site, int rowLen, int rowEnterLen) throws IOException {
		// 设定到0位
		randomChrFileInt.seek(0);
		// 实际序列在文件中的起点
		long siteReal = chrStart + rowEnterLen * getRowNum(site, rowLen) + getBias(site, rowLen);
		return siteReal;
	}

	/** 指定染色体的某个位点，返回该位点的偏移，也就是在某一行的第几个碱基 */
	private int getBias(long site, int rowLen) {
		return (int) (site % rowLen);
	}

	private long getRowNum(long site, int rowLen) {
		return site / rowLen;
	}

	/**
	 * Gets the next sequence if available, or null if not present.
	 * 
	 * @return next sequence if available, or null if not present.
	 */
	public ReferenceSequence nextSequence() {
		if (!indexIterator.hasNext()) return null;
		return getSequence(indexIterator.next().getContig());
	}

	/**
	 * Reset the iterator over the index.
	 */
	public void reset() {
		indexIterator = index.iterator();
	}

	/**
	 * A simple toString implementation for debugging.
	 * 
	 * @return String representation of the file.
	 */
	public String toString() {
		return this.file.getAbsolutePath();
	}

	@Override
	public void close() throws IOException {
		randomChrFileInt.close();
	}
}