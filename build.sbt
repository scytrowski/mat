ThisBuild / organization := "io.github.scytrowski"
ThisBuild / version := "0.1.0"
ThisBuild / scalaVersion := "3.7.1"
ThisBuild / licenses := Seq("MIT" -> url("https://github.com/scytrowski/mat/blob/master/LICENSE"))
ThisBuild / homepage := Some(url("https://github.com/scytrowski/mat"))
ThisBuild / scmInfo := Some(
  ScmInfo(
    browseUrl = url("https://github.com/scytrowski/mat"),
    connection = "scm:git:git@github.com:scytrowski/mat.git"
  )
)
ThisBuild / developers := List(
  Developer(
    id = "scytrowski",
    name = "Szymon Cytrowski",
    email = "szym.cytrowski@gmail.com",
    url = url("https://github.com/scytrowski")
  )
)
ThisBuild / versionScheme := Some("early-semver")

resolvers +=
  "Sonatype OSS Releases" at "https://s01.oss.sonatype.org/content/repositories/releases"

lazy val root = (project in file("."))
  .settings(
    name := "mat",
    // https://mvnrepository.com/artifact/org.scalatest/scalatest
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.19" % Test
  )
