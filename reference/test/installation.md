# Installing ZIO Test

> In order to use ZIO Test, we need to add the required configuration in our SBT settings:

In order to use ZIO Test, we need to add the required configuration in our SBT settings:

```scala
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-test"          % "2.1.25" % Test,
  "dev.zio" %% "zio-test-sbt"      % "2.1.25" % Test,
  "dev.zio" %% "zio-test-magnolia" % "2.1.25" % Test
)
```

If our SBT version is older than 1.8.0, we also need to add the test framework manually:

```scala
testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
```

**NOTE**: In order to use the live version of a service in our tests, we can use some new helpful test aspects e.g `withLiveClock`, `withLiveConsole`, `withLiveRandom`, `withLiveSystem`, etc.
