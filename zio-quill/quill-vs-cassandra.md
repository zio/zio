# Quill vs. Cassandra

> This document compares Quill to the [Datastax Java](https://github.com/datastax/java-driver) driver and the [Phantom](https://web.archive.org/web/20141229171448/http://websudos.github.io:80/phantom) library. This is an incomplete comparison, additions and corrections are welcome.

This document compares Quill to the [Datastax Java](https://github.com/datastax/java-driver) driver and the [Phantom](https://web.archive.org/web/20141229171448/http://websudos.github.io:80/phantom) library. This is an incomplete comparison, additions and corrections are welcome.

All examples have been properly tested, and they should work out of the box.

## Index ##

* [Prerequisites](#prerequisites)
* [Abstraction level](#abstraction-level)
* [Simple queries](#simple-queries)
* [Composable queries](#composable-queries)
* [Extensibility](#extensibility)
* [Custom data types](#custom-data-types)
* [Non-blocking IO](#non-blocking-io)
* [Other considerations](#other-considerations)

## Prerequisites ##

The keyspace and column family needed for all examples are defined in this CQL script:

```
CREATE KEYSPACE IF NOT EXISTS db
WITH replication = { 'class' : 'SimpleStrategy', 'replication_factor' : 1 };

CREATE TABLE IF NOT EXISTS db.weather_station (
  country TEXT,
  city TEXT,
  station_id TEXT,
  entry INT,
  value INT,
  PRIMARY KEY (country, city, station_id)
);
```

In order to make all Quill examples to work you will need to create this config file, named `application.conf` in your resources folder:

```
db.keyspace=db
db.preparedStatementCacheSize=100
db.session.contactPoint=127.0.0.1
db.session.queryOptions.consistencyLevel=ONE
```

Add your project's `build.sbt` dependencies as described in [Cassandra Contexts](https://github.com/getquill/quill#cassandra-contexts)

## Abstraction level ##

The Datastax Java driver provides simple abstractions that let you either write your queries as plain strings or use a declarative Query Builder. It also provides a higher level [Object Mapper](https://github.com/datastax/java-driver/tree/2.1/manual/object_mapper). For this comparison we will only use the Query Builder.

Although both Quill and Phantom represent column family rows as flat immutable structures (case classes without nested data) and provide a type-safe composable query DSL, they work at a different abstraction level. 

Phantom provides an embedded DSL that help you write CQL queries in a type-safe manner. Quill is referred as a Language Integrated Query library to match the available publications on the subject. The paper ["Language-integrated query using comprehension syntax: state of the art, open problems, and work in progress"](http://research.microsoft.com/en-us/events/dcp2014/cheney.pdf) has an overview with some of the available implementations of language integrated queries.

## Simple queries ##

This section compares how the different libraries let the user query a column family to obtain some elements.

**Java Driver (v3.0.0)**
```

object JavaDriver extends App {

  val nrOfCacheEntries: Int = 100

  val cluster: Cluster = Cluster.builder().addContactPoints("localhost").build()

  val session: Session = cluster.newSession()

  val cache: LoadingCache[String, PreparedStatement] =
    CacheBuilder.newBuilder().
      maximumSize(nrOfCacheEntries).
      build(
        new CacheLoader[String, PreparedStatement]() {
          def load(key: String): PreparedStatement = session.prepare(key.toString)
        }
      )

  case class WeatherStation(country: String, city: String, stationId: String, entry: Int, value: Int)

  object WeatherStation {

    def getAllByCountry(cache: LoadingCache[String, PreparedStatement], session: Session)(country: String): List[WeatherStation] = {
      val query: Statement =
        QueryBuilder.select().
          all().
          from("db", "weather_station").
          where(QueryBuilder.eq("country", QueryBuilder.bindMarker()))

      session.execute(cache.get(query.toString).bind(country)).all().asScala.map(
        row => WeatherStation(row.getString("country"), row.getString("city"), row.getString("station_id"), row.getInt("entry"), row.getInt("value"))
      ).to[List]
    }
  }

  val getAllByCountry: String => List[WeatherStation] = WeatherStation.getAllByCountry(cache, session)_

  getAllByCountry("UK")

  session.close()
  cluster.close()
}
```

The Java driver requires explicit handling of a `PreparedStatement`s cache to avoid preparing the same statement more that once, that could affect performance.

**Phantom (v1.22.0)**
```

object Phantom extends App {

  case class WeatherStation(country: String, city: String, stationId: String, entry: Int, value: Int)

  abstract class WeatherStationCF(override val tableName: String) extends CassandraTable[WeatherStationCF, WeatherStation] with RootConnector {

    object country extends StringColumn(this) with PartitionKey[String]
    object city extends StringColumn(this) with PrimaryKey[String]
    object stationId extends StringColumn(this) with PrimaryKey[String] {
      override lazy val name: String = "station_id"
    }
    object entry extends IntColumn(this) with PrimaryKey[Int]
    object value extends IntColumn(this)

    override def fromRow(r: Row): WeatherStation =
      WeatherStation(country(r), city(r), stationId(r), entry(r), value(r))
  }

  abstract class WeatherStationQueries extends WeatherStationCF("weather_station") {

    def getAllByCountry(country: String): Future[List[WeatherStation]] =
      select.where(_.country eqs country).fetch()
  }

  class DB(ks: KeySpaceDef) extends DatabaseImpl(ks) {

    object stations extends WeatherStationQueries with connector.Connector
  }

  val db = new DB(ContactPoint.local.keySpace("db"))

  val result = db.stations.getAllByCountry("UK")

  result.onComplete(_ => db.shutdown())
}
```

Phantom requires mapping classes to lift the database model to DSL types. The query definition also requires special equality operators.

**Quill**
```scala

object Quill extends App {

  val db = new CassandraAsyncContext(SnakeCase, "db")

  case class WeatherStation(country: String, city: String, stationId: String, entry: Int, value: Int)

  object WeatherStation {

    val getAllByCountry = quote {
      (country: String) =>
        query[WeatherStation].filter(_.country == country)
    }
  }

  val result = db.run(WeatherStation.getAllByCountry(lift("UK")))

  result.onComplete(_ => db.close())
}
```

During the compilation of this example, as the quotation is known statically, Quill will emit the CQL string as an info message, e.g. `SELECT country, city, station_id, entry, value FROM weather_station WHERE country = ?`.

## Composable queries ##

This section compares how the different libraries let the user compose queries.

**Java Driver (v3.0.0)** 

The Query Builder allows the user to partially construct queries and add filters later:

```

object JavaDriver extends App {

  val nrOfCacheEntries: Int = 100

  val cluster: Cluster = Cluster.builder().addContactPoints("localhost").build()

  val session: Session = cluster.newSession()

  val cache: LoadingCache[String, PreparedStatement] =
    CacheBuilder.newBuilder().
      maximumSize(nrOfCacheEntries).
      build(
        new CacheLoader[String, PreparedStatement]() {
          def load(key: String): PreparedStatement = session.prepare(key.toString)
        }
      )

  case class WeatherStation(country: String, city: String, stationId: String, entry: Int, value: Int)

  object WeatherStation {

    def getAll: Select =
      QueryBuilder.select().
        all().
        from("db", "weather_station")

    def getAllByCountry(cache: LoadingCache[String, PreparedStatement], session: Session)(country: String): List[WeatherStation] = {
      val query: Statement =
        getAll.
          where(QueryBuilder.eq("country", QueryBuilder.bindMarker()))

      session.execute(cache.get(query.toString).bind(country)).all().asScala.map(
        row => WeatherStation(row.getString("country"), row.getString("city"), row.getString("station_id"), row.getInt("entry"), row.getInt("value"))
      ).to[List]
    }
  }

  val getAllByCountry: String => List[WeatherStation] = WeatherStation.getAllByCountry(cache, session)_

  getAllByCountry("UK")

  session.close()
  cluster.close()
}
```

The DSL has limited composition compatibility.

**Phantom (v1.22.0)**
```

object Phantom extends App {

  case class WeatherStation(country: String, city: String, stationId: String, entry: Int, value: Int)

  abstract class WeatherStationCF(override val tableName: String) extends CassandraTable[WeatherStationCF, WeatherStation] with RootConnector {

    object country extends StringColumn(this) with PartitionKey[String]
    object city extends StringColumn(this) with PrimaryKey[String]
    object stationId extends StringColumn(this) with PrimaryKey[String]
    object entry extends IntColumn(this) with PrimaryKey[Int]
    object value extends IntColumn(this)

    override def fromRow(r: Row): WeatherStation =
      WeatherStation(country(r), city(r), stationId(r), entry(r), value(r))
  }

  abstract class WeatherStationQueries extends WeatherStationCF("weather_station") {

    def getAllByCountry(country: String): Future[List[WeatherStation]] =
      findAllByCountry(country).fetch()

    def getAllByCountryAndCity(country: String, city: String): Future[List[WeatherStation]] =
      findAllByCountryAndCity(country, city).fetch()

    def getAllByCountryCityAndId(country: String, city: String, stationId: String): Future[List[WeatherStation]] =
      findAllByCountryCityAndId(country, city, stationId).fetch()

    private def findAllByCountry(country: String) =
      select.where(_.country eqs country)

    private def findAllByCountryAndCity(country: String, city: String) =
      findAllByCountry(country).and(_.city eqs city)

    private def findAllByCountryCityAndId(country: String, city: String, stationId: String) =
      findAllByCountryAndCity(country, city).and(_.stationId eqs stationId)
  }

  class DB(ks: KeySpaceDef) extends DatabaseImpl(ks) {

    object stations extends WeatherStationQueries with connector.Connector
  }

  val db = new DB(ContactPoint.local.keySpace("db"))

  val result = db.stations.getAllByCountryCityAndId("UK", "London", "SW27")

  result.onComplete(_ => db.shutdown())
}
```

Phantom allows the user certain level of composability, but it gets a bit verbose due to the nature of the DSL. 

**Quill**
```scala

object Quill extends App {

  val db = new CassandraAsyncContext(SnakeCase, "db")

  case class WeatherStation(country: String, city: String, stationId: String, entry: Int, value: Int)

  object WeatherStation {

    val getAllByCountry = quote {
      (country: String) =>
        query[WeatherStation].filter(_.country == country)
    }

    val getAllByCountryAndCity = quote {
      (country: String, city: String) =>
        getAllByCountry(country).filter(_.city == city)
    }

    val getAllByCountryCityAndId = quote {
      (country: String, city: String, stationId: String) =>
        getAllByCountryAndCity(country, city).filter(_.stationId == stationId)
    }
  }

  val result = db.run(WeatherStation.getAllByCountryCityAndId("UK", "London", "SW2"))

  result.onComplete(_ => db.close())
}
```

Quill offers more advanced composability, but CQL being a much simpler query language than SQL can't benefit much from it. During the compilation of this example, as the quotation is known statically, Quill will emit the CQL string as an info message, e.g. `SELECT country, city, station_id, entry, value FROM weather_station WHERE country = ? AND city = ? AND station_id = ?`.

## Extensibility ##

This section explores the extensibility capabilities of each library .

**Java Driver (v3.0.0)**

There is no much offered by the driver to extend the Query Builder, e.g. add a missing CQL feature.

**Phantom (v1.22.0)**

You could extend Phantom by extending the DSL to add new features, although it might not be a straightforward process.

**Quill**

Quill provides an easy mechanism to add non-supported features through [infix](https://github.com/getquill/quill#infix). In fact, most of the [CQL specific features](https://github.com/getquill/quill/blob/master/quill-cassandra/src/main/scala/io/getquill/sources/cassandra/ops/package.scala) are added using infix.

## Custom data types ##

This section would allow us to compare how the different libraries let us read custom data types in a seamless way.

**Java Driver (v3.0.0)**
```

object JavaDriver extends App {

  case class Country(code: String)

  object Country {

    class Codec(charset: Charset) extends TypeCodec[Country](DataType.text(), classOf[Country]) {

      override def serialize(country: Country, protocolVersion: ProtocolVersion): ByteBuffer =
        if (country == null) null else ByteBuffer.wrap(country.code.getBytes(charset))

      override def parse(value: String): Country =
        if (value == null || value.isEmpty || value.equalsIgnoreCase("NULL"))
          null
        else if (!ParseUtils.isQuoted(value))
          throw new InvalidTypeException("text or varchar values must be enclosed by single quotes")
        else Country(ParseUtils.unquote(value))

      override def format(country: Country): String =
        if (country == null) "NULL"
        else ParseUtils.quote(country.code)

      override def deserialize(bytes: ByteBuffer, protocolVersion: ProtocolVersion): Country =
        if (bytes == null) null
        else if (bytes.remaining == 0) Country("")
        else Country(new String(Bytes.getArray(bytes), charset))
    }
  }

  val nrOfCacheEntries: Int = 100

  val cluster: Cluster = Cluster.builder().addContactPoints("localhost").build()

  cluster.getConfiguration.getCodecRegistry.register(new Country.Codec(Charset.forName("UTF-8")))

  val session: Session = cluster.newSession()

  val cache: LoadingCache[String, PreparedStatement] =
    CacheBuilder.newBuilder().
      maximumSize(nrOfCacheEntries).
      build(
        new CacheLoader[String, PreparedStatement]() {
          def load(key: String): PreparedStatement = session.prepare(key.toString)
        }
      )

  case class WeatherStation(country: Country, city: String, stationId: String, entry: Int, value: Int)

  object WeatherStation {

    def getAllByCountry(cache: LoadingCache[String, PreparedStatement], session: Session)(country: Country): List[WeatherStation] = {
      val query: Statement =
        QueryBuilder.select().
          all().
          from("db", "weather_station").
          where(QueryBuilder.eq("country", QueryBuilder.bindMarker()))

      session.execute(cache.get(query.toString).bind(country)).all().asScala.map(
        row => WeatherStation(row.get("country", classOf[Country]), row.getString("city"), row.getString("station_id"), row.getInt("entry"), row.getInt("value"))
      ).to[List]
    }
  }

  val getAllByCountry: Country => List[WeatherStation] = WeatherStation.getAllByCountry(cache, session)_

  getAllByCountry(Country("UK"))

  session.close()
  cluster.close()
}
```

It is necessary to create a new `TypeCodec` and register it in the `CodecRegistry`.

**Phantom (v1.22.0)**
```

object Phantom extends App {

  case class Country(code: String) extends AnyVal

  object Country {

    implicit object CountryIsPrimitive extends Primitive[Country] {

      override type PrimitiveType = Country

      override def fromRow(column: String, row: Row): Try[Country] =
        nullCheck(column, row) {
          r => Country(r.getString(column))
        }

      override val cassandraType: String = CQLSyntax.Types.Text

      override def fromString(code: String): Country = Country(code)

      override def asCql(country: Country): String = CQLQuery.empty.singleQuote(country.code)

      override val clz: Class[CountryIsPrimitive.PrimitiveType] = classOf[Country]
    }

    type Column[Owner <: CassandraTable[Owner, Record], Record] = PrimitiveColumn[Owner, Record, Country]
  }

  case class WeatherStation(country: Country, city: String, stationId: String, entry: Int, value: Int)

  case class People(name: String, age: Int)

  abstract class WeatherStationCF(override val tableName: String) extends CassandraTable[WeatherStationCF, WeatherStation] with RootConnector {

    object country extends Country.Column(this) with PartitionKey[Country]
    object city extends StringColumn(this) with PrimaryKey[String]
    object stationId extends StringColumn(this) with PrimaryKey[String] {
      override lazy val name: String = "station_id"
    }
    object entry extends IntColumn(this) with PrimaryKey[Int]
    object value extends IntColumn(this)

    override def fromRow(r: Row): WeatherStation =
      WeatherStation(country(r), city(r), stationId(r), entry(r), value(r))
  }

  abstract class WeatherStationQueries extends WeatherStationCF("weather_station") {

    def getAllByCountry(country: Country): Future[List[WeatherStation]] =
      select.where(_.country eqs country).fetch()
  }

  class DB(ks: KeySpaceDef) extends DatabaseImpl(ks) {

    object stations extends WeatherStationQueries with connector.Connector
  }

  val db = new DB(ContactPoint.local.keySpace("db"))

  val result = db.stations.getAllByCountry(Country("UK"))

  result.onComplete(_ => db.shutdown())
}
```

It is necessary to define a new `Column` type to be used when defining the data model.

**Quill**
```scala

object Quill extends App {

  val db = new CassandraAsyncContext(SnakeCase, "db")

  case class Country(code: String) extends AnyVal

  object Country {

    implicit val decode: MappedEncoding[String, Country] = MappedEncoding[String, Country](Country(_))
    implicit val encode: MappedEncoding[Country, String] = MappedEncoding[Country, String](_.code)
  }
  case class WeatherStation(country: Country, city: String, stationId: String, entry: Int, value: Int)

  object WeatherStation {

    val getAllByCountry = quote {
      (country: Country) =>
        query[WeatherStation].filter(_.country == country)
    }
  }

  val result = db.run(WeatherStation.getAllByCountry(lift(Country("UK"))))

  result.onComplete(_ => db.close())
}
```

Quill only requires definition of implicit encodings from/to `String`.

## Non-blocking IO ##

This section compares the different options the libraries offer to do non-blocking IO.

**Java Driver (v3.0.0)**

The Datastax driver allows the user to execute queries [asynchronously](https://github.com/datastax/java-driver/tree/2.1/manual/async), returning `ListenableFuture`s.
   
**Phantom (v1.22.0)**

Phantom is asynchronous by default and all operations return `Future`s. It also allows users to process the data coming from Cassandra in a streaming fashion using [`play-iteratees`](https://www.playframework.com/documentation/2.4.x/Iteratees) or [`play-streams-experimental`](https://www.playframework.com/documentation/2.4.x/ReactiveStreamsIntegration), that make it possible to integrate with other software that support [reactive-streams](https://github.com/reactive-streams/reactive-streams-jvm).

**Quill (v0.4.0)**

Quill provides blocking, asynchronous and streaming sources for Cassandra. The asynchronous source returns `Future`s on all operations. The streaming source uses [Monix](https://github.com/monixio/monix) to return an `Observable`. Monix is a reactive library compatible with the [reactive-streams](https://github.com/reactive-streams/reactive-streams-jvm) protocol.

## Other considerations ##

There other aspects the user might want to take into account like 3rd party dependencies. As both Phantom and Quill depend on the Datastax Java Driver, we are going to pay attention to which additional dependencies each of them add.

Phantom is composed by several modules, each of them with their 3rd party dependencies. Overall it adds more 3rd party dependencies than Quill and it has dependencies on libraries like shapeless, play-iteratees, play-streams-experimental or akka-actor. Quill, on the other hand, only adds dependencies on monix and scalamacros resetallattrs.
