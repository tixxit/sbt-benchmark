sbtPlugin := true

name := "sbt-benchmark"

organization := "net.tixxit"

version := "0.0.3"

scalaVersion := "2.10.4"

libraryDependencies ++= Seq(
  "org.openjdk.jmh" % "jmh-core" % "1.0",
  "org.openjdk.jmh" % "jmh-generator-bytecode" % "1.0")
