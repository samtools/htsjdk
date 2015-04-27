[![Build Status](https://travis-ci.org/samtools/htsjdk.svg?branch=master)](https://travis-ci.org/samtools/htsjdk)

## A Java API for high-throughput sequencing data (HTS) formats.  

HTSJDK is an implementation of a unified Java library for accessing
common file formats, such as [SAM][1] and [VCF][2], used for high-throughput
sequencing data.  There are also an number of useful utilities for 
manipulating HTS data.

Please see the [HTSJDK Documentation](http://samtools.github.io/htsjdk) for more information.

#### Licensing Information

Not all sub-packages of htsjdk are subject to the same license, so a license notice is included in each source file or sub-package as appropriate. Please check the relevant license notice whenever you start working with a part of htsjdk that you have not previously worked with to avoid any surprises. 

#### Java Minimum Version Support Policy

We will support all Java SE versions supported by Oracle until at least six months after Oracle's Public Updates period has ended ([see this link](http://www.oracle.com/technetwork/java/eol-135779.html)).

Java SE Major Release | End of Java SE Oracle Public Updates | Proposed End of Support in HTSJDK | Actual End of Support in HTSJDK
---- | ---- | ---- | ----
6 | Feb 2013 | Aug 2013 | Oct 2015
7 | Apr 2015 | Oct 2015 | Oct 2015
8* | Mar 2017 | Sep 2017 | Sep 2017

* to be finalized

[1]: http://samtools.sourceforge.net
[2]: http://vcftools.sourceforge.net/specs.html
