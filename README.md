[![Build and Test](https://github.com/samtools/htsjdk/actions/workflows/tests.yml/badge.svg?branch=master&event=push)](https://github.com/samtools/htsjdk/actions/workflows/tests.yml)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.samtools/htsjdk/badge.svg)](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.github.samtools%22%20AND%20a%3A%22htsjdk%22)
[![License](http://img.shields.io/badge/license-MIT-blue.svg)](https://github.com/samtools/htsjdk)
[![Language](http://img.shields.io/badge/language-java-brightgreen.svg)](https://www.java.com/)
[![Join the chat at https://gitter.im/samtools/htsjdk](https://badges.gitter.im/samtools/htsjdk.svg)](https://gitter.im/samtools/htsjdk?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

## A Java API for high-throughput sequencing data (HTS) formats.  

HTSJDK is an implementation of a unified Java library for accessing
common file formats, such as [SAM][1] and [VCF][2], used for high-throughput
sequencing data.  There are also a number of useful utilities for 
manipulating HTS data.

> **NOTE: _HTSJDK has only partial support for the latest Variant Call Format Specification.  VCFv4.3 can be read but not written and there is no support for BCFv2.2_**

### Documentation & Getting Help

API documentation for all versions of HTSJDK since `1.128` are available through [javadoc.io](http://www.javadoc.io/doc/com.github.samtools/htsjdk).

If you believe you have found a bug or have an issue with the library please a) search the open and recently closed issues to ensure it has not already been reported, then b) log an issue.

The project has a [gitter chat room](https://gitter.im/samtools/htsjdk) if you would like to chat with the developers and others involved in the project.

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
* Code supporting the reading/writing of SRA format is uncopyrighted & public domain

### Java Minimum Version Support Policy

Htsjdk currently targets Java 8 and is tested on both 8 and 11.

We intend to drop support for 8/11 and switch exclusively to 17+ in our next release (4.0.0).

Given our promise of 6 months of warning before a move off of 8, we will atttempt to provide 3.x releases on demand if
any critical bugs are discovered in the next 6 months.

Java SE Major Release | End of Java SE Oracle Public Updates / OpenJDK support | Proposed End of Support in HTSJDK | Actual End of Support in HTSJDK
---- | ---- |-----------------------------------| ----
6  | Feb 2013 | Aug 2013                          | Oct 2015
7  | Apr 2015 | Oct 2015                          | Oct 2015
8  | Jan 2019 | Feb 2022                          | TBD
11 | Sep 2022 | Feb 2022                          | TBD
17 | TBD      | TBD                               | TBD

 
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
  
Our current version number has 3 parts. **ex: 2.19.0**

* **Major version bumps (2.19.0 -> 3.0.0)** allow large changes to the existing API's and require substantial changes in downstream projects. These are extremely rare. 
* **Minor versions bumps ( 2.18.2 -> 2.19.0)** may include additions to the API and well as breaking changes which may require recompiling downstream projects. We attempt to limit breaking changes as much as possible and generally most projects which depend on htsjdk should be able to update to a new minor version with no changes or only simple and obvious changes. We may introduce deprecations which suggest but don't mandate more complex code changes. Minor releases may also remove functionality which has been deprecated for a long time.
* **Patch version changes (2.18.1 -> 2.18.2)** include additions and possibly deprecations but no breaking changes.



[1]: http://samtools.sourceforge.net
[2]: http://vcftools.sourceforge.net/specs.html

