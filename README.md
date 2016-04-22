Status of master branch build: [![Build Status](https://travis-ci.org/samtools/htsjdk.svg?branch=master)](https://travis-ci.org/samtools/htsjdk)

Status of downstream projects automatically built on top of the current htsjdk master branch. See [gatk-jenkins](https://gatk-jenkins.broadinstitute.org/view/HTSJDK%20Release%20Tests/) for detailed logs. Failure may indicate problems  in htsjdk, but may also be due to expected incompatibilities between versions, or unrelated failures in downstream projects.
- [Picard](https://github.com/broadinstitute/picard):  [![Build Status](https://gatk-jenkins.broadinstitute.org/buildStatus/icon?job=picard-on-htsjdk-master)](https://gatk-jenkins.broadinstitute.org/job/picard-on-htsjdk-master/)
- [GATK 4](https://github.com/broadinstitute/gatk): [![Build Status](https://gatk-jenkins.broadinstitute.org/buildStatus/icon?job=gatk-on-htsjdk-master)](https://gatk-jenkins.broadinstitute.org/job/gatk-on-htsjdk-master/)

## A Java API for high-throughput sequencing data (HTS) formats.  

HTSJDK is an implementation of a unified Java library for accessing
common file formats, such as [SAM][1] and [VCF][2], used for high-throughput
sequencing data.  There are also an number of useful utilities for 
manipulating HTS data.

Please see the [HTSJDK Documentation](http://samtools.github.io/htsjdk) for more information.

#### Licensing Information

Not all sub-packages of htsjdk are subject to the same license, so a license notice is included in each source file or sub-package as appropriate. Please check the relevant license notice whenever you start working with a part of htsjdk that you have not previously worked with to avoid any surprises. 

#### Java Minimum Version Support Policy

> **NOTE: _Effective November 24th 2015, HTSJDK has ended support of Java 7 and previous versions. Java 8 is now required_.**

We will support all Java SE versions supported by Oracle until at least six months after Oracle's Public Updates period has ended ([see this link](http://www.oracle.com/technetwork/java/eol-135779.html)).

Java SE Major Release | End of Java SE Oracle Public Updates | Proposed End of Support in HTSJDK | Actual End of Support in HTSJDK
---- | ---- | ---- | ----
6 | Feb 2013 | Aug 2013 | Oct 2015
7 | Apr 2015 | Oct 2015 | Oct 2015
8* | Mar 2017 | Sep 2017 | Sep 2017

* to be finalized

HTSJDK is migrating to semantic versioning (http://semver.org/). We will eventually adhere to it strictly and bump our major version whenever there are breaking changes to our API, but until we more clearly define what constitutes our official API, clients should assume that every release potentially contains at least minor changes to public methods.

[1]: http://samtools.sourceforge.net
[2]: http://vcftools.sourceforge.net/specs.html

