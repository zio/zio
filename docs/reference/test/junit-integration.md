---
id: junit-integration
title: "Integrating ZIO Test with JUnit"
sidebar_label: "JUnit Integration"
---

Unit testing is an essential practice in software development, enabling developers to validate the correctness and reliability of their code. JUnit, a widely adopted testing framework, has emerged as a standard choice for Java applications. With its robust features and extensive ecosystem, JUnit simplifies the process of writing and executing tests, empowering developers to deliver high-quality software.

In this section, we will explore the integration of ZIO Test, a powerful testing library for functional programming in Scala, with JUnit. By combining the strengths of both frameworks, developers can efficiently test ZIO-based applications under different build tools and IDEs.

To streamline the testing process, a custom JUnit runner is provided specifically for running ZIO Test specifications. Thus, we can conduct testing of ZIO specs within alternative build tools, such as Maven, Gradle, Bazel, and various integrated development environments (IDEs).

By adding the necessary dependency definition to the build tool, developers can effortlessly incorporate the ZIO Test JUnit runner:

```scala
libraryDependencies += "dev.zio" %% "zio-test-junit" % zioVersion % "test"
```

To make our spec appear as a JUnit test to build tools and IDEs, we can simply extend `zio.test.junit.JUnitRunnableSpec`:

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
import zio.test.{test, _}
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

To see practical examples in action, we encourage you to check out [the example code base](https://github.com/zio/zio-quickstarts/tree/master/zio-quickstart-junit-integration) provided in the [ZIO Quickstarts](https://github.com/zio/zio-quickstarts/) project on GitHub. This code base will help you dive deeper into testing ZIO specs using JUnit within different build tools and IDEs, enabling you to enhance the quality and stability of your ZIO applications. Happy testing!