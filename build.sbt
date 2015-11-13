name := "remote-pair-server-monitor"

version := "1.0"

scalaVersion := "2.11.7"

sbtVersion in ThisBuild := "0.13.9"

ivyScala := ivyScala.value map {
  _.copy(overrideScalaVersion = true)
}

libraryDependencies in ThisBuild ++= Seq(
  "org.scala-lang.modules" %% "scala-swing" % "1.0.1",
  "io.reactivex" %% "rxscala" % "0.25.0",
  "io.reactivex" % "rxswing" % "0.21.0",
  "com.github.benhutchison" %% "scalaswingcontrib" % "1.5",
  "com.thoughtworks" %% "remote-pair-server" % "0.9.0",
  "commons-lang" % "commons-lang" % "2.6",
  "commons-io" % "commons-io" % "2.0.1",
  "org.scalaz" %% "scalaz-core" % "7.1.3",
  "org.scalaz" %% "scalaz-effect" % "7.1.3",
  "org.json4s" %% "json4s-native" % "3.2.11",
  "org.json4s" %% "json4s-core" % "3.2.11",
  "org.json4s" %% "json4s-ext" % "3.2.11",
  "com.softwaremill.quicklens" %% "quicklens" % "1.4.2",
//  "com.github.julien-truffaut" %% "monocle-core" % "1.2.0-M1",
//  "com.github.julien-truffaut" %% "monocle-generic" % "1.2.0-M1",
//  "com.github.julien-truffaut" %% "monocle-macro" % "1.2.0-M1",
//  "com.github.julien-truffaut" %% "monocle-state" % "1.2.0-M1",
  "ch.qos.logback" % "logback-classic" % "1.1.2",
  "org.slf4j" % "slf4j-api" % "1.7.7",
  "org.specs2" %% "specs2-mock" % "2.4.2" % "test",
  "org.specs2" %% "specs2" % "2.4.2" % "test",
  "org.apache.commons" % "commons-vfs2" % "2.0" % "test",
  "com.github.julien-truffaut" %% "monocle-law" % "1.2.0-M1" % "test",
  "io.netty" % "netty-all" % "5.0.0.Alpha1"
)

// for @Lenses macro support
addCompilerPlugin("org.scalamacros" %% "paradise" % "2.0.1" cross CrossVersion.full)
