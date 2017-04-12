package htsjdk.variant.vcf;

import htsjdk.tribble.index.AbstractIndex;
import htsjdk.tribble.index.IndexFactory;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.openjdk.jmh.annotations.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Created by noamb on 4/11/17.
 */
@State(Scope.Benchmark)
public class AbstractVCFCodecBenchmark {

  // below we extract the resource from the jar and save to file - because bench-marked method IndexFactory.createLinearIndex() expects a file and not an input stream
  private final File tmpFile = new File(FileUtils.getTempDirectory() + File.separator + "tmp.vcf");


  @Setup(Level.Trial)
  public void setup() throws IOException {
    IOUtils.copy(this.getClass().getResourceAsStream("/test.vcf"), new FileOutputStream(tmpFile));
  }

  @TearDown(Level.Trial)
  public void tearDown() {
    FileUtils.deleteQuietly(tmpFile);
  }


  @Benchmark
  @BenchmarkMode(Mode.AverageTime)
  public int test() throws IOException {
    AbstractIndex idx = IndexFactory.createLinearIndex(tmpFile, new VCFCodec());
    return idx.getSequenceNames().size(); // always return something to avoid "dead code elimination"
  }
}
