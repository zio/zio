# How to package a CLI as a native binary

> This guide turns a **ZIO CLI** app into a standalone native executable with GraalVM `native-image`, driven from sbt by the [sbt-native-image](https://github.com/scalameta/sbt-native-image) plugin. The executable starts without a JVM, which makes a command-line tool start almost instantly.

## Prerequisites

Before starting, make sure you have:

- An sbt project with an app object that extends `ZIOCliDefault`, such as the `Greet` app from [Getting Started](getting-started.md).
- Either sbt 1.x or sbt 2.x. sbt-native-image is published for both.

You do not need to install GraalVM. By default, sbt-native-image downloads a GraalVM distribution with [Coursier](https://get-coursier.io/) the first time it builds an image. `native-image` still needs a C toolchain on the machine. On Linux, install `gcc` and the zlib development headers, for example `zlib1g-dev` on Debian and Ubuntu. On macOS, install the Xcode command line tools. On Windows, install the Visual Studio C++ build tools.

## Step 1: Add the Plugin

Add sbt-native-image to `project/plugins.sbt`:

```scala
addSbtPlugin("org.scalameta" % "sbt-native-image" % "0.6.0")
```

## Step 2: Enable the Plugin and Set the Main Class

Enable `NativeImagePlugin` on the project that contains the app, and set `Compile / mainClass` to the fully qualified name of the app object. The binary is named after the project, so we also set `name`:

```scala
lazy val root = project
  .in(file("."))
  .enablePlugins(NativeImagePlugin)
  .settings(
    name                := "greet",
    Compile / mainClass := Some("Greet"),
    nativeImageOptions += "--no-fallback"
  )
```

`nativeImageOptions` holds extra arguments for `native-image`, and it is empty by default. The `--no-fallback` option makes the build fail when GraalVM cannot compile the app ahead of time, instead of producing an image that still needs a JVM to run. When the project has exactly one main class, sbt finds it without `Compile / mainClass`; with several, the build stops with `no mainClass is specified` until you set it.

## Step 3: Build the Executable

Run the `nativeImage` task:

```bash
sbt nativeImage
```

The task compiles the project, runs `native-image` (which takes a minute or more), and logs the absolute path of the binary. The binary is written to `native-image/<project name>` under the project's target directory: `target/native-image/greet` for the project above on sbt 1.x. The first build also downloads GraalVM, which takes longer.

## Step 4: Run the Executable

Run the binary like any other program. To print the app's help screen:

```bash
./target/native-image/greet --help
```

From sbt, `nativeImageRun` runs the binary that the last `nativeImage` built, passing the arguments that follow it:

```bash
sbt nativeImage "nativeImageRun --help"
```

To copy the binary to a fixed location, for example in a CI job that uploads it, use `nativeImageCopy` with the destination path:

```bash
sbt "nativeImageCopy greet-linux-x86_64"
```

## Use a Locally Installed GraalVM

To use a GraalVM that is already installed instead of downloading one, set the environment variable `NATIVE_IMAGE_INSTALLED=true` (or `GRAALVM_INSTALLED=true`) before starting sbt:

```bash
NATIVE_IMAGE_INSTALLED=true GRAALVM_HOME=/path/to/graalvm sbt nativeImage
```

sbt-native-image then runs `bin/native-image` from the first of `GRAAL_HOME`, `GRAALVM_HOME` and `JAVA_HOME` that is set. To keep the download but change the GraalVM release it fetches, set `nativeImageJvm` and `nativeImageVersion`; in sbt-native-image 0.6.0 they default to `"graalvm-java25"` and `"25.0.1"`.

## Apps That Use Reflection

`native-image` must know at build time about every class that is loaded by reflection. If the binary fails at runtime with a missing class or method while the same app works on the JVM, run the app once under the GraalVM tracing agent with `nativeImageRunAgent`, then pass the generated configuration to `nativeImageOptions` and rebuild. The [sbt-native-image README](https://github.com/scalameta/sbt-native-image#nativeimagerunagent) shows the exact settings.

## The ZIO CLI sbt Plugin

The ZIO CLI repository also contains an sbt plugin, `sbt-zio-cli`, whose `zioCliBuildNative` task wraps the same `nativeImage` task. It is not published, so the steps above are the shorter path. See [ZIO CLI sbt Plugin](sbt-plugin.md) for what it adds.

## See Also

- [ZIO CLI sbt Plugin](sbt-plugin.md) describes the `sbt-zio-cli` settings and tasks.
- [How to add Tab completion in bash](how-to-add-shell-completion.md) generates a bash completion script for the installed executable.
- The [sbt-native-image README](https://github.com/scalameta/sbt-native-image) lists every setting and task of the plugin.
