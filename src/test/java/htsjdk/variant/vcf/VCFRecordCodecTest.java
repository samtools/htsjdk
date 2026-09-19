package htsjdk.variant.vcf;

import htsjdk.HtsjdkTest;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.GenotypeBuilder;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.VariantContextBuilder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.testng.Assert;
import org.testng.annotations.Test;

public class VCFRecordCodecTest extends HtsjdkTest {

    /** The codec spills records to disk as VCF text; what it decodes must be what it encoded, non-ASCII included. */
    @Test
    public void nonAsciiStringValuesSurviveARoundTrip() {
        final Set<VCFHeaderLine> lines = new LinkedHashSet<>();
        lines.add(new VCFInfoHeaderLine("NOTE", 1, VCFHeaderLineType.String, "A note"));
        final VCFHeader header = new VCFHeader(lines, List.of());
        final String note = "café→λ日本" + new String(Character.toChars(0x1F600));
        final List<Allele> alleles = List.of(Allele.create("A", true), Allele.create("C"));
        final VariantContext vc = new VariantContextBuilder("test", "chr1", 100, 100, alleles)
                .attribute("NOTE", note)
                .make();

        final VCFRecordCodec codec = new VCFRecordCodec(header);
        final ByteArrayOutputStream spilled = new ByteArrayOutputStream();
        codec.setOutputStream(spilled);
        codec.encode(vc);
        codec.setInputStream(new ByteArrayInputStream(spilled.toByteArray()));
        Assert.assertEquals(codec.decode().getAttribute("NOTE"), note);
        Assert.assertNull(codec.decode());
    }

    // Version-aware codec tests

    @Test
    public void roundTripsVersion44Genotype() {
        final Set<VCFHeaderLine> lines = new LinkedHashSet<>();
        lines.add(new VCFHeaderLine("fileformat", "VCFv4.4"));
        lines.add(new VCFFormatHeaderLine("GT", 1, VCFHeaderLineType.String, "genotype"));
        final VCFHeader header = new VCFHeader(lines, List.of("s1"));

        final List<Allele> alleles = List.of(Allele.create("A", true), Allele.create("C"));
        final VariantContext vc = new VariantContextBuilder("test", "chr1", 100, 100, alleles)
                .genotypes(new GenotypeBuilder("s1", alleles)
                        .allelePhasing(new boolean[] {true, false})
                        .make())
                .make();

        final VCFRecordCodec codec = new VCFRecordCodec(header);
        final ByteArrayOutputStream spilled = new ByteArrayOutputStream();
        codec.setOutputStream(spilled);
        codec.encode(vc);

        codec.setInputStream(new ByteArrayInputStream(spilled.toByteArray()));
        final VariantContext decoded = codec.decode();
        Assert.assertTrue(decoded.getGenotype("s1").needsLeadingPhaseIndicator());
        Assert.assertTrue(decoded.getGenotype("s1").isAllelePhased(0));
        Assert.assertFalse(decoded.getGenotype("s1").isAllelePhased(1));
    }

    @Test
    public void roundTripsPercentEncodedValue() {
        final Set<VCFHeaderLine> lines = new LinkedHashSet<>();
        lines.add(new VCFHeaderLine("fileformat", "VCFv4.3"));
        lines.add(new VCFInfoHeaderLine("NOTE", 1, VCFHeaderLineType.String, "A note"));
        final VCFHeader header = new VCFHeader(lines, List.of());

        final String note = "key=value;other";
        final List<Allele> alleles = List.of(Allele.create("A", true), Allele.create("C"));
        final VariantContext vc = new VariantContextBuilder("test", "chr1", 100, 100, alleles)
                .attribute("NOTE", note)
                .make();

        final VCFRecordCodec codec = new VCFRecordCodec(header);
        final ByteArrayOutputStream spilled = new ByteArrayOutputStream();
        codec.setOutputStream(spilled);
        codec.encode(vc);

        codec.setInputStream(new ByteArrayInputStream(spilled.toByteArray()));
        Assert.assertEquals(codec.decode().getAttribute("NOTE"), note);
    }

    @Test
    public void aListValuedStringInfoFieldSurvivesA43RoundTrip() {
        final Set<VCFHeaderLine> lines = new LinkedHashSet<>();
        lines.add(new VCFHeaderLine("fileformat", "VCFv4.3"));
        lines.add(new VCFInfoHeaderLine("ANN", VCFHeaderLineCount.UNBOUNDED, VCFHeaderLineType.String, "notes"));
        final VCFHeader header = new VCFHeader(lines, List.of());

        final List<Allele> alleles = List.of(Allele.create("A", true), Allele.create("C"));
        final VariantContext vc = new VariantContextBuilder("test", "chr1", 100, 100, alleles)
                .attribute("ANN", List.of("a,b", "c;d", "e"))
                .make();

        final VCFRecordCodec codec = new VCFRecordCodec(header);
        final ByteArrayOutputStream spilled = new ByteArrayOutputStream();
        codec.setOutputStream(spilled);
        codec.encode(vc);
        Assert.assertTrue(spilled.toString(StandardCharsets.UTF_8).contains("ANN=a%2Cb,c%3Bd,e"));

        codec.setInputStream(new ByteArrayInputStream(spilled.toByteArray()));
        Assert.assertEquals(codec.decode().getAttributeAsList("ANN"), List.of("a,b", "c;d", "e"));
    }

    @Test
    public void resolvesVersionFloor() {
        final Set<VCFHeaderLine> lines = new LinkedHashSet<>();
        lines.add(new VCFInfoHeaderLine("NOTE", 1, VCFHeaderLineType.String, "A note"));
        final VCFHeader header = new VCFHeader(lines, List.of());

        final List<Allele> alleles = List.of(Allele.create("A", true), Allele.create("C"));
        final VariantContext vc = new VariantContextBuilder("test", "chr1", 100, 100, alleles)
                .attribute("NOTE", "plain")
                .make();

        final VCFRecordCodec codec = new VCFRecordCodec(header);
        final ByteArrayOutputStream spilled = new ByteArrayOutputStream();
        codec.setOutputStream(spilled);
        codec.encode(vc);

        // The encoded text should NOT be percent-encoded since the floor is 4.2
        final String spilledText = spilled.toString(StandardCharsets.UTF_8);
        Assert.assertFalse(spilledText.contains("%"), "Should not percent-encode for versionless header");

        codec.setInputStream(new ByteArrayInputStream(spilled.toByteArray()));
        Assert.assertEquals(codec.decode().getAttribute("NOTE"), "plain");
    }
}
