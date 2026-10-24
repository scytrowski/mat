lazy val vendorScaladocAssets = taskKey[File](
  "Generate Scaladoc and vendor all external HTML/CSS assets locally"
)

ThisBuild / organization := "me.cytrowski"
ThisBuild / version := {
  val tag = sys.env.getOrElse("GITHUB_REF", "")
  val v = "refs/tags/v"
  if (tag.startsWith(v)) tag.stripPrefix(v) else "SNAPSHOT"
}
ThisBuild / scalaVersion := "3.10.0" // renovate: datasource=github-releases depName=scala/scala3
ThisBuild / crossScalaVersions := Seq(
  "3.8.4", // renovate: datasource=github-releases depName=scala/scala3
  "3.9.0", // renovate: datasource=github-releases depName=scala/scala3
  "3.10.0" // renovate: datasource=github-releases depName=scala/scala3
)
// Scala 3.8.x, 3.9.x and 3.10.x share the `_3` artifact suffix. Cross-build
// all supported versions for validation, but publish one artifact to avoid
// duplicate coordinates in the release repository.
ThisBuild / publish / skip := scalaVersion.value != crossScalaVersions.value.head
ThisBuild / coverageMinimumStmtTotal := 90.0
ThisBuild / coverageMinimumBranchTotal := 85.0
ThisBuild / coverageFailOnMinimum := true
ThisBuild / description := "Scala 3 macro library for compile-time materialization of literal types, tuples, named tuples, products, unions, intersections and singleton-sum ADTs"
ThisBuild / licenses := Seq(
  "MIT" -> uri("https://github.com/scytrowski/mat/blob/main/LICENSE")
)
ThisBuild / homepage := Some(uri("https://cytrowski.me/projects/mat"))
ThisBuild / scmInfo := Some(
  ScmInfo(
    browseUrl = uri("https://github.com/scytrowski/mat"),
    connection = "scm:git:https://github.com/scytrowski/mat.git",
    devConnection = Some("scm:git:https://github.com/scytrowski/mat.git")
  )
)
ThisBuild / developers := List(
  Developer(
    id = "scytrowski",
    name = "Szymon Cytrowski",
    email = "szymon@cytrowski.me",
    url = uri("https://cytrowski.me")
  )
)
ThisBuild / versionScheme := Some("early-semver")

resolvers +=
  "Sonatype OSS Releases" at "https://s01.oss.sonatype.org/content/repositories/releases"

lazy val Benchmark = config("benchmark") extend Compile

Global / commands += Command.args(
  "benchmarkMatrix",
  "<output-tsv> <repetitions>"
) { (initialState, args) =>
  if args.length != 2 then
    sys.error("Usage: benchmarkMatrix <output-tsv> <repetitions>")

  val repetitions = args(1).toInt
  if repetitions < 1 then sys.error("repetitions must be a positive integer")

  val root = Project.extract(initialState).get(baseDirectory)
  val output = file(args(0)).getAbsoluteFile
  val inputDir = root / "target" / "benchmark-inputs"
  IO.delete(inputDir)
  IO.createDirectory(inputDir)
  val cases = for
    kind <- Seq("tuple", "product", "union")
    size <- Seq(16, 24, 32)
  yield (kind, size)

  def compileOnce(state: State, kind: String, size: Int, iteration: String): (State, Long) =
    val original = root / "src" / "benchmark" / "scala" / kind / s"Benchmark$size.scala"
    val generated = inputDir / s"$kind-$size-$iteration.scala"
    IO.write(generated, IO.read(original) + s"\n// benchmark iteration: $iteration\n")

    val configured = Project.extract(state).appendWithSession(
      Seq(Benchmark / unmanagedSources := Seq(generated)),
      state
    )
    val outputDirectory = Project.extract(configured).get(Benchmark / Keys.classDirectory)
    IO.delete(outputDirectory)

    val started = System.nanoTime()
    val (nextState, _) = Project.extract(configured).runTask(Benchmark / compile, configured)
    (nextState, (System.nanoTime() - started) / 1000000L)

  var state = initialState
  val results = scala.collection.mutable.ArrayBuffer.empty[(String, Int, Long)]

  for (kind, size) <- cases do
    println(s"Warming up $kind benchmark with $size elements")
    val (afterWarmup, _) = compileOnce(state, kind, size, s"warmup-${java.util.UUID.randomUUID()}")
    state = afterWarmup

    val timings = scala.collection.mutable.ArrayBuffer.empty[Long]
    for run <- 1 to repetitions do
      println(s"Running $kind benchmark with $size elements (run $run/$repetitions)")
      val (nextState, elapsed) = compileOnce(
        state,
        kind,
        size,
        s"run-${java.util.UUID.randomUUID()}"
      )
      state = nextState
      timings += elapsed

    val median = timings.sorted.apply((timings.size - 1) / 2)
    results += ((kind, size, median))
    println(s"Completed in $median ms (median of $repetitions runs)")

  IO.write(
    output,
    "benchmark\tsize\telapsed_ms\n# benchmark-protocol=2\n" +
      results.map { case (kind, size, elapsed) => s"$kind\t$size\t$elapsed\n" }.mkString
  )
  println(s"Benchmark results written to $output")
  state
}

lazy val root = (project in file("."))
  .configs(Benchmark)
  .settings(
    name := "mat",
    inConfig(Benchmark)(Defaults.configSettings),
    Benchmark / unmanagedSourceDirectories := Seq.empty,
    Benchmark / unmanagedSources := {
      val benchmarkKind = sys.props.getOrElse("mat.benchmark", "tuple")
      val benchmarkSize = sys.props.getOrElse("mat.size", "16")
      Seq(
        baseDirectory.value / "src" / "benchmark" / "scala" / benchmarkKind /
          s"Benchmark$benchmarkSize.scala"
      )
    },
    vendorScaladocAssets := Def.uncached {
      val log = streams.value.log
      ScaladocAssets.vendor((Compile / doc).value, log)
    },
    Compile / doc / scalacOptions ++= Seq(
      "-project",
      name.value,
      "-project-version",
      version.value,
      "-versions-dictionary-url",
      "/projects/mat/docs/versions.json"
    ),
    // https://mvnrepository.com/artifact/org.scalatest/scalatest
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.20" % Test
  )
