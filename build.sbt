import de.johoop.testngplugin.TestNGPlugin._

name := "htsjdk"

version := "1.128"

scalaVersion := "2.11.4"

organization := "com.github.samtools"

libraryDependencies += "org.apache.commons" % "commons-jexl" % "2.1.1"

libraryDependencies += "commons-logging" % "commons-logging" % "1.2"

libraryDependencies += "org.xerial.snappy" % "snappy-java" % "1.1.1.3"

libraryDependencies += "org.testng" % "testng" % "6.8.8"

javaSource in Compile := baseDirectory.value / "src/java"

javaSource in Test := baseDirectory.value / "src/tests"

assemblySettings

testNGSettings

testNGSuites := Seq("src/tests/resources/testng.xml")

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}

publishMavenStyle := true

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

artifactName := { (sv: ScalaVersion, module: ModuleID, artifact: Artifact) =>
  val classifierStr = artifact.classifier match { case None => ""; case Some(c) => "-" + c }
  artifact.name + "-" + module.revision + classifierStr + "." + artifact.extension
}

crossPaths := false

javacOptions in Compile ++= Seq("-source", "1.6")
 
javacOptions in (Compile, compile) ++= Seq("-target", "1.6")

pomExtra := <url>http://samtools.github.io/htsjdk/</url>
  <licenses>
    <license>
      <name>MIT License</name>
      <url>http://opensource.org/licenses/MIT</url>
      <distribution>repo</distribution>
    </license>
  </licenses>
  <scm>
    <url>git@github.com:samtools/htsjdk.git</url>
    <connection>scm:git:git@github.com:samtools/htsjdk.git</connection>
  </scm>
  <developers>
    <developer>
      <id>picard</id>
      <name>Picard Team</name>
      <url>http://broadinstitute.github.io/picard/</url>
    </developer>
  </developers>