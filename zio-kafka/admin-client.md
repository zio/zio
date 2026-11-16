# Admin Client

> `AdminClient` manages a Kafka cluster from ZIO: it creates and deletes topics, reads and changes configuration, inspects and resets consumer groups, lists offsets, describes the cluster, and manages access control lists (ACLs). It wraps the Java [`Admin`](https://kafka.apache.org/documentation/#adminapi) client, turns every Kafka future into a ZIO effect, and replaces Java collections and nullable values with Scala collections, `Option` and small case classes. Unlike the Java client, a write operation (for example creating a topic) completes only after its effect is visible, wherever that is possible; see [Write Operations Wait Until They Are Visible](#write-operations-wait-until-they-are-visible). The main types are `AdminClient`, `AdminClientSettings`, the model case classes nested in the `AdminClient` companion object (`NewTopic`, `ConfigResource`, `OffsetSpec`, and so on), and the ACL types in the `zio.kafka.admin.acl` and `zio.kafka.admin.resource` packages.

The shape of the two entry points is:

```scala
trait AdminClient {
  def createTopic(newTopic: AdminClient.NewTopic, validateOnly: Boolean = false): Task[Unit]
  def listTopics(listTopicsOptions: Option[AdminClient.ListTopicsOptions] = None): Task[Map[String, AdminClient.TopicListing]]
  // ... about 40 more operations
}

final case class AdminClientSettings(closeTimeout: Duration, properties: Map[String, AnyRef])
```

## Write Operations Wait Until They Are Visible

The Java admin client completes a write operation as soon as the broker accepts it, but the change is not yet visible to the next read: a topic that was just created may be missing from `listTopics` for a while (see [KAFKA-18818](https://issues.apache.org/jira/browse/KAFKA-18818)). Zio-kafka hides this. Its write operations only complete once the change is visible, using one of two strategies:

- **Polling:** after the broker accepts the request, zio-kafka waits 50 ms and then repeatedly reads the state back, with exponential backoff starting at 50 ms, until the change shows up. If it does not show up within 5 seconds, the operation still succeeds: zio-kafka assumes that the write succeeded and was undone by another write.
- **Fixed sleep:** where polling is impractical, the operation sleeps for its `visibilitySleep` parameter (default 1 second) after the broker accepts the request. Pass `Duration.Zero` when you don't care when the operation takes effect.

The table lists which operations wait and how:

| Operation                                                             | How it waits                                                                                                                    |
| --------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------- |
| `createTopic`, `createTopics`                                         | Polls `listTopics` until every new topic is listed                                                                              |
| `deleteTopic`, `deleteTopics`                                         | Polls `listTopics` until no deleted topic is listed                                                                             |
| `createPartitions`                                                    | Polls `describeTopics` until each topic has at least the requested partition count                                              |
| `deleteConsumerGroups`                                                | Polls `listConsumerGroups` until no deleted group is listed                                                                     |
| `alterConsumerGroupOffsets`                                           | Polls `listConsumerGroupOffsets` until every partition shows the new offset                                                     |
| `incrementalAlterConfigs`, `incrementalAlterConfigsAsync`             | Polls `describeConfigs` for `Set`/`Delete` ops; sleeps `visibilitySleep` when the request contains an `Append` or `Subtract` op |
| `createAcls`, `createAclsAsync`                                       | Polls `describeAcls` until every new binding is listed                                                                          |
| `deleteAcls`, `deleteAclsAsync`                                       | Polls `describeAcls` with each filter until it matches nothing                                                                  |
| `deleteRecords`                                                       | Sleeps `visibilitySleep`                                                                                                        |
| `removeMembersFromConsumerGroup`, `removeAllMembersFromConsumerGroup` | Sleeps `visibilitySleep`                                                                                                        |

A request made with `validateOnly = true` changes nothing, so `createTopic`, `createTopics`, `createPartitions` and `incrementalAlterConfigs` skip the waiting when `validateOnly` is set and return as soon as the broker has validated the request. `incrementalAlterConfigsAsync` does not skip it: with `validateOnly = true` its inner effects still wait as described above, which for a `Set` op usually means polling until the 5-second limit, because the validated change never shows up. The waiting uses the ZIO `Clock`; in a ZIO Test spec that uses `TestClock`, add `@@ TestAspect.withLiveClock` (see [Writing Tests](writing-tests.md)).

## Creating an Admin Client

An `AdminClient` holds a network connection that must be closed when you are done with it. `AdminClient.make` and `AdminClient.live` close it automatically when their scope ends. With `AdminClient.fromScopedJavaClient`, the scoped effect you pass must close the Java client, for example by creating it with `ZIO.acquireRelease`. With `AdminClient.fromJavaClient` you close the Java client yourself. All constructors are in the `AdminClient` companion object:

```scala
object AdminClient {
  def make(settings: AdminClientSettings): ZIO[Scope, Throwable, AdminClient]
  val live: ZLayer[AdminClientSettings, Throwable, AdminClient]
  def fromJavaClient(javaClient: org.apache.kafka.clients.admin.Admin): URIO[Any, AdminClient]
  def fromScopedJavaClient[R, E](scopedJavaClient: ZIO[R & Scope, E, Admin]): ZIO[R & Scope, E, AdminClient]
  def javaClientFromSettings(settings: AdminClientSettings): ZIO[Scope, Throwable, Admin]
}
```

`AdminClient.make` is the usual entry point. It runs zio-kafka's SSL endpoint check (see [Security](security.md)), creates the Java client, and closes it, waiting at most `AdminClientSettings#closeTimeout`, when the scope closes. To create a topic and then list all topic names inside one scope:

```scala
import zio._
import zio.kafka.admin.{ AdminClient, AdminClientSettings }
import zio.kafka.admin.AdminClient.NewTopic

val bootstrapServers = List("localhost:9092")

val topicNames: ZIO[Any, Throwable, Set[String]] =
  ZIO.scoped {
    for {
      admin <- AdminClient.make(AdminClientSettings(bootstrapServers))
      _ <- admin.createTopic(
             NewTopic(
               name = "orders",
               numPartitions = 6,
               replicationFactor = 3,
               configs = Map("cleanup.policy" -> "compact")
             )
           )
      topics <- admin.listTopics()
    } yield topics.keySet
  }
```

Alternatively, you can provide an `AdminClient` as a ZIO layer. `AdminClient.live` builds the client from an `AdminClientSettings` in the environment, so the rest of the application can depend on `AdminClient` as a service:

```scala
import zio._
import zio.kafka.admin.{ AdminClient, AdminClientSettings }

val bootstrapServers = List("localhost:9092")

val adminLayer: ZLayer[Any, Throwable, AdminClient] =
  ZLayer.succeed(AdminClientSettings(bootstrapServers)) >>> AdminClient.live

val clusterId: ZIO[AdminClient, Throwable, String] =
  ZIO.serviceWithZIO[AdminClient](_.describeClusterId())
```

The other constructors work with a Java `Admin` client:

- `AdminClient.fromJavaClient(javaClient)` wraps a Java client that you created. You must close the Java client yourself.
- `AdminClient.fromScopedJavaClient(effect)` wraps the Java client that a scoped effect creates. It does not close the client itself: create the client with `ZIO.acquireRelease` in that effect so that it is closed when the scope ends.
- `AdminClient.javaClientFromSettings(settings)` creates a Java client from settings and closes it when the scope ends. `AdminClient.make` uses it. Use it when you also need the Java client, for example for an operation that zio-kafka does not wrap.

`AdminClient.fromJavaClient` and `AdminClient.fromScopedJavaClient` do not run the [SSL endpoint check](security.md#ssl-endpoint-check); `AdminClient.javaClientFromSettings` (and so `AdminClient.make`) does.

## AdminClientSettings

`AdminClientSettings` holds the Java client properties and the close timeout. Construct it from the bootstrap servers, which also sets a close timeout of 30 seconds, and refine it with the `with*` methods, which keeps your code source compatible with future releases. The close timeout has no `with` method; change it with `copy`. For every member, see the [`AdminClientSettings` API docs](https://javadoc.io/doc/dev.zio/zio-kafka_2.13/latest/zio/kafka/admin/AdminClientSettings.html).

Any [Java admin client configuration](https://kafka.apache.org/documentation/#adminclientconfigs) can be set as a property, and `AdminClientSettings#withCredentials` adds the TLS properties of a `KafkaCredentialStore` (see [Security](security.md)). To set a client id and a shorter request timeout, and to close faster:

```scala
import org.apache.kafka.clients.admin.AdminClientConfig
import zio._
import zio.kafka.admin.AdminClientSettings

val settings: AdminClientSettings =
  AdminClientSettings(List("localhost:9092"))
    .withProperty(AdminClientConfig.CLIENT_ID_CONFIG, "ops-tool")
    .withProperties(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG -> "15000")
    .copy(closeTimeout = 10.seconds)
```

## How the Types Work Together

Every operation takes zio-kafka model values and returns zio-kafka model values; the Java types stay inside the wrapper. Most operations also accept an optional options value (`Option[...Options] = None`) that carries a request `timeout` and operation-specific flags:

```
AdminClientSettings ──> AdminClient.make / AdminClient.live ──> AdminClient
                                                                   │
  NewTopic, NewPartitions ─────────────> topics ───────────────────┤──> TopicListing, TopicDescription
  ConfigResource + AlterConfigOp ──────> configs ──────────────────┤──> KafkaConfig (entries: ConfigEntry)
  groupId, ListConsumerGroupOffsetsSpec > consumer groups ─────────┤──> ConsumerGroupListing, ConsumerGroupDescription,
                                                                   │    OffsetAndMetadata
  TopicPartition + OffsetSpec ─────────> offsets ──────────────────┤──> ListOffsetsResultInfo
  DescribeClusterOptions ──────────────> cluster ──────────────────┤──> Node, cluster id, AclOperation
  AclBinding, AclBindingFilter ────────> ACLs ─────────────────────┘──> AclBinding
```

A typical admin task follows the same steps:

1. Build `AdminClientSettings` and create the client with `AdminClient.make` or `AdminClient.live`.
2. Describe the target with model values: a `NewTopic`, a `ConfigResource`, a `TopicPartition` with an `OffsetSpec`, an `AclBinding`.
3. Call the operation. Write operations return once the change is visible; read operations return Scala maps keyed by topic name, `TopicPartition`, `ConfigResource` or group id.
4. Close the scope, which closes the client.

:::note[Name clashes]
The `AdminClient` companion defines its own `TopicPartition`, `OffsetAndMetadata`, `Node`, `Metric` and `IsolationLevel`, which clash with the Java classes of the same name. Import the model types you need by name, for example `import zio.kafka.admin.AdminClient.{ NewTopic, TopicPartition }`, rather than with a wildcard.
:::

## Operations by Task

The operations group naturally by the part of the cluster they manage. Every operation returns a `Task` (`ZIO[Any, Throwable, *]`), and a Kafka error, such as a `TopicExistsException` or a `GroupNotEmptyException`, becomes the failure of that task. The sections below show one task per group; for every operation, its parameters and its options type, see the [`AdminClient` API docs](https://javadoc.io/doc/dev.zio/zio-kafka_2.13/latest/zio/kafka/admin/AdminClient.html), and for the model case classes, the [`AdminClient` companion API docs](https://javadoc.io/doc/dev.zio/zio-kafka_2.13/latest/zio/kafka/admin/AdminClient$.html).

### Topics

`createTopic`, `createTopics`, `deleteTopic`, `deleteTopics`, `listTopics`, `describeTopics`, `createPartitions` and `deleteRecords` create, inspect and remove topics and their data. A `NewTopic` describes a topic to create and `NewPartitions` raises a topic's partition count; `listTopics` leaves out internal topics unless `ListTopicsOptions#listInternal` is set, and `TopicDescription#authorizedOperations` is `None` unless the request set `includeAuthorizedOperations = true`. `deleteRecords` takes the Java `RecordsToDelete` class, for example `RecordsToDelete.beforeOffset(offset)`.

To add partitions to an existing topic and then read back its partition leaders:

```scala
import zio._
import zio.kafka.admin.AdminClient
import zio.kafka.admin.AdminClient.NewPartitions

def growTopic(admin: AdminClient, topic: String, partitions: Int): Task[Map[Int, Option[Int]]] =
  for {
    _            <- admin.createPartitions(Map(topic -> NewPartitions(totalCount = partitions)))
    descriptions <- admin.describeTopics(List(topic))
  } yield descriptions(topic).partitions.map(p => p.partition -> p.leader.map(_.id)).toMap
```

### Configuration

`describeConfigs` and `incrementalAlterConfigs`, and their `*Async` variants, read and change the configuration of topics, brokers and other resources. A `ConfigResource` names the resource; for a broker, its name is the broker id as a string, for example `"0"`. Each change is an `AlterConfigOp`: `AlterConfigOpType.Set` sets a value, `Delete` reverts it to the default, and `Append` and `Subtract` add to or remove from a list-valued config. The `AlterConfigsOptions` parameter of `incrementalAlterConfigs` is required, but `AlterConfigsOptions()` is enough in most cases. The result, a `KafkaConfig`, maps each config name to a Java `ConfigEntry`.

To set a topic's retention and read the new value back:

```scala
import org.apache.kafka.clients.admin.ConfigEntry
import zio._
import zio.kafka.admin.AdminClient
import zio.kafka.admin.AdminClient.{ AlterConfigOp, AlterConfigOpType, AlterConfigsOptions, ConfigResource, ConfigResourceType }

def setRetention(admin: AdminClient, topic: String, retention: Duration): Task[Option[String]] = {
  val resource = ConfigResource(ConfigResourceType.Topic, topic)
  val setOp    = AlterConfigOp(new ConfigEntry("retention.ms", retention.toMillis.toString), AlterConfigOpType.Set)
  for {
    _       <- admin.incrementalAlterConfigs(Map(resource -> List(setOp)), AlterConfigsOptions())
    configs <- admin.describeConfigs(List(resource))
  } yield configs.get(resource).flatMap(_.entries.get("retention.ms")).map(_.value())
}
```

Because `incrementalAlterConfigs` polls until the `Set` is visible, the `describeConfigs` call that follows already sees the new value.

### Consumer Groups

`listConsumerGroups`, `listGroups`, `describeConsumerGroups`, `listConsumerGroupOffsets`, `alterConsumerGroupOffsets`, `deleteConsumerGroups` and the `remove*MembersFromConsumerGroup` operations list, inspect, reset and delete consumer groups. `listConsumerGroups` returns consumer groups only, while `listGroups` returns groups of every type. A group must be empty before `deleteConsumerGroups` can delete it, and must have no active members before `alterConsumerGroupOffsets` can change its offsets; `removeMembersFromConsumerGroup` removes static members by their group instance id. As always in Kafka, a committed `OffsetAndMetadata` holds the offset of the *next* record the group will read.

To compute the lag of every partition a consumer group has committed offsets for, combine the group's committed offsets with the partitions' end offsets:

```scala
import zio._
import zio.kafka.admin.AdminClient
import zio.kafka.admin.AdminClient.{ OffsetSpec, TopicPartition }

def consumerLag(admin: AdminClient, groupId: String): Task[Map[TopicPartition, Long]] =
  for {
    committed  <- admin.listConsumerGroupOffsets(groupId)
    endOffsets <- admin.listOffsets(committed.map { case (tp, _) => tp -> OffsetSpec.LatestSpec })
  } yield committed.flatMap { case (tp, committedOffset) =>
    endOffsets.get(tp).map(end => tp -> (end.offset - committedOffset.offset))
  }
```

To rewind a group to the start of its partitions, for example to reprocess a topic after a bug fix, stop all members of the group first and then alter its offsets:

```scala
import zio._
import zio.kafka.admin.AdminClient
import zio.kafka.admin.AdminClient.{ GroupState, OffsetAndMetadata, OffsetSpec }

def rewindGroup(admin: AdminClient, groupId: String): Task[Unit] =
  for {
    description <- admin.describeConsumerGroups(groupId).map(_(groupId))
    _ <- ZIO.fail(new IllegalStateException(s"Group $groupId still has members"))
           .unless(description.state == GroupState.Empty)
    committed <- admin.listConsumerGroupOffsets(groupId)
    earliest  <- admin.listOffsets(committed.map { case (tp, _) => tp -> OffsetSpec.EarliestSpec })
    _ <- admin.alterConsumerGroupOffsets(
           groupId,
           earliest.map { case (tp, info) => tp -> OffsetAndMetadata(info.offset) }
         )
  } yield ()
```

### Offsets

`listOffsets` and `listOffsetsAsync` look up offsets in the partitions themselves, independently of any consumer group. Each partition, given as the zio-kafka admin `TopicPartition` case class rather than the Java one, comes with an `OffsetSpec`: `OffsetSpec.EarliestSpec` (the first offset still in the log), `OffsetSpec.LatestSpec` (the offset the next produced record will get), or `OffsetSpec.TimestampSpec(timestamp)` (the first offset whose timestamp is at or after `timestamp`, in epoch milliseconds). The isolation level in `ListOffsetsOptions` defaults to `IsolationLevel.ReadUncommitted`; with `IsolationLevel.ReadCommitted`, `LatestSpec` returns the last stable offset, which excludes records of open transactions, so use it for topics written by a [transactional producer](transactions.md).

### Cluster

The `describeCluster*` operations (`describeClusterNodes`, `describeClusterController`, `describeClusterId` and `describeClusterAuthorizedOperations`), `describeLogDirs` and `metrics` describe the brokers, their log directories and the client itself. `describeClusterAuthorizedOperations` returns an empty set unless `DescribeClusterOptions#includeAuthorizedOperations` is set. A `Node` describes a broker, with `Option` fields where Kafka uses empty or negative placeholders. `metrics` returns the Java admin client's own metrics; see [Metrics](metrics.md) for the consumer and producer metrics zio-kafka publishes.

### Access Control Lists

`describeAcls`, `createAcls` and `deleteAcls` (the last two also have `*Async` variants) need a broker with an authorizer configured; `deleteAcls` returns the bindings it deleted. They use the types from the `zio.kafka.admin.acl` and `zio.kafka.admin.resource` packages. An ACL is an `AclBinding(pattern, entry)` that binds a resource pattern to an access control entry:

```
AclBinding ──> ResourcePattern(resourceType, name, patternType)
           └─> AccessControlEntry(principal, host, operation, permissionType)

AclBindingFilter ──> ResourcePatternFilter(resourceType, name, patternType)
                 └─> AccessControlEntryFilter(principal, host, operation, permissionType)
```

A `PatternType.Literal` pattern matches the exact name (or `*` for all), and `PatternType.Prefixed` matches every name that starts with it. The `principal` is a Kafka principal such as `"User:alice"` (or `"User:*"`), and `host` is a client host, or `"*"` for any host. `AclBindingFilter(ResourcePatternFilter.Any, AccessControlEntryFilter.Any)` selects every ACL.

To let a principal read every topic whose name starts with a prefix, and then list the ACLs on that prefix:

```scala
import zio._
import zio.kafka.admin.AdminClient
import zio.kafka.admin.acl._
import zio.kafka.admin.resource.{ PatternType, ResourcePattern, ResourcePatternFilter, ResourceType }

def allowReadOnPrefix(admin: AdminClient, principal: String, prefix: String): Task[Set[AclBinding]] = {
  val binding = AclBinding(
    ResourcePattern(ResourceType.Topic, name = prefix, patternType = PatternType.Prefixed),
    AccessControlEntry(
      principal = principal,
      host = "*",
      operation = AclOperation.Read,
      permissionType = AclPermissionType.Allow
    )
  )
  val filter = AclBindingFilter(
    ResourcePatternFilter(ResourceType.Topic, prefix, PatternType.Prefixed),
    AccessControlEntryFilter.Any
  )
  admin.createAcls(Set(binding)) *> admin.describeAcls(filter)
}
```

## The Async Variants

Several operations come in two flavours: `describeConfigs` and `describeConfigsAsync`, `incrementalAlterConfigs` and `incrementalAlterConfigsAsync`, `listOffsets` and `listOffsetsAsync`, `describeLogDirs` and `describeLogDirsAsync`, `createAcls` and `createAclsAsync`, `deleteAcls` and `deleteAclsAsync`. The plain variant waits for the whole request and fails if any part of it fails. The `*Async` variant returns a nested effect, `Task[Map[K, Task[V]]]`:

- The outer effect sends the request and completes as soon as the Java client returns its per-item futures, without waiting for the broker's answer.
- Each inner effect waits for the result of one item (one config resource, partition, broker, ACL binding or filter). For `incrementalAlterConfigsAsync`, `createAclsAsync` and `deleteAclsAsync`, the inner effect also does the visibility wait described above.

This gives you a result, or a failure, per item: one missing topic does not fail the lookup of the others. The request is sent when the outer effect runs, so not running an inner effect does not cancel anything. To describe the configuration of several topics and keep a separate outcome for each:

```scala
import zio._
import zio.kafka.admin.AdminClient
import zio.kafka.admin.AdminClient.{ ConfigResource, ConfigResourceType, KafkaConfig }

def describeEach(admin: AdminClient, topics: List[String]): Task[Map[ConfigResource, Either[Throwable, KafkaConfig]]] =
  admin
    .describeConfigsAsync(topics.map(ConfigResource(ConfigResourceType.Topic, _)))
    .flatMap { perResource =>
      ZIO.foreachPar(perResource) { case (resource, config) => config.either.map(resource -> _) }
    }
```

## Using the Java Admin Client Directly

Zio-kafka wraps most, but not all, operations of the Java `Admin` interface. For an operation it does not wrap, create the Java client with `AdminClient.javaClientFromSettings` and convert its `KafkaFuture` results with the public helpers `AdminClient.fromKafkaFuture` and `AdminClient.fromKafkaFutureVoid`. To describe the cluster's feature flags, which zio-kafka does not wrap:

```scala
import org.apache.kafka.clients.admin.FeatureMetadata
import zio._
import zio.kafka.admin.{ AdminClient, AdminClientSettings }

val features: ZIO[Any, Throwable, FeatureMetadata] =
  ZIO.scoped {
    for {
      javaAdmin <- AdminClient.javaClientFromSettings(AdminClientSettings(List("localhost:9092")))
      metadata  <- AdminClient.fromKafkaFuture(ZIO.attempt(javaAdmin.describeFeatures().featureMetadata()))
    } yield metadata
  }
```

Operations called this way get none of zio-kafka's visibility waiting.

## See Also

- [Security](security.md) for TLS and SASL settings and the SSL endpoint check that `AdminClient.make` runs.
- [Writing Tests](writing-tests.md) for the testkit helpers that create admin clients against an embedded broker.
- [Transactions](transactions.md) for when `IsolationLevel.ReadCommitted` matters.
