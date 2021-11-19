## Tamer

 [Tamer](https://github.com/laserdisc-io/tamer) is a multi-functional Kafka connector for producing data based on [ZIO Kafka](https://github.com/zio/zio-kafka).

 ### Introduction

 Tamer is a completely customizable source connector that produces to Kafka. It ships with preconfigured modules for SQL, cloud storage and REST API, but you can provide your own functions and Tamer will take care of the rest.

 ### Installation

 Depending on the source you have at hand you can add the correct dependency in your `build.sbt`:

 ```scala
 libraryDependencies += "io.laserdisc" %% "tamer-db"                % "0.16.1"
 libraryDependencies += "io.laserdisc" %% "tamer-oci-objectstorage" % "0.16.1"
 libraryDependencies += "io.laserdisc" %% "tamer-rest"              % "0.16.1"
 libraryDependencies += "io.laserdisc" %% "tamer-s3"                % "0.16.1"
 ```

 ### Example

 Let's say you have a inventory DB that's compatible with [Doobie](https://github.com/tpolecat/doobie), you can get all of your items with just a few lines of code:

 ```scala
 import tamer._
 import tamer.db._

 import doobie.implicits.legacy.instant._
 import doobie.syntax.string._
 import zio._
 import zio.duration._
 import zio.json._

 import java.time.Instant

 case class Row(id: String, name: String, description: Option[String], modifiedAt: Instant)
     extends tamer.db.Timestamped(modifiedAt)

 object Row {
   implicit val rowJsonCodec = DeriveJsonCodec.gen[Row]
 }

 object DatabaseSimple extends zio.App {
   // Here we'll go with zio-json codec, you can use avro, circe and jsoniter
   // out-of-the box or plug yours!
   implicit val stateKeyJsonCodec = DeriveJsonCodec.gen[tamer.Tamer.StateKey]
   implicit val windowJsonCodec = DeriveJsonCodec.gen[tamer.db.Window]

   val program: RIO[ZEnv, Unit] = tamer.db.DbSetup
     .tumbling(window =>
       sql"""SELECT id, name, description, modified_at 
            |FROM users 
            |WHERE modified_at > ${window.from} AND modified_at <= ${window.to}""".stripMargin
         .query[Row]
     )(
       recordKey = (_, v) => v.id,
       from = Instant.parse("2020-01-01T00:00:00.00Z"),
       tumblingStep = 5.days
     )
     .runWith(dbLayerFromEnvironment ++ tamer.kafkaConfigFromEnvironment)

   override final def run(args: List[String]): URIO[ZEnv, ExitCode] =
     program.exitCode

   // If you have other codecs like circe in the classpath you have to disambiguate
   implicit lazy val stateKeyCodec: Codec[Tamer.StateKey] = Codec.optionalZioJsonCodec
   implicit lazy val windowCodec: Codec[tamer.db.Window] = Codec.optionalZioJsonCodec
   implicit lazy val stringCodec: Codec[String] = Codec.optionalZioJsonCodec
 }
 ```
 See full example [on the GitHub repo](https://github.com/laserdisc-io/tamer/blob/4e1a7646fb44041648d9aa3ba089decb81ebe487/example/src/main/scala/tamer/db/DatabaseSimple.scala)
