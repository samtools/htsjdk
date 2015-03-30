[![Build Status](https://travis-ci.org/samtools/htsjdk.svg?branch=master)](https://travis-ci.org/samtools/htsjdk)

I modify this package and now it can support hadoop hdfs. You should modify the configure file in src/java/hdfs.config.properties

Functions of read and write sam/bam/bai, metrics, IndexedFastaSequenceFile on Hdfs have been tested.  
#==============================================#
A Java API for high-throughput sequencing data (HTS) formats.  

HTSJDK is an implementation of a unified Java library for accessing
common file formats, such as [SAM][1] and [VCF][2], used for high-throughput
sequencing data.  There are also an number of useful utilities for 
manipulating HTS data.


Please see the [HTSJDK Documentation](http://samtools.github.io/htsjdk) for more information.

[1]: http://samtools.sourceforge.net
[2]: http://vcftools.sourceforge.net/specs.html
