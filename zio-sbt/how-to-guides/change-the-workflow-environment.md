# Change the Workflow Environment

> By default the workflow exports `JDK_JAVA_OPTIONS`, built from `ciJvmOptions`. That is not always wanted: it makes `java -version` print a note containing commas, which corrupts the cache keys computed by `setup-sbt` and `coursier/cache-action`. Assigning `ciWorkflowEnv` replaces the derived map, so JVM flags can go through `SBT_OPTS` instead:

```scala
ciWorkflowEnv := Map(
  "SBT_OPTS" -> "-XX:+PrintCommandLineFlags -Djava.locale.providers=CLDR,JRE"
)
```
