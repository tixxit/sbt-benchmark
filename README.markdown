SBT Benchmark
=============

Run JMH benchmarks from within SBT.

Getting Started
---------------

Create a new file `project/benchmark.sbt` for the benchmark SBT plugin:

    addSbtPlugin("net.tixxit" %% "sbt-benchmark" % "0.0.2")
    
    libraryDependencies ++= Seq(
      "org.openjdk.jmh" % "jmh-core" % "0.5.6",
      "org.openjdk.jmh" % "jmh-generator-bytecode" % "0.5.6")

Add the `BenchmarkPlugin.benchmark.settings` to your project settings:

    import net.tixxit.sbt.benchmark.BenchmarkPlugin._

    lazy val myProject = Project(
      ...
    ).settings(benchmark.settings)

Write some JMH benchmarks in `myProject/src/benchmark/...`.

Coming soon: running your benchmarks!

Gotchas
-------

This is implemented as a 2 step compilation process, since benchmarking tools
like JMH require the class files exist so it can generate wrappers for the
benchmarks. The current implementation uses JMH for source generation in SBT
for the benchmark configuration. This means the precompilation step cannot use
source generators from the benchmark configuration, which means that you cannot
use SBT source generation to generated code required by the benchmark from
within the benchmark -- at least not directly.

I think we can actually hack around this by using the (re)source generators
from benchmark too, but filter out the JMH source generation tasks.
