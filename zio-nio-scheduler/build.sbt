name := "zio-nio-scheduler"
version := "0.1.0"
scalaVersion := "2.13.13"
organization := "com.bounty"

libraryDependencies ++= Seq(
  "dev.zio" %% "zio" % "2.1.5",
  "dev.zio" %% "zio-test" % "2.1.5" % Test,
  "dev.zio" %% "zio-test-sbt" % "2.1.5" % Test,
  "org.openjdk.jmh" % "jmh-core" % "1.37",
  "org.openjdk.jmh" % "jmh-generator-annprocess" % "1.37"
)

testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")

scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-Xfatal-warnings"
)

// JMH benchmark settings
lazy val jmhSettings = Seq(
  fork := true,
  javaOptions += "-Djmh.ignoreLock=true"
)
