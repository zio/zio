# Contexts

> Contexts represent the database and provide an execution interface for queries.

Contexts represent the database and provide an execution interface for queries.

## Mirror context

Quill provides a mirror context for testing purposes. Instead of running the query, the mirror context returns a structure with the information that would be used to run the query. There are three mirror context instances:

- `io.getquill.MirrorContext`: Mirrors the quotation AST
- `io.getquill.SqlMirrorContext`: Mirrors the SQL query
- `io.getquill.CassandraMirrorContext`: Mirrors the CQL query

## Dependent contexts

The context instance provides all methods and types to interact with quotations and the database.
Contexts can be imported and passed around normally in constructors and function arguments.

```
class MyContext extends SqlMirrorContext(MirrorSqlDialect, Literal)
case class MySchema(c: MyContext) {

  val people = quote {
    querySchema[Person]("people")
  }
}
case class MyDao(c: MyContext, schema: MySchema) {
  def allPeople = c.run(schema.people)
}
```

### Context Traits

One way to compose applications with this kind of context is to use traits with an abstract context variable:

```scala
class MyContext extends SqlMirrorContext(MirrorSqlDialect, Literal)

trait MySchema {

  val c: MyContext

  val people = quote {
    querySchema[Person]("people")
  }
}

case class MyDao(c: MyContext) extends MySchema {

  def allPeople =
    c.run(people)
}
```

### Modular Contexts

Another simple way to modularize Quill code is by extending `Context` as a self-type and applying mixins. Using this strategy,
it is possible to create functionality that is fully portable across databases and even different types of databases
(e.g. creating common queries for both Postgres and Spark).

For example, create the following abstract context:

```scala
trait ModularContext[I <: Idiom, N <: NamingStrategy] { this: Context[I, N] =>
  def peopleOlderThan = quote {
    (age:Int, q:Query[Person]) => q.filter(p => p.age > age)
  }
}
```

Let's see how this can be used across different kinds of databases and Quill contexts.

#### Use `ModularContext` in a mirror context:

```scala
// Note: In some cases need to explicitly specify [MirrorSqlDialect, Literal].
val ctx =
  new SqlMirrorContext[MirrorSqlDialect, Literal](MirrorSqlDialect, Literal)
    with ModularContext[MirrorSqlDialect, Literal]

println( run(peopleOlderThan(22, query[Person])).string )
```

#### Use `ModularContext` to query a Postgres Database

```scala
val ctx =
  new PostgresJdbcContext[Literal](Literal, ds)
    with ModularContext[PostgresDialect, Literal]

val results = run(peopleOlderThan(22, query[Person]))
```

#### Use `ModularContext` to query a Spark Dataset

```scala
object CustomQuillSparkContext extends QuillSparkContext
  with ModularContext[SparkDialect, Literal]

val results = run(peopleOlderThan(22, liftQuery(dataset)))
```

## Spark Integration

Quill provides a fully type-safe way to use Spark's highly-optimized SQL engine. It's an alternative to `Dataset`'s weakly-typed API.

### Importing Quill Spark

```
libraryDependencies ++= Seq(
  "io.getquill" %% "quill-spark" % "4.8.6"
)
```

### Usage

Unlike the other modules, the Spark context is a companion object. Also, it does not depend on a spark session. To use it, add the following import:

```scala

// Create your Spark Context
val session =
  SparkSession.builder()
    .master("local")
    .appName("spark test")
    .getOrCreate()

// The Spark SQL Context must be provided by the user through an implicit value:
implicit val sqlContext = session.sqlContext

// Import the Quill Spark Context

```

> Note Unlike the other modules, the Spark context is a companion object. Also, it does not depend on a spark session.

> Also Note: Quill decoders and meta instances are not used by the quill-spark module, Spark's `Encoder`s are used instead.

### Using Quill-Spark

The `run` method returns a `Dataset` transformed by the Quill query using the SQL engine.

```scala
// Typically you start with some type dataset.
val peopleDS: Dataset[Person] = spark.read.parquet("path/to/people").as[Person]
val addressesDS: Dataset[Address] = spark.read.parquet("path/to/addresses").as[Address]

// The liftQuery method converts Datasets to Quill queries:
val people = quote { liftQuery(peopleDS) }
val addresses = quote { liftQuery(addressesDS) }

val peopleAndAddresses = quote {
  (people join addresses).on((p, a) => p.id == a.ownerFk)
}

val peopleAndAddressesDS: Dataset[(Person, Address)] = run(peopleAndAddresses)
```

#### Simplify it
Since the `run` method allows for Quill queries to be specified directly, and `liftQuery` can be used inside
of any Quoted block, you can shorten various steps of the above workflow:

```scala
val peopleDS: Dataset[Person] = spark.read.parquet("path/to/people")
val addressesDS: Dataset[Address] = spark.read.parquet("path/to/addresses")

val peopleAndAddressesDS: Dataset[(Person, Address)] = run {
  liftQuery(peopleDS)
    .join(liftQuery(addressesDS))
    .on((p, a) => p.id == a.ownerFk)
}
```

Here is an example of a Dataset being converted into Quill, filtered, and then written back out.

```scala

def filter(myDataset: Dataset[Person], name: String): Dataset[Int] =
  run {
    liftQuery(myDataset).filter(_.name == lift(name)).map(_.age)
  }
// SELECT x1.age _1 FROM (?) x1 WHERE x1.name = ?
```

#### Workflow

Due to the design of Quill-Spark, it can be used interchangeably throughout your Spark workflow:
- Lift a Dataset to Query to do some filtering and sub-selecting
  (with [Predicate and Filter Pushdown!](https://jaceklaskowski.gitbooks.io/mastering-spark-sql/spark-sql-Optimizer-PushDownPredicate.html)).
- Then covert it back to a Dataset to do Spark-Specific operations.
- Then convert it back to a Query to use Quills great Join DSL...
- Then convert it back to a Dataset to write it to a file or do something else with it...

### Custom Functions

TODO UDFs and UDAFs

### Restrictions

#### Top Level Classes
Spark only supports using top-level classes as record types. That means that
when using `quill-spark` you can only use a top-level case class for `T` in `Query[T]`.

TODO Get the specific error

#### Lifted Variable Interpolation

The queries printed from `run(myQuery)` during compile time escape question marks via a backslash them in order to
be able to substitute liftings properly. They are then returned back to their original form before running.
```scala

def filter(myDataset: Dataset[Person]): Dataset[Int] =
  run {
    liftQuery(myDataset).filter(_.name == "?").map(_.age)
  }
// This is generated during compile time:
// SELECT x1.age _1 FROM (?) x1 WHERE x1.name = '\?'
// It is reverted upon run-time:
// SELECT x1.age _1 FROM (ds1) x1 WHERE x1.name = '?'
```

## SQL Contexts

Example:

```scala
lazy val ctx = new MysqlJdbcContext(SnakeCase, "ctx")
```

### Dialect

The SQL dialect parameter defines the specific database dialect to be used. Some context types are specific to a database and thus not require it.

Quill has five built-in dialects:

- `io.getquill.H2Dialect`
- `io.getquill.MySQLDialect`
- `io.getquill.PostgresDialect`
- `io.getquill.SqliteDialect`
- `io.getquill.SQLServerDialect`
- `io.getquill.OracleDialect`

### Naming strategy

The naming strategy parameter defines the behavior when translating identifiers (table and column names) to SQL.

|           strategy                  |          example              |
|-------------------------------------|-------------------------------|
| `io.getquill.naming.Literal`        | some_ident  -> some_ident     |
| `io.getquill.naming.Escape`         | some_ident  -> "some_ident"   |
| `io.getquill.naming.UpperCase`      | some_ident  -> SOME_IDENT     |
| `io.getquill.naming.LowerCase`      | SOME_IDENT  -> some_ident     |
| `io.getquill.naming.SnakeCase`      | someIdent   -> some_ident     |
| `io.getquill.naming.CamelCase`      | some_ident  -> someIdent      |
| `io.getquill.naming.MysqlEscape`    | some_ident  -> \`some_ident\` |
| `io.getquill.naming.PostgresEscape` | $some_ident -> $some_ident    |

Multiple transformations can be defined using `NamingStrategy()`. For instance, the naming strategy

```NamingStrategy(SnakeCase, UpperCase)```

produces the following transformation:

```someIdent -> SOME_IDENT```

The transformations are applied from left to right.

### Configuration

The string passed to the context is used as the key in order to obtain configurations using the [typesafe config](https://github.com/typesafehub/config) library.

Additionally, the contexts provide multiple constructors. For instance, with `JdbcContext` it's possible to specify a `DataSource` directly, without using the configuration:

```scala
def createDataSource: javax.sql.DataSource with java.io.Closeable = ???

lazy val ctx = new MysqlJdbcContext(SnakeCase, createDataSource)
```

## quill-jdbc

The `quill-jdbc` module provides a simple blocking JDBC context for standard use-cases. For transactions, the JDBC connection is kept in a thread-local variable.

Quill uses [HikariCP](https://github.com/brettwooldridge/HikariCP) for connection pooling. Please refer to HikariCP's [documentation](https://github.com/brettwooldridge/HikariCP#configuration-knobs-baby) for a detailed explanation of the available configurations.

Note that there are `dataSource` configurations, that go under `dataSource`, like `user` and `password`, but some pool settings may go under the root config, like `connectionTimeout`.

#### transactions

The `JdbcContext` provides thread-local transaction support:

```
ctx.transaction {
  ctx.run(query[Person].delete)
  // other transactional code
}
```

The body of `transaction` can contain calls to other methods and multiple `run` calls since the transaction is propagated through a thread-local.

### MySQL (quill-jdbc)

#### sbt dependencies

```
libraryDependencies ++= Seq(
  "mysql" % "mysql-connector-java" % "8.0.17",
  "io.getquill" %% "quill-jdbc" % "4.8.6"
)
```

#### context definition
```scala
lazy val ctx = new MysqlJdbcContext(SnakeCase, "ctx")
```

#### application.properties
```
ctx.dataSourceClassName=com.mysql.cj.jdbc.MysqlDataSource
ctx.dataSource.url=jdbc:mysql://host/database
ctx.dataSource.user=root
ctx.dataSource.password=root
ctx.dataSource.cachePrepStmts=true
ctx.dataSource.prepStmtCacheSize=250
ctx.dataSource.prepStmtCacheSqlLimit=2048
ctx.connectionTimeout=30000
```

### Postgres (quill-jdbc)

#### sbt dependencies

```
libraryDependencies ++= Seq(
  "org.postgresql" % "postgresql" % "42.2.8",
  "io.getquill" %% "quill-jdbc" % "4.8.6"
)
```

#### context definition
```scala
lazy val ctx = new PostgresJdbcContext(SnakeCase, "ctx")
```

#### application.properties
```
ctx.dataSourceClassName=org.postgresql.ds.PGSimpleDataSource
ctx.dataSource.user=root
ctx.dataSource.password=root
ctx.dataSource.databaseName=database
ctx.dataSource.portNumber=5432
ctx.dataSource.serverName=host
ctx.connectionTimeout=30000
```

### Sqlite (quill-jdbc)

#### sbt dependencies

```
libraryDependencies ++= Seq(
  "org.xerial" % "sqlite-jdbc" % "3.28.0",
  "io.getquill" %% "quill-jdbc" % "4.8.6"
)
```

#### context definition
```scala
lazy val ctx = new SqliteJdbcContext(SnakeCase, "ctx")
```

#### application.properties
```
ctx.driverClassName=org.sqlite.JDBC
ctx.jdbcUrl=jdbc:sqlite:/path/to/db/file.db
```

### H2 (quill-jdbc)

#### sbt dependencies

```
libraryDependencies ++= Seq(
  "com.h2database" % "h2" % "1.4.199",
  "io.getquill" %% "quill-jdbc" % "4.8.6"
)
```

#### context definition
```scala
lazy val ctx = new H2JdbcContext(SnakeCase, "ctx")
```

#### application.properties
```
ctx.dataSourceClassName=org.h2.jdbcx.JdbcDataSource
ctx.dataSource.url=jdbc:h2:mem:yourdbname
ctx.dataSource.user=sa
```

### SQL Server (quill-jdbc)

#### sbt dependencies

```
libraryDependencies ++= Seq(
  "com.microsoft.sqlserver" % "mssql-jdbc" % "7.4.1.jre8",
  "io.getquill" %% "quill-jdbc" % "4.8.6"
)
```

#### context definition

```scala
lazy val ctx = new SqlServerJdbcContext(SnakeCase, "ctx")
```

### Oracle (quill-jdbc)

Quill supports Oracle version 12c and up although due to licensing restrictions, version 18c XE is used for testing.

Note that the latest Oracle JDBC drivers are not publicly available. In order to get them,
you will need to connect to Oracle's private maven repository as instructed [here](https://docs.oracle.com/middleware/1213/core/MAVEN/config_maven_repo.htm#MAVEN9012).
Unfortunately, this procedure currently does not work for SBT. There are various workarounds
available for this situation [here](https://stackoverflow.com/questions/1074869/find-oracle-jdbc-driver-in-maven-repository?rq=1).

#### sbt dependencies

```
libraryDependencies ++= Seq(
  "com.oracle.jdbc" % "ojdbc8" % "18.3.0.0.0",
  "io.getquill" %% "quill-jdbc" % "4.8.6"
)
```

#### context definition

```scala
lazy val ctx = new OracleJdbcContext(SnakeCase, "ctx")
```

#### application.properties
```
ctx.dataSourceClassName=com.microsoft.sqlserver.jdbc.SQLServerDataSource
ctx.dataSource.user=user
ctx.dataSource.password=YourStrongPassword
ctx.dataSource.databaseName=database
ctx.dataSource.portNumber=1433
ctx.dataSource.serverName=host
```

## ZIO (quill-jdbc-zio)

Quill context that executes JDBC queries inside of ZIO. Unlike most other contexts
that require passing in a `java.sql.DataSource` when the context is created, this context's
run methods return a ZIO that has a DataSource resource dependency.
Naturally, this should be provided later on in your application
(see `ZioJdbc` for helper methods that assist in doing this).

Since resource dependency is `DataSource` the result of a `run` call is `ZIO[DataSource, SQLException, T]`.
This means that if you have a `DataSource` object, you can just provide it!

```scala
def ds: DataSource = _
run(people).provide(Has(ds))
```

> Since most quill-zio methods return `ZIO[DataSource, SQLException, T]`
> the type `QIO[T]` i.e. Quill-IO has been defined as an alias.
>
> For underlying-contexts (see below) that depend on `Connection`,
> the alias `QCIO[T]` (i.e. Quill-Connection-IO) has been defined
> for `ZIO[Connection, SQLException, T]`.

Since in most JDBC use-cases, a connection-pool datasource (e.g. Hikari) is used,
constructor-methods `fromPrefix`, `fromConfig`, `fromJdbcConfig` are available on
`DataSourceLayer` to construct instances of a `ZLayer[Any, SQLException, DataSource]`
which can be easily used to provide a DataSource dependency.
You can use them like this:
```scala

val zioDs = DataSourceLayer.fromPrefix("testPostgresDB")
MyZioContext.run(query[Person]).provideCustomLayer(zioDS)
```

If in some rare cases, you wish to provide a `java.sql.Connection` to a `run` method directly, you can delegate
to the underlying-context. This is a more low-level context whose `run` methods have a `Connection` resource.
Here is an example of how this can be done.

```scala
def conn: Connection = _ // If you are starting with a connection object

// Import encoders/decoders of the underlying context. Do not import quote/run/prepare methods to avoid conflicts.

MyZioContext.underlying.run(people).provide(Has(conn))
```

If you are working with an underlying-context and want to provide a DataSource instead of a connection,
you can use the `onDataSource` method. Note however that this is *only* needed when working with an underlying-context.
When working with a normal context, `onDataSource` is not available or necessary
(since for a  normal contexts `R` will be `DataSource`).

```scala
val ds: DataSource = _

// Import encoders/decoders of the underlying context. Do not import quote/run/prepare methods to avoid conflicts.

MyZioContext.underlying.run(people).onDataSource.provide(Has(ds))
```

> Also note that if you are using a Plain Scala app however, you will need to manually run it i.e. using zio.Runtime
> ```scala
> Runtime.default.unsafeRun(MyZioContext.run(query[Person]).provideLayer(zioDS))
> ```

#### DAO helper

One additional useful pattern is to use `import io.getquill.context.qzio.ImplicitSyntax.Implicit` to provide
an implicit DataSource to one or multiple `run(qry)` calls in a context. This is very useful when creating
DAO patterns that will reuse a DataSource many times:

```scala
case class MyQueryService(ds: DataSource with Closeable) { // I.e. our DAO

 implicit val env = Implicit(Has(ds)) // This will be looked up in each `.implicitDS` call

 val joes = Ctx.run(query[Person].filter(p => p.name == "Joe")).implicitDS
 val jills = Ctx.run(query[Person].filter(p => p.name == "Jill")).implicitDS
 val alexes = Ctx.run(query[Person].filter(p => p.name == "Alex")).implicitDS
}
```

More examples of a Quill-JDBC-ZIO app [quill-jdbc-zio/src/test/scala/io/getquill/examples](https://github.com/getquill/quill/tree/master/quill-jdbc-zio/src/test/scala/io/getquill/examples).

#### streaming

The `ZioJdbcContext` can stream using zio.ZStream:

```
ctx.stream(query[Person])             // returns: ZStream[Connection, Throwable, Person]
  .run(Sink.collectAll).map(_.toList) // returns: ZIO[Connection, Throwable, List[T]]
```

#### transactions

The `ZioJdbcContext`s provide support for transactions without needing thread-local storage or similar
because they propagate the resource dependency in the ZIO effect itself (i.e. the `Connection` in `Zio[Connection, _, _]`).
As with the other contexts, if an exception is thrown anywhere inside a task or sub-task within a `transaction` block, the entire block
will be rolled back by the database.

Basic syntax:
```
val trans =
  ctx.transaction {
    for {
      _ <- ctx.run(query[Person].delete)
      _ <- ctx.run(query[Person].insertValue(Person("Joe", 123)))
      p <- ctx.run(query[Person])
    } yield p
  } //returns: ZIO[Connection, Throwable, List[Person]]

val result = Runtime.default.unsafeRun(trans.onDataSource.provide(ds)) //returns: List[Person]
```

#### json

The Zio Quill Postgres supports JSON encoding/decoding via the zio-json library. Just wrap your object in a `JsonValue` instance
and then define encoders/decoders via zio-json's `JsonEncoder`/`JsonDecoder` modules.

```scala

case class Person(name: String, age: Int)
case class MyTable(name: String, value: JsonValue[Person])

val joe = Person("Joe", 123)
val joeRow = MyTable("SomeJoe", JsonValue(joe))

// Declare an encoder/decoder for `Person` via zio-json
implicit val personEncoder: JsonEncoder[Person] = DeriveJsonEncoder.gen[Person]
implicit val personDecoder: JsonDecoder[Person] = DeriveJsonDecoder.gen[Person]

val myApp: ZIO[Any, SQLException, List[MyTable]] =
  for {
    // You can then insert the value:
    _ <- context.run(query[MyTable].insertValue(lift(joeRow)))
    // As well read it:
    value <- context.run(query[MyTable])
  } yield (value)
```

You can also encode/decode objects that have the type `zio.json.ast.Json` directly.
```scala

case class MyTable(name: String, value: JsonValue[Json])

// i.e. {name:"Joe", age:123}
val jsonJoe = Json.Obj(Chunk("name" -> Json.Str("Joe"), "age" -> Json.Num(123)))
val joeRow = MyTable("SomeJoe", JsonValue(jsonJoe))

testContext.run(jsonAstQuery.insertValue(lift(joeRow)))
```

### MySQL (quill-jdbc-zio)

#### sbt dependencies

```
libraryDependencies ++= Seq(
  "mysql" % "mysql-connector-java" % "8.0.17",
  "io.getquill" %% "quill-jdbc-zio" % "4.8.6"
)
```

#### context definition

```scala
val ctx = new MysqlZioJdbcContext(SnakeCase)
// Also can be static:
object MyContext extends MysqlZioJdbcContext(SnakeCase)
```

#### application.properties
```
ctx.dataSourceClassName=com.mysql.cj.jdbc.MysqlDataSource
ctx.dataSource.url=jdbc:mysql://host/database
ctx.dataSource.user=root
ctx.dataSource.password=root
ctx.dataSource.cachePrepStmts=true
ctx.dataSource.prepStmtCacheSize=250
ctx.dataSource.prepStmtCacheSqlLimit=2048
ctx.connectionTimeout=30000
```

### Postgres (quill-jdbc-zio)

#### sbt dependencies

```
libraryDependencies ++= Seq(
  "org.postgresql" % "postgresql" % "42.2.8",
  "io.getquill" %% "quill-jdbc-zio" % "4.8.6"
)
```

#### context definition

```scala
val ctx = new PostgresZioJdbcContext(SnakeCase)
// Also can be static:
object MyContext extends PostgresZioJdbcContext(SnakeCase)
```

#### application.properties
```
ctx.dataSourceClassName=org.postgresql.ds.PGSimpleDataSource
ctx.dataSource.user=root
ctx.dataSource.password=root
ctx.dataSource.databaseName=database
ctx.dataSource.portNumber=5432
ctx.dataSource.serverName=host
ctx.connectionTimeout=30000
```

### Sqlite (quill-jdbc-zio)

#### sbt dependencies

```
libraryDependencies ++= Seq(
  "org.xerial" % "sqlite-jdbc" % "3.28.0",
  "io.getquill" %% "quill-jdbc-zio" % "4.8.6"
)
```

#### context definition
```scala
val ctx = new SqlitezioJdbcContext(SnakeCase)
// Also can be static:
object MyContext extends SqlitezioJdbcContext(SnakeCase)
```

#### application.properties
```
ctx.driverClassName=org.sqlite.JDBC
ctx.jdbcUrl=jdbc:sqlite:/path/to/db/file.db
```

### H2 (quill-jdbc-zio)

#### sbt dependencies

```
libraryDependencies ++= Seq(
  "com.h2database" % "h2" % "1.4.199",
  "io.getquill" %% "quill-jdbc-zio" % "4.8.6"
)
```

#### context definition

```scala
val ctx = new H2ZioJdbcContext(SnakeCase)
// Also can be static:
object MyContext extends H2ZioJdbcContext(SnakeCase)
```

#### application.properties
```
ctx.dataSourceClassName=org.h2.jdbcx.JdbcDataSource
ctx.dataSource.url=jdbc:h2:mem:yourdbname
ctx.dataSource.user=sa
```

### SQL Server (quill-jdbc-zio)

#### sbt dependencies

```
libraryDependencies ++= Seq(
  "com.microsoft.sqlserver" % "mssql-jdbc" % "7.4.1.jre8",
  "io.getquill" %% "quill-jdbc-zio" % "4.8.6"
)
```

#### context definition
```scala
val ctx = new SqlServerZioJdbcContext(SnakeCase)
// Also can be static:
object MyContext extends SqlServerZioJdbcContext(SnakeCase)
```

#### application.properties
```
ctx.dataSourceClassName=com.microsoft.sqlserver.jdbc.SQLServerDataSource
ctx.dataSource.user=user
ctx.dataSource.password=YourStrongPassword
ctx.dataSource.databaseName=database
ctx.dataSource.portNumber=1433
ctx.dataSource.serverName=host
```

### Oracle (quill-jdbc-zio)

Quill supports Oracle version 12c and up although due to licensing restrictions, version 18c XE is used for testing.

#### sbt dependencies

```
libraryDependencies ++= Seq(
  "com.oracle.jdbc" % "ojdbc8" % "18.3.0.0.0",
  "io.getquill" %% "quill-jdbc-zio" % "4.8.6"
)
```

#### context definition
```scala
val ctx = new OracleZioJdbcContext(SnakeCase)
// Also can be static:
object MyContext extends OracleZioJdbcContext(SnakeCase)
```

#### application.properties
```
ctx.dataSourceClassName=oracle.jdbc.xa.client.OracleXADataSource
ctx.dataSource.databaseName=xe
ctx.dataSource.user=database
ctx.dataSource.password=YourStrongPassword
ctx.dataSource.driverType=thin
ctx.dataSource.portNumber=1521
ctx.dataSource.serverName=host
```

## quill-doobie

Quill 3.16.5 and above supports Doobie starting 1.0.0-RC1. You can use quill quotes to construct `ConnectionIO` programs.
Quill provides statement construction and type mapping, and doobie takes care of statement execution.

> Note that if you are migrating from the original `doobie-quill` integration (e.g. [here](https://search.maven.org/search?q=a:doobie-quill_2.12))
just add the below dependency and replace the `doobie.quill` package with `io.getquill.doobie`.
(If you are using the package provided by kubukoz (i.e. [here](https://github.com/polyvariant/doobie-quill)), then replace `org.polyvariant` with `io.getquill.doobie`.)

In order to use this feature, add the following dependency.
```
libraryDependencies += "io.getquill" %% "quill-doobie" % "4.6.1-SNAPSHOT"
```

The examples below require the following imports.

```

```

We can now construct a `DoobieContext` for our back-end database and import its members, as we would with a traditional Quill context. The options are `H2`, `MySQL`, `Oracle`, `Postgres`, `SQLite`, and `SQLServer`.

```
val dc = new DoobieContext.Postgres(Literal) // Literal naming scheme

```

> Instead of using Quill's `sql"MyUDF(${something})"` interpolator, use `qsql"MyUDF(${something})"` since we have excluded it.

We will be using the `country` table from our test database, so we need a data type of that name, with fields whose names and types line up with the table definition.

```scala
case class Country(code: String, name: String, population: Int)
```

We're now ready to construct doobie programs using Quill quotes. Note the return types from `run`, which are normal doobie types. You can freely mix Quill quotes into existing doobie programs.

#### running and streaming

```scala
val q1 = quote { query[Country].filter(_.code == "GBR") }

// Select all at once
run(q1)

// Stream in chunks of 16
stream(q1, 16)
```

#### actions

A simple update.

```scala
val u1 = quote { query[Country].filter(_.name like "U%").update(_.name -> "foo") }

// Update yielding count of affected rows
run(u1)
```

A batch update.

```scala
val u2 = quote {
  liftQuery(List("U%", "A%")).foreach { pat =>
    query[Country].filter(_.name like pat).update(_.name -> "foo")
  }
}

// Update yielding list of counts of affected rows
run(u2)
```

Now we will look at batch updates with generated keys. For this we will create a new table.

```sql
CREATE TABLE Foo (
  id    SERIAL,
  value VARCHAR(42)
)
```

And a related data type.

```scala
case class Foo(id: Int, value: String)
```

We can now write an update returning generated keys.

```scala
val u3 = quote {
  query[Foo].insert(lift(Foo(0, "Joe"))).returning(_.id)
}

// Update yielding a single id
run(u3)
```

And a batch update returning generated keys.

```scala
val u4 = quote {
  liftQuery(List(Foo(0, "Joe"), Foo(0, "Bob"))).foreach { a =>
    query[Foo].insert(a).returning(_.id)
  }
}

// Update yielding a list of ids
run(u4)
```

## Finagle Contexts

Support for the Twitter Finagle library is available with MySQL and Postgres databases.

### quill-finagle-mysql

#### transactions

The finagle context provides transaction support through a `Local` value. See twitter util's [scaladoc](https://github.com/twitter/util/blob/ee8d3140ba0ecc16b54591bd9d8961c11b999c0d/util-core/src/main/scala/com/twitter/util/Local.scala#L96) for more details.

```
ctx.transaction {
  ctx.run(query[Person].delete)
  // other transactional code
}
```

#### streaming

The finagle context allows streaming a query response, returning an `AsyncStream` value.

```
ctx.stream(query[Person]) // returns: Future[AsyncStream[Person]]
  .flatMap(_.toSeq())
```

The body of `transaction` can contain calls to other methods and multiple `run` calls since the transaction is automatically propagated through the `Local` value.

#### sbt dependencies

```
libraryDependencies ++= Seq(
  "io.getquill" %% "quill-finagle-mysql" % "4.8.6"
)
```

#### context definition
```scala
lazy val ctx = new FinagleMysqlContext(SnakeCase, "ctx")
```

#### application.properties
```
ctx.dest=localhost:3306
ctx.user=root
ctx.password=root
ctx.database=database
ctx.pool.watermark.low=0
ctx.pool.watermark.high=10
ctx.pool.idleTime=5 # seconds
ctx.pool.bufferSize=0
ctx.pool.maxWaiters=2147483647
```

### quill-finagle-postgres

#### transactions

The finagle context provides transaction support through a `Local` value. See twitter util's [scaladoc](https://github.com/twitter/util/blob/ee8d3140ba0ecc16b54591bd9d8961c11b999c0d/util-core/src/main/scala/com/twitter/util/Local.scala#L96) for more details.

```
ctx.transaction {
  ctx.run(query[Person].delete)
  // other transactional code
}
```

The body of `transaction` can contain calls to other methods and multiple `run` calls since the transaction is automatically propagated through the `Local` value.

#### sbt dependencies

```
libraryDependencies ++= Seq(
  "io.getquill" %% "quill-finagle-postgres" % "4.8.6"
)
```

#### context definition
```scala
lazy val ctx = new FinaglePostgresContext(SnakeCase, "ctx")
```

#### application.properties
```
ctx.host=localhost:3306
ctx.user=root
ctx.password=root
ctx.database=database
ctx.useSsl=false
ctx.hostConnectionLimit=1
ctx.numRetries=4
ctx.binaryResults=false
ctx.binaryParams=false
```

## quill-cassandra

#### sbt dependencies

```
libraryDependencies ++= Seq(
  "io.getquill" %% "quill-cassandra" % "4.8.6"
)
```

#### synchronous context
```scala
lazy val ctx = new CassandraSyncContext(SnakeCase, "ctx")
```

#### asynchronous context
```scala
lazy val ctx = new CassandraAsyncContext(SnakeCase, "ctx")
```

The configurations are set using runtime reflection on the [`Cluster.builder`](https://docs.datastax.com/en/drivers/java/2.1/com/datastax/driver/core/Cluster.Builder.html) instance. It is possible to set nested structures like `queryOptions.consistencyLevel`, use enum values like `LOCAL_QUORUM`, and set multiple parameters like in `credentials`.

#### application.properties
```
ctx.keyspace=quill_test
ctx.preparedStatementCacheSize=1000
ctx.session.contactPoint=127.0.0.1
ctx.session.withPort=9042
ctx.session.queryOptions.consistencyLevel=LOCAL_QUORUM
ctx.session.withoutMetrics=true
ctx.session.withoutJMXReporting=false
ctx.session.credentials.0=root
ctx.session.credentials.1=pass
ctx.session.maxSchemaAgreementWaitSeconds=1
ctx.session.addressTranslator=com.datastax.driver.core.policies.IdentityTranslator
```

## quill-cassandra-zio

Quill context that executes Cassandra queries inside of ZIO. Unlike most other contexts
that require passing in a Data Source, this context takes in a `CassandraZioSession`
as a resource dependency which can be provided later (see the `CassandraZioSession` object for helper methods
that assist in doing this).

The resource dependency itself is just a `CassandraZioSession` hence `run(qry)` and other methods in this context will return
`ZIO[CassandraZioSession, Throwable, T]`.  The type `CIO[T]` i.e. Cassandra-IO is an alias for this.
Providing a `CassandraZioSession` dependency is now very simple:

```scala
val session: CassandraZioSession = _
run(people)
  .provide(Has(session))
```

Various methods in the `io.getquill.CassandraZioSession` can assist in simplifying it's creation, for example, you can
provide a `Config` object instead of a `CassandraZioSession` like this:

```scala
 val zioSessionLayer: ZLayer[Any, Throwable, CassandraZioSession] =
   CassandraZioSession.fromPrefix("testStreamDB")
run(query[Person])
  .provideCustomLayer(zioSessionLayer)
```
> (Note that the resulting CassandraZioSession has a closing bracket)

If you are using a Plain Scala app, you will need to manually run it e.g. using zio.Runtime
```scala
 Runtime.default.unsafeRun(MyZioContext.run(query[Person]).provideCustomLayer(zioSessionLayer))
```

#### DAO helper

One additional useful pattern is to use `import io.getquill.context.qzio.ImplicitSyntax.Implicit` to provide
an implicit CassandraZioSession to one or multiple `run(qry)` calls in a context. This is very useful when creating
DAO patterns that will reuse a CassandraZioSession many times:

```scala
case class MyQueryService(cs: CassandraZioSession) {

  implicit val env = Implicit(Has(cs))

  def joes = Ctx.run { query[Person].filter(p => p.name == "Joe") }.implicitly
  def jills = Ctx.run { query[Person].filter(p => p.name == "Jill") }.implicitly
  def alexes = Ctx.run { query[Person].filter(p => p.name == "Alex") }.implicitly
}
```

More examples of a Quill-Cassandra-ZIO app [quill-cassandra-zio/src/test/scala/io/getquill/context/cassandra/zio/examples](https://github.com/getquill/quill/tree/master/quill-cassandra-zio/src/test/scala/io/getquill/context/cassandra/zio/examples).

#### sbt dependencies

```
libraryDependencies ++= Seq(
  "io.getquill" %% "quill-cassandra-zio" % "4.8.6"
)
```

## quill-cassandra-pekko

#### sbt dependencies

```
libraryDependencies ++= Seq(
  "io.getquill" %% "quill-cassandra-pekko" % "4.8.6"
)
```

See [Pekko Cassandra](https://pekko.apache.org/docs/pekko-connectors/current/cassandra.html#apache-cassandra) documentation page for more information.

#### context

```scala

val system: ActorSystem = ???
val pekkoSessionSettings = CassandraSessionSettings("quill-test.pekko.cassandra")
val pekkoSession: CassandraSession = CassandraSessionRegistry.get(system).sessionFor(pekkoSessionSettings)

lazy val ctx = new CassandraAlpakkaContext(SnakeCase, pekkoSession, preparedStatementCacheSize = 100)
```

#### application.properties
```
// pekko cassandra session with keyspace
quill-test.pekko.cassandra: ${pekko.cassandra} { // inheritance of pekko.cassandra session configuration
  // custom datastax driver setup
  datastax-java-driver-config = quill-test-datastax-java-driver
}

quill-test-datastax-java-driver {
  basic {
    // keyspace at datastax driver setup, as there is not different option now
    session-keyspace = "quill_test"
  }
}
```

## OrientDB Contexts

#### sbt dependencies

```
libraryDependencies ++= Seq(
  "io.getquill" %% "quill-orientdb" % "4.8.6"
)
```

#### synchronous context
```scala
lazy val ctx = new OrientDBSyncContext(SnakeCase, "ctx")
```

The configurations are set using [`OPartitionedDatabasePool`](https://orientdb.com/javadoc/latest/com/orientechnologies/orient/core/db/OPartitionedDatabasePool.html) which creates a pool of DB connections from which an instance of connection can be acquired. It is possible to set DB credentials using the parameter called `username` and `password`.

#### application.properties
```
ctx.dbUrl=remote:127.0.0.1:2424/GratefulDeadConcerts
ctx.username=root
ctx.password=root
```
