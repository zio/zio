---
id: index
title: "Introduction"
---

**ZIO Test** is a zero dependency testing library that makes it easy to test effectual programs. Begin by adding the required configuration in your SBT settings:

```scala
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-test"          % zioVersion % "test",
  "dev.zio" %% "zio-test-sbt"      % zioVersion % "test",
  "dev.zio" %% "zio-test-magnolia" % zioVersion % "test" // optional
)
testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
```
