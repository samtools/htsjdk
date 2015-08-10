import com.typesafe.sbt.SbtGit._
import de.johoop.testngplugin.TestNGPlugin._
import sbt.Package.ManifestAttributes

name := "htsjdk"

val buildVersion = "1.138"

organization := "com.github.samtools"

libraryDependencies += "org.apache.commons" % "commons-jexl" % "2.1.1"

libraryDependencies += "commons-logging" % "commons-logging" % "1.1.1"

libraryDependencies += "org.xerial.snappy" % "snappy-java" % "1.0.3-rc3"

libraryDependencies += "org.apache.commons" % "commons-compress" % "1.4.1"

libraryDependencies += "org.tukaani" % "xz" % "1.5"
 
libraryDependencies += "org.apache.ant" % "ant" % "1.8.2"

libraryDependencies += "org.testng" % "testng" % "6.8.8"

unmanagedBase := baseDirectory.value

javaSource in Compile := baseDirectory.value / "src/java"

javaSource in Test := baseDirectory.value / "src/tests"

testNGSettings

testNGSuites := Seq("src/tests/resources/testng.xml")

autoScalaLibrary := false

publishMavenStyle := true

publishArtifact in Test := false

pomIncludeRepository := { _ => false}

val gitVersion = settingKey[String]("The head commit git hash.")

gitVersion := git.gitHeadCommit.value.get

val gitBranch = settingKey[String]("The git branch.")

gitBranch := git.gitCurrentBranch.value

val buildSnapshot = settingKey[Boolean]("Is this build a snapshot.")

buildSnapshot := false

version := {
  if (buildSnapshot.value) {
    s"$buildVersion-${gitVersion.value.substring(0, 7)}-SNAPSHOT"
  } else {
    s"$buildVersion"
  }
}

val implementationVersion = settingKey[String]("Implementation version.")

implementationVersion := {
  if (buildSnapshot.value)
    s"$buildVersion(${gitVersion.value})(SNAPSHOT)"
  else
    s"$buildVersion(${gitVersion.value})"
}

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

artifactName := { (sv: ScalaVersion, module: ModuleID, artifact: Artifact) =>
  val classifierStr = artifact.classifier match {
    case None => "";
    case Some(c) => "-" + c
  }
  artifact.name + "-" + module.revision + classifierStr + "." + artifact.extension
}

crossPaths := false

javacOptions in Compile ++= Seq("-source", "1.6")

javacOptions in(Compile, compile) ++= Seq("-target", "1.6")

packageOptions := Seq(ManifestAttributes(
  ("Implementation-Version", s"${implementationVersion.value}"),
  ("Implementation-Vendor", "Broad Institute")
))

assemblyJarName := s"${name.value}-${version.value}.jar"

assemblyMergeStrategy in assembly := {
  case x if Assembly.isConfigFile(x) =>
    MergeStrategy.concat
  case PathList(ps@_*) if Assembly.isReadme(ps.last) || Assembly.isLicenseFile(ps.last) =>
    MergeStrategy.rename
  case PathList("META-INF", path@_*) =>
    path map {
      _.toLowerCase
    } match {
      case ("manifest.mf" :: Nil) | ("index.list" :: Nil) | ("dependencies" :: Nil) =>
        MergeStrategy.discard
      case ps@(x :: xs) if ps.last.endsWith(".sf") || ps.last.endsWith(".dsa") =>
        MergeStrategy.discard
      case "plexus" :: xs =>
        MergeStrategy.discard
      case "spring.tooling" :: xs =>
        MergeStrategy.discard
      case "services" :: xs =>
        MergeStrategy.filterDistinctLines
      case ("spring.schemas" :: Nil) | ("spring.handlers" :: Nil) =>
        MergeStrategy.filterDistinctLines
      case _ => MergeStrategy.deduplicate
    }
  case "asm-license.txt" | "overview.html" =>
    MergeStrategy.discard
  case _ => MergeStrategy.deduplicate
}

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
