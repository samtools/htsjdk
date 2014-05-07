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
package htsjdk.samtools.cram.common;

import htsjdk.samtools.Cigar;
import htsjdk.samtools.CigarElement;
import htsjdk.samtools.CigarOperator;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMFileWriter;
import htsjdk.samtools.SAMFileWriterFactory;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.SAMTag;
import htsjdk.samtools.SAMTextWriter;
import htsjdk.samtools.SamPairUtil;
import htsjdk.samtools.cram.encoding.read_features.ReadFeature;
import htsjdk.samtools.cram.structure.CramRecord;
import htsjdk.samtools.reference.IndexedFastaSequenceFile;
import htsjdk.samtools.reference.ReferenceSequence;
import htsjdk.samtools.reference.ReferenceSequenceFile;
import htsjdk.samtools.reference.ReferenceSequenceFileFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import net.sf.picard.util.Log;

public class Utils {

	private static Log log = Log.getInstance(Utils.class);

	public static class Version {
		public final int major;
		public final int minor;
		public final int build;

		public Version(int major, int minor, int build) {
			this.major = major;
			this.minor = minor;
			this.build = build;
		}

		public Version(String version) {
			String[] numbers = version.split("[\\.\\-b]");
			major = Integer.valueOf(numbers[0]);
			minor = Integer.valueOf(numbers[1]);
			if (numbers.length > 3)
				build = Integer.valueOf(numbers[3]);
			else
				build = 0;
		}

		@Override
		public String toString() {
			if (build > 0)
				return String.format("%d.%d-b%d", major, minor, build);
			else
				return String.format("%d.%d", major, minor);
		}
	}

	public static final Version CRAM_VERSION = getVersion();

	public static void reverse(final byte[] array, int offset, int len) {
		final int lastIndex = len - 1;

		int i, j;
		for (i = offset, j = offset + lastIndex; i < j; ++i, --j) {
			final byte tmp = array[i];
			array[i] = array[j];
			array[j] = tmp;
		}
		if (len % 2 == 1) {
			array[i] = array[i];
		}
	}

	public static void reverseComplement(final byte[] bases, int offset, int len) {
		final int lastIndex = len - 1;

		int i, j;
		for (i = offset, j = offset + lastIndex; i < j; ++i, --j) {
			final byte tmp = complement(bases[i]);
			bases[i] = complement(bases[j]);
			bases[j] = tmp;
		}
		if (len % 2 == 1) {
			bases[i] = complement(bases[i]);
		}
	}

	public static final byte a = 'a', c = 'c', g = 'g', t = 't', n = 'n',
			A = 'A', C = 'C', G = 'G', T = 'T', N = 'N';

	public static byte complement(final byte b) {
		switch (b) {
		case a:
			return t;
		case c:
			return g;
		case g:
			return c;
		case t:
			return a;
		case A:
			return T;
		case C:
			return G;
		case G:
			return C;
		case T:
			return A;
		default:
			return b;
		}
	}

	public static final byte upperCase(byte base) {
		return base >= 'a' ? (byte) (base - ('a' - 'A')) : base;
	}

	public static final byte[] upperCase(byte[] bases) {
		for (int i = 0; i < bases.length; i++)
			bases[i] = upperCase(bases[i]);
		return bases;
	}

	public static final byte normalizeBase(byte base) {
		switch (base) {
		case 'a':
		case 'A':
			return 'A';

		case 'c':
		case 'C':
			return 'C';

		case 'g':
		case 'G':
			return 'G';

		case 't':
		case 'T':
			return 'T';

		default:
			return 'N';
		}
	}

	public static final byte[] normalizeBases(byte[] bases) {
		for (int i = 0; i < bases.length; i++)
			bases[i] = normalizeBase(bases[i]);
		return bases;
	}

	public static Byte[] autobox(byte[] array) {
		Byte[] newArray = new Byte[array.length];
		for (int i = 0; i < array.length; i++)
			newArray[i] = array[i];
		return newArray;
	}

	public static Integer[] autobox(int[] array) {
		Integer[] newArray = new Integer[array.length];
		for (int i = 0; i < array.length; i++)
			newArray[i] = array[i];
		return newArray;
	}

	public static void changeReadLength(SAMRecord record, int newLength) {
		if (newLength == record.getReadLength())
			return;
		if (newLength < 1 || newLength >= record.getReadLength())
			throw new IllegalArgumentException("Cannot change read length to "
					+ newLength);

		List<CigarElement> newCigarElements = new ArrayList<CigarElement>();
		int len = 0;
		for (CigarElement ce : record.getCigar().getCigarElements()) {
			switch (ce.getOperator()) {
			case D:
				break;
			case S:
				// dump = true;
				// len -= ce.getLength();
				// break;
			case M:
			case I:
			case X:
				len += ce.getLength();
				break;

			default:
				throw new IllegalArgumentException(
						"Unexpected cigar operator: " + ce.getOperator()
								+ " in cigar " + record.getCigarString());
			}

			if (len <= newLength) {
				newCigarElements.add(ce);
				continue;
			}
			CigarElement newCe = new CigarElement(ce.getLength()
					- (record.getReadLength() - newLength), ce.getOperator());
			if (newCe.getLength() > 0)
				newCigarElements.add(newCe);
			break;
		}

		byte[] newBases = new byte[newLength];
		System.arraycopy(record.getReadBases(), 0, newBases, 0, newLength);
		record.setReadBases(newBases);

		byte[] newScores = new byte[newLength];
		System.arraycopy(record.getBaseQualities(), 0, newScores, 0, newLength);

		record.setCigar(new Cigar(newCigarElements));
	}

	public static void reversePositionsInRead(CramRecord record) {
		if (record.readFeatures == null || record.readFeatures.isEmpty())
			return;
		for (ReadFeature f : record.readFeatures)
			f.setPosition(record.readLength - f.getPosition() - 1);

		Collections.reverse(record.readFeatures);
	}

	public static byte[] getBasesFromReferenceFile(String referenceFilePath,
			String seqName, int from, int length) {
		ReferenceSequenceFile referenceSequenceFile = ReferenceSequenceFileFactory
				.getReferenceSequenceFile(new File(referenceFilePath));
		ReferenceSequence sequence = referenceSequenceFile.getSequence(seqName);
		byte[] bases = referenceSequenceFile.getSubsequenceAt(
				sequence.getName(), from, from + length).getBases();
		return bases;
	}

	public static void capitaliseAndCheckBases(byte[] bases, boolean strict) {
		for (int i = 0; i < bases.length; i++) {
			switch (bases[i]) {
			case 'A':
			case 'C':
			case 'G':
			case 'T':
			case 'N':
				break;
			case 'a':
				bases[i] = 'A';
				break;
			case 'c':
				bases[i] = 'C';
				break;
			case 'g':
				bases[i] = 'G';
				break;
			case 't':
				bases[i] = 'T';
				break;
			case 'n':
				bases[i] = 'N';
				break;

			default:
				if (strict)
					throw new RuntimeException("Illegal base at " + i + ": "
							+ bases[i]);
				else
					bases[i] = 'N';
				break;
			}
		}
	}

	/**
	 * Copied from net.sf.picard.sam.SamPairUtil. This is a more permissive
	 * version of the method, which does not reset alignment start and reference
	 * for unmapped reads.
	 * 
	 * @param rec1
	 * @param rec2
	 * @param header
	 */
	public static void setLooseMateInfo(final SAMRecord rec1,
			final SAMRecord rec2, final SAMFileHeader header) {
		if (rec1.getReferenceName() != SAMRecord.NO_ALIGNMENT_REFERENCE_NAME
				&& rec1.getReferenceIndex() == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX)
			rec1.setReferenceIndex(header.getSequenceIndex(rec1
					.getReferenceName()));
		if (rec2.getReferenceName() != SAMRecord.NO_ALIGNMENT_REFERENCE_NAME
				&& rec2.getReferenceIndex() == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX)
			rec2.setReferenceIndex(header.getSequenceIndex(rec2
					.getReferenceName()));

		// If neither read is unmapped just set their mate info
		if (!rec1.getReadUnmappedFlag() && !rec2.getReadUnmappedFlag()) {

			rec1.setMateReferenceIndex(rec2.getReferenceIndex());
			rec1.setMateAlignmentStart(rec2.getAlignmentStart());
			rec1.setMateNegativeStrandFlag(rec2.getReadNegativeStrandFlag());
			rec1.setMateUnmappedFlag(false);
			rec1.setAttribute(SAMTag.MQ.name(), rec2.getMappingQuality());

			rec2.setMateReferenceIndex(rec1.getReferenceIndex());
			rec2.setMateAlignmentStart(rec1.getAlignmentStart());
			rec2.setMateNegativeStrandFlag(rec1.getReadNegativeStrandFlag());
			rec2.setMateUnmappedFlag(false);
			rec2.setAttribute(SAMTag.MQ.name(), rec1.getMappingQuality());
		}
		// Else if they're both unmapped set that straight
		else if (rec1.getReadUnmappedFlag() && rec2.getReadUnmappedFlag()) {
			rec1.setMateNegativeStrandFlag(rec2.getReadNegativeStrandFlag());
			rec1.setMateUnmappedFlag(true);
			rec1.setAttribute(SAMTag.MQ.name(), null);
			rec1.setInferredInsertSize(0);

			rec2.setMateNegativeStrandFlag(rec1.getReadNegativeStrandFlag());
			rec2.setMateUnmappedFlag(true);
			rec2.setAttribute(SAMTag.MQ.name(), null);
			rec2.setInferredInsertSize(0);
		}
		// And if only one is mapped copy it's coordinate information to the
		// mate
		else {
			final SAMRecord mapped = rec1.getReadUnmappedFlag() ? rec2 : rec1;
			final SAMRecord unmapped = rec1.getReadUnmappedFlag() ? rec1 : rec2;

			mapped.setMateReferenceIndex(unmapped.getReferenceIndex());
			mapped.setMateAlignmentStart(unmapped.getAlignmentStart());
			mapped.setMateNegativeStrandFlag(unmapped
					.getReadNegativeStrandFlag());
			mapped.setMateUnmappedFlag(true);
			mapped.setInferredInsertSize(0);

			unmapped.setMateReferenceIndex(mapped.getReferenceIndex());
			unmapped.setMateAlignmentStart(mapped.getAlignmentStart());
			unmapped.setMateNegativeStrandFlag(mapped
					.getReadNegativeStrandFlag());
			unmapped.setMateUnmappedFlag(false);
			unmapped.setInferredInsertSize(0);
		}

		boolean firstIsFirst = rec1.getAlignmentStart() < rec2
				.getAlignmentStart();
		int insertSize = firstIsFirst ? SamPairUtil.computeInsertSize(rec1,
				rec2) : SamPairUtil.computeInsertSize(rec2, rec1);

		rec1.setInferredInsertSize(firstIsFirst ? insertSize : -insertSize);
		rec2.setInferredInsertSize(firstIsFirst ? -insertSize : insertSize);

	}

	public static final void setInsertSize(CramRecord record) {

	}

	public static int computeInsertSize(CramRecord firstEnd,
			CramRecord secondEnd) {
		if (firstEnd.isSegmentUnmapped() || secondEnd.isSegmentUnmapped()) {
			return 0;
		}
		if (firstEnd.sequenceId != secondEnd.sequenceId) {
			return 0;
		}

		final int right = Math
				.max(Math.max(firstEnd.alignmentStart,
						firstEnd.getAlignmentEnd()),
						Math.max(secondEnd.alignmentStart,
								secondEnd.getAlignmentEnd()));
		final int left = Math
				.min(Math.min(firstEnd.alignmentStart,
						firstEnd.getAlignmentEnd()),
						Math.min(secondEnd.alignmentStart,
								secondEnd.getAlignmentEnd()));
		final int tlen = right - left + 1;

		if (firstEnd.alignmentStart == left) {
			if (firstEnd.getAlignmentEnd() != right)
				firstEnd.templateSize = tlen;
			else if (firstEnd.isFirstSegment())
				firstEnd.templateSize = tlen;
			else
				firstEnd.templateSize = -tlen;
		} else {
			firstEnd.templateSize = -tlen;
		}
		if (secondEnd.alignmentStart == left) {
			if (secondEnd.getAlignmentEnd() != right)
				secondEnd.templateSize = tlen;
			else if (secondEnd.isFirstSegment())
				secondEnd.templateSize = tlen;
			else
				secondEnd.templateSize = -tlen;
		} else {
			secondEnd.templateSize = -tlen;
		}

		return tlen;
	}

	public static IndexedFastaSequenceFile createIndexedFastaSequenceFile(
			File file) throws RuntimeException, FileNotFoundException {
		if (IndexedFastaSequenceFile.canCreateIndexedFastaReader(file)) {
			IndexedFastaSequenceFile ifsFile = new IndexedFastaSequenceFile(
					file);

			return ifsFile;
		} else
			throw new RuntimeException(
					"Reference fasta file is not indexed or index file not found. Try executing 'samtools faidx "
							+ file.getAbsolutePath() + "'");
	}

	/**
	 * A rip off samtools bam_md.c
	 * 
	 * @param record
	 * @param ref
	 * @param flag
	 * @return
	 */
	public static void calculateMdAndNmTags(SAMRecord record, byte[] ref,
			boolean calcMD, boolean calcNM) {
		if (!calcMD && !calcNM)
			return;

		Cigar cigar = record.getCigar();
		List<CigarElement> cigarElements = cigar.getCigarElements();
		byte[] seq = record.getReadBases();
		int start = record.getAlignmentStart() - 1;
		int i, x, y, u = 0;
		int nm = 0;
		StringBuffer str = new StringBuffer();

		int size = cigarElements.size();
		for (i = y = 0, x = start; i < size; ++i) {
			CigarElement ce = cigarElements.get(i);
			int j, l = ce.getLength();
			CigarOperator op = ce.getOperator();
			if (op == CigarOperator.MATCH_OR_MISMATCH || op == CigarOperator.EQ
					|| op == CigarOperator.X) {
				for (j = 0; j < l; ++j) {
					int z = y + j;

					if (ref.length <= x + j)
						break; // out of boundary

					int c1 = 0;
					int c2 = 0;
					// try {
					c1 = seq[z];
					c2 = ref[x + j];

					if ((c1 == c2 && c1 != 15 && c2 != 15) || c1 == 0) {
						// a match
						++u;
					} else {
						str.append(u);
						str.appendCodePoint(ref[x + j]);
						u = 0;
						++nm;
					}
				}
				if (j < l)
					break;
				x += l;
				y += l;
			} else if (op == CigarOperator.DELETION) {
				str.append(u);
				str.append('^');
				for (j = 0; j < l; ++j) {
					if (ref[x + j] == 0)
						break;
					str.appendCodePoint(ref[x + j]);
				}
				u = 0;
				if (j < l)
					break;
				x += l;
				nm += l;
			} else if (op == CigarOperator.INSERTION
					|| op == CigarOperator.SOFT_CLIP) {
				y += l;
				if (op == CigarOperator.INSERTION)
					nm += l;
			} else if (op == CigarOperator.SKIPPED_REGION) {
				x += l;
			}
		}
		str.append(u);

		if (calcMD)
			record.setAttribute(SAMTag.MD.name(), str.toString());
		if (calcNM)
			record.setAttribute(SAMTag.NM.name(), nm);
	}

	public static int[][] sortByFirst(int[] array1, int[] array2) {
		int[][] sorted = new int[array1.length][2];
		for (int i = 0; i < array1.length; i++) {
			sorted[i][0] = array1[i];
			sorted[i][1] = array2[i];
		}

		Arrays.sort(sorted, intArray_2_Comparator);

		int[][] result = new int[2][array1.length];
		for (int i = 0; i < array1.length; i++) {
			result[0][i] = sorted[i][0];
			result[1][i] = sorted[i][1];
		}

		return result;
	}

	private static Comparator<int[]> intArray_2_Comparator = new Comparator<int[]>() {

		@Override
		public int compare(int[] o1, int[] o2) {
			int result = o1[0] - o2[0];
			if (result != 0)
				return -result;

			return -(o1[1] - o2[1]);
		}
	};

	public static void checkRefMD5(SAMSequenceDictionary d,
			ReferenceSequenceFile refFile, boolean checkExistingMD5,
			boolean failIfMD5Mismatch) throws NoSuchAlgorithmException {

		for (SAMSequenceRecord r : d.getSequences()) {
			ReferenceSequence sequence = refFile.getSequence(r
					.getSequenceName());
			if (!r.getAttributes().contains(SAMSequenceRecord.MD5_TAG)) {
				String md5 = calculateMD5String(sequence.getBases());
				r.setAttribute(SAMSequenceRecord.MD5_TAG, md5);
			} else {
				if (checkExistingMD5) {
					String existingMD5 = r
							.getAttribute(SAMSequenceRecord.MD5_TAG);
					String md5 = calculateMD5String(sequence.getBases());
					if (!md5.equals(existingMD5)) {

						String message = String
								.format("For sequence %s the md5 %s does not match the actual md5 %s.",
										r.getSequenceName(), existingMD5, md5);

						if (failIfMD5Mismatch)
							throw new RuntimeException(message);
						else
							log.warn(message);
					}
				}
			}
		}
	}

	public static String calculateMD5String(byte[] data)
			throws NoSuchAlgorithmException {
		return calculateMD5String(data, 0, data.length);
	}

	public static String calculateMD5String(byte[] data, int offset, int len) {
		byte[] digest = calculateMD5(data, offset, len);
		return String.format("%032x", new BigInteger(1, digest));
	}

	public static byte[] calculateMD5(byte[] data, int offset, int len) {
		MessageDigest md5_MessageDigest;
		try {
			md5_MessageDigest = MessageDigest.getInstance("MD5");
			md5_MessageDigest.reset();

			md5_MessageDigest.update(data, offset, len);
			return md5_MessageDigest.digest();
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}

	public static void main(String[] args) throws NoSuchAlgorithmException {
		byte b = 'a';
		byte u = upperCase((byte) 'a');
		System.out.printf("%d=%d, %c\n", b, u, u);

		System.out.println(calculateMD5String("363".getBytes()));
		System.out.println(calculateMD5String("a".getBytes()));
		System.out.println(calculateMD5String("Ჾ蠇".getBytes()));
		System.out.println(calculateMD5String("jk8ssl".getBytes()));
		System.out.println(calculateMD5String("0".getBytes()));
	}

	public static SAMFileWriter createSAMTextWriter(
			SAMFileWriterFactory factoryOrNull, OutputStream os,
			SAMFileHeader header, boolean printHeader) throws IOException {
		SAMFileWriter writer = null;
		if (printHeader) {
			if (factoryOrNull == null)
				factoryOrNull = new SAMFileWriterFactory();
			writer = factoryOrNull.makeSAMWriter(header, true, os);
		} else {
			SwapOutputStream sos = new SwapOutputStream();

			final SAMTextWriter ret = new SAMTextWriter(sos);
			ret.setSortOrder(header.getSortOrder(), true);
			ret.setHeader(header);
			ret.getWriter().flush();

			writer = ret;

			sos.delegate = os;
		}

		return writer;
	}

	private static class SwapOutputStream extends OutputStream {
		OutputStream delegate;

		@Override
		public void write(byte[] b) throws IOException {
			if (delegate != null)
				delegate.write(b);
		}

		@Override
		public void write(int b) throws IOException {
			if (delegate != null)
				delegate.write(b);
		}

		@Override
		public void write(byte[] b, int off, int len) throws IOException {
			if (delegate != null)
				delegate.write(b, off, len);
		}

		@Override
		public void flush() throws IOException {
			if (delegate != null)
				delegate.flush();
		}

		@Override
		public void close() throws IOException {
			if (delegate != null)
				delegate.close();
		}
	}

	private static Version getVersion() {
		String version = Utils.class.getPackage().getImplementationVersion();
		if (version == null)
			return new Version(2, 1, 0);
		else
			return new Version(version);
	}

	public static int getMajorVersion() {
		return CRAM_VERSION.major;
	}

	public static int getMinorVersion() {
		return CRAM_VERSION.minor;
	}

	public static String join(String[] words, String delimiter) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < words.length; i++) {
			sb.append(words[i]);
			if (i < words.length - 1)
				sb.append(delimiter);
		}

		return sb.toString();
	}

	public static String getJavaCommand() {
		return System.getProperty("sun.java.command");
	}
}
