---
id: zio-query
title: "ZIO Query"
---

[ZIO Query](https://github.com/zio/zio-query) is a library for writing optimized queries to data sources in a high-level compositional style. It can add efficient pipelining, batching, and caching to any data source.

## Introduction

Some key features of ZIO Query:

- **Batching** — ZIO Query detects parts of composite queries that can be executed in parallel without changing the semantics of the query.

- **Pipelining** — ZIO Query detects parts of composite queries that can be combined together for fewer individual requests to the data source.

- **Caching** — ZIO Query can transparently cache read queries to minimize the cost of fetching the same item repeatedly in the scope of a query.

Assume we have the following database access layer APIs:

```scala
def getAllUserIds: ZIO[Any, Nothing, List[Int]] = {
  // Get all user IDs e.g. SELECT id FROM users
  ZIO.succeed(???)
}

def getUserNameById(id: Int): ZIO[Any, Nothing, String] = {
  // Get user by ID e.g. SELECT name FROM users WHERE id = $id
  ZIO.succeed(???)
}
```

We can get their corresponding usernames from the database by the following code snippet:

```scala
val userNames = for {
  ids   <- getAllUserIds
  names <- ZIO.foreachPar(ids)(getUserNameById)
} yield names
```

It works, but this is not performant. It is going to query the underlying database _N + 1_ times.

In this case, ZIO Query helps us to write an optimized query that is going to perform two queries (one for getting user IDs and one for getting all usernames).

## Installation

In order to use this library, we need to add the following line in our `build.sbt` file:

```scala
libraryDependencies += "dev.zio" %% "zio-query" % "0.2.9"
```

## Example

Here is an example of using ZIO Query, which optimizes multiple database queries by batching all of them in one query:

```scala
import zio.console.putStrLn
import zio.query.{CompletedRequestMap, DataSource, Request, ZQuery}
import zio.{Chunk, ExitCode, Task, URIO, ZIO}

import scala.collection.immutable.AbstractSeq

object ZQueryExample extends zio.App {
  case class GetUserName(id: Int) extends Request[Nothing, String]

  lazy val UserDataSource: DataSource.Batched[Any, GetUserName] =
    new DataSource.Batched[Any, GetUserName] {
      val identifier: String = "UserDataSource"

      def run(requests: Chunk[GetUserName]): ZIO[Any, Nothing, CompletedRequestMap] = {
        val resultMap = CompletedRequestMap.empty
        requests.toList match {
          case request :: Nil =>
            val result: Task[String] = {
              // get user by ID e.g. SELECT name FROM users WHERE id = $id
              ZIO.succeed(???)
            }

            result.either.map(resultMap.insert(request))

          case batch: Seq[GetUserName] =>
            val result: Task[List[(Int, String)]] = {
              // get multiple users at once e.g. SELECT id, name FROM users WHERE id IN ($ids)
              ZIO.succeed(???)
            }

            result.fold(
              err =>
                requests.foldLeft(resultMap) { case (map, req) =>
                  map.insert(req)(Left(err))
                },
              _.foldLeft(resultMap) { case (map, (id, name)) =>
                map.insert(GetUserName(id))(Right(name))
              }
            )
        }
      }
    }

  def getUserNameById(id: Int): ZQuery[Any, Nothing, String] =
    ZQuery.fromRequest(GetUserName(id))(UserDataSource)

  val query: ZQuery[Any, Nothing, List[String]] =
    for {
      ids <- ZQuery.succeed(1 to 10)
      names <- ZQuery.foreachPar(ids)(id => getUserNameById(id)).map(_.toList)
    } yield (names)

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    query.run
      .tap(usernames => putStrLn(s"Usernames: $usernames"))
      .exitCode
}
```

## Resources

- [Wicked Fast API Calls with ZIO Query](https://www.youtube.com/watch?v=rUUxDXJMzJo) by Adam Fraser (July 2020) (https://www.youtube.com/watch?v=rUUxDXJMzJo)
