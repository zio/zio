# ZIO CLI SBT Plugin

> **ZIO CLI** offers an SBT plugin to provide some tasks to make generation and deployment of 
applications simple.

## Overview

**ZIO CLI** offers an SBT plugin to provide some tasks to make generation and deployment of 
applications simple.

## Current State

The current functionality of the plugin offers:

### Native Images 
This SBT plugin depends on and utilizes the 
[sbt-native-image](https://github.com/scalameta/sbt-native-image) plugin. Tasks and settings
related to building a native image map 1:1 to the upstream plugin.

## Outstanding Action Items
- [ ] Investigate if any cross versions are needed for different versions of SBT
- [ ] Add remaining corresponding upstream configuration options to pass into the `sbt-native-image` plugin.
- [ ] Implement `zioCliGenerateBashCompletion`, when ready.
- [ ] Implement `zioCliGenerateZshCompletion`, when ready.
- [ ] Implement `zioCliInstallCli`, when ready.

## Plugin Settings

### zioCliMainClass: Option\[String\]
The mainClass of the CLI App in the `Compile` scope. Defaults to `None`, and will need to be set.

### zioCliNativeImageOptions: Seq\[String\]
A collection of arguments to pass the native-image builder to customize native image generation. Defaults to
```scala
List(
      "--allow-incomplete-classpath",
      "--report-unsupported-elements-at-runtime",
      "--initialize-at-build-time",
      "--no-fallback"
    )
```

### zioCliNativeImageReady: \(\) => Unit
A side-effecting callback that is called the native image is ready. Defaults to
```scala
() => {
        println("ZIO CLI App Native Image Ready!")
      } 
```

## Plugin Tasks

### zioCliBuildNative: Unit
Sets mainClass in Compile, and attempts to run `nativeImage` with any applied `nativeImageOptions`. 
Fires `zioCliNativeImageReady` on success.

### zioCliGenerateBashCompletion: Unit
Not currently implemented; prints `"TODO: Not Implemented!"`

### zioCliGenerateZshCompletion: Unit
Not currently implemented; prints `"TODO: Not Implemented!"`

### zioCliInstallCli: Unit
Not currently implemented; prints `"TODO: Not Implemented!"`

## End User Project Setup

There are several steps that are needed to use this in an end user project:

### Locally Publishing the Plugin
To compile and locally publish a copy of the SBT plugin, simply call  `publishLocal` on the `sbtZioCli` project. Example:
```
sbt> project sbtZioCli
sbt> publishLocal
```
This will locally publish an artifact: `"zio.cli.sbt" % "sbt-zio-cli" % "0.0.0-SNAPSHOT"`

### End User Project
In your project, configure SBT to use the plugin by adding to `project/plugins.sbt`:
```
addSbtPlugin("zio.cli.sbt" % "sbt-zio-cli" % "0.0.0-SNAPSHOT")
```

Then, enable the plugin for your project in your `build.sbt` file. Example:
```
lazy val root = project
  .in(file("."))
  .enablePlugins(ZIOCLIPlugin)
  .settings(
    CLIPlugin.zioCliMainClass := Some("zio.cli.YourApp")
  )
```

At this point, you should be able to build a native image of your application with the task:
```
sbt> zioCliBuildNative
```
