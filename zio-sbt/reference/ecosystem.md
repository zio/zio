# ZIO SBT Ecosystem

> `ZioSbtEcosystemPlugin` provides the settings, tasks and commands that are common to every ZIO project. It is an auto plugin with `trigger = allRequirements`, so having it on the classpath enables it. It requires the sbt-header, sbt-scalafix, sbt-scalafmt and sbt-buildinfo plugins, which come with it.

To install it, add `addSbtPlugin("dev.zio" % "zio-sbt-ecosystem" % "0.8.12")` to `project/plugins.sbt`, as described in [Installation](../index.md#installation).

## Settings

The plugin defines these keys. The first seven are set at the `ThisBuild` level. `welcomeBannerEnabled` and `usefulTasksAndSettings` are set in each project.

| Setting                  | Type                  | Default                                  | Description                                                                                                       |
| ------------------------ | --------------------- | ---------------------------------------- | ----------------------------------------------------------------------------------------------------------------- |
| `scala212`               | `String`              | `"2.12.21"`                              | The Scala 2.12 version of the build                                                                               |
| `scala213`               | `String`              | `"2.13.18"`                              | The Scala 2.13 version of the build                                                                               |
| `scala3`                 | `String`              | `"3.9.0"`                                | The Scala 3 version of the build                                                                                  |
| `scalaVersion`           | `String`              | `scala213.value`                         | Set by the plugin, so a build compiles with Scala 2.13 unless it overrides this                                   |
| `crossScalaVersions`     | `Seq[String]`         | `Seq(scala212, scala213, scala3)`        | Set by the plugin                                                                                                 |
| `zioVersion`             | `String`              | `"2.1.22"`                               | The ZIO version that `enableZIO` adds as a dependency                                                             |
| `javaPlatform`           | `String`              | `"17"`                                   | The Java version passed to `-source` and `-target` and used for Scala compiler options                            |
| `welcomeBannerEnabled`   | `Boolean`             | `true`                                   | Whether sbt prints the welcome banner with the list of useful commands on startup                                 |
| `usefulTasksAndSettings` | `Map[String, String]` | the `lint`, `fmt`, `fix`, `prepare`, `quiet` and `publish-all` commands and the strict-compile tasks      | Name and description pairs that the welcome banner prints                                                         |

The defaults of `scala212`, `scala213`, `scala3` and `zioVersion` are baked into the version of the plugin, so upgrading the plugin changes them. Set `zioVersion` explicitly in every build:

```scala
ThisBuild / zioVersion := "2.1.22"
```

:::note
Don't rely on `zioVersion`'s default. It's baked into whichever version of zio-sbt-ecosystem you depend on, so upgrading the plugin—including via an automated dependency-update PR—can silently change your project's own ZIO version along with it. Setting `zioVersion` explicitly keeps that a deliberate decision you make, not a side effect of a plugin bump. Every ZIO ecosystem project (zio-logging, zio-kafka, zio-cache, and others) already follows this practice.
:::

The Scala version settings are plain `String` keys, so they can define `crossScalaVersions` or any other setting:

```scala
crossScalaVersions := Seq(scala212.value, scala213.value, scala3.value)
```

The plugin also sets these build-wide defaults: `organization := "dev.zio"`, the Apache 2 `licenses`, `homepage` and `scmInfo` derived from the lower-cased project name (`https://zio.dev/<name>` and `https://github.com/zio/<name>`), the `pgp*` keys for signing, and `onChangedBuildSource := ReloadOnSourceChanges`.

## Commands

The plugin registers these sbt commands. Each one except `quiet off` first turns the welcome banner off.

| Command       | What it runs                                                                                          |
| ------------- | ----------------------------------------------------------------------------------------------------- |
| `fmt`         | `scalafmtSbt`, then `+scalafmt` and `+Test / scalafmt`: formats the sources                           |
| `lint`        | `enableStrictCompile`, the cross-built scalafmt checks, `scalafix --check`, then `disableStrictCompile` |
| `fix`         | `Compile / scalafix` and `Test / scalafix`: applies the scalafix rules                                |
| `prepare`     | The scalafmt formatting tasks, then `fix`: prepares the sources for a pull request                    |
| `publish-all` | `project /` then `+publishSigned`: signs and publishes all artifacts                                  |
| `quiet`       | `quiet on` mutes the welcome banner and `quiet off` shows it again                                    |

`addCommand(commands, name, description)` adds a command of your own that the welcome banner lists:

```scala
addCommand(List("scalafmtAll", "Test / compile"), "check-all", "Formats and compiles everything")
```

## Tasks

The plugin defines three tasks in `zio.sbt.Tasks`.

| Task                   | Description                                                                                                                       |
| ---------------------- | --------------------------------------------------------------------------------------------------------------------------------- |
| `enableStrictCompile`  | Sets the system property `enable.strict.compilation` to `true`                                                                    |
| `disableStrictCompile` | Sets the system property `enable.strict.compilation` to `false`                                                                   |
| `build`                | Runs `clean`, `enableStrictCompile`, `Compile / compile`, `Test / compile` and `Test / test`, then `disableStrictCompile`         |

The plugin's own settings do not read the `enable.strict.compilation` property. `stdSettings` adds `-Xfatal-warnings` on Scala 2 when the `CI` environment variable is set, whatever the property says. A build that wants warnings as errors only under `lint` can read the property in its own `scalacOptions`.

## Standard Settings

`stdSettings` is the entry point for configuring a project. Its parameters are:

| Parameter                       | Type             | Default | Description                                                                                  |
| ------------------------------- | ---------------- | ------- | -------------------------------------------------------------------------------------------- |
| `name`                          | `Option[String]` | `None`  | Sets the sbt `name` when present                                                             |
| `packageName`                   | `Option[String]` | `None`  | Enables `buildInfoSettings` with this package for sbt-buildinfo when present                 |
| `javaPlatform`                  | `String`         | `"17"`  | Sets the `javaPlatform` setting and the `-source` and `-target` javac options                |
| `enableKindProjector`           | `Boolean`        | `true`  | Adds the kind-projector compiler plugin on Scala 2                                           |
| `enableCrossProject`            | `Boolean`        | `false` | Adds `crossProjectSettings`                        |
| `enableScalafix`                | `Boolean`        | `true`  | Adds `scalafixSettings`                                                                      |
| `turnCompilerWarningIntoErrors` | `Boolean`        | `true`  | Adds `-Xfatal-warnings` on Scala 2 when the `CI` environment variable is set                  |

Besides these, `stdSettings` adds the standard `scalacOptions` for the Scala version, runs `Test / parallelExecution` on Scala 2 only, enables `autoAPIMappings` and adds the better-monadic-for compiler plugin.

`stdSettings` takes the project name and, optionally, the package name for build info:

```scala
lazy val core = project
  .settings(stdSettings(name = Some("my-library"), packageName = Some("com.example.mylibrary")))
```

## Dependency and Platform Settings

These methods return sbt settings that a project adds with `.settings(...)`.

| Method                                                | Description                                                                                          |
| ----------------------------------------------------- | ---------------------------------------------------------------------------------------------------- |
| `enableZIO(enableStreaming = false, enableTesting = true)` | Adds `zio` with `zioVersion`, plus `zio-streams` and the ZIO Test framework as requested            |
| `jsSettings`                                          | Settings for Scala.js projects: forks `Test` only on the JVM platform                               |
| `nativeSettings`                                      | Settings for Scala Native projects: skips `doc` and replaces `Test / test` with a compile of the tests, and forks `Test` only on the JVM platform      |
| `scalajs`                                             | Adds the `-scalajs` option on Scala 3                                                                |
| `scalafixSettings`                                    | Enables semanticdb (except on Scala 3) and adds the scaluzzi scalafix rules                                                                    |
| `scalaReflectTestSettings`                            | Adds `scala-reflect` in the `Test` configuration, from the Scala 2.13 version on Scala 3                                                            |
| `crossProjectSettings`                                | Adds the platform- and Scala-version-specific source directories (for example `scala-2.13+`, `scala-3.x`) to `Compile` and `Test`                           |
| `buildInfoSettings(packageName)`                      | Exposes `name`, `version`, `scalaVersion`, `sbtVersion` and `isSnapshot` in sbt-buildinfo            |
| `macroExpansionSettings`                              | Enables macro annotations on Scala 2.13 and the macro paradise plugin on 2.12                        |
| `macroDefinitionSettings`                             | Adds `-language:experimental.macros` and the provided `scala-reflect` and `scala-compiler` on Scala 2 |

`enableZIO` adds ZIO using `zioVersion`:

```scala
lazy val core = project
  .settings(enableZIO(enableStreaming = true))
```

## Version-Specific Helpers

Each helper takes the Scala binary versions it applies to, such as `"2.13"` or `"3"`, then the options or dependencies. The `optionsOn*` and `dependenciesOn*` methods return initializations and the `add*` methods return settings.

| Method                                     | Description                                                                                        |
| ------------------------------------------ | -------------------------------------------------------------------------------------------------- |
| `optionsOn(versions*)(options*)`           | The options when the Scala binary version is one of `versions`, an empty `Seq` otherwise           |
| `optionsOnExcept(versions*)(options*)`     | The options when the Scala binary version is none of `versions`                                    |
| `optionsOnOrElse(versions*)(options*)(other*)` | The options on the listed versions and the other options everywhere else                       |
| `addOptionsOn`, `addOptionsOnExcept`, `addOptionsOnOrElse` | The same three, as a setting that appends to `scalacOptions`                        |
| `dependenciesOn(versions*)(modules*)`      | The modules when the Scala binary version is one of `versions`                                     |
| `dependenciesOnExcept(versions*)(modules*)` | The modules when the Scala binary version is none of `versions`                                   |
| `dependenciesOnOrElse(versions*)(modules*)(other*)` | The modules on the listed versions and the other modules everywhere else                  |
| `addDependenciesOn`, `addDependenciesOnExcept`, `addDependenciesOnOrElse` | The same three, as a setting that appends to `libraryDependencies`           |

`addOptionsOn` adds compiler options for the listed Scala binary versions only:

```scala
addOptionsOn("2.13")("-Ymacro-annotations")
```
