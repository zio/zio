---
id: junit-integration
title: "ZIO Test JUnit Integration"
---

JUnit provides a simple and elegant way to write, organize, and execute tests, making it an indispensable tool for developers striving to deliver high-quality code. In this section, we will explore how to leverage the power of JUnit with ZIO Test. 

A custom JUnit runner is provided for running ZIO Test specs under other build tools (like Maven, Gradle, Bazel, etc.) and under IDEs.

To get the runner, we need to add the equivalent of following dependency definition under our build tool:

```scala
libraryDependencies += "dev.zio" %% "zio-test-junit" % zioVersion % "test"
```

To make our spec appear as a JUnit test to build tools and IDEs, we can simple extend `zio.test.junit.JUnitRunnableSpec`:

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}
import zio.test.junit.JUnitRunnableSpec

object MySpec extends JUnitRunnableSpec {
  def spec = suite("MySpec")(
    test("test") {
      for {
        _ <- ZIO.unit
      } yield assertCompletes
    }
  )
}
```

Now, we can run our spec from the command line by running `sbt test`:

```bash
sbt:zio-quickstart-junit> test
+ MySpec
  + test
1 tests passed. 0 tests failed. 0 tests ignored.

Executed in 215 ms

[info] Completed tests
[success] Total time: 1 s, completed Jun 13, 2023, 4:39:27 PM
```

Or we can convert `MySpec` object to a scala `class` and annotate it with `@RunWith(classOf[ZTestJUnitRunner])`:

```scala mdoc:compile-only
import org.junit.runner.RunWith
import zio._
import zio.test._
import zio.test.junit.ZTestJUnitRunner

@RunWith(classOf[ZTestJUnitRunner])
class MySpec extends ZIOSpecDefault {
  def spec = suite("MySpec")(
    test("test") {
      for {
        _ <- ZIO.unit
      } yield assertCompletes
    }
  )
}
```

To run the above test using `sbt test` we also need to add the following line to our `build.sbt`:

```scala
libraryDependencies += "com.github.sbt" % "junit-interface" % "0.13.3" % Test
```

Now, we can run our spec by running `sbt test` from the command line:

```bash
sbt:zio-quickstart-junit> test
+ MySpec
  + test
[info] Passed: Total 1, Failed 0, Errors 0, Passed 1
[success] Total time: 1 s, completed Jun 13, 2023, 4:37:32 PM
```
