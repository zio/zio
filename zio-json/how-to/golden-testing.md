# Catch Unintended Changes to Your JSON with Golden Tests

> Set up zio-json-golden snapshot tests, accept the generated golden file, and review or fix a change when the encoded JSON differs.

Goal: make a test fail whenever the JSON your encoder writes for a type changes, so a renamed field or a changed encoding never reaches other systems by accident.

A golden test stores the encoded JSON of a set of sample values in a file, the golden file, and compares every later run against it. The [Golden Testing API](../reference/golden-testing.md) reference describes every option, file and behaviour used below. The example type is a `Banana` with one field.

## 1. Add the Dependency

Add `zio-json-golden` to the test classpath. It brings ZIO Test, its sbt runner and `zio-test-magnolia` with it:

```scala
libraryDependencies += "dev.zio" %% "zio-json-golden" % "1.1.3" % Test
```

The module is JVM only. The test finds the module directory by looking for the `target` directory above the compiled test classes, which matches sbt 1. With sbt 2 or another build tool, also pass the module directory as a system property to a forked test JVM:

```scala
// sbt 2, with `Test / fork := true`
Test / javaOptions += s"-Dzio.json.golden.moduleRoot=${baseDirectory.value}"
```

## 2. Write the Golden Test

Add one `goldenTest` per type to a ZIO Test spec. It takes a generator for the type, and the type's `JsonEncoder` must be in scope; `DeriveGen` builds the generator for a case class:

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

Write a golden test for every type whose JSON leaves the program: HTTP request and response bodies, messages and stored documents.

## 3. Run the Test and Accept the Golden File

The first run has nothing to compare against, so it writes the samples to `src/test/resources/golden/Banana_new.json` and fails:

```text
+ BananaGoldenSpec
  - golden test for Banana
    ✗ No existing golden test for <module>/src/test/resources/golden/Banana.json. Remove _new from the suffix and re-run the test.
```

Open `Banana_new.json` and check that the JSON looks the way other systems expect. It holds 20 encoded samples:

```json
{
  "samples" : [
    {
      "curvature" : 0.6983764484234759
    },
    {
      "curvature" : 0.6062659035461998
    },
    ...
  ]
}
```

To accept it, remove the `_new` suffix, run the test again and commit the file:

```bash
mv src/test/resources/golden/Banana_new.json src/test/resources/golden/Banana.json
```

The second run generates the same samples, finds them equal to `Banana.json`, and passes.

## 4. Review a Failing Change, Then Accept or Fix It

Later, someone renames the field from `curvature` to `bend`. The derived encoder now writes `"bend"`, so the golden test fails, writes the new output to `Banana_changed.json`, and prints a diff of the stored samples against the new ones (trimmed here):

```text
- golden test for Banana
  ✗ There was a difference
    ...
    Diff -expected +obtained
    Seq(
      0 = Map(
        curvature = Num(value = 0.6983764484234759),
        bend = Num(value = 0.6983764484234759)
      ),
      ...
```

Decide whether the change is intended:

- **The change is intended** (the API is meant to change, and its consumers are ready): copy `Banana_changed.json` over `Banana.json`, delete `Banana_changed.json`, run the test again and commit the new golden file.
- **The change is not intended:** fix the code so it writes the old JSON again, and delete `Banana_changed.json`. Here, `@jsonField` keeps the old field name in JSON while the Scala field is called `bend`:

```scala
import zio.json._

final case class Banana(@jsonField("curvature") bend: Double)

object Banana {
  implicit val codec: JsonCodec[Banana] = DeriveJsonCodec.gen[Banana]
}
```

With this fix the test passes against the unchanged `Banana.json`.

## 5. Adjust the Configuration If Needed

A `GoldenConfiguration` in implicit scope changes the sub-directory of the golden file and the number of samples. Set `relativePath` when two tested types have the same short name, for example `List[Int]` and `List[String]`, which would otherwise both write `List.json`. Lower `sampleSize` to make the golden file shorter to review:

```scala
import zio.json._
import zio.json.golden._
import zio.test._
import zio.test.magnolia.DeriveGen

final case class Banana(curvature: Double)

object Banana {
  implicit val codec: JsonCodec[Banana] = DeriveJsonCodec.gen[Banana]
}

object FruitGoldenSpec extends ZIOSpecDefault {
  def spec = suite("FruitGoldenSpec")(
    {
      implicit val config: GoldenConfiguration =
        GoldenConfiguration.default.copy(relativePath = "fruit", sampleSize = 5)
      goldenTest(DeriveGen[Banana])
    },
    {
      implicit val config: GoldenConfiguration =
        GoldenConfiguration.default.copy(relativePath = "int-lists")
      goldenTest(Gen.listOfN(3)(Gen.int(0, 9)))
    }
  )
}
```

The `Banana` file is now `src/test/resources/golden/fruit/Banana.json` with five samples, and the list file is `src/test/resources/golden/int-lists/List.json`. Changing either setting for an existing test means generating and accepting a new golden file in the new place.

## Common Mistakes

These mistakes make a golden test fail for the wrong reason, or pass when it should not:

- **Editing a golden file by hand.** The test then compares the encoder against your edit, not against output it once produced. Change golden files only by accepting a `_new` or `_changed` file.
- **Using a generator that is not repeatable.** A generator that reads the clock, the file system or a live random source produces different samples on each run, so the test always fails. Build generators from ZIO Test's `Gen` and `DeriveGen` only.
- **Not committing the golden files.** Without `Banana.json` in version control, every fresh checkout or CI run starts again at step 3.
- **Committing the `_new` or `_changed` files.** They are review copies; the test never reads them.

## See Also

- [Golden Testing API](../reference/golden-testing.md) for `goldenTest`, `GoldenConfiguration`, the file lifecycle, sample generation and module root lookup.
- [JSON Shapes and Round Trips](../explanation/wire-format.md#pin-the-format-with-golden-tests) for the kinds of change a golden test catches.
- [Annotations](../reference/annotations.md) for `@jsonField` and the other annotations that control field names.
