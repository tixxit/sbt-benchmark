import bintray.Keys._

sbtPlugin := true

name := "sbt-benchmark"

organization := "net.tixxit"

version := "0.1.1"

bintrayResolverSettings

libraryDependencies ++= Seq(
  "org.openjdk.jmh" % "jmh-core" % "1.0",
  "org.openjdk.jmh" % "jmh-generator-bytecode" % "1.0")

licenses += ("MIT", url("http://opensource.org/licenses/MIT"))

publishMavenStyle := false

publishArtifact in (Compile, packageDoc) := false

bintrayPublishSettings

repository in bintray := "sbt-plugins"

bintrayOrganization in bintray := None

packageLabels in bintray := Seq("sbt", "sbt-plugin", "jmh", "benchmarking")
