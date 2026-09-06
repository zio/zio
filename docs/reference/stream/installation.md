---
id: installation
title: "Installing ZIO Streams"
sidebar_label: "Installation"
---

In order to use ZIO Streams, we need to add the required configuration in our SBT settings:

```scala mdoc:passthrough
println(s"""```scala""")
println(
s"""libraryDependencies += "dev.zio" %% "zio-streams" % "${zio.BuildInfo.version.split('+').head}""""
)
println(s"""```""")
```
