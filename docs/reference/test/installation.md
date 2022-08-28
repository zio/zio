---
id: installation
title: "Installing ZIO Test"
sidebar_label: "Installation"
---

In order to use ZIO Test, we need to add the required configuration in our SBT settings:

```scala mdoc:passthrough
println(s"""```scala""")
println(s"""libraryDependencies ++= Seq(
  "dev.zio" %% "zio-test"          % "${zio.BuildInfo.version.split('+').head}" % Test,
  "dev.zio" %% "zio-test-sbt"      % "${zio.BuildInfo.version.split('+').head}" % Test,
  "dev.zio" %% "zio-test-magnolia" % "${zio.BuildInfo.version.split('+').head}" % Test
)
testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")""")
println(s"""```""")
```
