# Golden Testing

> Golden testing (snapshot testing) checks that the JSON produced by your encoder does not change unexpectedly.
> The first time a golden test runs, it generates sample values, encodes them, and stores the JSON in a file. Later runs generate new samples and compare them with the stored file. If the output differs, the test fails and writes the new output next to the stored file, so you can review the change.

The `zio-json-golden` module provides the `goldenTest` function for [ZIO Test](https://zio.dev/reference/test/). It removes the need to write a hand-made expected string for each type.

The module is available for the JVM only, and it is a test-scope dependency:

```scala
libraryDependencies += "dev.zio" %% "zio-json-golden" % "1.1.2" % Test
```

## Writing a Golden Test

A golden test is a ZIO Test spec. The `goldenTest` function takes a `Gen` from ZIO Test for the type under test, and the type needs a `JsonEncoder` in scope.
`DeriveGen` from `zio.test.magnolia` creates a generator for a case class automatically.

We define the type with a codec in its companion object, and then write the spec:

```scala
import zio.json._
import zio.test._
import zio.test.magnolia.DeriveGen
import zio.json.golden._

case class Banana(curvature: Double)

object Banana {
  implicit val codec: JsonCodec[Banana] = DeriveJsonCodec.gen[Banana]
}

object BananaGoldenSpec extends ZIOSpecDefault {

  def spec = suite("BananaGoldenSpec")(
    goldenTest(DeriveGen[Banana])
  )
}
```

The test name is `golden test for <TypeName>`, and the type name is taken from the short name of the type.

## Lifecycle of a Golden File

The golden file is stored under `src/test/resources/golden/` in the module. The file name is the short type name with a JSON file extension, for example `src/test/resources/golden/Banana.json`. A golden file contains a JSON array with the encoded samples, and the default number of samples is 20.

The test behaves differently depending on which files exist:

- **No golden file exists:** the test creates `Banana_new.json` with the samples and fails with a message. To accept the output, remove the `_new` suffix from the file name and run the test again.
- **The golden file exists and the output matches:** the test passes.
- **The golden file exists and the output differs:** the test writes the new output to `Banana_changed.json` and fails. If the change is intended, copy the contents of `Banana_changed.json` over `Banana.json` and run the test again. If the change is not intended, the test has found a regression.

Commit the golden files to your repository, because the tests depend on them.

## Configuration

`GoldenConfiguration` has two fields. `relativePath` is a sub-directory under `golden/`, and `sampleSize` is the number of values to generate. Its default values are an empty `relativePath` and a `sampleSize` of `20`.

A `GoldenConfiguration` is looked up as an implicit value. Provide one in a local scope to change the settings for a single test:

```scala
import zio.json._
import zio.test._
import zio.test.magnolia.DeriveGen
import zio.json.golden._

case class Banana(curvature: Double)

object Banana {
  implicit val codec: JsonCodec[Banana] = DeriveJsonCodec.gen[Banana]
}

object BananaConfiguredSpec extends ZIOSpecDefault {

  def spec = suite("BananaConfiguredSpec")(
    {
      implicit val config: GoldenConfiguration =
        GoldenConfiguration.default.copy(relativePath = "fruit", sampleSize = 100)
      goldenTest(DeriveGen[Banana])
    }
  )
}
```

Set `relativePath` when two types have the same short name, because both golden files would otherwise be written to the same place. The `relativePath` option also keeps related files together, for example `golden/fruit/Banana.json`.

## Sample Generation

Golden tests compare the output of your encoder with samples from the generator, so the generator must return the same values on each run. Avoid generators that depend on the clock, the file system or other external state.

Generators with a fixed range or a small set of cases are easier to review than generators with large ranges, because the golden file grows with `sampleSize`.

## Locating the Module Root

The module root is the directory that holds `src/test/resources/golden/`. By default, the test walks up from its classes to the enclosing `target/` directory, which matches the layout used by sbt 1.

Build tools that place test classes somewhere else, such as sbt 2 under the build's root `target/out/`, must pass the module root with the `zio.json.golden.moduleRoot` system property:

```scala
// sbt 2, with `Test / fork := true`
Test / javaOptions += s"-Dzio.json.golden.moduleRoot=${baseDirectory.value}"
```

## Common Mistakes

If a golden file is edited by hand, the test compares the sample with the edited file. Change golden files only through the `_new` and `_changed` workflow above, so that the content always matches what the encoder produces.
