package zio.query

import zio.console.Console
import zio.query.DataSourceAspect._
import zio.test.Assertion._
import zio.test.TestAspect.{ after, nonFlaky, silent }
import zio.test._
import zio.test.environment.{ TestConsole, TestEnvironment }
import zio.{ console, Chunk, Has, Promise, Ref, ZIO, ZLayer }

object ZQuerySpec extends ZIOBaseSpec {

  override def spec: ZSpec[TestEnvironment, Any] =
    suite("ZQuerySpec")(
      testM("N + 1 selects problem") {
        for {
          _   <- getAllUserNames.run
          log <- TestConsole.output
        } yield assert(log)(hasSize(equalTo(2)))
      },
      testM("mapError does not prevent batching") {
        import zio.CanFail.canFail
        val a = getUserNameById(1).zip(getUserNameById(2)).mapError(identity)
        val b = getUserNameById(3).zip(getUserNameById(4)).mapError(identity)
        for {
          _   <- ZQuery.collectAllPar(List(a, b)).run
          log <- TestConsole.output
        } yield assert(log)(hasSize(equalTo(2)))
      },
      testM("failure to complete request is query failure") {
        for {
          result <- getUserNameById(27).run.run
        } yield assert(result)(dies(equalTo(QueryFailure(UserRequestDataSource, GetNameById(27)))))
      },
      test("query failure is correctly reported") {
        val failure = QueryFailure(UserRequestDataSource, GetNameById(27))
        assert(failure.getMessage)(
          equalTo("Data source UserRequestDataSource did not complete request GetNameById(27).")
        )
      },
      testM("timed does not prevent batching") {
        val a = getUserNameById(1).zip(getUserNameById(2)).timed
        val b = getUserNameById(3).zip(getUserNameById(4))
        for {
          _   <- ZQuery.collectAllPar(List(a, b)).run
          log <- TestConsole.output
        } yield assert(log)(hasSize(equalTo(2)))
      },
      testM("optional converts a query to one that returns its value optionally") {
        for {
          result <- getUserNameById(27).map(identity).optional.run
        } yield assert(result)(isNone)
      },
      testM("queries to multiple data sources can be executed in parallel") {
        for {
          promise <- Promise.make[Nothing, Unit]
          _       <- (neverQuery <&> succeedQuery(promise)).run.fork
          _       <- promise.await
        } yield assertCompletes
      },
      testM("arbitrary effects can be executed in parallel") {
        for {
          promise <- Promise.make[Nothing, Unit]
          _       <- (ZQuery.never <&> ZQuery.fromEffect(promise.succeed(()))).run.fork
          _       <- promise.await
        } yield assertCompletes
      },
      testM("zipPar does not prevent batching") {
        for {
          _   <- ZQuery.collectAllPar(List.fill(100)(getAllUserNames)).run
          log <- TestConsole.output
        } yield assert(log)(hasSize(equalTo(2)))
      } @@ nonFlaky,
      suite("zipPar")(
        testM("arbitrary effects are executed in order") {
          for {
            ref    <- Ref.make(List.empty[Int])
            query1 = ZQuery.fromEffect(ref.update(1 :: _))
            query2 = ZQuery.fromEffect(ref.update(2 :: _))
            _      <- (query1 *> query2).run
            result <- ref.get
          } yield assert(result)(equalTo(List(2, 1)))
        } @@ nonFlaky,
        testM("requests are executed in order") {
          val query = Cache.put(0, 1) *> Cache.getAll <* Cache.put(1, -1)
          assertM(query.run)(equalTo(Map(0 -> 1)))
        } @@ after(Cache.clear) @@ nonFlaky,
        testM("requests are pipelined") {
          val query = Cache.put(0, 1) *> Cache.getAll <* Cache.put(1, -1)
          assertM(query.run *> Cache.log)(hasSize(equalTo(1)))
        } @@ after(Cache.clear) @@ nonFlaky,
        testM("intervening flatMap prevents pipelining") {
          val query = Cache.put(0, 1).flatMap(ZQuery.succeed(_)) *> Cache.getAll <* Cache.put(1, -1)
          assertM(query.run *> Cache.log)(hasSize(equalTo(2)))
        } @@ after(Cache.clear) @@ nonFlaky,
        testM("trailing flatMap does not prevent pipelining") {
          val query = Cache.put(0, 1) *> Cache.getAll <* Cache.put(1, -1).flatMap(ZQuery.succeed(_))
          assertM(query.run *> Cache.log)(hasSize(equalTo(1)))
        } @@ after(Cache.clear) @@ nonFlaky,
        testM("short circuits on failure") {
          for {
            ref    <- Ref.make(true)
            query  = ZQuery.fail("fail") *> ZQuery.fromEffect(ref.set(false))
            _      <- query.run.ignore
            result <- ref.get
          } yield assert(result)(isTrue)
        } @@ nonFlaky,
        testM("does not deduplicate uncached requests") {
          val query = Cache.getAll *> Cache.put(0, 1) *> Cache.getAll
          assertM(query.run)(equalTo(Map(0 -> 1)))
        } @@ nonFlaky
      ).provideCustomLayer(Cache.live),
      testM("stack safety") {
        val effect = (0 to 100000)
          .map(ZQuery.succeed(_))
          .foldLeft(ZQuery.succeed(0)) { (query1, query2) =>
            for {
              acc <- query1
              i   <- query2
            } yield acc + i
          }
          .run
        assertM(effect)(equalTo(705082704))
      },
      testM("data sources can be raced") {
        for {
          promise <- Promise.make[Nothing, Unit]
          _       <- raceQuery(promise).run
          _       <- promise.await
        } yield assertCompletes
      },
      testM("max batch size") {
        val query = getAllUserNames @@ maxBatchSize(3)
        for {
          result <- query.run
          log    <- TestConsole.output
        } yield assert(result)(hasSameElements(userNames.values)) &&
          assert(log)(hasSize(equalTo(10)))
      },
      testM("multiple data sources do not prevent batching") {
        for {
          _   <- ZQuery.collectAllPar(List(getFoo, getBar)).run
          log <- TestConsole.output
        } yield assert(log)(hasSize(equalTo(2)))
      },
      testM("efficiency of large queries") {
        val query = for {
          users <- ZQuery.fromEffect(
                    ZIO.succeed(
                      List.tabulate(Sources.totalCount)(id => User(id, "user name", id, id))
                    )
                  )
          richUsers <- ZQuery.foreachPar(users) { user =>
                        Sources
                          .getPayment(user.paymentId)
                          .zipPar(Sources.getAddress(user.addressId))
                          .map {
                            case (payment, address) =>
                              (user, payment, address)
                          }
                      }
        } yield richUsers.size
        assertM(query.run)(equalTo(Sources.totalCount))
      }
    ) @@ silent @@ TestAspect.timed

  val userIds: List[Int]          = (1 to 26).toList
  val userNames: Map[Int, String] = userIds.zip(('a' to 'z').map(_.toString)).toMap

  sealed trait UserRequest[+A] extends Request[Nothing, A]

  case object GetAllIds                 extends UserRequest[List[Int]]
  final case class GetNameById(id: Int) extends UserRequest[String]

  val UserRequestDataSource: DataSource[Console, UserRequest[Any]] =
    DataSource.Batched.make[Console, UserRequest[Any]]("UserRequestDataSource") { requests =>
      ZIO.when(requests.toSet.size != requests.size)(ZIO.dieMessage("Duplicate requests)")) *>
        console.putStrLn("Running query") *>
        ZIO.succeed {
          requests.foldLeft(CompletedRequestMap.empty) {
            case (completedRequests, GetAllIds) => completedRequests.insert(GetAllIds)(Right(userIds))
            case (completedRequests, GetNameById(id)) =>
              userNames.get(id).fold(completedRequests)(name => completedRequests.insert(GetNameById(id))(Right(name)))
          }
        }
    }

  val getAllUserIds: ZQuery[Console, Nothing, List[Int]] =
    ZQuery.fromRequest(GetAllIds)(UserRequestDataSource)

  def getUserNameById(id: Int): ZQuery[Console, Nothing, String] =
    ZQuery.fromRequest(GetNameById(id))(UserRequestDataSource)

  val getAllUserNames: ZQuery[Console, Nothing, List[String]] =
    for {
      userIds   <- getAllUserIds
      userNames <- ZQuery.foreachPar(userIds)(getUserNameById)
    } yield userNames

  case object GetFoo extends Request[Nothing, String]
  val getFoo: ZQuery[Console, Nothing, String] = ZQuery.fromRequest(GetFoo)(
    DataSource.fromFunctionM("foo")(_ => console.putStrLn("Running foo query") *> ZIO.effectTotal("foo"))
  )

  case object GetBar extends Request[Nothing, String]
  val getBar: ZQuery[Console, Nothing, String] = ZQuery.fromRequest(GetBar)(
    DataSource.fromFunctionM("bar")(_ => console.putStrLn("Running bar query") *> ZIO.effectTotal("bar"))
  )

  case object NeverRequest extends Request[Nothing, Nothing]

  val neverQuery: ZQuery[Any, Nothing, Nothing] =
    ZQuery.fromRequest(NeverRequest)(DataSource.never)

  final case class SucceedRequest(promise: Promise[Nothing, Unit]) extends Request[Nothing, Unit]

  val succeedDataSource: DataSource[Any, SucceedRequest] =
    DataSource.fromFunctionM("succeed") {
      case SucceedRequest(promise) => promise.succeed(()).unit
    }

  def succeedQuery(promise: Promise[Nothing, Unit]): ZQuery[Any, Nothing, Unit] =
    ZQuery.fromRequest(SucceedRequest(promise))(succeedDataSource)

  val raceDataSource: DataSource[Any, SucceedRequest] =
    DataSource.never.race(succeedDataSource)

  def raceQuery(promise: Promise[Nothing, Unit]): ZQuery[Any, Nothing, Unit] =
    ZQuery.fromRequest(SucceedRequest(promise))(raceDataSource)

  sealed trait CacheRequest[+A] extends Request[Nothing, A]

  final case class Get(key: Int)             extends CacheRequest[Option[Int]]
  case object GetAll                         extends CacheRequest[Map[Int, Int]]
  final case class Put(key: Int, value: Int) extends CacheRequest[Unit]

  type Cache = Has[Cache.Service]

  object Cache {

    trait Service extends DataSource[Any, CacheRequest[Any]] {
      val clear: ZIO[Any, Nothing, Unit]
      val log: ZIO[Any, Nothing, List[List[Set[CacheRequest[Any]]]]]
    }

    val live: ZLayer[Any, Nothing, Cache] =
      ZLayer.fromEffect {
        for {
          cache <- Ref.make(Map.empty[Int, Int])
          ref   <- Ref.make[List[List[Set[CacheRequest[Any]]]]](Nil)
        } yield new Service {
          val clear: ZIO[Any, Nothing, Unit] =
            cache.set(Map.empty) *> ref.set(List.empty)
          val log: ZIO[Any, Nothing, List[List[Set[CacheRequest[Any]]]]] =
            ref.get
          val identifier: String =
            "CacheDataSource"
          def runAll(requests: Chunk[Chunk[CacheRequest[Any]]]): ZIO[Any, Nothing, CompletedRequestMap] =
            ref.update(requests.map(_.toSet).toList :: _) *>
              ZIO
                .foreach(requests) { requests =>
                  ZIO
                    .foreachPar(requests) {
                      case Get(key)        => cache.get.map(_.get(key))
                      case GetAll          => cache.get
                      case Put(key, value) => cache.update(_ + (key -> value))
                    }
                    .map(requests.zip(_).foldLeft(CompletedRequestMap.empty) {
                      case (map, (k, v)) => map.insert(k)(Right(v))
                    })
                }
                .map(_.foldLeft(CompletedRequestMap.empty)(_ ++ _))
        }
      }

    def get(key: Int): ZQuery[Cache, Nothing, Option[Int]] =
      for {
        cache <- ZQuery.environment[Cache].map(_.get)
        value <- ZQuery.fromRequestUncached(Get(key))(cache)
      } yield value

    val getAll: ZQuery[Cache, Nothing, Map[Int, Int]] =
      for {
        cache <- ZQuery.environment[Cache].map(_.get)
        value <- ZQuery.fromRequestUncached(GetAll)(cache)
      } yield value

    def put(key: Int, value: Int): ZQuery[Cache, Nothing, Unit] =
      for {
        cache <- ZQuery.environment[Cache].map(_.get)
        value <- ZQuery.fromRequestUncached(Put(key, value))(cache)
      } yield value

    val clear: ZIO[Cache, Nothing, Unit] =
      ZIO.accessM(_.get.clear)

    val log: ZIO[Cache, Nothing, List[List[Set[CacheRequest[Any]]]]] =
      ZIO.accessM(_.get.log)
  }

  case class Bearer(value: String)

  case class User(id: Int, name: String, addressId: Int, paymentId: Int)
  case class Address(id: Int, street: String)
  case class Payment(id: Int, name: String)

  object Sources {

    val totalCount = 15000

    case class GetPayment(id: Int) extends Request[Nothing, Payment]
    val paymentSource: DataSource[Any, GetPayment] =
      DataSource.fromFunctionBatchedOptionM("PaymentSource") { requests: Chunk[GetPayment] =>
        ZIO
          .succeed(
            List.tabulate(totalCount)(Payment(_, "payment name"))
          )
          .map(payments => requests.map(req => payments.find(_.id == req.id)))
      }

    def getPayment(id: Int): UQuery[Payment] =
      ZQuery.fromRequest(GetPayment(id))(paymentSource)

    case class GetAddress(id: Int) extends Request[Nothing, Address]
    val addressSource: DataSource[Any, GetAddress] =
      DataSource.fromFunctionBatchedOptionM("AddressSource") { requests: Chunk[GetAddress] =>
        ZIO
          .succeed(
            List.tabulate(totalCount)(Address(_, "street"))
          )
          .map(addresses => requests.map(req => addresses.find(_.id == req.id)))
      }

    def getAddress(id: Int): UQuery[Address] =
      ZQuery.fromRequest(GetAddress(id))(addressSource)
  }
}
