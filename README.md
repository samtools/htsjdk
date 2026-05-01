[![Build and Test](https://github.com/samtools/htsjdk/actions/workflows/tests.yml/badge.svg?branch=master&event=push)](https://github.com/samtools/htsjdk/actions/workflows/tests.yml)
[![Maven Central](https://img.shields.io/maven-central/v/com.github.samtools/htsjdk.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/com.github.samtools/htsjdk)
[![License](http://img.shields.io/badge/license-MIT-blue.svg)](https://github.com/samtools/htsjdk)
[![Language](http://img.shields.io/badge/language-java-brightgreen.svg)](https://www.java.com/)
## A Java API for high-throughput sequencing data (HTS) formats.

HTSJDK is an implementation of a unified Java library for accessing
common file formats, such as [SAM][1] and [VCF][2], used for high-throughput
sequencing data.  There are also a number of useful utilities for 
manipulating HTS data.

> **NOTE: _HTSJDK has only partial support for the latest Variant Call Format Specification.  VCFv4.3 can be read but not written, VCFv4.4 can be read in lenient mode only, and there is no support for BCFv2.2._**

> **NOTE: _HTSJDK now supports both reading and writing CRAM 3.1 files.  CRAM 3.1 write support includes all codecs defined in the specification (rANS Nx16, adaptive arithmetic Range coder, FQZComp, Name Tokenisation, and STRIPE), configurable compression profiles (FAST, NORMAL, SMALL, ARCHIVE), and trial compression for automatic codec selection.  Files produced by htsjdk are interoperable with samtools/htslib._**

### Documentation & Getting Help

API documentation for all versions of HTSJDK since `1.128` are available through [javadoc.io](http://www.javadoc.io/doc/com.github.samtools/htsjdk).

If you believe you have found a bug or have an issue with the library please: 
1. Search the open and recently closed issues to ensure it has not already been reported;
1. Then log an issue.

To receive announcements of releases and other significant project news please subscribe to the [htsjdk-announce](https://groups.google.com/forum/#!forum/htsjdk-announce) google group.

### Building HTSJDK

HTSJDK is built using [gradle](http://gradle.org/).

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

1. Select from the menu: `File -> New -> Project from Existing Sources`
2. In the resulting dialog, chose `Import from existing model`, select `Gradle` and `Next`
3. Choose the `default gradle wrapper` and `Finish`.

From time to time if dependencies change in htsjdk you may need to refresh the project from the `View -> Gradle` menu.

### Code style
Style guides files are included for Intellij and Eclipse.  These are a variation of the [Google Java Style](https://google.github.io/styleguide/javaguide.html) with 4 space indentation.
This style is suggested for new code but not rigidly checked.  We allow for contributors to deviate from the style when it improves clarity or to match surrounding code. 
Existing code does not necessarily conform to this and does not need to be modified to do so, but users are encouraged to correct the formatting of code that they modify.

### Licensing Information

Not all sub-packages of htsjdk are subject to the same license, so a license notice is included in each source file or sub-package as appropriate. 
Please check the relevant license notice whenever you start working with a part of htsjdk that you have not previously worked with to avoid any surprises. 
Broadly speaking the majority of the code is covered under the MIT license with the following notable exceptions:

* Much of the CRAM code is under the Apache License, Version 2
* Core `tribble` code (underlying VCF reading/writing amongst other things) is under LGPL

### Java Minimum Version Support Policy

Htsjdk requires Java 17 or later.  Support for Java 8 was dropped in the 4.0.0 release (August 2023).

Java SE Major Release | End of Java SE Oracle Public Updates / OpenJDK support | End of Support in HTSJDK
---- | ---- | ----
6  | Feb 2013 | Oct 2015
7  | Apr 2015 | Oct 2015
8  | Jan 2019 | Aug 2023 (htsjdk 4.0.0)
17 | Sep 2027 | Current minimum

 
### Meaning of the Htsjdk version number
We encourage downstream projects to use the most recent htsjdk release in order to have access to the most up to date features and bug fixes.  It is therefore important therefore to make upgrading to newer versions as easy as possible. We make a best effort to adhere to the following principles in order to minimize disruption to projects that depend on htsjdk:
* Avoid making breaking changes whenever possible. A breaking change is one which requires downstream projects to recompile against the new version of htsjdk or make changes to their source code.  These include both binary incompatibilities and source incompatibilities. 
* Deprecate and provide new alternatives instead of removing existing APIs.
* Document breaking changes in the release notes.
* Provide clear instructions for upgrading to new API's when breaking changes/ deprecations occur.
* Provide explanations for the rare cases when functionality is deprecated or removed without replacement.

We treat any accessible class/method/field as part of our API and attempt to minimize changes to it with the following exceptions:
  * The `htsjdk.samtools.cram` package and subpackages are considered unstable and are undergoing major changes.
  * Code which has not yet been released in a numbered version is considered unstable and subject to change without warning.
  * We consider changes to *public* code more disruptive than changes to *protected* code in classes that we believe are not generally subclassed by the downstream community.
  
Our current version number has 3 parts. **ex: 4.3.0**

* **Major version bumps (3.0.5 -> 4.0.0)** allow large changes to the existing APIs and require substantial changes in downstream projects. These are extremely rare.
* **Minor version bumps (4.2.0 -> 4.3.0)** may include additions to the API as well as breaking changes which may require recompiling downstream projects. We attempt to limit breaking changes as much as possible and generally most projects which depend on htsjdk should be able to update to a new minor version with no changes or only simple and obvious changes. We may introduce deprecations which suggest but don't mandate more complex code changes. Minor releases may also remove functionality which has been deprecated for a long time.
* **Patch version changes (4.0.1 -> 4.0.2)** include additions and possibly deprecations but no breaking changes.



[1]: http://samtools.sourceforge.net
[2]: http://vcftools.sourceforge.net/specs.html

