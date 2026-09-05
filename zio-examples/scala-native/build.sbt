scalaVersion   := "3.9.0"
publish / skip := true

libraryDependencies ++= Seq(
  "dev.zio" %% "zio"          % "2.1.25",
  "dev.zio" %% "zio-streams"  % "2.1.25",
  "dev.zio" %% "zio-test"     % "2.1.25" % Test,
  "dev.zio" %% "zio-test-sbt" % "2.1.25" % Test
)

testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
