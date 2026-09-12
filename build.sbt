ThisBuild / organization := "me.cytrowski"
ThisBuild / version := {
  val tag = sys.env.getOrElse("GITHUB_REF", "")
  val v = "refs/tags/v"
  if (tag.startsWith(v)) tag.stripPrefix(v) else "SNAPSHOT"
}
ThisBuild / scalaVersion := "3.8.4" // renovate: datasource=github-releases depName=scala/scala3
ThisBuild / crossScalaVersions := Seq(
  "3.8.4", // renovate: datasource=github-releases depName=scala/scala3
  "3.9.0" // renovate: datasource=github-releases depName=scala/scala3
)
// Scala 3.8.x and 3.9.x share the `_3` artifact suffix. Cross-build both
// versions for validation, but publish one artifact to avoid duplicate
// coordinates in the release repository.
ThisBuild / publish / skip := scalaVersion.value != crossScalaVersions.value.head
ThisBuild / coverageMinimumStmtTotal := 90.0
ThisBuild / coverageMinimumBranchTotal := 85.0
ThisBuild / coverageFailOnMinimum := true
ThisBuild / description := "Scala 3 library for compile-time materialization of literal values, tuples, products, unions, intersections and singleton-sum ADTs"
ThisBuild / licenses := Seq(
  "MIT" -> url("https://github.com/scytrowski/mat/blob/master/LICENSE")
)
ThisBuild / homepage := Some(url("https://github.com/scytrowski/mat"))
ThisBuild / scmInfo := Some(
  ScmInfo(
    browseUrl = url("https://github.com/scytrowski/mat"),
    connection = "scm:git:https://github.com/scytrowski/mat.git",
    devConnection = Some("scm:git:https://github.com/scytrowski/mat.git")
  )
)
ThisBuild / developers := List(
  Developer(
    id = "scytrowski",
    name = "Szymon Cytrowski",
    email = "szym.cytrowski@gmail.com",
    url = url("https://cytrowski.me")
  )
)
ThisBuild / versionScheme := Some("early-semver")

resolvers +=
  "Sonatype OSS Releases" at "https://s01.oss.sonatype.org/content/repositories/releases"

lazy val Benchmark = config("benchmark") extend Compile

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
    // https://mvnrepository.com/artifact/org.scalatest/scalatest
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.20" % Test
  )
