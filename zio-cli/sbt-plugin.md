# ZIO CLI sbt Plugin

> The `sbt-zio-cli` plugin is an sbt plugin in the **ZIO CLI** repository. It enables the [sbt-native-image](https://github.com/scalameta/sbt-native-image) plugin, sets a few native-image defaults, and adds one working task, `zioCliBuildNative`, which builds a native executable of your app. Its other tasks are placeholders that print `TODO: Not Implemented!`.

For most builds, the published sbt-native-image plugin alone does the same job with fewer steps; see [How to package a CLI as a native binary](how-to-package-native-binary.md).

## Availability

The plugin is not published to Maven Central or any other repository. To use it, publish it to your local Ivy repository from a clone of the ZIO CLI repository:

```bash
git clone https://github.com/zio/zio-cli.git
cd zio-cli
sbt sbtZioCli/publishLocal
```

The published module is `"zio.cli.sbt" % "sbt-zio-cli"`. It is compiled with Scala 2.12, so it is a plugin for sbt 1.x builds. The plugin has no fixed version: sbt derives it from the latest git tag and commit, for example `0.8.2+28-5b6890e2-SNAPSHOT`. Use the version that `publishLocal` prints in its log, or print it on its own with `sbt "show sbtZioCli/version"`.

## Enabling the Plugin

The plugin is an `AutoPlugin` named `ZIOCLIPlugin`. It is not triggered automatically, and enabling it also enables sbt-native-image's `NativeImagePlugin`. Add the locally published version to `project/plugins.sbt`, replacing the version with the one that `publishLocal` printed:

```scala
addSbtPlugin("zio.cli.sbt" % "sbt-zio-cli" % "0.8.2+28-5b6890e2-SNAPSHOT")
```

`ZIOCLIPlugin` is defined in the root package and has no `autoImport` object, so sbt does not import its setting and task keys into `build.sbt`. Import them from the plugin object, then enable the plugin on the project that contains the app:

```scala
import ZIOCLIPlugin._

lazy val root = project
  .in(file("."))
  .enablePlugins(ZIOCLIPlugin)
  .settings(
    Compile / mainClass := Some("com.example.GreetApp")
  )
```

Without `import ZIOCLIPlugin._`, sbt reports the `zioCli*` keys as not found. The plugin object itself needs no import, because it is in the root package.

## Settings and Tasks

All keys are defined in the `ZIOCLIPluginKeys` trait, which `ZIOCLIPlugin` extends:

```scala
trait ZIOCLIPluginKeys {
  lazy val zioCliMainClass: SettingKey[Option[String]]
  lazy val zioCliNativeImageOptions: SettingKey[Seq[String]]
  lazy val zioCliNativeImageReady: SettingKey[() => Unit]

  lazy val zioCliBuildNative: TaskKey[Unit]
  lazy val zioCliGenerateBashCompletion: TaskKey[Unit]
  lazy val zioCliGenerateZshCompletion: TaskKey[Unit]
  lazy val zioCliInstallCli: TaskKey[Unit]
}
```

The settings configure the native-image build that `zioCliBuildNative` runs.

### Settings

The plugin sets these defaults on every project that enables it:

| Setting                    | Type             | Default                                                                                                              | Effect                                                     |
| -------------------------- | ---------------- | -------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------- |
| `zioCliMainClass`          | `Option[String]` | `None`                                                                                                               | None in 0.8.2; see the caution below                       |
| `zioCliNativeImageOptions` | `Seq[String]`    | `--allow-incomplete-classpath`, `--report-unsupported-elements-at-runtime`, `--initialize-at-build-time`, `--no-fallback` | Copied into sbt-native-image's `nativeImageOptions`        |
| `zioCliNativeImageReady`   | `() => Unit`     | Prints `ZIO CLI App Native Image Ready!`                                                                             | Copied into sbt-native-image's `nativeImageReady` callback |

Because the plugin assigns `nativeImageOptions := zioCliNativeImageOptions.value`, the plugin's list replaces sbt-native-image's own default, which is empty. To add one option, append to the plugin's setting, for example `zioCliNativeImageOptions += "--verbose"`. A `nativeImageOptions` setting written later in your `build.sbt` overrides `zioCliNativeImageOptions` completely.

:::caution
In 0.8.2, `zioCliMainClass` does not choose the main class: `zioCliBuildNative` ignores it. sbt-native-image builds the class in `Compile / mainClass`, so set that setting instead, as in the example above. When the project has exactly one main class, sbt finds it on its own and neither setting is needed; with several main classes and no `Compile / mainClass`, the build stops with `no mainClass is specified`.
:::

### Tasks

The plugin adds four tasks:

| Task                           | Behaviour                                                                                         |
| ------------------------------ | ------------------------------------------------------------------------------------------------- |
| `zioCliBuildNative`            | Runs sbt-native-image's `nativeImage` task and returns nothing                                    |
| `zioCliGenerateBashCompletion` | Prints `TODO: Not Implemented!` and does nothing else                                             |
| `zioCliGenerateZshCompletion`  | Prints `TODO: Not Implemented!` and does nothing else                                             |
| `zioCliInstallCli`             | Prints `TODO: Not Implemented!` and does nothing else                                             |

`zioCliBuildNative` produces the same executable as `nativeImage`: it compiles the project, runs GraalVM `native-image` with the options above, writes the binary to `native-image/<project name>` under the project's target directory, logs its absolute path, and then calls the `zioCliNativeImageReady` callback. Run it from the sbt shell:

```bash
sbt zioCliBuildNative
```

For shell completion, use the built-in `--shell-completion-script` option of the app instead; see [How to add Tab completion in bash](how-to-add-shell-completion.md).

## sbt-native-image Keys

Only the two settings above are forwarded to sbt-native-image. Every other sbt-native-image setting and task, such as `nativeImageVersion`, `nativeImageInstalled`, `nativeImageRun`, `nativeImageCopy` and `nativeImageRunAgent`, is available unchanged on a project that enables `ZIOCLIPlugin`. Those keys, how GraalVM is downloaded, and where the binary is written are described in [How to package a CLI as a native binary](how-to-package-native-binary.md). The plugin itself depends on sbt-native-image 0.5.0, so the defaults described there apply only when you also add sbt-native-image 0.6.0 to `project/plugins.sbt`.

## See Also

- [How to package a CLI as a native binary](how-to-package-native-binary.md) builds a native executable with sbt-native-image directly.
- [How to add Tab completion in bash](how-to-add-shell-completion.md) generates a bash completion script from the app's built-in options.
