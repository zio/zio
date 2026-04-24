# Getting Started with Quill

> > ### [Scastie](https://scastie.scala-lang.org/) is a great tool to try out Quill without having to prepare a local environment. It works with [mirror contexts](contexts.md#mirror-context), see [this](https://scastie.scala-lang.org/QwOewNEiR3mFlKIM7v900A) snippet as an example.

> ### [Scastie](https://scastie.scala-lang.org/) is a great tool to try out Quill without having to prepare a local environment. It works with [mirror contexts](contexts.md#mirror-context), see [this](https://scastie.scala-lang.org/QwOewNEiR3mFlKIM7v900A) snippet as an example.

Quill has integrations with many libraries. If you are using a regular RDBMS e.g. PostgreSQL
and want to use Quill to query it with an asynchronous, non-blocking, reactive application, the easiest way to get
started is by using ZIO.

A simple ZIO + Quill application looks like this:
```scala
case class Person(name: String, age: Int)

class DataService(quill: Quill.Postgres[SnakeCase]) {

  def getPeople: ZIO[Any, SQLException, List[Person]] = run(query[Person])
}
object DataService {
  def getPeople: ZIO[DataService, SQLException, List[Person]] =
    ZIO.serviceWithZIO[DataService](_.getPeople)

  val live = ZLayer.fromFunction(new DataService(_))
}
object Main extends ZIOAppDefault {
  override def run = {
    DataService.getPeople
      .provide(
        DataService.live,
        Quill.Postgres.fromNamingStrategy(SnakeCase),
        Quill.DataSource.fromPrefix("myDatabaseConfig")
      )
      .debug("Results")
      .exitCode
  }
}
```

Add the following to build.sbt:

```scala
libraryDependencies ++= Seq(
  "io.getquill"          %% "quill-jdbc-zio" % "4.8.6",
  "org.postgresql"       %  "postgresql"     % "42.3.1"
)
```

You can find this code (with some more examples) complete with a docker-provided Postgres database [here](https://github.com/deusaquilus/zio-quill-gettingstarted).
A variety of other examples using Quill with ZIO are available in the [examples](https://github.com/zio/zio-quill/tree/master/quill-jdbc-zio/src/test/scala/io/getquill/examples) folder.

## Choosing a Module

Choose the quill module that works for you!

* If you are starting from scratch with a regular RDBMS try using the `quill-jdbc-zio` module as shown above.
* If you are developing a legacy Java project and don't want/need reactive, use `quill-jdbc`.
* If you are using Cassandra, Spark, or OrientDB, try the corresponding modules for each of them.
