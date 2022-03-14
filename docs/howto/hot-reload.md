---
id: hot-reload
title: "How to configure a hot-reloading dev server?"
---

### How to configure hot-reloading
For an efficient development process of server applications, a file-watching and hot-reloading development server should be used.

ZIO applications, like most Scala applications, can use [sbt-revolver](https://github.com/spray/sbt-revolver) to do so.

Add the following dependency to your `project/plugins.sbt`

```scala
addSbtPlugin("io.spray" % "sbt-revolver" % "0.9.1")
```

Then start your development server from an sbt console using

```scala
~reStart
```

This will watch the source code for changes and re-compile when files are changed and then restart the server.