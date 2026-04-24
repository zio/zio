# Quill Changelog

> Starting from the versions superior v4.6.1, the release notes and changelog will be provided in the GitHub Releases of the project.
See https://github.com/zio/zio-quill/releases

Starting from the versions superior v4.6.1, the release notes and changelog will be provided in the GitHub Releases of the project.
See https://github.com/zio/zio-quill/releases

# 4.6.1

- [Remove support of Scala 2.11 && Update ZIO to 2.0.12](https://github.com/zio/zio-quill/pull/2722)
  - Remove support of Scala 2.11
  - Update Scala versions
  - Update ZIO to 2.0.12
  - Update zio-logging to 2.1.12
  - Update sbt to 1.7.3
  - Update sbt-scoverage to 2.0.0
  - Update zio-json to 0.5.0

#### Notes:
- Besides for Scala-version support update and the dependency bumps (thank you Guizmaii and Juliano!) the purpose of this
  release is to set the stage of future infrastructure changes e.g. adding Scalafmt to do formatting instead
  of the long-standing scalariform.

# 4.6.0

- [Implementing Postgres json/jsonb encoding via zio-json](https://github.com/zio/zio-quill/pull/2615)

# 4.5.0

- [Remove the need to make things embedded, happens automatically](https://github.com/zio/zio-quill/pull/2607)
- [Date encoders for major date types. Extensible context.](https://github.com/zio/zio-quill/pull/2598)
- [Warning about embedding fields that should have encoders](https://github.com/zio/zio-quill/pull/2610)
- [jasync zio current schema configuration, jasync version  update](https://github.com/zio/zio-quill/pull/2588)

#### Migration Notes:
- It is no longer necessary to do extend `Embedded` for case classes that should be embedded within an entity.
  In the case that the embedded case class "looks" like it should be encoded/decoded (i.e. it has only one field),
  an additional warning has been introduced to notify the user of this potential issue.

# 4.4.1

- [Fixing VALUES-clause update query naming](https://github.com/zio/zio-quill/pull/2595)

# 4.4.0

- [UPDATE with VALUES optimization for Postgres. Various macro refactoring.](https://github.com/zio/zio-quill/pull/2571)

# 4.3.0

- [Values clause batch insert](https://github.com/zio/zio-quill/pull/2565)
- [Slightly better batch logging](https://github.com/zio/zio-quill/pull/2562)
- ["transaction" supports ZIO effects with mixed environments](https://github.com/zio/zio-quill/pull/2515)

# 4.2.0

- [Implement ZIO-Idiomatic JDBC Context](https://github.com/zio/zio-quill/pull/2537)
- [Update idiomatic pattern based on discussions](https://github.com/zio/zio-quill/pull/2546)
- [Implement ZIO idiomatic pattern for cassandra](https://github.com/zio/zio-quill/pull/2549)
- [Add switch to manually disable returning/output-clauses](https://github.com/zio/zio-quill/pull/2550)
- [cassandra - update if exists](https://github.com/zio/zio-quill/pull/2359)
- [Change infix”$content” to sql”$content”](https://github.com/zio/zio-quill/pull/2547)
- [Remove mysql as](https://github.com/zio/zio-quill/pull/2540)

#### Migration Notes:
- The `infix` interpolator is now deprecated because in Scala 2, infix is a keyword. Instead of
  `infix"MyUdf(${person.name})"` use `sql"MyUdf(${person.name})"`. For contexts such as Doobie that already
  have an `sql` interpolator. Import `context.compat._` and use the `qsql` interpolator instead.

# 4.1.0

- [Implement groupByMap and EnableTrace/DisablePhase functionality](https://github.com/zio/zio-quill/pull/2517)

# 4.0.0

- [Bump to ZIO 2.0.0 Release Version](https://github.com/zio/zio-quill/pull/2511)

# 3.19.0

- [Implementing insert/update/delete.returningMany](https://github.com/zio/zio-quill/pull/2509)
- [Update jasync](https://github.com/zio/zio-quill/pull/2374)
- [Add support for SSL Client connections](https://github.com/zio/zio-quill/pull/2491)

# 3.18.0

- [Check all columns for null-ness for Option[Product] to be None](https://github.com/zio/zio-quill/pull/2504)
- [Fixing Correlated Subquery Issues](https://github.com/zio/zio-quill/pull/2489)
- [Corrects Like operator generating wrong SQLs](https://github.com/zio/zio-quill/pull/2502)
- [Implement filterIfDefined](https://github.com/zio/zio-quill/pull/2501)
- [Remove invalid 'AS' for Oracle Queries](https://github.com/zio/zio-quill/pull/2499)
- [Remove twitter-chill library](https://github.com/zio/zio-quill/pull/2500)

#### Version Bumps:
- [sbt-scalajs-crossproject to 1.2.0](https://github.com/zio/zio-quill/pull/2444)
- [logback-classic to 1.2.11](https://github.com/zio/zio-quill/pull/2439)
- [h2 to 2.1.212](https://github.com/zio/zio-quill/pull/2462)
- [zio, zio-streams to 1.0.14](https://github.com/zio/zio-quill/pull/2465)
- [cassandra-driver-core to 3.11.2](https://github.com/zio/zio-quill/pull/2470)
- [java-driver-core to 4.14.1](https://github.com/zio/zio-quill/pull/2469)
- [scala-collection-compat to 2.7.0](https://github.com/zio/zio-quill/pull/2445)
- [mysql-connector-java to 8.0.29](https://github.com/zio/zio-quill/pull/2467)
- [scala3-library, ... to 3.1.2](https://github.com/zio/zio-quill/pull/2464)
- [sbt-sonatype to 3.9.13](https://github.com/zio/zio-quill/pull/2496)
- [postgresql to 42.3.6](https://github.com/zio/zio-quill/pull/2495)

#### Migration Notes:
- As a result of [2504](https://github.com/zio/zio-quill/pull/2504), the handling of optional-product rows (technically parts of rows) is now different. Whereas before, if any non-optional column of an optional-product row was null, then entre optional-product would be null. Now however, an optional-product will only be null if every column inside is null. For example, before, if a query returning `Person(name:Option(Name(first:String, last:String)), age: Int)` resulted in the row `ResultRow("Joe", null, 123)` before the entity would be decoded into `Person(None, 123)` (i.e. the optional-product `Option[Name]` would decode to `None`).
  Now however, `Option[Name]` only decodes to `None` if every column inside it is null. This means that the `ResultRow("Joe", null, 123)` decodes to `Person(Name("Joe", 0 /*default-placeholder for null*/), 123)`. Only when the both `first` and `last` columns in Name are null i.e. `ResultRow(null, null, 123)` will the result be: `Person(None, 123)`. Have a look at the PR [2504](https://github.com/zio/zio-quill/pull/2504) as well as it's corresponding issue [2505](https://github.com/zio/zio-quill/issues/2505) for more details on how this works and the rationale for it.

# 3.16.5

- [Re-integrating Doobie support](https://github.com/zio/zio-quill/pull/2478)

# 3.16.4

- [Support Spark 3.2.x and Scala 2.13 in quill-spark](https://github.com/zio/zio-quill/pull/2460)
- [Fix dynamic query quat error](https://github.com/zio/zio-quill/pull/2458)
- [Implement DistinctOn](https://github.com/zio/zio-quill/pull/2375)
- [Sync Contexts with ProtoQuill and move out ProtoQuill conflicts](https://github.com/zio/zio-quill/pull/2472)
- [Fix the typo `ZioCassandraSession`  in document.](https://github.com/zio/zio-quill/pull/2303)

# 3.16.3

- [Remove anonymous class made by Property.Opinionated and make NullValue case object](https://github.com/zio/zio-quill/pull/2426)

#### Note
* This change is to allow ProtoQuill transition to BooPickle AST Serialization in https://github.com/zio/zio-protoquill/pull/72

# 3.16.2

- [Remove second apply method on ast.CaseClass due to incremental-compile errors](https://github.com/zio/zio-quill/pull/2423)

# 3.16.1

- [Top-Level quat passing to query expansions](https://github.com/zio/zio-quill/pull/2420)

# 3.16.0

- [Remove deprecated EntityQuery.update/insert APIs](https://github.com/zio/zio-quill/pull/2412)

#### Migration Notes
* This change removes the deprecated `EntityQuery.insert(CaseClass)` and `EntityQuery.update(CaseClass)` APIs that have been
  updated to `EntityQuery.insertValue(CaseClass)` and `EntityQuery.updateValue(CaseClass)`.
  This is the only change in this release so that you can update when ready.
  This change is needed due to the upstream Dotty issue: lampepfl/dotty#14043.

# 3.15.0

- [cassandra - update if exists](https://github.com/zio/zio-quill/pull/2359)
- [Change update to updateValue](https://github.com/zio/zio-quill/pull/2405)

#### Migration Notes
* Similar to `EntityQuery.insert(CaseClass)`, the method `EntityQuery.update(CaseClass)` e.g. `query[Person].update(Person("Joe", 123))` has been replaced with `updateValue`.
  The original `insert` method has been deprecated and will be removed in an upcoming Quill release.

# 3.14.1

- [Fix Infix Ast Quat issues](https://github.com/zio/zio-quill/pull/2403)

# 3.14.0

- [Single path-element alias in top-level selects](https://github.com/zio/zio-quill/pull/2393)
- [Update zio-logging](https://github.com/zio/zio-quill/pull/2392)

# 3.13.0

- [JAsync ZIO implementation](https://github.com/zio/zio-quill/pull/2267)
- [cassandra-alpakka](https://github.com/zio/zio-quill/pull/2365)
- [Need to change EntityQuery.insert(CaseClass) to EntityQuery.insertValue(CaseClass) for upstream Scala 3 issues.](https://github.com/zio/zio-quill/pull/2379)
- [Update ScalaJS to latest](https://github.com/zio/zio-quill/pull/2364)
- [Work on removing tuple elaboration](https://github.com/zio/zio-quill/pull/2381)
- [Option to Disable Nested Subexpansion](https://github.com/zio/zio-quill/pull/2383)
- [Remove deprecated async modules](https://github.com/zio/zio-quill/pull/2362)
- [Add Scala 3 cross-build for quill-engine](https://github.com/zio/zio-quill/pull/2361)
- [Move quill-core-portable & quill-sql-portable to common quill-engine module](https://github.com/zio/zio-quill/pull/2360)
- [Sheath leaf map clauses that cannot be reduced so still have their column in queries](https://github.com/zio/zio-quill/pull/2352)

#### Migration Notes
* The method `EntityQuery.insert(CaseClass)` e.g. `query[Person].insert(Person("Joe", 123))` has been replaced with `insertValue`.
  The original `insert` method has been deprecated and will be removed in the next Quill release.
* The `quill-async` modules using Mauricio's deprecated library ([here](https://github.com/mauricio/postgresql-async))
  have been removed. Please move to the `quill-jasync` libraries as soon as possible.
* Quill for ScalaJS has been updated to ScalaJS 1.8.
* `quill-core-portable` and `quill-sql-portable` are now merged into a cross-built `quill-engine` module.
* In 3.12.0 addition of field-aliases has been introduced in sub-queries but [#2340](https://github.com/zio/zio-quill/issues/2340)
  then occurred. A compile-time switch `-Dquill.query.subexpand=false` has been introduced to disable the feature
  until it can be fixed.

# 3.12.0

- [cassandra - Datastax4x upgrade](https://github.com/getquill/quill/pull/2315)
- [Implement dynamic query caching](https://github.com/getquill/quill/pull/2088)
- [Fix Quat-based query schema rename issue](https://github.com/getquill/quill/pull/2322)
- [forUpdate via infix](https://github.com/getquill/quill/pull/2268)
- [Disable file infra log by default](https://github.com/getquill/quill/pull/2332)
- [Fix `QIO.apply` function type](https://github.com/getquill/quill/pull/2318)
- [doc: update CODEGEN.md](https://github.com/getquill/quill/pull/2316)

#### Migration Notes - Datastax Drivers:

The Datastax drivers have been moved to Version 4, this adds support for many new features with the caveat that the configuration
file format must be changed. In Version 4, the Datastax standard configuration file format and properties
are in the HOCON format. They are used to configure the driver.

Sample HOCON:
```hocon
MyCassandraDb {
  preparedStatementCacheSize=1000
  keyspace=quill_test

  session {
    basic.contact-points = [ ${?CASSANDRA_CONTACT_POINT_0}, ${?CASSANDRA_CONTACT_POINT_1} ]
    basic.load-balancing-policy.local-datacenter = ${?CASSANDRA_DC}
    basic.request.consistency = LOCAL_QUORUM
    basic.request.page-size = 3
  }

}
```

The `session` entry values and keys are described in the datastax documentation:
[Reference configuration](https://docs.datastax.com/en/developer/java-driver/4.13/manual/core/configuration/reference/)

The ZioCassandraSession constructors:

```scala
 val zioSessionLayer: ZLayer[Any, Throwable, CassandraZioSession] =
  CassandraZioSession.fromPrefix("MyCassandraDb")
run(query[Person])
  .provideCustomLayer(zioSessionLayer)
```

Additional parameters can be added programmatically:
```scala
 val zioSessionLayer: ZLayer[Any, Throwable, CassandraZioSession] =
  CassandraZioSession.fromContextConfig(LoadConfig("MyCassandraDb").withValue("keyspace", ConfigValueFactory.fromAnyRef("data")))
run(query[Person])
  .provideCustomLayer(zioSessionLayer)
```

`session.queryOptions.fetchSize=N` config entry should be replaced by
`basic.request.page-size=N`

```hocon
testStreamDB {
  preparedStatementCacheSize=1000
  keyspace=quill_test

  session {
    ...
    basic.request.page-size = 3
  }
  ...
}
```

#### Migration Notes - Query Log File:

Production of the query-log file `queries.txt` has been disabled by default due to issues with SBT
and metals. In order to use it, launch the compiler JVM (e.g. SBT) with the argument `-Dquill.log.file=my_queries.sql`
or set the `quill_log_file` environment variable (e.g. `export quill_log_file=my_queries.sql`).

#### Migration Notes - Monix:

The monix context wrapper `MonixJdbcContext.Runner` has been renamed to `MonixJdbcContext.EffectWrapper`.
The type `Runner` needs to be used by ProtoQuill to define quill-context-specific execution contexts.

# 3.11.0

- [Implement `transaction` on outer zio-jdbc-context using fiber refs](https://github.com/getquill/quill/pull/2302)
- [Feature Request: write compile-time queries to a file](https://github.com/getquill/quill/issues/1715)
- [`transaction` supports ZIO effects with mixed environments](https://github.com/getquill/quill/pull/2304)
- [Apple M1 Build Updates & Instructions](https://github.com/getquill/quill/pull/2296)

#### Migration Notes:

All ZIO JDBC context `run` methods have now switched from have switched their dependency (i.e. `R`) from `Connection` to
`DataSource`. This should clear up many innocent errors that have happened because how this `Connection` is supposed
to be provided was unclear. As I have come to understand, nearly all DAO service patterns involve grabbing a connection from a
pooled DataSource, doing one single crud operation, and then returning the connection back to the pool. The new JDBC ZIO context
memorialize this pattern.

* The signature of `QIO[T]` has been changed from `ZIO[Connection, SQLException, T]` to `ZIO[DataSource, SQLException, T]`.
  a new type-alias `QCIO[T]` (lit. Quill Connection IO) has been introduced that represents `ZIO[Connection, SQLException, T]`.

* If you are using the `.onDataSource` command, migration should be fairly easy. Whereas previously, a usage of quill-jdbc-zio 3.10.0
  might have looked like this:
  ```scala
  object MyPostgresContext extends PostgresZioJdbcContext(Literal); import MyPostgresContext._
  val zioDS = DataSourceLayer.fromPrefix("testPostgresDB")

  val people = quote {
    query[Person].filter(p => p.name == "Alex")
  }

  MyPostgresContext.run(people).onDataSource
    .tap(result => printLine(result.toString))
    .provideCustomLayer(zioDs)
  ```
  In 3.11.0 simply remove the `.onDataSource` in order to use the new context.
  ```scala
  object MyPostgresContext extends PostgresZioJdbcContext(Literal); import MyPostgresContext._
  val zioDS = DataSourceLayer.fromPrefix("testPostgresDB")

  val people = quote {
    query[Person].filter(p => p.name == "Alex")
  }

  MyPostgresContext.run(people)  // Don't need `.onDataSource` anymore
    .tap(result => printLine(result.toString))
    .provideCustomLayer(zioDs)
  ```

* If you are creating a Hikari DataSource directly, passing of the dependency is now also simpler. Instead having to pass
  the Hikari-pool-layer into `DataSourceLayer`, just provide the Hikari-pool-layer directly.

  From this:
  ```scala
  def hikariConfig = new HikariConfig(JdbcContextConfig(LoadConfig("testPostgresDB")).configProperties)
  def hikariDataSource: DataSource with Closeable = new HikariDataSource(hikariConfig)

  val zioConn: ZLayer[Any, Throwable, Connection] =
    Task(hikariDataSource).toLayer >>> DataSourceLayer.live

  MyPostgresContext.run(people)
    .tap(result => printLine(result.toString))
    .provideCustomLayer(zioConn)
  ```
  To this:
  ```scala
  def hikariConfig = new HikariConfig(JdbcContextConfig(LoadConfig("testPostgresDB")).configProperties)
  def hikariDataSource: DataSource with Closeable = new HikariDataSource(hikariConfig)

  val zioDS: ZLayer[Any, Throwable, DataSource] =
    Task(hikariDataSource).toLayer // Don't need `>>> DataSourceLayer.live` anymore!

  MyPostgresContext.run(people)
    .tap(result => printLine(result.toString))
    .provideCustomLayer(zioConn)
  ```

* If you want to provide a `java.sql.Connection` to a ZIO context directly, you can still do it using the `underlying` variable.
  ```
  object Ctx extends PostgresZioJdbcContext(Literal); import MyPostgresContext._
  Ctx.underlying.run(qr1)
    .provide(zio.Has(conn: java.sql.Connection))
  ```

* Also, when using an underlying context, you can still use `onDataSource` to go from a `Connection` dependency
  back to a `DataSource` dependency (note that it no longer has to be `with Closable`).
  ```
    object Ctx extends PostgresZioJdbcContext(Literal); import MyPostgresContext._
    Ctx.underlying.run(qr1)
      .onDataSource
      .provide(zio.Has(ds: java.sql.DataSource))
    ```

* Finally, that the `prepare` methods have been unaffected by this change. They still require a `Connection`
  and have the signature `ZIO[Connection, SQLException, PreparedStatement]`. This is because in order to work
  with the result of this value (i.e. to work with `PreparedStatement`), the connection that created it must
  still be open.

# 3.10.0

- [Defunct AsyncZioCache accidentally returned in #2174. Remove it.](https://github.com/getquill/quill/pull/2246)
- [Open connection on ZIO blocking pool](https://github.com/getquill/quill/pull/2244)
- [Line-up core API with ProtoQuill so child contexts can have same code](https://github.com/getquill/quill/pull/2231)

#### Migration Notes:

No externally facing API changes have been made.
This release aligns Quill's internal Context methods with the API defined in ProtoQuill and introduces
a root-level context (in the `quill-sql-portable` module) that will be shared together with ProtoQuill.
Two arguments `info: ExecutionInfo` and `dc: DatasourceContext` have been introduced to all `execute___`
and `prepare___` methods. For Scala2-Quill, these arguments should be ignored as they contain no
relevant information. ProtoQuill uses them in order to pass Ast information as well as whether
the query is Static or Dynamic into execute and prepare methods. In the future, Scala2-Quill may be enhanced
to use them as well.

# 3.9.0

- [Pass Session to all Encoders/Decoders allowing UDT Encoding without local session variable in contexts e.g. ZIO and others](https://github.com/getquill/quill/pull/2219)
- [Fixing on-conflict case with querySchema/schemaMeta renamed columns](https://github.com/getquill/quill/pull/2218)

#### Migration Notes:

This release modifies Quill's core encoding DSL however this is very much an internal API.
If you are using MappedEncoder, which should be the case for most users, you will be completely unaffected.
The MappedEncoder signatures remain the same.

Quill's core encoding API has changed:
```scala
// From:
type BaseEncoder[T] = (Index, T, PrepareRow) => PrepareRow
type BaseDecoder[T] = (Index, ResultRow) => T
// To:
type BaseEncoder[T] = (Index, T, PrepareRow, Session) => PrepareRow
type BaseDecoder[T] = (Index, ResultRow, Session) => T
```
That means that internal signature of all encoders has also changed. For example, the JdbcEncoder has changed:
```scala
// From:
case class JdbcEncoder[T](sqlType: Int, encoder: BaseEncoder[T]) extends BaseEncoder[T] {
  override def apply(index: Index, value: T, row: PrepareRow) =
    encoder(index + 1, value, row)
}
// To:
case class JdbcEncoder[T](sqlType: Int, encoder: BaseEncoder[T]) extends BaseEncoder[T] {
  override def apply(index: Index, value: T, row: PrepareRow, session: Session) =
    encoder(index + 1, value, row, session)
}
```
If you are writing encoders that directly implement `BaseEncoder`, they will have to be modified with an
additional `session: Session` parameter.
> The actual type that `Session` is will vary. For JDBC this will be `Connection`, for `Cassandra` this will be some
implementation of `CassandraSession`, for other systems that use a entirely different session paradigm
this will just be `Unit`.

Again, if you are using MappedEncoders for all of your custom encoding needs, you will not be affected by this change.

# 3.8.0

- [Use ZIO-Native Iterator chunking for JDBC result sets](https://github.com/getquill/quill/pull/2196)
- [Remove 'with Blocking' from all signatures](https://github.com/getquill/quill/pull/2174)
- [Update Microsoft SQL Server Docker image](https://github.com/getquill/quill/pull/2183)

#### Migration Notes:
The `quill-jdbc-zio` contexts' `.run` method was designed to work with ZIO in an idiomatic way. As such, the environment variable
of their return type including the `zio.blocking.Blocking` dependency. This added a significant amount of complexity.
Instead of `ZIO[Connection, SQLException, T]`, the return type became `ZIO[Connection with Blocking, SQLException, T]`.
Instead of `ZIO[DataSource with Closeable, SQLException, T]`, the return type became `ZIO[DataSource with Closeable with Blocking, SQLException, T]`.
Various types such as `QConnection` and `QDataSource` were created in order to encapsulate these concepts but this only led to additional confusion.
Furthermore, actually supplying a `Connection` or `DataSource with Closeable` required first peeling off the `with Blocking` clause, calling a `.provide`,
and then appending it back on. The fact that a Connection needs to be opened from a Data Source (which will typically be a Hikari connection pool)
further complicates the problem because this aforementioned process needs to be done twice. All of leads to the clear conclusion that the `with Blocking`
construct has bad ergonomics. For this reason, the ZIO team has decided to drop the concept of `with Blocking` in ZIO 2 altogether.

As a result of this, I have decided to drop the `with Blocking` construct in advance. Quill queries resulting from the `run(qry)` command and
still run on the blocking pool but `with Blocking` is not included in the signature. This also means that and the need for `QConnection` and `QDataSource` disappears since they are now just `Connection` and `Datasource with Closeable`
respectively. This also means that all the constructors on the corresponding objects e.g. `QDataSource.fromPrefix("myDB")` are not consistent with
any actual construct in QIO, therefore they are not needed either.

Instead, I have introduced a simple layer-constructor called `DataSourceLayer` which has a `.live` implementation which converts
`ZIO[Connection, SQLException, T]` to `ZIO[DataSource with Closeable, SQLException, T]` by taking a connection from the
data-source and returning it immediately afterward, this is the analogue of what `QDataSource.toConnection` use to do.
You can use it like this:
```scala
def hikariDataSource: DataSource with Closeable = ...
val zioConn: ZLayer[Any, Throwable, Connection] =
  Task(hikariDataSource).toLayer >>> DataSourceLayer.live
run(people)
  .provideCustomLayer(zioConn)
```

You can also use the extension method `.onDataSource` (or `.onDS` for short) to do the same thing:
```scala
def hikariDataSource: DataSource with Closeable = ...
run(people)
  .onDataSource
  .provide(Has(hikariDataSource))
```

Also, constructor-methods `fromPrefix`, `fromConfig`, `fromJdbcConfig` and `fromDataSource` are available on
`DataSourceLayer` to construct instances of `ZLayer[DataSource with Closeable, SQLException, Connection]`.
Combined with the `toDataSource` construct, these provide a simple way to construct various Hikari pools from
a corresponding typesafe-config file `application.conf`.
```scala
run(people)
  .onDataSource
  .provideLayer(DataSourceLayer.fromPrefix("testPostgresDB"))
```

Also note that the objects `QDataSource` and `QConnection` have not yet been removed. Instead, all of their methods
have been marked as deprecated and a comment on what calls using `DataSourceLayer`/`onDataSource` to use instead
have been added.

#### Cassandra:

Similar changes have been made in quill-cassandra-zio. `CassandraZioSession with Blocking` has been replaced
with just `CassandraZioSession` so now this is much easier to provide:

```scala
val session: CassandraZioSession = _
run(people)
  .provide(Has(session))
```

The CassandraZioSession constructors however are all still fine to use:

```scala
 val zioSessionLayer: ZLayer[Any, Throwable, CassandraZioSession] =
   CassandraZioSession.fromPrefix("testStreamDB")
run(query[Person])
  .provideCustomLayer(zioSessionLayer)
```

# 3.7.2

- [Fix FutureAsyncCache](https://github.com/getquill/quill/pull/2162)
- [Catch close() exceptions](https://github.com/getquill/quill/pull/2166)
- [Make all AST classes final](https://github.com/getquill/quill/pull/2165)

# 3.7.1

- [Add a single ZIO-inspired root type QAC for all actions](https://github.com/getquill/quill/pull/2130)
- [Adding fromDataSource ZIO API and example](https://github.com/getquill/quill/pull/2131)

# 3.7.0

- [ZIO Cassandra](https://github.com/getquill/quill/pull/2106)
- [Zio](https://github.com/getquill/quill/pull/1989)

Migration Notes:
In order to properly accommodate a good ZIO experience, several refactorings had to be done to various
internal context classes, none of these changes modify class structure in a breaking way.

The following was done for quill-jdbc-zio
- Query Preparation base type definitions have been moved out of `JdbcContextSimplified` into `JdbcContextBase`
  which inherits a class named `StagedPrepare` which defines prepare-types (e.g. `type PrepareQueryResult = Session => Result[PrepareRow]`).
- This has been done so that the ZIO JDBC Context can define prepare-types via the ZIO `R` parameter instead of
  a lambda parameter (e.g. `ZIO[QConnection, SQLException, PrepareRow]` a.k.a. `QIO[PrepareRow]`).
- In order prevent user-facing breaking changes. The contexts in `BaseContexts.scala` now extend from both `JdbcContextSimplified` (indirectly)
  and `JdbcContextBase` thus preserving the `Session => Result[PrepareRow]` prepare-types.
- The context `JdbcContextSimplified` now contains the `prepareQuery/Action/BatchAction` methods used by all contexts other than the ZIO
  contexts which define these methods independently (since they use the ZIO `R` parameter).
- All remaining context functionality (i.e. the `run(...)` series of functions) has been extracted out into `JdbcRunContext` which the
  ZIO JDBC Contexts in `ZioJdbcContexts.scala` as well as all the other JDBC Contexts now extend.

Similarly for quill-cassandra-zio
- The CassandraSessionContext on which the CassandraMonixContext and all the other Cassandra contexts are based on keeps internal state (i.e. session, keyspace, caches).
- This state was pulled out as separate classes e.g. `SyncCache`, `AsyncFutureCache` (the ZIO equivalent of which is `AsyncZioCache`).
- Then a `CassandraZioSession` is created which extends these state-containers however, it is not directly a base-class of the `CassandraZioContext`.
- Instead it is returned as a dependency from the CassandraZioContext run/prepare commands as part of the type
  `ZIO[CassandraZioSession with Blocking, Throwable, T]` (a.k.a `CIO[T]`). This allows the primary context CassandraZioContext to be stateless.

# 3.6.1

- [Memoize Passed-By-Name Quats of Asts Ident, Entity, and Others](https://github.com/getquill/quill/pull/2084)
- [Minor Quat Fixes and More Tests](https://github.com/getquill/quill/pull/2057)

Migration Notes:

 - Memoization of Quats should improve performance of dynamic queries based on some profiling analysis. This
   change should not have any user-facing changes.

# 3.6.0
This description is an aggregation of the 3.6.0-RC1, RC2 and RC3 as well as several new items.

 - [Quat Enhancements to Support Needed Spark Use Cases](https://github.com/getquill/quill/pull/2010)
 - [Add support for scala 2.13 to quill-cassandra-lagom](https://github.com/getquill/quill/pull/1909)
 - [Change all Quat fields to Lazy](https://github.com/getquill/quill/pull/2004)
 - [Smart serialization based on number of Quat fields](https://github.com/getquill/quill/pull/1997)
 - [Better Dynamic Query DSL For Quats on JVM](https://github.com/getquill/quill/pull/1993)
 - [Fix incorrect Quat.Value parsing issues](https://github.com/getquill/quill/pull/1987)
 - [Fix Query in Nested Operation and Infix](https://github.com/getquill/quill/pull/1980)
 - [Fix Logic table, replicate Option.getOrElse optimization to Boolean Quats](https://github.com/getquill/quill/pull/1975)
 - [Fixes + Enhancements to Boolean Optional APIs](https://github.com/getquill/quill/pull/1970)
 - [Fix for Boolean Quat Issues](https://github.com/getquill/quill/pull/1967)

Migration Notes:

 - The Cassandra base UDT class `io.getquill.context.cassandra.Udt` has been moved to `io.getquill.Udt`.
 - When working with databases which do not support boolean literals (SQL Server, Oracle, etc...) infixes representing booleans
   will be converted to equality-expressions.

   For example:
   ```
   query[Person].filter(p => sql"isJoe(p.name)".as[Boolean])
   // SELECT ... FROM Person p WHERE isJoe(p.name)
   // Becomes> SELECT ... FROM Person p WHERE 1 = isJoe(p.name)
   ```
   This is because the aforementioned databases do not directly support boolean literals (i.e. true/false) or expressions
   that yield them.

   In some cases however, it is desirable for the above behavior not to happen and for the whole infix statement to be treated
   as an expression. For example
   ```
   query[Person].filter(p => sql"${p.age} > 21".as[Boolean])
   // We Need This> SELECT ... FROM Person p WHERE p.age > 21
   // Not This> SELECT ... FROM Person p WHERE 1 = p.age > 21
   ```
   In order to have this behavior, instead of `sql"...".as[Boolean]`, use `sql"...".asCondition`.
   ```
   query[Person].filter(p => sql"${p.age} > 21".asCondition)
   // We Need This> SELECT ... FROM Person p WHERE p.age > 21
   ```
   If the condition represents a pure function, be sure to use `sql"...".pure.asCondition`.

# 3.6.0-RC3

 - [Add support for scala 2.13 to quill-cassandra-lagom](https://github.com/getquill/quill/pull/1909)
 - [Change all Quat fields to Lazy](https://github.com/getquill/quill/pull/2004)
 - [Smart serialization based on number of Quat fields](https://github.com/getquill/quill/pull/1997)
 - [Better Dynamic Query DSL For Quats on JVM](https://github.com/getquill/quill/pull/1993)

# 3.6.0-RC2

- [Fix incorrect Quat.Value parsing issues](https://github.com/getquill/quill/pull/1987)

Migration Notes:

 - When working with databases which do not support boolean literals (SQL Server, Oracle, etc...) infixes representing booleans
   will be converted to equality-expressions.

   For example:
   ```
   query[Person].filter(p => sql"isJoe(p.name)".as[Boolean])
   // SELECT ... FROM Person p WHERE isJoe(p.name)
   // Becomes> SELECT ... FROM Person p WHERE 1 = isJoe(p.name)
   ```
   This is because the aforementioned databases do not directly support boolean literals (i.e. true/false) or expressions
   that yield them.

   In some cases however, it is desirable for the above behavior not to happen and for the whole infix statement to be treated
   as an expression. For example
   ```
   query[Person].filter(p => sql"${p.age} > 21".as[Boolean])
   // We Need This> SELECT ... FROM Person p WHERE p.age > 21
   // Not This> SELECT ... FROM Person p WHERE 1 = p.age > 21
   ```
   In order to have this behavior, instead of `sql"...".as[Boolean]`, use `sql"...".asCondition`.
   ```
   query[Person].filter(p => sql"${p.age} > 21".asCondition)
   // We Need This> SELECT ... FROM Person p WHERE p.age > 21
   ```
   If the condition represents a pure function, be sure to use `sql"...".pure.asCondition`.
 - This release is not binary compatible with any Quill version before 3.5.3.
 - Any code generated by the Quill Code Generator with `quote { ... }` blocks will have to be regenerated with this
   Quill version if generated before 3.5.3.
 - In most SQL dialects (i.e. everything except Postgres) boolean literals and expressions yielding them are
    not supported so statements such as `SELECT foo=bar FROM ...` are not supported. In order to get equivalent logic,
    it is necessary to user case-statements e.g.
    ```sql
    SELECT CASE WHERE foo=bar THEN 1 ELSE 0`.
    ```
    On the other hand, in a WHERE-clause, it is the opposite:
    ```sql
    SELECT ... WHERE CASE WHEN (...) foo ELSE bar`
    ```
    is invalid and needs to be rewritten.
    Naively, a `1=` could be inserted:
    ```sql
    SELECT ... WHERE 1 = (CASE WHEN (...) foo ELSE bar)
    ```
    Note that this behavior can disabled via the `-Dquill.query.smartBooleans` switch
    when issued during compile-time for compile-time queries and during runtime for runtime
    queries.

    Additionally, in certain situations, it is far more preferable to express this without the `CASE WHEN` construct:
    ```sql
    SELECT ... WHERE ((...) && foo) || !(...) && foo
    ```
   This is because CASE statements in SQL are not sargable and generally [cannot be well optimized](https://dba.stackexchange.com/questions/209025/sargability-of-queries-against-a-view-with-a-case-expression).

 - A large portion of the Quill DSL has been moved outside of QueryDsl into the top level under the `io.getquill` package. Due to this change, it may be necessary to import `io.getquill.Query` if you are not already importing `io.getquill._`.

# 3.6.0-RC1

- [Fix Query in Nested Operation and Infix](https://github.com/getquill/quill/pull/1980)
- [Fix Logic table, replicate Option.getOrElse optimization to Boolean Quats](https://github.com/getquill/quill/pull/1975)
- [Fixes + Enhancements to Boolean Optional APIs](https://github.com/getquill/quill/pull/1970)
- [Fix for Boolean Quat Issues](https://github.com/getquill/quill/pull/1967)

Migration Notes:

 - This release is not binary compatible with any Quill version before 3.5.3.
 - Any code generated by the Quill Code Generator with `quote { ... }` blocks will have to be regenerated with this
   Quill version if generated before 3.5.3.
 - In most SQL dialects (i.e. everything except Postgres) boolean literals and expressions yielding them are
    not supported so statements such as `SELECT foo=bar FROM ...` are not supported. In order to get equivalent logic,
    it is necessary to user case-statements e.g.
    ```sql
    SELECT CASE WHERE foo=bar THEN 1 ELSE 0`.
    ```
    On the other hand, in a WHERE-clause, it is the opposite:
    ```sql
    SELECT ... WHERE CASE WHEN (...) foo ELSE bar`
    ```
    is invalid and needs to be rewritten.
    Naively, a `1=` could be inserted:
    ```sql
    SELECT ... WHERE 1 = (CASE WHEN (...) foo ELSE bar)
    ```
    Note that this behavior can disabled via the `-Dquill.query.smartBooleans` switch
    when issued during compile-time for compile-time queries and during runtime for runtime
    queries.

    Additionally, in certain situations, it is far more preferable to express this without the `CASE WHEN` construct:
    ```sql
    SELECT ... WHERE ((...) && foo) || !(...) && foo
    ```
   This is because CASE statements in SQL are not sargable and generally [cannot be well optimized](https://dba.stackexchange.com/questions/209025/sargability-of-queries-against-a-view-with-a-case-expression).

 - A large portion of the Quill DSL has been moved outside of QueryDsl into the top level under the `io.getquill` package. Due to this change, it may be necessary to import `io.getquill.Query` if you are not already importing `io.getquill._`.

# 3.5.3

Please skip this release and proceed directly to the 3.6.0-RC line. This release was originally a test-bed for the new Quats-based functionality which was supposed to be a strictly internal mechanism. Unfortunately multiple issues were found. They will be addressed in the 3.6.X line.

- [Adding Quill-Application-Types (Quats) to AST](https://github.com/getquill/quill/pull/1911)
- [Translate boolean literals](https://github.com/getquill/quill/pull/1923)
- [breakdown caseclasses in groupBy clause](https://github.com/getquill/quill/pull/1939)
- [allowed distinct to be placed on an infix](https://github.com/getquill/quill/pull/1935)
- [Change Subquery Expansion to be Quat-based](https://github.com/getquill/quill/pull/1920)
- [Use quats to expand nested queries in Spark](https://github.com/getquill/quill/pull/1932)
- [Fixed bug where alias of filter clause did not match alias of inner query.](https://github.com/getquill/quill/pull/1924)
- [Add default implementations so Query can be more easily inherited from Dotty](https://github.com/getquill/quill/pull/1879)
- [Monix streaming with NDBC](https://github.com/getquill/quill/pull/1750)
- [Fix SqlServer snake case - OUTPUT i_n_s_e_r_t_e_d.id](https://github.com/getquill/quill/pull/1867)

Migration Notes:`
 - Quill 3.5.3 is source-compatible but not binary-compatible with Quill 3.5.2.
 - Any code generated by the Quill Code Generator with `quote { ... }` blocks will have to be regenerated with
 Quill 3.5.3 as the AST has substantially changed.
 - The implementation of Quill Application Types (Quats) has changed the internals of nested query expansion. Queries
 with a `querySchema` or a `schemaMeta` will be aliased between nested clauses slightly differently.
 Given:
   ```
   case class Person(firstName:String, lastName:String)
   val ctx = new SqlMirrorContext(PostgresDialect, Literal)
   ```
   Before:
   ```
   SELECT x.first_name, x.last_name FROM (
     SELECT x.first_name, x.last_name FROM person x) AS x
   ```
   After:
   ```
   SELECT x.firstName, x.lastName FROM (
     SELECT x.first_name AS firstName, x.last_name AS lastName FROM person x) AS x
   ```
   Note however that the semantic result of the queries should be the same. No user-level code change for this
   should be required.

# 3.5.2

- [Add support jasync-sql for postgres](https://github.com/getquill/quill/pull/1793)
- [Add quill-jasync-mysql](https://github.com/getquill/quill/pull/1813)
- [Delete returning](https://github.com/getquill/quill/pull/1870)
- [Fix SqlServer snake case - OUTPUT i_n_s_e_r_t_e_d.id](https://github.com/getquill/quill/pull/1867)
- [Add translate to NDBC Context](https://github.com/getquill/quill/pull/1865)
- [Apply NamingStrategy after applying prefix](https://github.com/getquill/quill/pull/1807)
- [Remove use of `Row#getAnyOption` from `FinaglePostgresDecoders`](https://github.com/getquill/quill/pull/1848)
- [Better error message about lifting for enum types](https://github.com/getquill/quill/pull/1803)
- [More 2.13 modules](https://github.com/getquill/quill/pull/1753)

Migration Notes:
 - Much of the content in `QueryDsl` has been moved to the top-level for better portability with the upcoming Dotty
implementation. This means that things like `Query` are no longer part of `Context` but now are directly in the
`io.getquill` package. If you are importing `io.getquill._` your code should be unaffected.
 - Custom decoders written for Finagle Postgres no longer require a `ClassTag`.

# 3.5.1

- [Fix Ndbc runtime logging](https://github.com/getquill/quill/pull/1748)
- [Support for onConflictIgnore to H2 in PostgreSQL mode](https://github.com/getquill/quill/pull/1731)
- [Update Returning](https://github.com/getquill/quill/pull/1720)

# 3.5.0

- [Ndbc Postgres Support](https://github.com/getquill/quill/pull/1702)
- [MS SQL Server returning via OUTPUT](https://github.com/getquill/quill/pull/1681)
- [Pretty Print SQL Queries](https://github.com/getquill/quill/pull/1646)
- [Fix shadowing via aggressive uncapture](https://github.com/getquill/quill/pull/1713)
- [Fix Issues with Short](https://github.com/getquill/quill/pull/1698)
- [Pull Oracle jdbc driver from Maven Central](https://github.com/getquill/quill/pull/1677)

# 3.4.10

- [support scala 2.13](https://github.com/getquill/quill/pull/1644)
- [Remove redundant null checks](https://github.com/getquill/quill/pull/1648)
- [Improve error message](https://github.com/getquill/quill/pull/1503)

# 3.4.9

- [Properly unhide properties in nested queries](https://github.com/getquill/quill/pull/1637)

# 3.4.8

- [Additional Fixes for Embedded Entities in Nested Queries](https://github.com/getquill/quill/pull/1628)
- [Fix java.sql.SQLException corner case](https://github.com/getquill/quill/pull/1614)
- [Feature/local time support](https://github.com/getquill/quill/pull/1596)
- [Update monix-eval, monix-reactive to 3.0.0](https://github.com/getquill/quill/pull/1607)

Documentation Updates:
- [Update database drivers versions to latest in docs](https://github.com/getquill/quill/pull/1617)
- [Document remote debugging in IntelliJ](https://github.com/getquill/quill/pull/1615)

Migration Notes:
- Monix 3.0.0 is not binary compatible with 3.0.0-RC3 which was a dependency of Quill 3.4.7.
If you are using the Quill Monix modules, please update your dependencies accordingly.

# 3.4.7

- [Fix RenameProperties with Schema Re-Arrangement](https://github.com/getquill/quill/pull/1613)

# 3.4.6

- [Fixing Embedded Coproduct Column Duplication Issue](https://github.com/getquill/quill/pull/1604)

# 3.4.5

- [Express Infix Clauses not in Select List](https://github.com/getquill/quill/pull/1597)

# 3.4.4

- [added MappedEncoding for encoding and decoding OffsetDateTime](https://github.com/getquill/quill/pull/1595)
- [Fix onConflict with custom schemaMeta](https://github.com/getquill/quill/pull/1589)

# 3.4.3

- [Nested Query does not work correctly with NamingSchemas](https://github.com/getquill/quill/issues/1577)

# 3.4.2

- [Fix NamingStrategy override of querySchema](https://github.com/getquill/quill/pull/1560)
- [Monix JDBC scheduling fixes ](https://github.com/getquill/quill/pull/1546)

Migration Notes:
- `NamingStrategy` is no longer applied on column and table names defined in `querySchema`, all
column and table names defined in `querySchema` are now final. If you are relying on this behavior to
name your columns/tables correctly, you will need to update your `querySchema` objects.

# 3.4.1

- [Fix property order expanded sub-queries](https://github.com/getquill/quill/pull/1541)

Migration Notes:
- Nested sub-queries will now have their terms re-ordered in certain circumstances although the functionality
of the entire query should not change. If you have deeply nested queries with Infixes, double check that
they are in the correct position.

# 3.4.0

- [Nest queries with infix values to allow infix-impurity](https://github.com/getquill/quill/pull/1534)

Migration Notes:
- Infixes are now not treated as pure functions by default. This means wherever they are used, nested queries may be created.
You can use `.pure` (e.g. `sql"MY_PURE_UDF".pure.as[T]`) to revert to the previous behavior. See the
[Infix](https://github.com/getquill/quill/tree/36842c4801c95a7609ba94c450645f3c022b3e2e#infix)
section of the documentation for more detail.

# 3.3.0

- [Returning Record](https://github.com/getquill/quill/pull/1489)
- [Change == and != to be Scala-idiomatic ](https://github.com/getquill/quill/pull/1453)
- [Optimize === comparisons when ANSI behavior assumed](https://github.com/getquill/quill/pull/1454)
- [API to get PreparedStatement from Query for Low Level Use-cases](https://github.com/getquill/quill/pull/1459)
- [Add BoundStatement support for all context.](https://github.com/getquill/quill/pull/1425)
- [Only decode when field is non-null](https://github.com/getquill/quill/pull/1514)
- [Fix support of nested transactions in Finagle-Postgres](https://github.com/getquill/quill/pull/1501)
- [Returning shadow fix](https://github.com/getquill/quill/pull/1509)
- [Fix SQL Server Subqueries with Order By](https://github.com/getquill/quill/pull/1478)
- [Explicitly pass AsyncContext type params](https://github.com/getquill/quill/pull/1457)
- [Remove unneeded Tuple reduction clause](https://github.com/getquill/quill/pull/1481)
- [Fix join subquery+map+distinct and sortBy+distinct](https://github.com/getquill/quill/pull/1462)
- [Fix Java9 depreciation message](https://github.com/getquill/quill/pull/1504)

Noteworthy Version Bumps:
- monix - 3.0.0-RC3
- cassandra-driver-core - 3.7.2
- orientdb-graphdb - 3.0.21
- postgresql - 42.2.6
- sqlite-jdbc - 3.28.0

Migration Notes:
- The `returning` method no long excludes the specified ID column from the insertion as it used to.
Use the `returningGenerated` method in order to achieve that.
See the 'Database-generated values' section of the documentation for more detail.
- The `==` method now works Scala-idiomatically.
That means that when two `Option[T]`-wrapped columns are compared, `None == None` will now yield `true`.
The `===` operator can be used in order to compare `Option[T]`-wrapped columns in a ANSI-SQL
idiomatic way i.e. `None == None := false`. See the 'equals' section of the documentation for more detail.

# 3.2.0

- [Allow == for Option[T] and/or T columns](https://github.com/getquill/quill/pull/1437)
- [Introducing Code Generator](https://github.com/getquill/quill/pull/1396)
- [Fix variable shadowing issue in action metas](https://github.com/getquill/quill/pull/1412)
- [Change effect to protected](https://github.com/getquill/quill/pull/1413)
- [Update spark-sql to 2.4.1](https://github.com/getquill/quill/pull/1398)
- [Update orientdb-graphdb to 3.0.17](https://github.com/getquill/quill/pull/1379)
- [Update sqlite-jdbc to 3.27.2.1](https://github.com/getquill/quill/pull/1392)

# 3.1.0

- [oracle support](https://github.com/getquill/quill/pull/1295)
- [quill cassandra for lagom](https://github.com/getquill/quill/pull/1299)
- [Fix the problem with re-preparing already prepared statements](https://github.com/getquill/quill/issues/1268)
- [Rely on ANSI null-fallthrough where possible](https://github.com/getquill/quill/pull/1341)
- [Fix for non-fallthrough null operations in map/flatMap/exists](https://github.com/getquill/quill/pull/1302)
- [Move basic encoders into EncodingDsl](https://github.com/getquill/quill/pull/1327)
- [Make string column name as property](https://github.com/getquill/quill/pull/1332)
- [Update MySQL driver/datasource](https://github.com/getquill/quill/pull/1326)
- [Provide a better "Can't tokenize a non-scalar lifting" error message](https://github.com/getquill/quill/pull/1311)

# 3.0.1

- [Fix Monix JDBC Connection Leak](https://github.com/getquill/quill/pull/1313)
- [Action task needs to flatMap](https://github.com/getquill/quill/pull/1307)

# 3.0.0

- [First-class support for dynamic queries](https://github.com/getquill/quill/pull/1262)
- [support dynamic strings within infix](https://github.com/getquill/quill/pull/1256)
- [Create a streaming module for Monix over JDBC - combined approach](https://github.com/getquill/quill/pull/1263)
- [Better implementation of Spark nested objects.](https://github.com/getquill/quill/pull/1227)
- [Spark 2.4 (with Scala 2.12 support)](https://github.com/getquill/quill/pull/1250)
- [Create quill-cassandra-monix](https://github.com/getquill/quill/pull/1278)
- [Move `io.getquill.CassandraStreamContext` into `quill-cassandra-streaming-monix` module](https://github.com/getquill/quill/pull/1259)
- [filterIf method for dynamic queries](https://github.com/getquill/quill/pull/1281)
- [Make UDT encoding to support options](https://github.com/getquill/quill/pull/1266)
- [fix column name conflict](https://github.com/getquill/quill/pull/1254)
- [#1204 add explicit `AS` for aliases (except table context)](https://github.com/getquill/quill/pull/1252)
- [sqlite dialect - translate boolean literals into 1/0](https://github.com/getquill/quill/pull/1248)
- [sqlite dialect - ignore null ordering](https://github.com/getquill/quill/pull/1247)
- [fail is property is not a case accessor](https://github.com/getquill/quill/pull/1246)
- [verify table references](https://github.com/getquill/quill/pull/1244)
- [fix property renaming for nested queries within infixes](https://github.com/getquill/quill/pull/1243)
- [expand map.distinct](https://github.com/getquill/quill/pull/1242)
- [quill-spark: fix groupby with multiple columns](https://github.com/getquill/quill/pull/1240)
- [quill-spark: escape strings](https://github.com/getquill/quill/pull/1239)
- [StatementInterpolator performance improvements](https://github.com/getquill/quill/pull/1280)
- [fix async transactions for scala future + io monad](https://github.com/getquill/quill/pull/1187)
- [Update orientdb-graphdb to 3.0.13](https://github.com/getquill/quill/pull/1286)
- [update guava version to 27.0.1-jre](https://github.com/getquill/quill/pull/1294)
- [documentation improvements](https://github.com/getquill/quill/pull/1241)

### Migration notes

- `io.getquill.CassandraStreamContext` is moved into `quill-cassandra-monix` module and now uses Monix 3.
- `io.getquill.CassandraMonixContext` has been introduced which should eventually replace `io.getquill.CassandraStreamContext`.
- Spark queries with nested objects will now rely on the star `*` operator and `struct` function to generate sub-schemas as opposed to full expansion of the selection.
- Most functionality from `JdbcContext` has been moved to `JdbcContextBase` for the sake of re-usability. `JdbcContext` is only intended to be used for synchronous JDBC.

# 2.6.0

- [add noFailFast option to FinagleMysqlContextConfig](https://github.com/getquill/quill/pull/1212)
- [add transactionWithIsolation to FinagleMysqlContext](https://github.com/getquill/quill/pull/1213)
- [Add encoding between java.time.ZonedDateTime and java.util.Date](https://github.com/getquill/quill/pull/1189)
- [Fix Infix causing ignoring renamings](https://github.com/getquill/quill/pull/1183)
- [Cassandra async improvements](https://github.com/getquill/quill/pull/1149)
- [Add upsert support for SQLite](https://github.com/getquill/quill/pull/1191)
- [add IO.lift](https://github.com/getquill/quill/pull/1146)
- [Minor performance improvements](https://github.com/getquill/quill/pull/1148)
- [Add encoder/decoder for Byte](https://github.com/getquill/quill/pull/1143)
- [Use Option.getOrElse(boolean) to generate ... OR IS [NOT] NULL queries](https://github.com/getquill/quill/pull/1142)
- [Upgrade finagle to 18.8.0](https://github.com/getquill/quill/pull/1144)
- [Fix renaming fields with schema/query meta for queries where unary/binary operation produces nested query](https://github.com/getquill/quill/pull/1134)
- [scala-js 0.6.24](https://github.com/getquill/quill/pull/1125)
- [Add question mark escaping for Spark](https://github.com/getquill/quill/pull/1112)
- [Allow mapping MySQL `TIMESTAMP` and `DATETIME` to Joda `DateTime` type.](https://github.com/getquill/quill/pull/1116)
- [added error message example in the documentation.](https://github.com/getquill/quill/pull/1113)
- [Wrong timeout configs](https://github.com/getquill/quill/pull/1089)
- [Fix unnecessary nesting of infix queries](https://github.com/getquill/quill/pull/1131)

### Migration notes

- When the infix starts with a query, the resulting sql query won't be nested

# 2.5.4

- [Adds master-slave capability to FinagleMysqlContext](https://github.com/getquill/quill/pull/1079)
- [Fix concatenation operator for SQL Server](https://github.com/getquill/quill/commit/83b80a1e894e53608e1ccca80a85038e7351b0e1)
- [Use PreparedStatement.getConnection for JDBC Array Encoders](https://github.com/getquill/quill/pull/1086)
- [CassandraSessionContext : change session to a lazy val](https://github.com/getquill/quill/pull/1082)

# 2.5.0, 2.5.1, 2.5.2, and 2.5.3

Broken releases, do not use.

# 2.4.2

- [Fix for Nested Objects with Spark](https://github.com/getquill/quill/pull/1074)

# 2.4.1

- [Add support of upsert for Postgres and MySQL](https://github.com/getquill/quill/pull/987)
- [Add flatMap, flatten, getOrElse and Option.apply](https://github.com/getquill/quill/pull/1044)
- [`quill-cassandra`: Add encoding for `Byte` and `Short`](https://github.com/getquill/quill/pull/1049)
- [Fix renaming aggregated properties in groupBy with custom querySchema](https://github.com/getquill/quill/pull/1038)
- [Change referencing `super.prepare` call to `this.prepare` in quill-cassandra contexts](https://github.com/getquill/quill/pull/1067)
- [Add connectTimeout option into FinagleMysqlContextConfig](https://github.com/getquill/quill/pull/1070)

# 2.3.3

- [Dependency updates](https://github.com/getquill/quill/pull/1042)
- [update finagle-postgres to 0.7.0](https://github.com/getquill/quill/pull/1030)
- [fixing unions with Ad-Hoc tuples](https://github.com/getquill/quill/pull/1026)
- [Fix removing assignment in returning insert if embedded field has columns with the same name as in parent case class](https://github.com/getquill/quill/pull/1010)

# 2.3.2

- [Simplify multiple `AND` `OR` sql generation](https://github.com/getquill/quill/pull/1003)
- [Fix SQLServer take/drop SQL syntax](https://github.com/getquill/quill/pull/1005)
- [Fix for Ad-Hoc Case Class producing Dynamic Queries](https://github.com/getquill/quill/pull/1000)
- [Fix throwing exception instead of failed future in cassandra async prepare](https://github.com/getquill/quill/pull/996)
- [Fix invalid alias with distinct](https://github.com/getquill/quill/pull/986)
- [Log errors instead of throwing exception directly in several places](https://github.com/getquill/quill/pull/995)
- [Update finagle to 17.12.0](https://github.com/getquill/quill/pull/999)

# 2.3.1

- [Fix Ad-Hoc Case Classes for Spark](https://github.com/getquill/quill/pull/981)
- [Make the error reporting of comparing `Option` to `null` to point actual position](https://github.com/getquill/quill/pull/982)
- [Fix postgres query probing failing for queries with wildcards](https://github.com/getquill/quill/pull/983)
- [Dependency updates](https://github.com/getquill/quill/pull/977)
- [Update finagle to 17.11.0](https://github.com/getquill/quill/pull/976)

# 2.3.0

- [Ad-Hoc Tuple Support in Quotations](https://github.com/getquill/quill/pull/957)
- [fix regression: allow table references](https://github.com/getquill/quill/pull/968)

# 2.2.0

- [Fix StackOverflowError in select distinct with aggregation](https://github.com/getquill/quill/pull/954)
- [Add support of java.time.Instant/java.time.LocalDate for quill-casandra](https://github.com/getquill/quill/pull/953)
- [Fix select query for unlimited optional embedded case classes](https://github.com/getquill/quill/pull/955)
- [`concatMap`, `startsWith`, and `split` support](https://github.com/getquill/quill/pull/956)
- [Upgrade finagle to 17.10.0](https://github.com/getquill/quill/pull/959)

# 2.1.0

- [Spark SQL support](https://github.com/getquill/quill/pull/941)
- [Add support of postgres sql arrays operators](https://github.com/getquill/quill/pull/940)
- [Fix reversed log parameter binds](https://github.com/getquill/quill/pull/931)
- [Fix renaming properties for unlimited optional and raw `Embedded` case classes](https://github.com/getquill/quill/pull/934)
- [Improve coverage](https://github.com/getquill/quill/pull/933)
- [Dependency updates](https://github.com/getquill/quill/pull/937)
- [Converge of PostgreSQL and MySQL behavior](https://github.com/getquill/quill/pull/938)

# 2.0.0

We're proud to announce the Quill 2.0. **All** bugs were fixed, so this release doesn't have any known bugs!

- [IO monad](https://github.com/getquill/quill/pull/881)
- [fall back to dynamic queries if dialect/naming isn't available](https://github.com/getquill/quill/pull/886)
- [Cassandra UDT encoding](https://github.com/getquill/quill/pull/852)
- [Add support of 'contains' operation on Cassandra collections](https://github.com/getquill/quill/pull/813)
- [Add org.joda.time.DateTime and java.time.ZonedDateTime encoding for quill-async-postgres](https://github.com/getquill/quill/pull/896)
- [Update dependencies](https://github.com/getquill/quill/pull/927)
- [give a better error message for option.get](https://github.com/getquill/quill/pull/905)
- [Remove OrientDB async context](https://github.com/getquill/quill/pull/884)
- [remove anonymous class support](https://github.com/getquill/quill/pull/891)
- [Remove client.ping from the FinagleMysqlContext constructor](https://github.com/getquill/quill/pull/895)

## Fixes

[#872](https://github.com/getquill/quill/pull/872), [#874](https://github.com/getquill/quill/pull/874), [#875](https://github.com/getquill/quill/pull/875), [#877](https://github.com/getquill/quill/pull/877), [#879](https://github.com/getquill/quill/pull/879), [#889](https://github.com/getquill/quill/pull/889), [#890](https://github.com/getquill/quill/pull/890), [#892](https://github.com/getquill/quill/pull/892), [#894](https://github.com/getquill/quill/pull/894), [#897](https://github.com/getquill/quill/pull/897), [#899](https://github.com/getquill/quill/pull/899), [#900](https://github.com/getquill/quill/pull/900), [#903](https://github.com/getquill/quill/pull/903), [#902](https://github.com/getquill/quill/pull/902), [#904](https://github.com/getquill/quill/pull/904), [#906](https://github.com/getquill/quill/pull/906), [#907](https://github.com/getquill/quill/pull/907), [#908](https://github.com/getquill/quill/pull/908), [#909](https://github.com/getquill/quill/pull/909), [#910](https://github.com/getquill/quill/pull/910), [#913](https://github.com/getquill/quill/pull/913), [#915](https://github.com/getquill/quill/pull/915), [#917](https://github.com/getquill/quill/pull/917), [#920](https://github.com/getquill/quill/pull/920), [#921](https://github.com/getquill/quill/pull/921), [#925](https://github.com/getquill/quill/pull/925), [#928](https://github.com/getquill/quill/pull/928)

### Migration notes

- Sources now take a parameter for idiom and naming strategy instead of just type parameters. For instance, `new SqlSource[MysqlDialect, Literal]` becomes `new SqlSource(MysqlDialect, Literal)`.
- Composite naming strategies don't use mixing anymore. Instead of the type `Literal with UpperCase`, use parameter value `NamingStrategy(Literal, UpperCase)`.
- Anonymous classes aren't supported for function declaration anymore. Use a method with a type parameter instead. For instance, replace `val q = quote { new { def apply[T](q: Query[T]) = ... } }` by `def q[T] = quote { (q: Query[T] => ... }`

# 1.4.0

- [Allow unlimited nesting of embedded case classes and optionals](https://github.com/getquill/quill/pull/660)
- [Accept traversables for batch action](https://github.com/getquill/quill/pull/682)
- [Add joda time encoding to `quill-async`](https://github.com/getquill/quill/pull/837)
- [Remove unnecessary `java.sql.Types` usage in JDBC decoders](https://github.com/getquill/quill/pull/841)
- [Add mappedEncoder and mappedDecoder for AnyVal](https://github.com/getquill/quill/pull/839)
- [Support contains, exists, forall for optional embedded case classes with optional fields](https://github.com/getquill/quill/pull/838)
- [Improve error message for "Can't expand nested value ..." error](https://github.com/getquill/quill/pull/840)
- [Improve error message for query probing](https://github.com/getquill/quill/pull/845)
- [Report the exactly tree position while typechecking the query](https://github.com/getquill/quill/pull/854)
- [Fix inserting single auto generated column](https://github.com/getquill/quill/pull/850)
- [Update finagle to 7.0.0](https://github.com/getquill/quill/pull/858)
- [Dependency updates](https://github.com/getquill/quill/pull/856)

### Migration notes

- `quill-async` contexts: `java.time.LocalDate` now supports only `date` sql types, `java.time.LocalDateTime` -
only `timestamp` sql types. Joda times follow this conventions accordingly.
Exception is made to `java.util.Date` it supports both `date` and `timestamp` types due to
historical moments (`java.sql.Timestamp` extents `java.util.Date`).
- `quill-jdbc` encoders do not accept `java.sql.Types` as a first parameter anymore.

# 1.3.0

- [SQLServer support](https://github.com/getquill/quill/pull/731)
- [OrientDB support](https://github.com/getquill/quill/pull/816)
- [Query bind variables logging](https://github.com/getquill/quill/pull/809)
- [Add url configuration property for quill-async](https://github.com/getquill/quill/pull/821)
- [Add support infix for batch actions](https://github.com/getquill/quill/pull/824)
- [Better support for empty lifted queries](https://github.com/getquill/quill/pull/794)
- [SQLLite 3.18.0](https://github.com/getquill/quill/pull/792)
- [Fix nested query stack overflow](https://github.com/getquill/quill/pull/786)
- [Performance optimization of Interleave](https://github.com/getquill/quill/pull/811)
- [Performance optimization of ReifyStatement](https://github.com/getquill/quill/pull/823)
- [Fix invalid nested queries with take/drop](https://github.com/getquill/quill/pull/779)
- [Fix NPE when using nested quoted binding](https://github.com/getquill/quill/pull/817)
- [Make `withConnection` method protected in AsyncContext](https://github.com/getquill/quill/pull/826)

# 1.2.1

- [upgrade finagle-postgres to 0.4.2](https://github.com/getquill/quill/pull/759)
- [add collections support for row elements (SQL Arrays, Cassandra Collection)](https://github.com/getquill/quill/pull/758)
- [allow querySchema/schemaMeta to rename optional embedded case classes](https://github.com/getquill/quill/pull/767)
- [make Quill compatible with Scala 2.12.2](https://github.com/getquill/quill/pull/770)
- [upgrade finagle-mysql to 6.44.0](https://github.com/getquill/quill/pull/772)

# 1.1.1

**see migration notes below**

- [avoid dynamic query generation for option.contains](https://github.com/getquill/quill/pull/696)
- [fix forall behaviour in quotation](https://github.com/getquill/quill/pull/699)
- [change query compilation log level to debug](https://github.com/getquill/quill/pull/709)
- [fix infix query compilation](https://github.com/getquill/quill/pull/711)
- [add support for Cassandra DATE type](https://github.com/getquill/quill/pull/723)
- [fix finagle timezone issues](https://github.com/getquill/quill/pull/722)
- [add max prepare statement configuration](https://github.com/getquill/quill/pull/722)
- [upgrade finagle-mysql to 6.43.0](https://github.com/getquill/quill/pull/726)
- [fix compilation issue when import List type](https://github.com/getquill/quill/pull/735)
- [upgrade cassandra-driver to 3.2.0](https://github.com/getquill/quill/pull/736)
- [apply NamingStrategy to returning column](https://github.com/getquill/quill/pull/734)
- [upgrade scala to 2.11.11](https://github.com/getquill/quill/pull/752)
- [fix finagle mysql context constructor with timezone](https://github.com/getquill/quill/pull/744)
- [rename Cassandra property address translater to translator](https://github.com/getquill/quill/pull/757)
- [fix timezone handling for finagle-mysql)](https://github.com/getquill/quill/pull/754)

### Migration notes

- Cassandra context property `ctx.session.addressTranslater` is renamed to `ctx.session.addressTranslator`

# 1.1.0

**see migration notes below**

- [materialize encoding for generic value classes](https://github.com/getquill/quill/pull/657)
- [sbt option to hide debug messages during compilation](https://github.com/getquill/quill/pull/633)
- [support Option.contains](https://github.com/getquill/quill/pull/669)
- [recursive optional nested expanding](https://github.com/getquill/quill/pull/681)
- [apply naming strategy to column alias](https://github.com/getquill/quill/pull/676)
- [fix existing and add missing encoders and decoders for java.util.UUID](https://github.com/getquill/quill/pull/665)
- [upgrade finagle-postgres to 0.3.2](https://github.com/getquill/quill/pull/692)

### Migration notes

- JDBC contexts are implemented in separate classes - `PostgresJdbcContext`, `MysqlJdbcContext`, `SqliteJdbcContext`, `H2JdbcContext`
- all contexts are supplied with default `java.util.UUID` encoder and decoder

# 1.0.1

- [include SQL type info in Encoder/Decoder](https://github.com/getquill/quill/pull/588)
- [make encoder helpers and wrapper type public for quill-finagle-postgres](https://github.com/getquill/quill/pull/608)
- [fix property renaming normalization order](https://github.com/getquill/quill/pull/609)
- [workaround compiler bug involving reflective calls](https://github.com/getquill/quill/pull/612)
- [fix flat joins support](https://github.com/getquill/quill/pull/613)
- [encoders and decoders refactoring](https://github.com/getquill/quill/pull/614)
- [avoid alias conflict for multiple nested explicit joins](https://github.com/getquill/quill/pull/616)
- [avoid merging filter condition into a groupBy.map](https://github.com/getquill/quill/pull/618)
- [move Embedded from `io.getquill.dsl.MetaDsl` inner context to `io.getquill` package](https://github.com/getquill/quill/pull/621)
- [make `currentConnection` protected](https://github.com/getquill/quill/pull/628)
- [add abstract encoders/decoders to CassandraContext and uuid mirror encoder/decoder](https://github.com/getquill/quill/pull/635)
- [made the SQL types for AsyncEncoder/AsyncDecoder generic](https://github.com/getquill/quill/pull/637)

# 1.0.0-RC1 - 20-Oct-2016

- [introduce `finagle-postgres`](https://github.com/getquill/quill/pull/560)
- [introduce meta dsl](https://github.com/getquill/quill/pull/554)
- [expand meta dsl](https://github.com/getquill/quill/pull/590)
- [encoder for java 8 LocalDate & LocalDateTime](https://github.com/getquill/quill/pull/583)
- [Upgraded to Monix 2.x](https://github.com/getquill/quill/pull/578)
- [Make withClient function not private](https://github.com/getquill/quill/pull/575)
- [pass ssl settings to async driver](https://github.com/getquill/quill/pull/587)

### Migration notes

- New API for schema definition: `query[Person].schema(_.entity("people").columns(_.id -> "person_id")` becomes `querySchema[Person]("People", _.id -> "person_id")`. Note that the entity name ("People") is now always required.
- `WrappedValue[T]` no longer exists, Quill can now automatically encode `AnyVal`s.

# 0.10.0 - 5-Sep-2016

**see migration notes below**

* [check types when parsing assignments and equality operations](https://github.com/getquill/quill/pull/532)
* [Update finagle-mysql to finagle 6.37.0](https://github.com/getquill/quill/pull/549/files)
* [Split quill-async into quill-async-mysql and quill-async-postgres](https://github.com/getquill/quill/pull/540)
* [cql: support `+` operator](https://github.com/getquill/quill/pull/530)
* [cassandra context constructor with ready-made Cluster](https://github.com/getquill/quill/pull/529)
* [support forced nested queries](https://github.com/getquill/quill/pull/527)
* [support mapped encoding definition without a context instance](https://github.com/getquill/quill/pull/526)
* [fix class cast exception for returned values](https://github.com/getquill/quill/pull/536)
* [fix free variables detection for the rhs of joins](https://github.com/getquill/quill/pull/528)

### Migration notes

- `mappedEncoding` has been renamed to `MappedEncoding`.
- The way we add async drivers has been changed. To add mysql async to your project use `quill-async-mysql` and for postgre async `quill-async-postgres`. It is no longer necessary to add `quill-async` by yourself.
- Action assignments and equality operations are now typesafe. If there's a type mismatch between the operands, the quotation will not compile.

# 0.9.0 - 22-Aug-2016

**see migration notes below**

* [new encoding, macros refactoring, and additional fixes](https://github.com/getquill/quill/pull/512)
* [Refactor generated to returning keyword in order to return the correct type](https://github.com/getquill/quill/pull/444)
* [Allow finagle-mysql to use Long with INT columns](https://github.com/getquill/quill/pull/467)
* [create sub query if aggregation on distinct query](https://github.com/getquill/quill/pull/472)
* [upgrade dependency to finagle 6.36.0](https://github.com/getquill/quill/pull/479)
* [Make decoder function public](https://github.com/getquill/quill/pull/487)
* [set the scope of all cassandra context type definitions to public](https://github.com/getquill/quill/pull/492)
* [make the cassandra decoder fail when encountering a column with value null](https://github.com/getquill/quill/pull/499)
* [fix Option.\{isEmpty, isDefined, nonEmpty\} show on action.filter](https://github.com/getquill/quill/pull/505)
* [Encoder fix](https://github.com/getquill/quill/pull/503/files)
* [enclose operand-queries of SetOperation in parentheses](https://github.com/getquill/quill/pull/510)

### Migration notes

* The fallback mechanism that looks for implicit encoders defined in the context instance has been removed. This means that if you don't `import context._`, you have to change the specific imports to include the encoders in use.
* `context.run` now receives only one parameter. The second parameter that used to receive runtime values now doesn't exist anymore. Use [`lift` or `liftQuery`](https://github.com/getquill/quill/#bindings) instead.
* Use [`liftQuery` + `foreach`](https://github.com/getquill/quill/#bindings) to perform batch actions and define contains/in queries.
* `insert` now always receives a parameter, that [can be a case class](https://github.com/getquill/quill/#actions).
- Non-lifted collections aren't supported anymore. Example: `query[Person].filter(t => List(10, 20).contains(p.age))`. Use `liftQuery` instead.
* `schema(_.generated())` has been replaced by [`returning`](https://github.com/getquill/quill/#schema).

# 0.8.0 / 17-Jul-2016

**see migration notes below**

* [introduce contexts](https://github.com/getquill/quill/pull/417)
* [sqlite support](https://github.com/getquill/quill/pull/449)
* [scala.js support](https://github.com/getquill/quill/pull/452)
* [support `toInt` and `toLong`](https://github.com/getquill/quill/pull/428)
* [quill-jdbc: support nested `transaction` calls](https://github.com/getquill/quill/pull/430)
* [fix bind order for take/drop with extra param](https://github.com/getquill/quill/pull/429)
* [quotation: allow lifting of `AnyVal`s ](https://github.com/getquill/quill/pull/421)
* [make liftable values work for the cassandra module](https://github.com/getquill/quill/pull/425)
* [apply intermediate map before take/drop](https://github.com/getquill/quill/pull/419)
* [support decoding of optional single-value case classes](https://github.com/getquill/quill/pull/420)
* [make type aliases for `run` results public](https://github.com/getquill/quill/pull/440)
* [fail compilation if query is defined outside a `quote`](https://github.com/getquill/quill/pull/433)
* [fix empty sql string](https://github.com/getquill/quill/pull/443)

### Migration notes

This version [introduces `Context`](https://github.com/getquill/quill/pull/417) as a replacement for `Source`. This change makes the quotation creation dependent on the context to open the path for a few refactorings and improvements we're planning to work on before the `1.0-RC1` release.

Migration steps:

- Remove any import that is not `import io.getquill._`
- Replace the `Source` creation by a `Context` creation. See the [readme](https://github.com/getquill/quill#contexts) for more details. All types necessary to create the context instances are provided by `import io.getquill._`.
- Instead of importing from `io.getquill._` to create quotations, import from you context instance `import myContext._`. The context import will provide all types and methods to interact with quotations and the database.
- See the documentation about [dependent contexts](https://github.com/getquill/quill#dependent-contexts) in case you get compilation errors because of type mismatches.

# 0.7.0 / 2-Jul-2016

* [transform quoted reference](https://github.com/getquill/quill/pull/416)
* [simplify `finagle-mysql` action result type](https://github.com/getquill/quill/pull/358)
* [provide default values for plain-sql query execution](https://github.com/getquill/quill/pull/360)
* [quotation: fix binding conflict](https://github.com/getquill/quill/pull/363)
* [don't consider `?` a binding if inside a quote](https://github.com/getquill/quill/pull/361)
* [fix query generation for wrapped types](https://github.com/getquill/quill/pull/364)
* [use querySingle/query for parametrized query according to return type](https://github.com/getquill/quill/pull/375)
* [remove implicit ordering](https://github.com/getquill/quill/pull/378)
* [remove implicit from max and min](https://github.com/getquill/quill/pull/384)
* [support explicit `Predef.ArrowAssoc` call](https://github.com/getquill/quill/pull/386)
* [added handling for string lists in ClusterBuilder](https://github.com/getquill/quill/pull/395)
* [add naming strategy for pluralized table names](https://github.com/getquill/quill/pull/396)
* [transform ConfiguredEntity](https://github.com/getquill/quill/pull/409)

# 0.6.0 / 9-May-2016

* [explicit bindings using `lift`](https://github.com/getquill/quill/pull/335/files#diff-04c6e90faac2675aa89e2176d2eec7d8R157)
* [Code of Conduct](https://github.com/getquill/quill/pull/296)
* [dynamic type parameters](https://github.com/getquill/quill/pull/351)
* [support contains for Traversable](https://github.com/getquill/quill/pull/290)
* [`equals` support](https://github.com/getquill/quill/pull/328)
* [Always return List for any type of query](https://github.com/getquill/quill/pull/324)
* [quill-sql: support value queries](https://github.com/getquill/quill/pull/354)
* [quill-sql: `in`/`contains` - support empty sets](https://github.com/getquill/quill/pull/329)
* [Support `Ord` quotation](https://github.com/getquill/quill/pull/301)
* [`blockParser` off-by-one error](https://github.com/getquill/quill/pull/292)
* [show ident instead of indent.toString](https://github.com/getquill/quill/pull/307)
* [decode bit as boolean](https://github.com/getquill/quill/pull/308)

# 0.5.0 / 17-Mar-2016

* [Schema mini-DSL and generated values](https://github.com/getquill/quill/pull/226/files#diff-04c6e90faac2675aa89e2176d2eec7d8R212)
* [Support for inline vals in quotation blocks](https://github.com/getquill/quill/pull/271/files#diff-02749abf4d0d51be99715cff7074bc9eR775)
* [Support for Option.\{isEmpty, nonEmpty, isDefined\}](https://github.com/getquill/quill/pull/238/files#diff-02749abf4d0d51be99715cff7074bc9eR688)
* [Tolerant function parsing in option operation](https://github.com/getquill/quill/pull/243/files#diff-6858983f3617753cfb9852426edaa121R481)
* [quill-sql: rename properties and assignments](https://github.com/getquill/quill/pull/250)
* [quill-cassandra: rename properties and assignments](https://github.com/getquill/quill/pull/254)
* [Fix log category](https://github.com/getquill/quill/pull/259)
* [Accept unicode arrows](https://github.com/getquill/quill/pull/257)
* [Add set encoder to SqlSource](https://github.com/getquill/quill/pull/258)
* [Don't quote the source creation tree if query probing is disabled](https://github.com/getquill/quill/pull/268)
* [Bind `drop.take` according to the sql terms order](https://github.com/getquill/quill/pull/278)
* [Avoid silent error when importing the source's implicits for the encoding fallback resolution](https://github.com/getquill/quill/pull/279)
* [Quotation: add identifier method to avoid wrong type refinement inference](https://github.com/getquill/quill/pull/280)
* [Unquote multi-param quoted function bodies automatically](https://github.com/getquill/quill/pull/277)

# 0.4.1 / 28-Feb-2016

* [quill-sql: h2 dialect](https://github.com/getquill/quill/pull/189)
* [support for auto encoding of wrapped types](https://github.com/getquill/quill/pull/199/files#diff-04c6e90faac2675aa89e2176d2eec7d8R854)
* [non-batched actions](https://github.com/getquill/quill/pull/202/files#diff-04c6e90faac2675aa89e2176d2eec7d8L485)
* `distinct` support [[0]](https://github.com/getquill/quill/pull/186/files#diff-02749abf4d0d51be99715cff7074bc9eR38) [[1]](https://github.com/getquill/quill/pull/212)
* [postgres naming strategy](https://github.com/getquill/quill/pull/218)
* [quill-core: unquote quoted function bodies automatically](https://github.com/getquill/quill/pull/222)
* [don't fail if the source annotation isn't available](https://github.com/getquill/quill/pull/210)
* [fix custom aggregations](https://github.com/getquill/quill/pull/207)
* [quill-finagle-mysql: fix finagle mysql execute result loss](https://github.com/getquill/quill/pull/197)
* [quill-cassandra: stream source - avoid blocking queries](https://github.com/getquill/quill/pull/185)

# 0.4.0 / 19-Feb-2016

* [new sources creation mechanism](https://github.com/getquill/quill/pull/136)
* [simplified join syntax](https://github.com/getquill/quill/commit/bfcfe49fbdbda04cce7fe7e7d382fb1adbfcbd7f)
* [Comparison between Quill and other alternatives for CQL](https://github.com/getquill/quill/pull/164)
* [`contains` operator (sql `in`)](https://github.com/getquill/quill/pull/165/files#diff-04c6e90faac2675aa89e2176d2eec7d8R377)
* [unary sql queries](https://github.com/getquill/quill/pull/179/files#diff-02749abf4d0d51be99715cff7074bc9eR207)
* [query probing is now opt-in](https://github.com/getquill/quill/pull/176/files#diff-04c6e90faac2675aa89e2176d2eec7d8R453)
* [quill-cassandra: upgrade Datastax Java Driver to version 3.0.0](https://github.com/getquill/quill/pull/171)
* [support implicit quotations with type parameters](https://github.com/getquill/quill/pull/163)
* [quill-cassandra: UUID support](https://github.com/getquill/quill/pull/142)
* [quill-async: more reasonable numeric type decodes](https://github.com/getquill/quill/pull/139)

# 0.3.1 / 01-Feb-2016

* [fix #134 - ignore the property `queryProbing` when creating the hikari data source](https://github.com/getquill/quill/issues/134)

# 0.3.0 / 26-Jan-2016

* [quill-cassandra: first version of the module featuring async and sync sources](https://github.com/getquill/quill/#cassandra-sources)
* [quill-cassandra: reactive streams support via Monix](https://github.com/getquill/quill/#cassandra-sources)
* [quill-core: updates using table columns](https://github.com/getquill/quill/commit/0681b21aad8d75cb7793840c4f905b80645872cc#diff-04c6e90faac2675aa89e2176d2eec7d8R458)
* [quill-core: explicit inner joins](https://github.com/getquill/quill/commit/902eb858e0e844f41978f8179156da9c69f2d847#diff-2e097508346e0e431a36abcb2c1cc4cbR270)
* [quill-core: configuration option to disable the compile-time query probing](https://github.com/getquill/quill/commit/130919d62a1f852c2d26203c035361ccb3284e53#diff-04c6e90faac2675aa89e2176d2eec7d8L840)
* [quill-core: `if/`else` support (sql `case`/`when`)](https://github.com/getquill/quill/commit/16674ba77fdc880a64af719d150560351ac6a8f6#diff-2e097508346e0e431a36abcb2c1cc4cbR598)
* [quill-async: uuid encoding](https://github.com/getquill/quill/commit/743227aaa3ec76cefcffb405ac658069d90118fc#diff-7bfbe03bba9c515d3f16f88115eb2f9fR24)
* [quill-core: custom ordering](https://github.com/getquill/quill/commit/2fe7556279c5919aa9c1e22bf9c8caf4c67e53e7#diff-04c6e90faac2675aa89e2176d2eec7d8R257)
* [quill-core: expressions in sortBy](https://github.com/getquill/quill/commit/0dbb492de7334cb8ad34dc5c6246ec6908d328bc#diff-2e097508346e0e431a36abcb2c1cc4cbR107)

# 0.2.1 / 28-Dec-2015

* [expire and close compile-time sources automatically](https://github.com/getquill/quill/issues/10)
* [Aggregation sum should return an Option](https://github.com/getquill/quill/pull/69)
* [Changed min/max implicit from Numeric to Ordering](https://github.com/getquill/quill/pull/70)
* [provide implicit to query case class companion objects directly](https://github.com/getquill/quill/pull/73)
* [don't fuse multiple `sortBy`s](https://github.com/getquill/quill/issues/71)
* [actions now respect the naming strategy](https://github.com/getquill/quill/issues/74)

# 0.2.0 / 24-Dec-2015

* [Insert/update using case class instances](https://github.com/getquill/quill/commit/aed630bdb514b3d71a3a3cc47299ff28c0472023)
* [Better IntelliJ IDEA support](https://github.com/getquill/quill/issues/23)
* [Implicit quotations](https://github.com/getquill/quill/commit/1991d694a2bdad645d6d169acefba51f90acde62#diff-6858983f3617753cfb9852426edaa121R491)
* [`like` operator](https://github.com/getquill/quill/commit/f05763ff6cfbe850d7cab2e15d570603cad194c4#diff-04c6e90faac2675aa89e2176d2eec7d8R420)
* [string interpolation support](https://github.com/getquill/quill/commit/c510ee8a6daa98caf45743fd7fc75230cbb3d71e#diff-6858983f3617753cfb9852426edaa121R290)
* [Finagle pool configuration](https://github.com/getquill/quill/pull/60)
* [Allow empty password in Finagle Mysql client](https://github.com/getquill/quill/pull/59)
* Bug fixes:
	* https://github.com/getquill/quill/issues/53
	* https://github.com/getquill/quill/issues/62
	* https://github.com/getquill/quill/issues/63

# 0.1.0 / 27-Nov-2015

* Initial release
