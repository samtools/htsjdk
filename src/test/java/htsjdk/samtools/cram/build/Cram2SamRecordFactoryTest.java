package htsjdk.samtools.cram.build;

import htsjdk.samtools.*;
import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.cram.encoding.readfeatures.*;
import htsjdk.samtools.cram.structure.CramCompressionRecord;
import htsjdk.samtools.util.SequenceUtil;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by vadim on 20/02/2017.
 */
public class Cram2SamRecordFactoryTest {

    @Test
    public void testCreate() {
        CramCompressionRecord cramRecord = new CramCompressionRecord();
        cramRecord.readName = "name";
        cramRecord.readFeatures = new ReadFeaturesBuilder().sub(2).features;
        cramRecord.readBases = "AAA".getBytes();
        cramRecord.readLength = cramRecord.readBases.length;
        cramRecord.qualityScores = "!!!".getBytes();
        cramRecord.tags = new SAMBinaryTagAndValue(SAMTagUtil.getSingleton().AS, 123);
        cramRecord.sequenceId = 1;
        cramRecord.alignmentStart = 123;
        cramRecord.mappingQuality = 31;
        cramRecord.setFirstSegment(true);
        cramRecord.readGroupID = 1;

        List<SAMSequenceRecord> seqs = new ArrayList<>();
        seqs.add(new SAMSequenceRecord("1", 10));
        seqs.add(new SAMSequenceRecord("2", 10));
        SAMSequenceDictionary dic = new SAMSequenceDictionary(seqs);
        SAMFileHeader header = new SAMFileHeader(dic);
        header.addReadGroup(new SAMReadGroupRecord("rg1"));
        header.addReadGroup(new SAMReadGroupRecord("rg2"));

        SAMRecord samRecord = new Cram2SamRecordFactory(header).create(cramRecord);
        String expected = String.format("%s\t%d\t%s\t%d\t%d\t%s\t%s\t%d\t%d\t%s\t%s\t%s\t%s",
                cramRecord.readName,
                SAMFlag.FIRST_OF_PAIR.intValue(),
                header.getSequence(cramRecord.sequenceId).getSequenceName(),
                cramRecord.alignmentStart, cramRecord.mappingQuality,
                "3M", "*",
                0, 0,
                new String(cramRecord.readBases), SAMUtils.phredToFastq(cramRecord.qualityScores),
                "RG:Z:"+header.getReadGroups().get(cramRecord.readGroupID).getId(), "AS:i:123");

        String actual = samRecord.getSAMString().trim();
        Assert.assertEquals(actual, expected);
    }


    @DataProvider(name = "rf2cigarExpectations")
    public Object[][] rf2cigarExpectations() {
        return new Object[][]{
                // <read length>, <read features>, <expected cigar string>:

                // empty cigar should be all M:
                {0, new ReadFeaturesBuilder().features, SAMRecord.NO_ALIGNMENT_CIGAR},
                {1, new ReadFeaturesBuilder().features, "1M"},
                {2, new ReadFeaturesBuilder().features, "2M"},

                // substitutions result in max(read_len, last_sub_pos)M,
                // read length add to cigar only if it is greater then last op pos:
                {0, new ReadFeaturesBuilder().sub(1).features, "1M"},
                {0, new ReadFeaturesBuilder().sub(2).features, "2M"},
                {1, new ReadFeaturesBuilder().sub(1).features, "1M"},
                {2, new ReadFeaturesBuilder().sub(1).features, "2M"},
                {2, new ReadFeaturesBuilder().sub(2).features, "2M"},

                // zero slips in:
                {0, new ReadFeaturesBuilder().ins(1, 0).features, "0I"},
                {0, new ReadFeaturesBuilder().del(1, 0).features, "0D"},
                {0, new ReadFeaturesBuilder().pad(1, 0).features, "0P"},
                {0, new ReadFeaturesBuilder().hard(1, 0).features, "0H"},
                {0, new ReadFeaturesBuilder().soft(1, 0).features, "0S"},
                {0, new ReadFeaturesBuilder().skip(1, 0).features, "0N"},
                {0, new ReadFeaturesBuilder().bases(1, 0).features, "0M"},
                {0, new ReadFeaturesBuilder().scores(1, 0).features, "0M"},

                // single read base consuming ops:
                {1, new ReadFeaturesBuilder().ins(1, 1).features, "1I"},
                {1, new ReadFeaturesBuilder().soft(1, 1).features, "1S"},
                {1, new ReadFeaturesBuilder().bases(1, 1).features, "1M"},
                {1, new ReadFeaturesBuilder().scores(1, 1).features, "1M"},
                // single read base non-consuming ops:
                {1, new ReadFeaturesBuilder().del(1, 1).features, "1D1M"},
                {1, new ReadFeaturesBuilder().pad(1, 1).features, "1P1M"},
                {1, new ReadFeaturesBuilder().hard(1, 1).features, "1H1M"},
                {1, new ReadFeaturesBuilder().skip(1, 1).features, "1N1M"},

                // mixing inserts with match/mismatch:
                {0, new ReadFeaturesBuilder().ins(1).features, "1I"},
                {0, new ReadFeaturesBuilder().ins(2).features, "1M1I"},
                {1, new ReadFeaturesBuilder().ins(1).features, "1I"},
                {2, new ReadFeaturesBuilder().ins(1).features, "1I1M"},

                // merge adjacent ops:
                {4, new ReadFeaturesBuilder().sub(1).ins(1).ins(2, 2).features, "1M3I1M"},

                // scores don't affect cigar:
                {0, new ReadFeaturesBuilder().score(1).features, "0M"},
                {2, new ReadFeaturesBuilder().score(1).features, "2M"},
                {2, new ReadFeaturesBuilder().sub(1).ins(2, 3).sub(2).features, "1M3I1M"},
                {2, new ReadFeaturesBuilder().sub(1).score(1).ins(2, 3).sub(2).features, "1M3I1M"},
        };
    }

    @Test(dataProvider = "rf2cigarExpectations")
    public void testGetCigar(int readLength, List<ReadFeature> features, String cigarSringExpectation) {
        Assert.assertEquals(Cram2SamRecordFactory.getCigar(features, readLength).toString(), cigarSringExpectation);
    }

    private static byte A = 'A';
    private static byte C = 'C';
    private static byte G = 'G';
    private static byte T = 'T';
    private static byte N = 'N';
    // some quality score:
    private static byte S = '!';

    private static class ReadFeaturesBuilder {
        List<ReadFeature> features = new ArrayList<>();

        ReadFeaturesBuilder score(int pos) {
            features.add(new BaseQualityScore(pos, S));
            return this;
        }

        ReadFeaturesBuilder score(int pos, byte score) {
            features.add(new BaseQualityScore(pos, score));
            return this;
        }

        ReadFeaturesBuilder bases(int pos, int len) {
            byte[] bases = new byte[len] ;
            Arrays.fill(bases, A);
            features.add(new Bases(pos, bases));
            return this;
        }

        ReadFeaturesBuilder bases(int pos, String bases) {
            features.add(new Bases(pos, bases.getBytes()));
            return this;
        }

        ReadFeaturesBuilder del(int pos, int len) {
            features.add(new Deletion(pos, len));
            return this;
        }

        ReadFeaturesBuilder hard(int pos, int len) {
            features.add(new HardClip(pos, len));
            return this;
        }

        ReadFeaturesBuilder ins(int pos) {
            return ins(pos, A);
        }

        ReadFeaturesBuilder ins(int pos, byte base) {
            features.add(new InsertBase(pos, base));
            return this;
        }

        ReadFeaturesBuilder ins(int pos, int len) {
            byte[] bases = new byte[len] ;
            Arrays.fill(bases, A);
            features.add(new Insertion(pos, bases));
            return this;
        }
        ReadFeaturesBuilder ins(int pos, String bases) {
            features.add(new Insertion(pos, bases.getBytes()));
            return this;
        }

        ReadFeaturesBuilder pad(int pos, int len) {
            features.add(new Padding(pos, len));
            return this;
        }

        ReadFeaturesBuilder base(int pos) {
            features.add(new ReadBase(pos, A, S));
            return this;
        }

        ReadFeaturesBuilder base(int pos, byte base, byte score) {
            features.add(new ReadBase(pos, base, score));
            return this;
        }

        ReadFeaturesBuilder skip(int pos, int len) {
            features.add(new RefSkip(pos, len));
            return this;
        }

        ReadFeaturesBuilder scores(int pos, int len) {
            byte[] scores = new byte[len] ;
            Arrays.fill(scores, S);
            features.add(new Scores(pos, scores));
            return this;
        }

        ReadFeaturesBuilder scores(int pos, String scores) {
            features.add(new Scores(pos, scores.getBytes()));
            return this;
        }

        ReadFeaturesBuilder soft(int pos, int len) {
            byte[] bases = new byte[len] ;
            Arrays.fill(bases, A);
            features.add(new SoftClip(pos, bases));
            return this;
        }

        ReadFeaturesBuilder soft(int pos, String bases) {
            features.add(new SoftClip(pos, bases.getBytes()));
            return this;
        }

        ReadFeaturesBuilder sub(int pos) {
            return sub(pos, A, C);
        }

        ReadFeaturesBuilder sub(int pos, byte base, byte ref) {
            Substitution substitution = new Substitution();
            substitution.setPosition(pos);
            substitution.setBase(base);
            substitution.setReferenceBase(ref);
            features.add(substitution);
            return this;
        }
    }


}
