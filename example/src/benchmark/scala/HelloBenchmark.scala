package net.tixxit.sbt.benchmark.example

import org.openjdk.jmh.annotations.GenerateMicroBenchmark

class HelloBenchmark {
  @GenerateMicroBenchmark
  def sayHello = "hello"
}
