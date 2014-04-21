package net.tixxit.sbt.benchmark

import scala.annotation.tailrec
import scala.collection.JavaConverters._

import sbt._
import Def.{ Setting, Classpath }
import Keys._

import org.openjdk.jmh.generators.core.{ BenchmarkGenerator, FileSystemDestination }
import org.openjdk.jmh.generators.bytecode.ASMGeneratorSource

object BenchmarkPlugin extends Plugin {

  lazy val BenchmarkPrecompile = config("benchmark-precompile").extend(Runtime)
  lazy val Benchmark = config("benchmark").extend(Runtime)

  case class JmhGeneratedSources(sources: Seq[File], resources: Seq[File])

  object BenchmarkKeys {
    val jmhGenBenchmarks = taskKey[JmhGeneratedSources]("Generate JMH benchmarks")
  }

  import BenchmarkKeys._

  object benchmark {
    lazy val settings: Seq[Setting[_]] =
      inConfig(Benchmark)(Defaults.configSettings) ++
      inConfig(Benchmark)(jmhGenSettings(BenchmarkPrecompile)) ++
      inConfig(BenchmarkPrecompile)(Defaults.configSettings) ++
      inConfig(BenchmarkPrecompile)(jmhPrecompileSettings(Benchmark))

    def jmhPrecompileSettings(compileConfig: Configuration): Seq[Setting[_]] = Seq(
      sourceDirectory <<= (sourceDirectory in compileConfig)
    )

    def jmhGenSettings(precompileConfig: Configuration): Seq[Setting[_]] = Seq(
      jmhGenBenchmarks := generateJmhBenchmark(
        sourceManaged.value,
        resourceManaged.value,
        (exportedProducts in precompileConfig).value,
        streams.value),
      compile <<= compile dependsOn (compile in precompileConfig),
      sourceGenerators <+= Def.task { jmhGenBenchmarks.value.sources },
      resourceGenerators <+= Def.task { jmhGenBenchmarks.value.resources }
    )
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

  // Courtesy of Doub Tangren (https://groups.google.com/forum/#!topic/simple-build-tool/CYeLHcJjHyA)
  private def withClassLoader[A](classLoader: ClassLoader)(f: => A): A = {
    val real = Thread.currentThread.getContextClassLoader
    try {
      Thread.currentThread.setContextClassLoader(classLoader)
      f
    } finally {
      Thread.currentThread.setContextClassLoader(real)
    }
  }

  private def generateJmhBenchmark(sourceDir: File, resourceDir: File, cp: Classpath, s: TaskStreams): JmhGeneratedSources = {
    IO.withTemporaryDirectory { tmpDir =>
      val tmpResourceDir = new File(tmpDir, "resources")
      val tmpSourceDir = new File(tmpDir, "sources")

      withClassLoader(classOf[BenchmarkGenerator].getClassLoader) {
        val source = new ASMGeneratorSource
        val destination = new FileSystemDestination(tmpResourceDir, tmpSourceDir)
        val generator = new BenchmarkGenerator

        source.processClasses(cp.flatMap(f => collectClasses(f.data)).asJava)
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
