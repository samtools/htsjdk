package htsjdk.variant.vcf;

import htsjdk.HtsjdkTest;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.VariantContextBuilder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
}
