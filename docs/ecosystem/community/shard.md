---
id: shard
title: "Shard"
---

[Shard](https://ghostdogpr.github.io/shard/) is a Scala library that makes it easy to distribute entities across multiple servers, and to interact with those entities using their ID without knowing their actual location. This is also known as _entity sharding_ with _location transparency_. Shard exposes a purely functional API and depends heavily on ZIO.

Shard 3 is the successor of [Shardcake](https://github.com/devsisters/shardcake) 2.x, with the same concepts and a new messaging API. The documentation has a [migration guide](https://ghostdogpr.github.io/shard/migration).

## Introduction

Entity sharding is a way to avoid concurrent updates of the same data when an application runs on multiple servers. Instead of using a global lock, all the requests for an entity, for example a guild in a game, are redirected to a single entity that handles them sequentially. The entities are spread across the servers, so each entity exists in only one place at a time.

Shard provides components to:
- Automatically manage the assignment of entities to servers.
- Send messages to entities using their IDs.

The architecture has two main parts. The **Shard Manager** is an independent component, with a single running instance, that assigns shards to pods. The **entities** run on the application servers and process the messages sent to them. The behavior of an entity, including its persistence, is up to us.

Four parts are pluggable:
- `Storage`: where the shard assignments are stored. Shard provides an implementation using Redis.
- `Pods`: how to communicate with remote pods. Shard provides an implementation using gRPC.
- `MessageCodec[Msg]`: how to encode and decode messages. Shard provides backends using Kryo and Proteus.
- `PodsHealth`: how to check if a pod is healthy. Shard provides an implementation using the Kubernetes API.

## Installation

In order to use this library, we need to add the following lines to our `build.sbt` file. The Shard Manager needs `shard-manager`, and the application servers need `shard-entities`:

```scala
libraryDependencies ++= Seq(
  "com.github.ghostdogpr" %% "shard-manager"            % "3.0.0-RC1",
  "com.github.ghostdogpr" %% "shard-entities"           % "3.0.0-RC1",
  "com.github.ghostdogpr" %% "shard-protocol-grpc"      % "3.0.0-RC1",
  "com.github.ghostdogpr" %% "shard-serialization-kryo" % "3.0.0-RC1"
)
```

## Example

First, we start the Shard Manager. This example uses an in-memory storage and a simple `PodsHealth` that pings a pod to see if it's alive:

```scala
import shard._
import shard.interfaces._
import zio._

object ShardManagerApp extends ZIOAppDefault {
  def run: Task[Nothing] =
    Server.run.provide(
      ZLayer.succeed(ManagerConfig.default),
      ZLayer.succeed(GrpcConfig.default),
      PodsHealth.local, // just ping a pod to see if it's alive
      GrpcPods.live,    // use gRPC protocol
      Storage.memory,   // store data in memory
      ShardManager.live // shard manager logic
    )
}
```

Then we define an entity. The messages are an `enum` with two type parameters: `E` is the type of the errors a message can fail with, and `A` is the type of its reply. An `EntityType` gives the entity a unique name:

```scala
import shard.kryo.given

case class GuildFull(maxMembers: Int)

enum GuildMessage[E, A] {
  case Join(userId: String)  extends GuildMessage[GuildFull, Set[String]]
  case Leave(userId: String) extends GuildMessage[Nothing, Unit]
}

object Guild extends EntityType[GuildMessage]("guild")
```

The behavior of the entity is a function that is called when the entity starts, and returns the handler that processes its messages one at a time. The `Scope` is closed when the entity stops, so it can be used to save the entity state:

```scala
def behavior(entityId: String): ZIO[Scope, Nothing, Guild.Handler] =
  Ref.make(Set.empty[String]).map { state =>
    new Guild.Handler {
      def handle[E, A](message: GuildMessage[E, A]): IO[E, A] =
        message match {
          case GuildMessage.Join(userId)  =>
            state.get.flatMap(members =>
              if (members.size >= 5) ZIO.fail(GuildFull(5))
              else state.updateAndGet(_ + userId)
            )
          case GuildMessage.Leave(userId) =>
            state.update(_ - userId)
        }
    }
  }
```

Finally, we register the entity, register the pod to the Shard Manager, and send messages using only the entity ID:

```scala
val program =
  for {
    _     <- Sharding.registerEntity(Guild)(behavior)
    _     <- Sharding.registerScoped
    guild <- Sharding.messenger(Guild)
    _     <- guild.send("guild1")(GuildMessage.Join("user1")).either.debug
    _     <- guild.send("guild1")(GuildMessage.Join("user2")).either.debug
  } yield ()

def run: Task[Unit] =
  ZIO.scoped(program).provide(
    ZLayer.succeed(Config.default),
    ZLayer.succeed(GrpcConfig.default),
    Storage.memory,           // store data in memory
    ShardManagerClient.live,  // client to communicate with the Shard Manager
    GrpcPods.live,            // use gRPC protocol
    GrpcShardingService.live, // expose gRPC service
    Sharding.live             // sharding logic
  )
```

The same code can run on multiple pods, and the sharding system ensures that each guild runs on a single pod. For a real deployment, we would use an actual `Storage` implementation, such as Redis, and save the entity state outside of memory.

For more details, see the [Shard documentation](https://ghostdogpr.github.io/shard/).
