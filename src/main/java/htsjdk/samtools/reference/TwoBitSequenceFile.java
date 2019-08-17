package htsjdk.samtools.reference;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.util.Interval;
import htsjdk.samtools.util.Locatable;
import htsjdk.samtools.util.RuntimeIOException;

public class TwoBitSequenceFile implements Closeable {
    /** standard suffix of 2bit files */
    public static final String SUFFIX=".2bit";
    // https://github.com/rpique/UCSC-Browser-code-add-ons/blob/ba859c047e46d8074b93700d34cb601fa9ba4288/src/lib/dnautil.c
    private static final int MASKED_BASE_BIT= 8;
//    Numerical values for bases. 
    private static final int T_BASE_VAL =0;
    //private static final int U_BASE_VAL =0;
    private static final int C_BASE_VAL =1;
    private static final int A_BASE_VAL =2;
    private static final int G_BASE_VAL =3;
    private static final int N_BASE_VAL =4;//  Used in 1/2 byte representation. 

    private static final byte valToNucl[]=new byte[(N_BASE_VAL|MASKED_BASE_BIT)+1];

    {{{
    valToNucl[T_BASE_VAL] = valToNucl[T_BASE_VAL|MASKED_BASE_BIT] = 't';
    valToNucl[C_BASE_VAL] = valToNucl[C_BASE_VAL|MASKED_BASE_BIT] = 'c';
    valToNucl[A_BASE_VAL] = valToNucl[A_BASE_VAL|MASKED_BASE_BIT] = 'a';
    valToNucl[G_BASE_VAL] = valToNucl[G_BASE_VAL|MASKED_BASE_BIT] = 'g';
    valToNucl[N_BASE_VAL] = valToNucl[N_BASE_VAL|MASKED_BASE_BIT] = 'n';
    }}}
    
    /* Signature into 2bit file (2 bits per nucleotide DNA file) plus
     * information on N and masked bases. */
    private final static int twoBitSig = 0x1A412743;

    /* Signature of byte-swapped two-bit file. */
   private final static int twoBitSwapSig = 0x4327411A;
    
    private final SeekableByteChannel channel;
    private final ByteBuffer byteBuffer = ByteBuffer.allocate(2*Long.BYTES);
    private final ByteOrder byteOrder;
    private Map<String,TwoBitIndex> seq2index;
    private SAMSequenceDictionary dict = null;
    /* Cache information about last sequence accessed, including
     * nBlock and mask block.  This doesn't include the data.
     * This speeds fragment reads.  */
    private TwoBit seqCache=null;
    private long dataOffsetCache = -1L;  /* file offset of data for seqCache seqeunce */
    
    private static class Block {
        int count;
        int starts[];
        int sizes[];
    }
    
    private static class TwoBit {
        String name;            /* Name of sequence. */
        byte[] data;        /* DNA at two bits per base. */
        int size;        /* Size of this sequence. */
        int nBlockCount;     /* Count of blocks of Ns. */
        int[] nStarts;        /* Starts of blocks of Ns. */
        int[] nSizes;     /* Sizes of blocks of Ns. */
        int maskBlockCount;  /* Count of masked blocks. */
        int[] maskStarts;     /* Starts of masked regions. */
        int[] maskSizes;      /* Sizes of masked regions. */
        int reserved;        /* Reserved for future expansion. */
    }
    
    private static class TwoBitIndex {
        String name;
        long offset;
        @Override
        public String toString() {
            return name+" offset:"+offset;
            }
    }
    
    public TwoBitSequenceFile(final Path path) {
        try {
            this.channel = Files.newByteChannel(path);
            byteBuffer.clear();
            byteBuffer.limit(Integer.BYTES*4);
            channel.read(byteBuffer);
            byteBuffer.flip();
            final int sig = byteBuffer.getInt();
            if(sig == twoBitSig)
                {
                this.byteOrder = ByteOrder.BIG_ENDIAN;
                }
            else if( sig == twoBitSwapSig) {
                this.byteOrder = ByteOrder.LITTLE_ENDIAN;
                }
            else
                {
                throw new IOException("Cannot read header from "+path);
                }
            this.byteBuffer.order(this.byteOrder);
            final int version = byteBuffer.getInt();
            if(version!=0) {
                throw new IOException("Can only handle version 0 or version 1 of this file. This is version "+version);
            }
            int seqCount = byteBuffer.getInt();
            System.err.println("n="+seqCount);
            /* int reserved ignored */ byteBuffer.getInt();
            this.seq2index = new HashMap<>(seqCount);
            for(int i=0;i< seqCount;i++) {
                final TwoBitIndex twoBitIndex = new TwoBitIndex();
                twoBitIndex.name = readString();
                twoBitIndex.offset = readInt();
                this.seq2index.put(twoBitIndex.name, twoBitIndex);
            }
            
            
        } catch (final IOException err) {
           throw new RuntimeIOException(err);
        }
    }
    
    public byte[] query(final Locatable loc,boolean doMask) throws IOException {
        int  remainder, midStart, midEnd;
        final TwoBitIndex tbi = this.seq2index.get(loc.getContig());
        if(tbi==null) {
            throw new IllegalArgumentException("cannot find sequence " + loc.getContig());
            }
        final TwoBit twoBit = getTwoBitSeqHeader(loc.getContig());
        System.err.println("length "+twoBit.size);
        int fragStart = loc.getStart()-1;
        int fragEnd = loc.getEnd();

        /* validate range. */
        if (fragEnd > twoBit.size) {
            throw new IllegalArgumentException("twoBitReadSeqFrag in "+loc.getContig()+" end ("+fragEnd+") >= seqSize ("+twoBit.size+")");
        }    
        final int outSize = fragEnd - fragStart;
        if (outSize < 1)
            {
            throw new IllegalArgumentException("twoBitReadSeqFrag in  "+loc.getContig()+" start ("+fragStart+") >= end ("+fragEnd+")");
            }
        
        int packedStart = (fragStart>>2);
        int packedEnd = ((fragEnd+3)>>2);
        int packByteCount = packedEnd - packedStart;
        this.channel.position(this.channel.position() + packedStart);
        final ByteBuffer buf = ByteBuffer.allocate(packByteCount);
        this.channel.read(buf);
        buf.flip();
        final byte packed[] = buf.array();
        System.err.println("packed size="+packed.length);
        final byte dna[] = new byte[outSize];
        int dna_idx=0;
        int packed_idx=0;
        
/* Handle case where everything is in one packed byte */
if (packByteCount == 1)
    {
    System.err.println("ICI-0");
    int pOff = (packedStart<<2);
    int pStart = fragStart - pOff;
    int pEnd = fragEnd - pOff;
    int partial = Byte.toUnsignedInt(packed[0]);
    assert(pEnd <= 4);
    assert(pStart >= 0);
    for (int i=pStart; i<pEnd; ++i) {
        dna[dna_idx++] = valToNt((partial >> (6-i-i)) & 3);
        }
    }
else
    {
    System.err.println("ICI-A");
    /* Handle partial first packed byte. */
    midStart = fragStart;
    remainder = (fragStart&3);
    System.err.println("ICI-C remind"+remainder);
    if (remainder > 0)
        {
        int partial = Byte.toUnsignedInt(packed[packed_idx++]);
        int partCount = 4 - remainder;
        for (int i=partCount-1; i>=0; --i)
            {
            dna[dna_idx+i] = valToNt(partial&3);
            partial >>= 2;
            }
        midStart += partCount;
        dna_idx += partCount;
        System.err.println("ICI-B"+dna_idx);
        }

    /* Handle middle bytes. */
    remainder = fragEnd&3;
    midEnd = fragEnd - remainder;
    System.err.println("ICI-D "+remainder+" "+dna_idx);
    for (int i=midStart; i<midEnd; i += 4)
        {
        System.err.println("i="+i+" "+dna_idx);
        int b = Byte.toUnsignedInt(packed[packed_idx++]);
        dna[dna_idx+3] = valToNt(b&3);
        b >>= 2;
        dna[dna_idx+2] = valToNt(b&3);
        b >>= 2;
        dna[dna_idx+1] = valToNt(b&3);
        b >>= 2;
        dna[dna_idx+0] = valToNt(b&3);
        dna_idx += 4;
        }

    if (remainder >0)
        {
        int part = Byte.toUnsignedInt(packed[packed_idx]);
        part >>= (8-remainder-remainder);
        for (int i=remainder-1; i>=0; --i)
            {
            dna[dna_idx + i] = valToNt(part&3);
            part >>= 2;
            }
        }
    }

if (twoBit.nBlockCount > 0)
    {
    int startIx = findGreatestLowerBound(twoBit.nBlockCount, twoBit.nStarts, fragStart);
    for (int i=startIx; i<twoBit.nBlockCount; ++i)
        {
        int s = twoBit.nStarts[i];
        int e = s + twoBit.nSizes[i];
        if (s >= fragEnd)
            break;
        if (s < fragStart)
           s = fragStart;
        if (e > fragEnd)
           e = fragEnd;
        if (s < e)
            {
            //TODO check offset
            Arrays.fill(dna,s-fragStart,s-fragStart+e-s,(byte)'n');
            //memset(seq->dna + s - fragStart, 'n', e - s);
            }
        }
    }

if (doMask)
        {
        toUpperN(dna);
        if (twoBit.maskBlockCount > 0)
            {
            int startIx = findGreatestLowerBound(twoBit.maskBlockCount, twoBit.maskStarts,
                fragStart);
            for (int i=startIx; i<twoBit.maskBlockCount; ++i)
                {
                int s = twoBit.maskStarts[i];
                int e = s + twoBit.maskSizes[i];
                if (s >= fragEnd)
                break;
                if (s < fragStart)
                s = fragStart;
                if (e > fragEnd)
                e = fragEnd;
                if (s < e)
                    {
                    for(int x=0;x<e-s;++x) {
                        dna[s-fragStart+x]=(byte)Character.toLowerCase(dna[s-fragStart+x]);
                        }
                    //toLowerN(seq->dna + s - fragStart, e - s);
                    }
                }
            }
        }
    return dna;
    }
    
    public SAMSequenceDictionary getDictionary() {
        if(this.dict==null) {
            List<SAMSequenceRecord> ssrs = new ArrayList<>(this.seq2index.size());
            try {
            for( final TwoBitIndex twoBitIndex: this.seq2index.values()) {
                this.channel.position(twoBitIndex.offset);
                final  int length = readInt();
                ssrs.add(new SAMSequenceRecord(twoBitIndex.name, length));
                }
            } catch(IOException err) {
                throw new RuntimeIOException(err);
            }
            this.dict = new SAMSequenceDictionary(ssrs);
        }
        return dict;
    }
    
    private byte valToNt(int b) {
        /*
        #define MASKED_BASE_BIT 8
        
         Numerical values for bases. 
#define T_BASE_VAL 0
#define U_BASE_VAL 0
#define C_BASE_VAL 1
#define A_BASE_VAL 2
#define G_BASE_VAL 3
#define N_BASE_VAL 4  Used in 1/2 byte representation. 

            valToNt[T_BASE_VAL] = valToNt[T_BASE_VAL|MASKED_BASE_BIT] = 't';
    valToNt[C_BASE_VAL] = valToNt[C_BASE_VAL|MASKED_BASE_BIT] = 'c';
    valToNt[A_BASE_VAL] = valToNt[A_BASE_VAL|MASKED_BASE_BIT] = 'a';
    valToNt[G_BASE_VAL] = valToNt[G_BASE_VAL|MASKED_BASE_BIT] = 'g';
valToNt[N_BASE_VAL] = valToNt[N_BASE_VAL|MASKED_BASE_BIT] = 'n';
        */
        return valToNucl[b];
    }
    
    private ByteBuffer mustRead(int nBytes) throws IOException {
        this.byteBuffer.clear();
        this.byteBuffer.limit(nBytes);
        final int nRead=this.channel.read(this.byteBuffer);
        if(nRead!=nBytes) throw new IOException("cannot read "+nBytes+" byte(s) got "+nRead);
        this.byteBuffer.flip();
        return this.byteBuffer;
    }
    
    private int readInt()  throws IOException {
        return mustRead(Integer.BYTES).getInt();
    }
    
    private byte readByte()  throws IOException {
        return mustRead(Byte.BYTES).get();
    }
    
    private String readString() throws IOException {
        final int nchar = Byte.toUnsignedInt(this.readByte());
        final ByteBuffer charbuf = ByteBuffer.allocate(nchar);
        if(this.channel.read(charbuf)!=nchar) {
            throw new IOException("cannot read string("+nchar+")");
        }
        charbuf.flip();
        final byte array[]= charbuf.array();
        return new String(array);
    }
    
    @Override
    public void close() throws IOException {
        channel.close();
        }
    

private TwoBit getTwoBitSeqHeader(final String name) throws IOException
/* get the sequence header information using the cache.  Position file
 * right at data. */
{
if (this.seqCache!=null && this.seqCache.name.equals(name))
    {
    this.channel.position(this.dataOffsetCache);
    }
else
    {
    // fetch new and cache
    this.seqCache = readTwoBitSeqHeader(name);
    this.dataOffsetCache = this.channel.position();
    }
return this.seqCache;
}

    
  
private TwoBit readTwoBitSeqHeader(final String name) throws IOException
    /* read a sequence header, nBlocks and maskBlocks from a twoBit file,
     * leaving file pointer at data block */
    {
    final TwoBit twoBit = new TwoBit();
    twoBit.name = name;
    

    /* Find offset in index and seek to it */
    twoBitSeekTo(name);

    /* Read in seqSize. */
    twoBit.size = readInt();

    /* Read in blocks of N. */
    Block b= readBlockCoords();
    //tbf, isSwapped, &(twoBit->nBlockCount),
    //        &(twoBit->nStarts), &(twoBit->nSizes));
    twoBit.nBlockCount = b.count;
    twoBit.nStarts = b.starts;
    twoBit.nSizes = b.sizes;
    
    /* Read in masked blocks. */
    b = readBlockCoords();
    twoBit.maskBlockCount = b.count;
    twoBit.maskStarts = b.starts;
    twoBit.maskSizes = b.sizes;
    

    /* Reserved word. */
    twoBit.reserved = readInt();

    return twoBit;
    }

private void toUpperN(byte array[])  {
    
}

private void toLowerN(byte array[])  {
    
}


private Block readBlockCoords() throws IOException
/* Read in blockCount, starts and sizes from file. (Same structure used for
* both blocks of N's and masked blocks.) */
{
final Block block = new Block();
block.count = readInt();
if (block.count == 0)
    {
    block.starts = null;
    block.sizes = null;
    }
else
    {
    block.starts = new int[block.count];
    block.sizes = new int[block.count];
   final ByteBuffer buf =  ByteBuffer.allocate(block.count*Integer.BYTES);
   buf.order(this.byteOrder);
   this.channel.read(buf);
   buf.flip();
   block.starts =buf.asIntBuffer().array();
   
   buf.clear();
   this.channel.read(buf);
   buf.flip();
   block.sizes =buf.asIntBuffer().array();
   }
return block;
}


/* Seek to start of named record.  Abort if can't find it. */
private void twoBitSeekTo(final String name) throws IOException
{
    final TwoBitIndex index = this.seq2index.get(name);
    if (index == null) throw new IllegalArgumentException("not int dict" +name);
    this.channel.position(index.offset);
}

private int findGreatestLowerBound(int blockCount, int [] pos, int val)
/* Find index of greatest element in posArray that is less 
 * than or equal to val using a binary search. */
{
int startIx=0, endIx=blockCount-1, midIx;
int posVal;

for (;;)
    {
    if (startIx == endIx)
        {
    posVal = pos[startIx];
    if (posVal <= val || startIx == 0)
        return startIx;
    else
        return startIx-1;
    }
    midIx = ((startIx + endIx)>>1);
    posVal = pos[midIx];
    if (posVal < val)
        startIx = midIx+1;
    else
        endIx = midIx;
    }
}

public static void main(String[] args) {
    try {
        TwoBitSequenceFile r= new TwoBitSequenceFile(Paths.get("/home/lindenb/src/jvarkit-git/src/test/resources/rotavirus_rf.2bit"));
        byte array[]=r.query(new Interval("RF01",1,20),false);
        System.err.println(new String(array));
        r.close();
        System.err.println("done");
    } catch(Exception err) {
        err.printStackTrace();
    }
}
}
