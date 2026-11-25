# Golden Testing API

> Reference for zio-json-golden: goldenTest, GoldenConfiguration, golden file names and lifecycle, sample generation, module root lookup and file format.

The `zio-json-golden` module provides golden tests (also called snapshot tests) for [ZIO Test](https://zio.dev/reference/test/). A golden test encodes a set of generated sample values with a type's `JsonEncoder`, stores the JSON in a file the first time it runs, and from then on fails whenever the encoder produces different JSON for the same samples. It catches unintended changes to the JSON a program writes, such as a renamed field or a changed sum-type encoding.

The public API lives in the `zio.json.golden` package: the `goldenTest` function, the `GoldenConfiguration` settings, the `GoldenSample` file format and a `filehelpers` object:

```scala
package object golden {
  def goldenTest[A: Tag: JsonEncoder](gen: Gen[Sized, A])(implicit
    trace: Trace,
    config: GoldenConfiguration
  ): Spec[TestEnvironment, Throwable]
}

final case class GoldenConfiguration(
  relativePath: String,
  sampleSize: Int
)

case class GoldenSample(samples: Json)
```

The module is available for the JVM only, on Scala 2.12, 2.13 and 3.

## Usage

A golden test is one entry in a ZIO Test suite. It needs a generator for the type and a `JsonEncoder` in scope. `DeriveGen` from `zio-test-magnolia` builds a generator for a case class:

```scala
import zio.json._
import zio.json.golden._
import zio.test._
import zio.test.magnolia.DeriveGen

final case class Banana(curvature: Double)

object Banana {
  implicit val codec: JsonCodec[Banana] = DeriveJsonCodec.gen[Banana]
}

object BananaGoldenSpec extends ZIOSpecDefault {
  def spec = suite("BananaGoldenSpec")(
    goldenTest(DeriveGen[Banana])
  )
}
```

The first run writes `src/test/resources/golden/Banana_new.json` and fails. After the file is renamed to `Banana.json`, later runs pass as long as the encoder output stays the same.

## Installation

Add the module to the test classpath:

```scala
libraryDependencies += "dev.zio" %% "zio-json-golden" % "1.1.3" % Test
```

The module depends on `zio-json`, `zio-test`, `zio-test-sbt` and `zio-test-magnolia`, so `DeriveGen` and the ZIO Test runner come with it.

## `goldenTest`

`goldenTest` creates one ZIO Test test for the type `A`:

```scala
package object golden {
  def goldenTest[A: Tag: JsonEncoder](gen: Gen[Sized, A])(implicit
    trace: Trace,
    config: GoldenConfiguration
  ): Spec[TestEnvironment, Throwable]
}
```

Its parameters and implicit requirements are:

| Parameter                     | Meaning                                                                                            |
| ----------------------------- | -------------------------------------------------------------------------------------------------- |
| `gen: Gen[Sized, A]`          | The ZIO Test generator that produces the sample values.                                            |
| `Tag[A]`                      | Supplies the type name used for the test name and the file name.                                  |
| `JsonEncoder[A]`              | The encoder under test. A `JsonCodec[A]` in implicit scope also provides it.                       |
| `config: GoldenConfiguration` | The sub-directory and sample size. The companion object supplies a default, so none is required.   |

The test is named `golden test for <Name>`. `<Name>` is the short name of the type from `Tag[A]`: no package, no enclosing object and no type arguments. A case class `Banana` nested in a spec object gives `Banana`, and `List[Int]` gives `List`.

## `GoldenConfiguration`

`GoldenConfiguration` holds the two settings of a golden test:

```scala
final case class GoldenConfiguration(
  relativePath: String,
  sampleSize: Int
)

object GoldenConfiguration {
  implicit val default: GoldenConfiguration
}
```

The fields and their defaults are:

| Field          | Default | Meaning                                                                                    |
| -------------- | ------- | ------------------------------------------------------------------------------------------ |
| `relativePath` | `""`    | A sub-directory of `src/test/resources/golden/` for this test's files. It may contain `/`. |
| `sampleSize`   | `20`    | The number of sample values the test generates and stores.                                 |

`goldenTest` takes the configuration as an implicit parameter. Without an implicit value in scope, the compiler uses `GoldenConfiguration.default` from the companion object. A local implicit value overrides it for the tests in its scope:

```scala
import zio.json._
import zio.json.golden._
import zio.test._
import zio.test.magnolia.DeriveGen

final case class Banana(curvature: Double)

object Banana {
  implicit val codec: JsonCodec[Banana] = DeriveJsonCodec.gen[Banana]
}

object BananaConfiguredSpec extends ZIOSpecDefault {
  def spec = suite("BananaConfiguredSpec")(
    {
      implicit val config: GoldenConfiguration =
        GoldenConfiguration.default.copy(relativePath = "fruit", sampleSize = 5)
      goldenTest(DeriveGen[Banana])
    }
  )
}
```

With this configuration the golden file is `src/test/resources/golden/fruit/Banana.json` and holds five samples.

Two golden tests whose types have the same short name, such as `List[Int]` and `List[String]`, or two `Banana` classes in different packages, write to the same file unless they use different `relativePath` values.

## Golden Files

Each golden test reads and writes files in one directory, `<module root>/src/test/resources/golden/<relativePath>/`. The test creates the directory when it is missing. The [module root](#module-root-lookup) is the directory of the build module that holds the test.

### File Names and Lifecycle

A golden test uses up to three files, named after the type:

| File                  | Written by the test                       | Read by the test |
| --------------------- | ----------------------------------------- | ---------------- |
| `<Name>.json`         | Never                                     | Yes              |
| `<Name>_new.json`     | When `<Name>.json` does not exist         | No               |
| `<Name>_changed.json` | When the output differs from `<Name>.json` | No               |

Each run generates the samples and then takes one of three paths:

- **No `<Name>.json`:** the test writes the samples to `<Name>_new.json`, replacing any earlier `_new` file, and fails with the message `No existing golden test for <path to Name.json>. Remove _new from the suffix and re-run the test.`
- **`<Name>.json` exists and the samples are equal to it:** the test passes.
- **`<Name>.json` exists and the samples differ:** the test writes the new samples to `<Name>_changed.json`, replacing any earlier `_changed` file, and fails. The failure shows a ZIO Test diff between the stored samples (expected) and the new ones (obtained).

The test never renames or deletes a file. Accepting new output means renaming `<Name>_new.json` to `<Name>.json`, or copying `<Name>_changed.json` over `<Name>.json` and deleting the `_changed` file. A leftover `_changed` file does not affect later runs. The `<Name>.json` files belong in version control, because the tests compare against them.

### Comparison

The test parses `<Name>.json` into a `GoldenSample` and compares it with the new samples as `Json` values. Whitespace and the order of fields inside an object do not affect the result. The order of the samples and every field value do.

### File Format

A golden file is the JSON encoding of `GoldenSample`, an object with one field, `samples`, that holds an array of the encoded samples:

```scala
case class GoldenSample(samples: Json)

object GoldenSample {
  implicit val jsonCodec: JsonCodec[GoldenSample]
}
```

The test writes the file in UTF-8 with `toJsonPretty`, so it uses two-space indentation and a space before each colon. A file for three `Banana` samples looks like this:

```json
{
  "samples" : [
    {
      "curvature" : 0.6983764484234759
    },
    {
      "curvature" : 0.6062659035461998
    },
    {
      "curvature" : 0.00503862614288475
    }
  ]
}
```

## Sample Generation

The test draws values from `gen` until it has `sampleSize` of them, encodes each one with `JsonEncoder#toJsonAST`, and keeps them in order. A value whose `JsonEncoder#toJsonAST` returns a `Left` is skipped and does not count toward `sampleSize`. The generator runs in the test's environment, so it uses ZIO Test's `Sized` service and the test `Random` service.

A golden test can only pass if the generator returns the same values on every run. ZIO Test's test `Random` starts from the same seed in each run, so generators built from `Gen` and `DeriveGen` are repeatable. A generator that reads the clock, the file system, a live random source or other outside state is not, and its test fails on every run.

Generators with small ranges or a few fixed cases give samples that are easier to review in a diff, and a smaller `sampleSize` gives a shorter file.

## Module Root Lookup

The module root is the directory that holds `src/test/resources/golden/`. The test finds it in one of two ways:

1. When the system property `zio.json.golden.moduleRoot` is set, its value is the module root.
2. Otherwise the test starts from the class-path root of the test classes, walks up to the nearest directory named `target`, and uses that directory's parent. This matches sbt 1, which puts test classes under `<module>/target/`.

When the test classes have no `target` directory above them and the property is not set, the test fails with a `NullPointerException`. Build tools that place classes elsewhere, such as sbt 2 (under the build's root `target/out/`) or scala-cli, need the property. The test reads it from the JVM that runs the tests, so in sbt set it on a forked test JVM:

```scala
// sbt 2, with `Test / fork := true`
Test / javaOptions += s"-Dzio.json.golden.moduleRoot=${baseDirectory.value}"
```

The name of the property is also available as `filehelpers.moduleRootProperty`.

## File Helpers

The `zio.json.golden.filehelpers` object holds the file operations `goldenTest` uses. They are public, but most projects never call them:

```scala
object filehelpers {
  val moduleRootProperty: String
  def getRootDir(file: File): Task[File]
  def createGoldenDirectory(pathToDir: String): Task[Path]
  def writeSampleToFile(path: Path, sample: GoldenSample): IO[IOException, Unit]
  def readSampleFromFile(path: Path): Task[GoldenSample]
}
```

Their behaviour is:

- `moduleRootProperty` is the string `"zio.json.golden.moduleRoot"`.
- `getRootDir` walks up from `file` and returns the nearest directory named `target`, including `file` itself.
- `createGoldenDirectory` resolves `pathToDir` against the module root, creates the directory if it is missing, and returns its path.
- `writeSampleToFile` writes `sample` to `path` as pretty-printed UTF-8 JSON, replacing the file's contents.
- `readSampleFromFile` reads the first JSON value from `path` and decodes it as a `GoldenSample`.

## See Also

- [Catch Unintended Changes to Your JSON with Golden Tests](../how-to/golden-testing.md) for the step-by-step workflow.
- [JSON Shapes and Round Trips](../explanation/wire-format.md) for the kinds of change a golden test catches.
- [JsonEncoder](json-encoder.md) for the encoder a golden test checks.
