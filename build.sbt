sbtPlugin := true

name := "sbt-benchmark"

organization := "net.tixxit"

version := "0.0.2"

scalaVersion := "2.10.3"

libraryDependencies ++= Seq(
  "org.openjdk.jmh" % "jmh-core" % "0.5.6",
  "org.openjdk.jmh" % "jmh-generator-bytecode" % "0.5.6")
