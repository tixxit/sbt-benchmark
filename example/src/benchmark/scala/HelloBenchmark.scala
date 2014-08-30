package net.tixxit.sbt.benchmark.example

import org.openjdk.jmh.annotations.Benchmark

class HelloBenchmark {
  @Benchmark
  def sayHello = "hello"
}
