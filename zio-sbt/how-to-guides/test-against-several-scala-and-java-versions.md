# Test Against Several Scala and Java Versions

> The default testing strategy for ZIO SBT CI plugin is to run `sbt +test` on Corretto Java 17, 21 and 25. So this will generate the following job:

## Default Testing Strategy

```yaml
test:
  name: Test
  runs-on: ubuntu-latest
  continue-on-error: false
  strategy:
    fail-fast: false
    matrix:
      java: ['17', '21', '25']
  steps:
  - name: Setup Scala
    uses: actions/setup-java@v5
    with:
      distribution: corretto
      java-version: ${{ matrix.java }}
      check-latest: true
  - name: Cache Dependencies
    uses: coursier/cache-action@v6
  - name: Git Checkout
    uses: actions/checkout@v6
    with:
      fetch-depth: '0'
  - name: Test
    run: sbt --no-colors +test
```

The `sbt +test` command will run the `test` task for all submodules in the project against all Scala versions defined in the `crossScalaVersions` setting.

## Concurrent Testing Strategy

In some cases, we may have multiple submodules in our project and we want to test them concurrently using GitHub Actions matrix strategy.

The `ciTargetScalaVersions` setting key is used to define a mapping of project names to the Scala versions that should be used for testing phase of continuous integration (CI).

For example, suppose we have a project with the name "submoduleA" and we want to test it against Scala `2.12.20`, and for the "submoduleB" we want to test it against Scala `2.12.20` and `2.13.18` and `3.3.7`, We can define the `ciTargetScalaVersions` setting as follows:

```scala
ThisBuild / ciTargetScalaVersions := Map(
    "submoduleA" -> Seq("2.12.20"),
    "submoduleB" -> Seq("2.12.20", "2.13.18", "3.3.7")
  )
```

In the example provided, `ciTargetScalaVersions` is defined at the `ThisBuild` level, meaning that the setting will apply to all projects within the build. The setting defines a Map where the key is the name of the current project, obtained by calling the `id` method on the `thisProject` setting, and the value is a sequence of Scala versions obtained from the `crossScalaVersions` of each submodule setting.

To simplify this process, we can populate the versions using each submodule's crossScalaVersions setting as follows:

```scala
ThisBuild / ciTargetScalaVersions := Map(
  (submoduleA / thisProject).value.id -> (submoduleA / crossScalaVersions).value,
  (submoduleB / thisProject).value.id -> (submoduleB / crossScalaVersions).value
)
```

The above code can be simplified further by using `targetScalaVersionsFor` helper method, it takes a list of submodules and returns a Map of project names to their `crossScalaVersions`:

```scala
ThisBuild / ciTargetScalaVersions := targetScalaVersionsFor(submoduleA, submoduleB).value
```

This will generate the following job:

```yaml
test:
  name: Test
  runs-on: ubuntu-latest
  continue-on-error: false
  strategy:
    fail-fast: false
    matrix:
      java: ['17', '21', '25']
      scala-project:
      - ++2.12.20 submoduleA
      - ++2.12.20 submoduleB
      - ++2.13.18 submoduleB
      - ++3.3.7 submoduleB
  steps:
  - name: Setup Scala
    uses: actions/setup-java@v5
    with:
      distribution: corretto
      java-version: ${{ matrix.java }}
      check-latest: true
  - name: Cache Dependencies
    uses: coursier/cache-action@v6
  - name: Git Checkout
    uses: actions/checkout@v6
    with:
      fetch-depth: '0'
  - name: Test
    run: sbt --no-colors ${{ matrix.scala-project }}/test
```
