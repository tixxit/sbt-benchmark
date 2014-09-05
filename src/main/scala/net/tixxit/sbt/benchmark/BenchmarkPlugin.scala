package net.tixxit.sbt.benchmark

import java.net.URLClassLoader

import scala.annotation.tailrec
import scala.collection.JavaConverters._

import sbt._
import Def.{ Setting, Classpath }
import Keys._

import org.openjdk.jmh.generators.core.{ BenchmarkGenerator, FileSystemDestination }
import org.openjdk.jmh.generators.asm.ASMGeneratorSource
import org.openjdk.jmh.runner.{ Runner, RunnerException }
import org.openjdk.jmh.runner.options.{ Options, OptionsBuilder }


object BenchmarkPlugin extends sbt.AutoPlugin {
  object autoImport {
    val benchmark = inputKey[Unit]("Runs the benchmarks")
    val jmhVersion = settingKey[String]("JMH version to pull-in")

    lazy val BenchmarkPrecompile = config("benchmark-precompile").extend(Runtime)
    lazy val Benchmark = config("benchmark").extend(BenchmarkPrecompile)
  }
  import autoImport.{ benchmark => benchmarkRun, _ }

  case class JmhGeneratedSources(sources: Seq[File], resources: Seq[File])
  val jmhGenBenchmarks = taskKey[JmhGeneratedSources]("Generate JMH benchmarks")

  override lazy val projectConfigurations =
    Seq(BenchmarkPrecompile, Benchmark)

  override lazy val projectSettings =
    commonSettings ++
    inConfig(Benchmark)(Defaults.configSettings) ++
    inConfig(Benchmark)(jmhGenSettings(BenchmarkPrecompile)) ++
    inConfig(BenchmarkPrecompile)(Defaults.configSettings) ++
    inConfig(BenchmarkPrecompile)(jmhPrecompileSettings(Benchmark))

  private def commonSettings: Seq[Setting[_]] = Seq(
    jmhVersion := "1.0",
    libraryDependencies += {
      "org.openjdk.jmh" % "jmh-core" % jmhVersion.value % "benchmark-precompile,benchmark"
    }
  )

  private def jmhPrecompileSettings(compileConfig: Configuration): Seq[Setting[_]] = Seq(
    sourceDirectory <<= (sourceDirectory in compileConfig),
    compile <<= compile dependsOn (compile in Compile)
  )

  private def jmhGenSettings(precompileConfig: Configuration): Seq[Setting[_]] = Seq(
    fork in run := true, // This is actually required for correctness.
    benchmarkRun := runBenchmarks(fullClasspath.value, (runner in run).value, streams.value),
    jmhGenBenchmarks := generateJmhBenchmark(
      sourceManaged.value,
      resourceManaged.value,
      (exportedProducts in precompileConfig).value,
      (fullClasspath in precompileConfig).value,
      streams.value
    ),
    compile <<= compile dependsOn (compile in precompileConfig),
    sourceGenerators <+= Def.task { jmhGenBenchmarks.value.sources },
    resourceGenerators <+= Def.task { jmhGenBenchmarks.value.resources }
  )

  private def runBenchmarks(cp: Classpath, scalaRun: ScalaRun, s: TaskStreams): Unit = {
    scalaRun.run("org.openjdk.jmh.Main", cp.map(_.data), Seq("-f", "1", ".*"), s.log)
  }

  private def listFilesRecursively(root: File)(pred: File => Boolean): List[File] = {
    def loop(fs0: List[File], files: List[File]): List[File] = fs0 match {
      case f :: fs if f.isDirectory => loop(fs ++ IO.listFiles(f), files)
      case f :: fs if pred(f) => loop(fs, f :: files)
      case _ :: fs => loop(fs, files)
      case Nil => files.reverse
    }

    loop(root :: Nil, Nil)
  }

  private def collectClasses(root: File): List[File] =
    listFilesRecursively(root)(_.getName endsWith ".class")

  private def copy(from: File, to: File): List[File] = for {
    src <- listFilesRecursively(from)(_ => true)
    tail <- IO.relativize(from, src)
    dest = new File(to, tail)
    _ = IO.move(src, dest)
  } yield dest

  // Courtesy of Doug Tangren (https://groups.google.com/forum/#!topic/simple-build-tool/CYeLHcJjHyA)
  private def withClassLoader[A](cp: Classpath)(f: => A): A = {
    val originalLoader = Thread.currentThread.getContextClassLoader
    val jmhLoader = classOf[BenchmarkGenerator].getClassLoader
    val classLoader = new URLClassLoader(cp.map(_.data.toURI.toURL).toArray, jmhLoader)
    try {
      Thread.currentThread.setContextClassLoader(classLoader)
      f
    } finally {
      Thread.currentThread.setContextClassLoader(originalLoader)
    }
  }

  private def generateJmhBenchmark(sourceDir: File, resourceDir: File, benchmarkClasspath: Classpath, fullClaspath: Classpath, s: TaskStreams): JmhGeneratedSources = {
    IO.withTemporaryDirectory { tmpDir =>
      val tmpResourceDir = new File(tmpDir, "resources")
      val tmpSourceDir = new File(tmpDir, "sources")

      withClassLoader(fullClaspath) {
        val source = new ASMGeneratorSource
        val destination = new FileSystemDestination(tmpResourceDir, tmpSourceDir)
        val generator = new BenchmarkGenerator

        source.processClasses(benchmarkClasspath.flatMap(f => collectClasses(f.data)).asJava)
        generator.generate(source, destination)
        generator.complete(source, destination)
        if (destination.hasErrors) {
          s.log.error("JMH Benchmark generator failed")
          for (e <- destination.getErrors.asScala) {
            s.log.error(e.toString)
          }
        }
      }

      JmhGeneratedSources(copy(tmpSourceDir, sourceDir), copy(tmpResourceDir, resourceDir))
    }
  }
}
