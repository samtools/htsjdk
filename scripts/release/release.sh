#!/bin/sh

mvn -P sonatype-oss-release -Dresume=false release:clean release:prepare release:perform
