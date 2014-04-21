addSbtPlugin("net.tixxit" %% "sbt-benchmark" % "0.0.2")

libraryDependencies ++= Seq(
  "org.openjdk.jmh" % "jmh-core" % "0.5.6",
  "org.openjdk.jmh" % "jmh-generator-bytecode" % "0.5.6")
