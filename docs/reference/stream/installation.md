---
id: installation
title: "Installing ZIO Streams"
sidebar_label: "Installation"
---

In order to use ZIO Streaming, we need to add the required configuration in our SBT settings:

```scala mdoc:passthrough
println(s"""```scala""")
println(
s"""libraryDependencies += Seq(
  "dev.zio" %% "zio"         % "${zio.BuildInfo.version.split('+').head}" % Test
  "dev.zio" %% "zio-streams" % "${zio.BuildInfo.version.split('+').head}" % Test
)"""
)
println(s"""```""")
```
