# Introduction to ZIO Profiling

> ZIO Profiling is a collection of different profilers for better understanding the runtime behavior of ZIO programs.

[![Concept](https://img.shields.io/badge/Project%20Stage-Concept-orange.svg)](https://github.com/zio/zio/wiki/Project-Stages) ![CI Badge](https://github.com/zio/zio-profiling/workflows/CI/badge.svg) [![Sonatype Releases](https://img.shields.io/maven-central/v/dev.zio/zio-profiling_2.13.svg?label=Sonatype%20Release)](https://central.sonatype.com/artifact/dev.zio/zio-profiling_2.13) [![Sonatype Snapshots](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fcentral.sonatype.com%2Frepository%2Fmaven-snapshots%2Fdev%2Fzio%2Fzio-profiling_2.13%2Fmaven-metadata.xml&label=Sonatype%20Snapshot)](https://central.sonatype.com/repository/maven-snapshots/dev/zio/zio-profiling_2.13/) [![javadoc](https://javadoc.io/badge2/dev.zio/zio-profiling_2.13/javadoc.svg)](https://javadoc.io/doc/dev.zio/zio-profiling_2.13) [![ZIO Profiling](https://img.shields.io/github/stars/zio/zio-profiling?style=social)](https://github.com/zio/zio-profiling)

## Introduction

Normal cpu profilers cannot really be used to profile code using an effect system. Profilers operating on a thread level will only see that threads are spending time in the evaluation loop of the effect system, producing profiles that are not useful for application developers.

Instead, profiling a program written using an effect system requires a profiler that is aware of the effect system and can report where the effects were constructed / which user code the effect system is spending time on. ZIO profiling aims to be that library for the ZIO effect system.

The library focuses exclusively on cpu profiling. For heap profiling please consider using other tools such as async-profiler or VisualVM.

ZIO Profiling ships a [sampling profiler](reference/sampling-profiler.md) that shows where your program spends its time, and a [causal profiler](reference/causal-profiler.md) that shows which part is worth optimizing. To choose between them, see [Which profiler do I need?](concepts/which-profiler.md).

## Installation

All modules are published for Scala 2.12, Scala 2.13 and Scala 3, on the JVM only, and depend on ZIO 2.

```scala
libraryDependencies ++= Seq(
  // Both profilers, CostCenter and the profiling results (JVM)
  "dev.zio" %% "zio-profiling" % "0.3.5"
)
```

## Modules

### Tagging compiler plugin

The plugin names each ZIO effect and stream in your code after its source location. Both profilers report per name, so you almost always want it. See [Set up the tagging compiler plugin](how-to/setup-tagging-plugin.md) and the [plugin reference](reference/tagging-plugin.md).

```scala
libraryDependencies ++= Seq(
  // Compiler plugin that tags effects with their source location (JVM)
  compilerPlugin("dev.zio" %% "zio-profiling-tagging-plugin" % "0.3.5")
)
```

### JMH integration

The module attaches the sampling profiler to JMH benchmarks. See [Profile JMH benchmarks](how-to/profile-jmh-benchmarks.md) and the [JMH profiler reference](reference/jmh-profiler.md).

```scala
libraryDependencies ++= Seq(
  // JMH profiler that writes a flamegraph input file per benchmark (JVM)
  "dev.zio" %% "zio-profiling-jmh" % "0.3.5"
)
```

## Where to go next

New to the library? Follow [Getting Started](getting-started.md), then the [tutorial](tutorials/first-flamegraph.md).
