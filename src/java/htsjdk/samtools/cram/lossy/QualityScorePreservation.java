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
package htsjdk.samtools.cram.lossy;

import htsjdk.samtools.CigarElement;
import htsjdk.samtools.CigarOperator;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.cram.encoding.read_features.BaseQualityScore;
import htsjdk.samtools.cram.encoding.read_features.ReadFeature;
import htsjdk.samtools.cram.ref.ReferenceTracks;
import htsjdk.samtools.cram.structure.CramCompressionRecord;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

public class QualityScorePreservation {
	private String specification;
	private List<PreservationPolicy> policyList;

	public QualityScorePreservation(String specification) {
		this.specification = specification;
		policyList = parsePolicies(specification);
	}

	public List<PreservationPolicy> getPreservationPolicies() {
		return policyList;
	}

	private static final int readParam(LinkedList<Character> list) {
		int value = 0;

		while (!list.isEmpty() && Character.isDigit(list.getFirst()))
			value = value * 10 + (list.removeFirst() - 48);

		return value;
	}

	private static final QualityScoreTreatment readTreament(
			LinkedList<Character> list) {
		int param = readParam(list);
		QualityScoreTreatment t;
		switch (param) {
		case 0:
			t = QualityScoreTreatment.drop();
			break;
		case 40:
			t = QualityScoreTreatment.preserve();
			break;

		default:
			t = QualityScoreTreatment.bin(param);
			break;

		}
		return t;
	}

	public static final List<PreservationPolicy> parsePolicies(String spec) {
		List<PreservationPolicy> policyList = new ArrayList<PreservationPolicy>();
		for (String s : spec.split("-")) {
			if (s.length() == 0)
				continue;
			PreservationPolicy policy = parseSinglePolicy(s);
			policyList.add(policy);
		}

		Collections.sort(policyList, new Comparator<PreservationPolicy>() {

			@Override
			public int compare(PreservationPolicy o1, PreservationPolicy o2) {
				QualityScoreTreatment t1 = o1.treatment;
				QualityScoreTreatment t2 = o2.treatment;
				int result = t2.type.ordinal() - t1.type.ordinal();
				if (result != 0)
					return result;

				return 0;
			}
		});

		return policyList;
	}

	private static final PreservationPolicy parseSinglePolicy(String spec) {
		PreservationPolicy p = new PreservationPolicy();
		LinkedList<Character> list = new LinkedList<Character>();
		for (char b : spec.toCharArray())
			list.add(b);

		while (!list.isEmpty()) {
			char code = list.removeFirst();
			switch (code) {
			case 'R':
				p.baseCategories.add(BaseCategory.match());
				p.treatment = readTreament(list);
				break;
			case 'N':
				p.baseCategories.add(BaseCategory.mismatch());
				p.treatment = readTreament(list);
				break;
			case 'X':
				int coverage = readParam(list);
				p.baseCategories
						.add(BaseCategory.lower_than_coverage(coverage));
				break;
			case 'D':
				p.baseCategories.add(BaseCategory.flanking_deletion());
				p.treatment = readTreament(list);
				break;
			case 'M':
				int score = readParam(list);
				p.readCategory = ReadCategory.higher_than_mapping_score(score);
				break;
			case 'm':
				score = readParam(list);
				p.readCategory = ReadCategory.lower_than_mapping_score(score);
				break;
			case 'U':
				p.readCategory = ReadCategory.unplaced();
				p.treatment = readTreament(list);
				break;
			case 'P':
				int mismatches = readParam(list);
				p.baseCategories.add(BaseCategory.pileup(mismatches));
				p.treatment = readTreament(list);
				break;
			case 'I':
				p.baseCategories.add(BaseCategory.insertion());
				p.treatment = readTreament(list);
				break;
			case '_':
				p.treatment = readTreament(list);
				break;
			case '*':
				p.readCategory = ReadCategory.all();
				p.treatment = readTreament(list);
				break;

			default:
				throw new RuntimeException("Uknown read or base category: "
						+ code);
			}

			if (p.treatment == null)
				p.treatment = QualityScoreTreatment.preserve();
		}

		return p;
	}

	public static final void applyBinning(byte[] scores) {
		for (int i = 0; i < scores.length; i++)
			scores[i] = Binning.Illumina_binning_matrix[scores[i]];
	}

	public static final byte applyTreatment(byte score, QualityScoreTreatment t) {
		switch (t.type) {
		case BIN:
			return Binning.Illumina_binning_matrix[score];
		case DROP:
			return -1;
		case PRESERVE:
			return score;

		}
		throw new RuntimeException("Unknown quality score treatment type: "
				+ t.type.name());
	}

	public void addQualityScores(SAMRecord s, CramCompressionRecord r,
			ReferenceTracks t) {
		if (s.getBaseQualities() == SAMRecord.NULL_QUALS) {
			r.qualityScores = SAMRecord.NULL_QUALS;
			r.setForcePreserveQualityScores(false);
			return;
		}

		byte[] scores = new byte[s.getReadLength()];
		Arrays.fill(scores, (byte) -1);
		for (PreservationPolicy p : policyList)
			addQS(s, r, scores, t, p);

		if (!r.isForcePreserveQualityScores()) {
			for (int i = 0; i < scores.length; i++) {
				if (scores[i] > -1) {
					if (r.readFeatures == null)
						r.readFeatures = new LinkedList<ReadFeature>();
					r.readFeatures.add(new BaseQualityScore(i + 1, scores[i]));
				}
			}
			if (r.readFeatures != null)
				Collections.sort(r.readFeatures, readFeaturePositionComparator);
		}
		r.qualityScores = scores;
	}

	private static final Comparator<ReadFeature> readFeaturePositionComparator = new Comparator<ReadFeature>() {

		@Override
		public int compare(ReadFeature o1, ReadFeature o2) {
			return o1.getPosition() - o2.getPosition();
		}
	};

	public boolean areReferenceTracksRequired() {
		if (policyList == null || policyList.isEmpty()) return false ;
		for (PreservationPolicy p : policyList) {
			if (p.baseCategories == null || p.baseCategories.isEmpty())
				continue;
			for (BaseCategory c : p.baseCategories) {
				switch (c.type) {
				case LOWER_COVERAGE:
				case PILEUP:
					return true;

				default:
					break;
				}
			}
		}
		return false;

	}

	private static final void addQS(SAMRecord s, CramCompressionRecord r,
			byte[] scores, ReferenceTracks t, PreservationPolicy p) {
		int alSpan = s.getAlignmentEnd() - s.getAlignmentStart();
		byte[] qs = s.getBaseQualities();

		// check if read is falling into the read category:
		if (p.readCategory != null) {
			boolean properRead = false;
			switch (p.readCategory.type) {
			case ALL:
				properRead = true;
				break;
			case UNPLACED:
				properRead = s.getReadUnmappedFlag();
				break;
			case LOWER_MAPPING_SCORE:
				properRead = s.getMappingQuality() < p.readCategory.param;
				break;
			case HIGHER_MAPPING_SCORE:
				properRead = s.getMappingQuality() > p.readCategory.param;
				break;

			default:
				throw new RuntimeException("Unknown read category: "
						+ p.readCategory.type.name());
			}

			if (!properRead) // nothing to do here:
				return;
		}

		// apply treamtent if there is no per-base policy:
		if (p.baseCategories == null || p.baseCategories.isEmpty()) {
			switch (p.treatment.type) {
			case BIN:
				if (r.qualityScores == null)
					r.qualityScores = s.getBaseQualities();
				System.arraycopy(s.getBaseQualities(), 0, scores, 0,
						scores.length);
				applyBinning(scores);
				r.setForcePreserveQualityScores(true);
				break;
			case PRESERVE:
				System.arraycopy(s.getBaseQualities(), 0, scores, 0,
						scores.length);
				r.setForcePreserveQualityScores(true);
				break;
			case DROP:
				r.qualityScores = null;
				r.setForcePreserveQualityScores(false);
				break;

			default:
				throw new RuntimeException(
						"Unknown quality score treatment type: "
								+ p.treatment.type.name());
			}

			// nothing else to do here:
			return;
		}

		// here we go, scan all bases to check if the policy applies:
		boolean[] mask = new boolean[qs.length];

		int alStart = s.getAlignmentStart();
		// must be a mapped read at this point:
		if (alStart == SAMRecord.NO_ALIGNMENT_START)
			return;
		t.ensureRange(alStart, alSpan);

		for (BaseCategory c : p.baseCategories) {
			int pos;
			int refPos;
			switch (c.type) {
			case FLANKING_DELETION:
				pos = 0;
				for (CigarElement ce : s.getCigar().getCigarElements()) {
					if (ce.getOperator() == CigarOperator.D) {
						// if (pos > 0)
						mask[pos] = true;
						if (pos + 1 < mask.length)
							mask[pos + 1] = true;
					}

					pos += ce.getOperator().consumesReadBases() ? ce
							.getLength() : 0;
				}
				break;
			case MATCH:
			case MISMATCH:
				pos = 0;
				refPos = s.getAlignmentStart();
				for (CigarElement ce : s.getCigar().getCigarElements()) {
					if (ce.getOperator().consumesReadBases()) {
						for (int i = 0; i < ce.getLength(); i++) {
							boolean match = s.getReadBases()[pos + i] == t
									.baseAt(refPos + i);
							if ((c.type == BaseCategoryType.MATCH && match)
									|| (c.type == BaseCategoryType.MISMATCH && !match)) {
								mask[pos + i] = true;
							} else
								mask[pos + i] = false;
						}
						pos += ce.getLength();
					}
					refPos += ce.getOperator().consumesReferenceBases() ? ce
							.getLength() : 0;
				}
				break;
			case INSERTION:
				pos = 0;
				for (CigarElement ce : s.getCigar().getCigarElements()) {
					switch (ce.getOperator()) {
					case I:
						for (int i = 0; i < ce.getLength(); i++)
							mask[pos + i] = true;
						break;
					default:
						break;
					}

					pos += ce.getOperator().consumesReadBases() ? ce
							.getLength() : 0;
				}
				break;
			case LOWER_COVERAGE:
				pos = 1;
				refPos = s.getAlignmentStart();
				for (CigarElement ce : s.getCigar().getCigarElements()) {
					switch (ce.getOperator()) {
					case M:
					case EQ:
					case X:
						for (int i = 0; i < ce.getLength(); i++)
							mask[pos + i - 1] = t.coverageAt(refPos + i) < c.param;
						break;
					default:
						break;
					}

					pos += ce.getOperator().consumesReadBases() ? ce
							.getLength() : 0;
					refPos += ce.getOperator().consumesReferenceBases() ? ce
							.getLength() : 0;
				}
				break;
			case PILEUP:
				for (int i = 0; i < qs.length; i++)
					if (t.mismatchesAt(alStart + i) > c.param)
						mask[i] = true;
				break;

			default:
				break;
			}

		}

		int maskedCount = 0;
		for (int i = 0; i < mask.length; i++)
			if (mask[i]) {
				scores[i] = applyTreatment(qs[i], p.treatment);
				maskedCount++;
			}

		// safety latch, store all qs if there are too many individual score
		// to store:
		if (maskedCount > s.getReadLength() / 2)
			r.setForcePreserveQualityScores(true);
	}
}
