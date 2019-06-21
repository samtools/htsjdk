[![Coverage Status](https://codecov.io/gh/samtools/htsjdk/branch/master/graph/badge.svg)](https://codecov.io/gh/samtools/htsjdk)
[![Build Status](https://travis-ci.org/samtools/htsjdk.svg?branch=master)](https://travis-ci.org/samtools/htsjdk)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.samtools/htsjdk/badge.svg)](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.github.samtools%22%20AND%20a%3A%22htsjdk%22)
[![License](http://img.shields.io/badge/license-MIT-blue.svg)](https://github.com/samtools/htsjdk)
[![Language](http://img.shields.io/badge/language-java-brightgreen.svg)](https://www.java.com/)
[![Join the chat at https://gitter.im/samtools/htsjdk](https://badges.gitter.im/samtools/htsjdk.svg)](https://gitter.im/samtools/htsjdk?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

Status of downstream projects automatically built on top of the current htsjdk master branch. See [gatk-jenkins](https://gatk-jenkins.broadinstitute.org/view/HTSJDK%20Release%20Tests/) for detailed logs. Failure may indicate problems  in htsjdk, but may also be due to expected incompatibilities between versions, or unrelated failures in downstream projects.
- [Picard](https://github.com/broadinstitute/picard):  [![Build Status](https://gatk-jenkins.broadinstitute.org/buildStatus/icon?job=picard-on-htsjdk-master)](https://gatk-jenkins.broadinstitute.org/job/picard-on-htsjdk-master/)
- [GATK 4](https://github.com/broadinstitute/gatk): [![Build Status](https://gatk-jenkins.broadinstitute.org/buildStatus/icon?job=gatk-on-htsjdk-master)](https://gatk-jenkins.broadinstitute.org/job/gatk-on-htsjdk-master/)

## A Java API for high-throughput sequencing data (HTS) formats.  

HTSJDK is an implementation of a unified Java library for accessing
common file formats, such as [SAM][1] and [VCF][2], used for high-throughput
sequencing data.  There are also an number of useful utilities for 
manipulating HTS data.

> **NOTE: _HTSJDK has only partial support for the latest Variant Call Format Specification.  VCFv4.3 can be read but not written and there is no support for BCFv2.2_**

### Documentation & Getting Help

API documentation for all versions of HTSJDK since `1.128` are available through [javadoc.io](http://www.javadoc.io/doc/com.github.samtools/htsjdk).

If you believe you have found a bug or have an issue with the library please a) search the open and recently closed issues to ensure it has not already been reported, then b) log an issue.

The project has a [gitter chat room](https://gitter.im/samtools/htsjdk) if you would like to chat with the developers and others involved in the project.

To receive announcements of releases and other significant project news please subscribe to the [htsjdk-announce](https://groups.google.com/forum/#!forum/htsjdk-announce) google group.

### Building HTSJDK

HTSJDK is now built using [gradle](http://gradle.org/).

A wrapper script (`gradlew`) is included which will download the appropriate version of gradle on the first invocation.

Example gradle usage from the htsjdk root directory:
 - compile and build a jar 
 ```
 ./gradlew
 ```
 or
 ```
 ./gradlew jar
 ```
 The jar will be in build/libs/htsjdk-\<version\>.jar where version is based on the current git commit.

 - run tests, a specific test class, or run a test and wait for the debugger to connect
 ```
 ./gradlew test

 ./gradlew test --tests AlleleUnitTest

 ./gradlew test --tests AlleleUnitTest --debug-jvm
 ```

- run tests and collect coverage information (report will be in `build/reports/jacoco/test/html/index.html`)
```
./gradlew jacocoTestReport
```

 - clean the project directory
 ```
 ./gradlew clean
 ```

 - build a monolithic jar that includes all of htsjdk's dependencies
 ```
 ./gradlew shadowJar
 ```
 
 - create a snapshot and install it into your local maven repository
 ```
 ./gradlew install
 ```

 - for an exhaustive list of all available targets
 ```
 ./gradlew tasks
 ```

### Create an HTSJDK project in IntelliJ
To create a project in IntelliJ IDE for htsjdk do the following:

1. Select fom the menu: `File -> New -> Project from Existing Sources`
2. In the resulting dialog, chose `Import from existing model`, select `Gradle` and `Next`
3. Choose the `default gradle wrapper` and `Finish`.

From time to time if dependencies change in htsjdk you may need to refresh the project from the `View -> Gradle` menu.

### Code style
Style guides files are included for Intellij and Eclipse.  These are a variation of the [Google Java Style](https://google.github.io/styleguide/javaguide.html) with 4 space indentation.
This style is suggested for new code but not rigidly checked.  We allow for contributors to deviate from the style when it improves clarity or to match surrounding code. Existing code does not necessarily conform to this and does not need to be modified to do so. 

### Licensing Information

Not all sub-packages of htsjdk are subject to the same license, so a license notice is included in each source file or sub-package as appropriate. 
Please check the relevant license notice whenever you start working with a part of htsjdk that you have not previously worked with to avoid any surprises. 
Broadly speaking the majority of the code is covered under the MIT license with the following notable exceptions:

* Much of the CRAM code is under the Apache License, Version 2
* Core `tribble` code (underlying VCF reading/writing amongst other things) is under LGPL
* Code supporting the reading/writing of SRA format is uncopyrighted & public domain

### Java Minimum Version Support Policy

Htsjdk currently targets Java 8 and is tested on both 8 and 11.
We do not currently plan to support versions of Java which are not long-term-support releases (ex: 9, 10, or 12).  

We will notify the community at least 6 months before we plan to stop support for Java 8.  

Java SE Major Release | End of Java SE Oracle Public Updates / OpenJDK support | Proposed End of Support in HTSJDK | Actual End of Support in HTSJDK
---- | ---- | ---- | ----
6  | Feb 2013 | Aug 2013 | Oct 2015
7  | Apr 2015 | Oct 2015 | Oct 2015
8  | Jan 2019 | TBD      | TBD
11 | Sep 2022 | TBD      | TBD


HTSJDK is migrating to semantic versioning (http://semver.org/). We will eventually adhere to it strictly and bump our major version whenever there are breaking changes to our API, but until we more clearly define what constitutes our official API, clients should assume that every release potentially contains at least minor changes to public methods.

[1]: http://samtools.sourceforge.net
[2]: http://vcftools.sourceforge.net/specs.html

