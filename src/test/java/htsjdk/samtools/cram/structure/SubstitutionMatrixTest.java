package htsjdk.samtools.cram.structure;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.encoding.readfeatures.Substitution;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

public class SubstitutionMatrixTest extends HtsjdkTest {
    // The default substitution code produced for any given base that has no substitutions, or that has a
    // substitution frequency profile that happens to align with the default code order (decreasing substitution
    // frequency for each of {A, C, G, T, N}).
    final static byte defaultCode = 0x1B; // 00 01 10 11

    // These test cases all test various substitution frequencies for a single reference base (so the resulting
    // matrix only has a non-default substitution code in one position)
    @DataProvider(name = "frequenciesForSingleReferenceBase")
    public Object[][] frequenciesForSingleReferenceBase() {
        return new Object[][]{
                // NOTE: For a given base, the matrix entry (substitution code) for that base looks like the following:
                //
                //  with substitution counts: 10, 20, 30, 40: 00 01 10 11 = 0xE4
                //  with substitution counts: 40, 30, 20, 10: 11 10 01 00 = 0x1B

                // reference base, number of A, number of C, number of G, number of T, number of N, expected matrix bytes

                {SubstitutionBase.A, 0, 0, 0, 0, 0, new byte[]{defaultCode, defaultCode, defaultCode, defaultCode, defaultCode}},
                //C, G, T, N = 1 2 0 3 = 01 10 00 11 = 0x63
                {SubstitutionBase.A, 0, 0, 0, 1, 0, new byte[]{(byte)0x63,  defaultCode, defaultCode, defaultCode, defaultCode}},
                {SubstitutionBase.A, 0, 1, 2, 3, 4, new byte[]{(byte)0xE4,  defaultCode, defaultCode, defaultCode, defaultCode}},
                // this matches the default (no substitutions) matrix because the frequency distribution follows the default ordering
                {SubstitutionBase.A, 0, 4, 3, 2, 1, new byte[]{defaultCode, defaultCode, defaultCode, defaultCode, defaultCode}},

                {SubstitutionBase.C, 0, 0, 0, 0, 0, new byte[]{defaultCode, defaultCode, defaultCode, defaultCode, defaultCode}},
                {SubstitutionBase.C, 0, 0, 0, 1, 0, new byte[]{defaultCode, 0x63,        defaultCode, defaultCode, defaultCode}},
                {SubstitutionBase.C, 1, 0, 2, 3, 4, new byte[]{defaultCode, (byte) 0xE4, defaultCode, defaultCode, defaultCode}},
                {SubstitutionBase.C, 4, 0, 3, 2, 1, new byte[]{defaultCode, defaultCode, defaultCode, defaultCode, defaultCode}},

                {SubstitutionBase.G, 0, 0, 0, 0, 0, new byte[]{defaultCode, defaultCode, defaultCode, defaultCode, defaultCode}},
                {SubstitutionBase.G, 0, 0, 0, 1, 0, new byte[]{defaultCode, defaultCode, 0x63,        defaultCode, defaultCode}},
                {SubstitutionBase.G, 1, 2, 0, 3, 4, new byte[]{defaultCode, defaultCode, (byte) 0xE4, defaultCode, defaultCode}},
                {SubstitutionBase.G, 4, 3, 0, 2, 1, new byte[]{defaultCode, defaultCode, defaultCode, defaultCode, defaultCode}},

                {SubstitutionBase.T, 0, 0, 0, 0, 0, new byte[]{defaultCode, defaultCode, defaultCode, defaultCode, defaultCode}},
                // Note that the "1" here is in the last position, rather than the position 4 (for T) like the others, because
                // the reference base in this case is 'T', and we can't represent a base that is a substitute for itself
                // 1, 2, 3, 0 = 01 10 11 00 = 0x6C
                {SubstitutionBase.T, 0, 0, 0, 0, 1, new byte[]{defaultCode, defaultCode, defaultCode, 0x6C,        defaultCode}},
                {SubstitutionBase.T, 1, 2, 3, 0, 4, new byte[]{defaultCode, defaultCode, defaultCode, (byte) 0xE4, defaultCode}},
                {SubstitutionBase.T, 4, 3, 2, 0, 1, new byte[]{defaultCode, defaultCode, defaultCode, defaultCode, defaultCode}},

                {SubstitutionBase.N, 0, 0, 0, 0, 0, new byte[]{defaultCode, defaultCode, defaultCode, defaultCode, defaultCode}},
                {SubstitutionBase.N, 0, 0, 0, 1, 0, new byte[]{defaultCode, defaultCode, defaultCode, defaultCode, 0x6C}},
                {SubstitutionBase.N, 1, 2, 3, 4, 0, new byte[]{defaultCode, defaultCode, defaultCode, defaultCode, (byte) 0xE4}},
                {SubstitutionBase.N, 4, 3, 2, 1, 0, new byte[]{defaultCode, defaultCode, defaultCode, defaultCode, defaultCode}}
        };
    }

    @Test(dataProvider = "frequenciesForSingleReferenceBase")
    public void testSingleReferenceBaseSubstitution (
            final SubstitutionBase refBase,
            final int nA,
            final int nC,
            final int nG,
            final int nT,
            final int nN,
            final byte[] expectedMatrixBytes) {
        // make sure the matrix generated for the given substitution frequencies has the codes ordered as expected
        final SubstitutionMatrix substitutionMatrix = getSubstitutionMatrixForFrequencies(refBase, nA, nC, nG, nT, nN);
        Assert.assertEquals(substitutionMatrix.getEncodedMatrix(), expectedMatrixBytes);

        System.out.println(substitutionMatrix);
        // create a "roundTrip" matrix using the expected bytes as input, and cross-validate that the codes match
        final SubstitutionMatrix roundTrippedMatrix = new SubstitutionMatrix(expectedMatrixBytes);
        for (final SubstitutionBase referenceBase : SubstitutionBase.values()) {
            for (final SubstitutionBase targetBase : SubstitutionBase.values()) {
                if (referenceBase != targetBase) {
                    // for each substitution of target for source, get the substitution code and then use
                    // that code to query for the substitute base to validate that it matches the expected
                    // target base
                    byte subCode = substitutionMatrix.code(referenceBase.getBase(), targetBase.getBase());
                    byte actualSubstitutedBase = substitutionMatrix.base(referenceBase.getBase(), subCode);
                    Assert.assertEquals(actualSubstitutedBase, targetBase.getBase());

                    // now cross-validate the results for the same query, but against the roundTrippedMatrix
                    subCode = roundTrippedMatrix.code(referenceBase.getBase(), targetBase.getBase());
                    actualSubstitutedBase = roundTrippedMatrix.base(referenceBase.getBase(), subCode);
                    Assert.assertEquals(actualSubstitutedBase, targetBase.getBase());
                } else {
                    final byte subCode = substitutionMatrix.code(referenceBase.getBase(), targetBase.getBase());
                    Assert.assertEquals(subCode, 0);
                }
            }
        }
    }

    @Test
    public void testMultipleReferenceBaseubstitution () {
        final List<CramCompressionRecord> cramRecords = new ArrayList<>();
        cramRecords.addAll(getCramRecordsWithSubstitutions(SubstitutionBase.A, SubstitutionBase.T, 10));
        cramRecords.addAll(getCramRecordsWithSubstitutions(SubstitutionBase.A, SubstitutionBase.G, 20));
        cramRecords.addAll(getCramRecordsWithSubstitutions(SubstitutionBase.G, SubstitutionBase.C, 10));
        cramRecords.addAll(getCramRecordsWithSubstitutions(SubstitutionBase.G, SubstitutionBase.N, 20));

        // for 'A' -> 10, 00, 01, 11 = 0x87
        // for 'G' -> 10, 01, 11, 00 = 0x9C

        final byte[] expectedMatrixBytes = new byte[]{ (byte) 0x87, defaultCode, (byte) 0x9C, defaultCode, defaultCode};

        final SubstitutionMatrix substitutionMatrix = new SubstitutionMatrix(cramRecords);
        Assert.assertEquals(substitutionMatrix.getEncodedMatrix(), expectedMatrixBytes);

        // make sure highest substitution frequencies yield the shortest code (0)
        Assert.assertEquals(substitutionMatrix.code(SubstitutionBase.A.getBase(), SubstitutionBase.G.getBase()), 0);
        Assert.assertEquals(substitutionMatrix.code(SubstitutionBase.G.getBase(), SubstitutionBase.N.getBase()), 0);

        // create a "roundTrip" matrix using the expected bytes as input, and cross-validate the same query against that
        final SubstitutionMatrix roundTrippedMatrix = new SubstitutionMatrix(expectedMatrixBytes);
        Assert.assertEquals(roundTrippedMatrix.code(SubstitutionBase.A.getBase(), SubstitutionBase.G.getBase()), 0);
        Assert.assertEquals(roundTrippedMatrix.code(SubstitutionBase.G.getBase(), SubstitutionBase.N.getBase()), 0);
    }

    @Test(dataProvider = "frequenciesForSingleReferenceBase")
    public void testLowerCaseReferenceBaseSubstitution (
            final SubstitutionBase refBase,
            final int nA,
            final int nC,
            final int nG,
            final int nT,
            final int nN,
            final byte[] unusedMatrixBytes) {
        final SubstitutionMatrix substitutionMatrix = getSubstitutionMatrixForFrequencies(refBase, nA, nC, nG, nT, nN);

        for (final SubstitutionBase referenceBase : SubstitutionBase.values()) {
            for (final SubstitutionBase substituteBase : SubstitutionBase.values()) {
                if (referenceBase != substituteBase) {
                    // NOTE: to test lower case substitution, we need to use the upper case reference to look
                    // up the substitution code, since the implementation doesn't populate the codes for lower
                    // case reference bases, only the substitution lookups for the case where its' been handed
                    // a code from a CRAM stream generated by some other implementation that generated a Substitution
                    // read feature for a lower case base...
                    final byte upperCode = substitutionMatrix.code(referenceBase.getBase(), substituteBase.getBase());

                    // now retrieve the substitution base for the lower case reference base given the code
                    // and make sure it matches the substitution base for that code for the upper case reference base
                    final byte lowerSubstitutedBase = substitutionMatrix.base((byte) Character.toLowerCase((char) referenceBase.getBase()), upperCode);
                    final byte upperSubstitutedBase = substitutionMatrix.base(referenceBase.getBase(), upperCode);

                    // first make sure we got the expected base for the upper case substitute
                    Assert.assertEquals(upperSubstitutedBase, substituteBase.getBase());
                    // now make sure we got the same base for the lower case substitute
                    Assert.assertEquals(lowerSubstitutedBase, substituteBase.getBase());
                }
            }
        }
    }

    // Create a SubstitutionMatrix with the given substitution frequencies for a single reference base.
    private SubstitutionMatrix getSubstitutionMatrixForFrequencies(
            final SubstitutionBase refBase,
            final int nA,
            final int nC,
            final int nG,
            final int nT,
            final int nN) {
        final List<CramCompressionRecord> cramRecords = new ArrayList<>(nA + nC + nG + nT + nN);
        cramRecords.addAll(getCramRecordsWithSubstitutions(refBase, SubstitutionBase.A, nA));
        cramRecords.addAll(getCramRecordsWithSubstitutions(refBase, SubstitutionBase.C, nC));
        cramRecords.addAll(getCramRecordsWithSubstitutions(refBase, SubstitutionBase.G, nG));
        cramRecords.addAll(getCramRecordsWithSubstitutions(refBase, SubstitutionBase.T, nT));
        cramRecords.addAll(getCramRecordsWithSubstitutions(refBase, SubstitutionBase.N, nN));

        return new SubstitutionMatrix(cramRecords);
    }

    // create a set of CRAM records of size nSubs that each have the given refBase->readBase Substitution read feature
    private List<CramCompressionRecord> getCramRecordsWithSubstitutions(
            final SubstitutionBase refBase,
            final SubstitutionBase readBase,
            final int nSubs) {
        final List<CramCompressionRecord> cramRecords = new ArrayList<>(nSubs);
        for (int i = 0; i < nSubs; i++) {
            final CramCompressionRecord rec = new CramCompressionRecord();
            final Substitution sub = new Substitution(i, readBase.getBase(), refBase.getBase());
            rec.addReadFeature(sub);
            cramRecords.add(rec);
        }
        return cramRecords;
    }

}
