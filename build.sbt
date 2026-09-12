ThisBuild / organization := "me.cytrowski"
ThisBuild / version := {
  val tag = sys.env.getOrElse("GITHUB_REF", "")
  val v = "refs/tags/v"
  if (tag.startsWith(v)) tag.stripPrefix(v) else "SNAPSHOT"
}
ThisBuild / scalaVersion := "3.8.4"
ThisBuild / crossScalaVersions := Seq("3.8.4", "3.9.0")
ThisBuild / name := "mat"
ThisBuild / description := "Scala 3 library for type-level materialization of constant values, tuples, products and singleton sums"
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
