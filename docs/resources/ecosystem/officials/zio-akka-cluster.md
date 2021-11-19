---
id: zio-akka-cluster
title:  "ZIO Akka Cluster"
---

The [ZIO Akka Cluster](https://github.com/zio/zio-akka-cluster) library is a ZIO wrapper on [Akka Cluster](https://doc.akka.io/docs/akka/current/index-cluster.html). We can use clustering features of the Akka toolkit without the need to use the actor model.

## Introduction

This library provides us following features:

- **Akka Cluster** — This feature contains two Akka Cluster Membership operations called `join` and `leave` and also it has some methods to retrieve _Cluster State_ and _Cluster Events_.

- **Akka Distributed PubSub** — Akka has a _Distributed Publish Subscribe_ facility in the cluster. It helps us to send a message to all actors in the cluster that have registered and subscribed for a specific topic name without knowing their physical address or without knowing which node they are running on.

- **Akka Cluster Sharding** — Cluster sharding is useful when we need to _distribute actors across several nodes in the cluster_ and want to be able to interact with them using their logical identifier without having to care about their physical location in the cluster, which might also change over time. When we have many stateful entities in our application that together they consume more resources (e.g. memory) than fit on one machine, it is useful to use _Akka Cluster Sharding_ to distribute our entities to multiple nodes.

## Installation

In order to use this library, we need to add the following line in our `build.sbt` file:

```scala
libraryDependencies += "dev.zio" %% "zio-akka-cluster" % "0.2.0" // Check the repo for the latest version
```

## Example

In the following example, we are using all these three features. We have a distributed counter application that lives in the Akka Cluster using _Akka Cluster Sharding_ feature. So the location of `LiveUsers` and `TotalRequests` entities in the cluster is transparent for us. We send the result of each entity to the _Distributed PubSub_. So every node in the cluster can subscribe and listen to those results. Also, we have created a fiber that is subscribed to the cluster events. All the new events will be logged to the console:

```scala
import akka.actor.ActorSystem
import com.typesafe.config.{Config, ConfigFactory}
import zio.akka.cluster.Cluster
import zio.akka.cluster.sharding.{Entity, Sharding}
import zio.console.putStrLn
import zio.{ExitCode, Has, Managed, Task, URIO, ZIO, ZLayer}

sealed trait Counter extends Product with Serializable
case object Inc extends Counter
case object Dec extends Counter

case class CounterApp(port: String) {
  val config: Config =
    ConfigFactory.parseString(
      s"""
         |akka {
         |  actor {
         |    provider = "cluster"
         |  }
         |  remote {
         |    netty.tcp {
         |      hostname = "127.0.0.1"
         |      port = $port
         |    }
         |  }
         |  cluster {
         |    seed-nodes = ["akka.tcp://CounterApp@127.0.0.1:2551"]
         |  }
         |}
         |""".stripMargin)

  val actorSystem: ZLayer[Any, Throwable, Has[ActorSystem]] =
    ZLayer.fromManaged(
      Managed.make(Task(ActorSystem("CounterApp", config)))(sys =>
        Task.fromFuture(_ => sys.terminate()).either
      )
    )

  val counterApp: ZIO[zio.ZEnv, Throwable, Unit] =
    actorSystem.build.use(sys =>
      for {
        queue <- Cluster
          .clusterEvents(true)
          .provideCustom(ZLayer.succeedMany(sys))

        pubsub <- zio.akka.cluster.pubsub.PubSub
          .createPubSub[Int]
          .provideCustom(ZLayer.succeedMany(sys))

        liveUsersLogger <- pubsub
          .listen("LiveUsers")
          .flatMap(
            _.take.tap(u => putStrLn(s"Number of live users: $u")).forever
          )
          .fork
        totalRequestLogger <- pubsub
          .listen("TotalRequests")
          .flatMap(
            _.take.tap(r => putStrLn(s"Total request until now: $r")).forever
          )
          .fork

        clusterEvents <- queue.take
          .tap(x => putStrLn("New event in cluster: " + x.toString))
          .forever
          .fork

        counterEntityLogic = (c: Counter) =>
          for {
            entity <- ZIO.environment[Entity[Int]]
            newState <- c match {
              case Inc =>
                entity.get.state.updateAndGet(s => Some(s.getOrElse(0) + 1))
              case Dec =>
                entity.get.state.updateAndGet(s => Some(s.getOrElse(0) - 1))
            }
            _ <- pubsub.publish(entity.get.id, newState.getOrElse(0)).orDie
          } yield ()
        cluster <- Sharding
          .start("CounterEntity", counterEntityLogic)
          .provideCustom(ZLayer.succeedMany(sys))

        _ <- cluster.send("LiveUsers", Inc)
        _ <- cluster.send("TotalRequests", Inc)
        _ <- cluster.send("LiveUsers", Dec)
        _ <- cluster.send("LiveUsers", Inc)
        _ <- cluster.send("LiveUsers", Inc)
        _ <- cluster.send("TotalRequests", Inc)
        _ <- cluster.send("TotalRequests", Inc)

        _ <-
          clusterEvents.join zipPar liveUsersLogger.join zipPar totalRequestLogger.join
      } yield ()
    )
}
```

Now, let's create a cluster comprising two nodes:

```scala
object CounterApp1 extends zio.App {
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = 
    CounterApp("2551").counterApp.exitCode
}

object CounterApp2 extends zio.App {
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = 
    CounterApp("2552").counterApp.exitCode
}
```

