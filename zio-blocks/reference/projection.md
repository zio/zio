# Projection

> `zio-blocks-projection` provides event-sourced projections backed by per-entity
> SQLite files. Each projection type gets its own isolated `.db` file, giving you
> WAL-mode writes, offline queryability, and zero contention between unrelated
> entities.

## Installation

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-projection" % "0.0.56"

// JVM only — SQLite-backed stores
libraryDependencies += "dev.zio" %% "zio-blocks-projection" % "0.0.56"

// ZIO integration (transactor cache, engine runtime)
libraryDependencies += "dev.zio" %% "zio-blocks-sql-zio" % "0.0.56"

// for SQLite persistence add: libraryDependencies += "org.xerial" % "sqlite-jdbc" % "3.53.4.0" // or InMemory fallback if not present
libraryDependencies += "org.xerial" % "sqlite-jdbc" % "3.53.4.0"
```

`sqlite-jdbc` is `% Test` by default in `zio-blocks-projection`; consumers that
want SQLite persistence at runtime must add the dependency above explicitly.
If it is not present the engine falls back to `InMemoryProjectionStore`.

## Overview

Projections turn an event stream into queryable read models. Instead of
querying the event store directly, you define how events map to entities, and
the engine materializes those entities into SQLite files you can read
anytime.

**Why per-entity SQLite?**

A single shared database means every projection contends on the same locks,
the same WAL, and the same file. With per-entity files, each projection type
gets isolated storage:

| Approach | Contention | WAL contention | Offline access | Backup granularity |
|----------|-----------|---------------|----------------|-------------------|
| Single DB | High | High | Yes | Full DB |
| Per-entity SQLite | Zero | Zero | Yes | Per projection |

Each projection store lives at `<basePath>/<specName>.db`. Global
aggregates go to `global/<specName>.db`. The `TransactorCache` reuses
connections via an LRU cache so you don't open hundreds of file handles.

**Three projection scopes:**

- **PerEntity** (default): one row per entity ID. Events routed by
  `ctx.entityId`. Good for user profiles, order state, anything keyed by
  a single ID.
- **CrossEntity**: events routed by an arbitrary key extracted from the
  event. Multiple event types converge on the same projection. Good for
  "all repos owned by user X" views.
- **Global**: a single shared row (or a few rows keyed by a grouping
  string). Events from all sources funnel into atomic counter updates.
  Good for dashboards, daily aggregates, live counters.

## Quick Start

Define your events, your projection entity, wire up a spec, and start the
engine:

```scala
import zio.*
import zio.blocks.projection.*
import zio.blocks.projection.testing.TestEngine
import zio.blocks.schema.{Modifier, Schema}

// 1. Define events
case class UserCreated(name: String, email: String)
object UserCreated {
  implicit val schema: Schema[UserCreated] = Schema.derived[UserCreated]
}

// 2. Define the projection entity
case class UserProfile(@Modifier.id id: String, name: String, email: String)
object UserProfile {
  implicit val schema: Schema[UserProfile]         = Schema.derived[UserProfile]
  implicit val entityPath: EntityPath[UserProfile] = EntityPath.derived[UserProfile]
}

// 3. Define the projection
val projection = Projection[UserProfile]("userProfiles")
  .from("users")
  .routeToSelf
  .on[UserCreated]
  .insert((e, ctx) => UserProfile(ctx.entityId, e.name, e.email))

// 4. Create a test engine (auto-creates stores and hubs)
val program: ZIO[Scope, Throwable, Unit] = for {
  engine <- TestEngine.make(projection)
  // Append events
  _ <- engine.append("user-1", UserCreated("Alice", "alice@example.com"))
  _ <- engine.append("user-2", UserCreated("Bob", "bob@example.com"))
  // Query
  u1 <- engine.query(projection, "user-1")
  u2 <- engine.query(projection, "user-2")
} yield ()
```

The engine starts a catch-up fiber that reads all historical events, then
switches to live mode via the Hub subscription. Query results appear once
the engine processes the relevant events.

## Entity Path Conventions

`EntityPath[A]` tells the engine two things: which folder stores the SQLite
file, and which field holds the entity ID. The ID field is identified via
`@Modifier.id` (from `zio.blocks.schema.Modifier`) or, for ergonomics, a
field named `id`.

```scala
import zio.blocks.projection.*
import zio.blocks.schema.{Modifier, Schema}

case class Order(@Modifier.id id: Long, total: BigDecimal)
object Order {
  implicit val schema: Schema[Order] = Schema.derived[Order]
}

val orderId = "42"      // String id
val raw     = orderId // String = "42"
```

```scala
import zio.blocks.projection.*
import zio.blocks.schema.{Modifier, Schema}

case class UserProfile(@Modifier.id userId: String, name: String, email: String)
object UserProfile {
  implicit val schema: Schema[UserProfile]         = Schema.derived[UserProfile]
  implicit val entityPath: EntityPath[UserProfile] = EntityPath.derived[UserProfile]
}
// basePath = "users", entityIdField = "userId"
```

**Derivation rules** (`EntityPath.derived[A]`):

1. Look for a field annotated with `@Modifier.id`. If found, use it as
   the entity ID field.
2. Otherwise, find the field named `id`.
3. If neither is found, fail with `Entity must have @Modifier.id field`.
4. Derive the folder name from the ID field name:
   - Strip trailing `Id` suffix: `userId` → `user`
   - Convert to snake_case: `userId` → `user_id`
   - Pluralize: `user` → `users`
   - Result: `basePath = "users"`, `entityIdField = "userId"`

If the field is exactly `id`, `basePath` is `ids` — prefer `userId` or use `@path` to override.

**Override with `@path`:**

```scala
import zio.blocks.projection.*
import zio.blocks.schema.{Modifier, Schema}

@path("custom_users")
case class UserProfile(@Modifier.id id: String, name: String)
object UserProfile {
  implicit val schema: Schema[UserProfile]         = Schema.derived[UserProfile]
  implicit val entityPath: EntityPath[UserProfile] = EntityPath.derived[UserProfile]
}
// basePath = "custom_users", entityIdField = "id"
```

**Manual construction:**

```scala
import zio.blocks.projection.*
import zio.blocks.schema.{Modifier, Schema}

case class MyEntity(@Modifier.id id: String, value: Int)
object MyEntity {
  implicit val schema: Schema[MyEntity] = Schema.derived[MyEntity]
  implicit val entityPath: EntityPath[MyEntity] = EntityPath[MyEntity]("my_entities", "id")
}
```

## Multi-Source Projections

Cross-entity projections receive events from multiple sources and route them
by a key extracted from the event:

```scala
import zio.blocks.projection.*
import zio.blocks.schema.{Modifier, Schema}

case class RepoCreated(ownerId: String, repoName: String)
object RepoCreated {
  implicit val schema: Schema[RepoCreated] = Schema.derived[RepoCreated]
}

case class RepoListEntry(@Modifier.id id: String, ownerId: String, repoName: String)
object RepoListEntry {
  implicit val schema: Schema[RepoListEntry]         = Schema.derived[RepoListEntry]
  implicit val entityPath: EntityPath[RepoListEntry] = EntityPath.derived[RepoListEntry]
}

val spec = Projection[RepoListEntry]("repoListEntries")
  .from("repos")
  .routedBy[RepoCreated](_.ownerId)
  .on[RepoCreated]
  .custom((e, _) => ProjectionAction.Upsert(RepoListEntry(e.ownerId, e.ownerId, e.repoName)))

// spec.scope == ProjectionScope.CrossEntity(extractor)
```

The `routedBy` call tells the engine to extract a routing key from each
event. Events with the same key go to the same shard store, so querying
`engine.query(spec, "alice")` searches Alice's shard first.

**Routing modes:**

| Mode | Behavior | Scope derived |
|------|----------|---------------|
| `.routeToSelf` | Uses `ctx.entityId` as key | `PerEntity` |
| `.routedBy[E](_.field)` | Extracts key from event | `CrossEntity` |
| `.routeToAll` | All events go to same store | `Global` (if `isGlobal`) |

## Aggregate Projections

Global projections aggregate events from multiple sources into a single
row (or a few rows keyed by a grouping string). The engine applies atomic
counter updates:

```scala
import zio.blocks.projection.*
import zio.blocks.schema.{Modifier, Schema}

case class UserCreated(name: String, email: String)
object UserCreated {
  implicit val schema: Schema[UserCreated] = Schema.derived[UserCreated]
}
case class RepoCreated(ownerId: String, repoName: String)
object RepoCreated {
  implicit val schema: Schema[RepoCreated] = Schema.derived[RepoCreated]
}

case class DailyStats(@Modifier.id date: String, userCount: Int, repoCount: Int)
object DailyStats {
  implicit val schema: Schema[DailyStats]         = Schema.derived[DailyStats]
  implicit val entityPath: EntityPath[DailyStats] = EntityPath.derived[DailyStats]
}

val spec = Projection
  .global[DailyStats]("dailyStats")
  .from("users")
  .routeToAll
  .on[UserCreated]
  .aggregate(FieldUpdate.Increment("user_count", 1L))
  .from("repos")
  .routeToAll
  .on[RepoCreated]
  .aggregate(FieldUpdate.Increment("repo_count", 1L))
```

**FieldUpdate operations:**

| Operation | SQL translation | Description |
|-----------|----------------|-------------|
| `Set(field, value)` | `SET col = ?` | Replace field value |
| `Increment(field, by)` | `SET col = COALESCE(col,0) + ?` | Atomic increment |
| `Decrement(field, by)` | `SET col = COALESCE(col,0) - ?` | Atomic decrement |
| `Max(field, value)` | `SET col = MAX(COALESCE(col, ?), ?)` | Keep highest value |
| `Min(field, value)` | `SET col = MIN(COALESCE(col, ?), ?)` | Keep lowest value |

The `COALESCE` handles missing rows. The engine runs `INSERT OR IGNORE`
before any `UPDATE`, so incrementing a counter that doesn't exist yet
creates the row with value `1` (not `0 + 1`).

**Concurrent safety:** Counter operations are atomic at the SQL level. Ten
fibers each incrementing the same counter produce the correct total without
lost updates.

## Schema Evolution

When you change a projection entity's schema (add a field, rename a column),
the engine detects the mismatch and rebuilds the projection from events.

### How it works

1. On startup, `ProjectionEngine` computes `SchemaHash.compute[A]` for
   each spec's entity type. This is a SHA-256 hash of the schema structure
   (field names, types, order).
2. The hash is stored in `_projection_meta.schema_hash`.
3. If the stored hash doesn't match the current hash, the engine rebuilds:
   - Truncate the projection store
   - Replay all events from the EventStore through the spec's handlers
   - Store the new hash

```scala
import zio.blocks.projection.*
import zio.blocks.schema.{Modifier, Schema}

case class UserProfileV1(@Modifier.id id: String, name: String)
object UserProfileV1 {
  implicit val schema: Schema[UserProfileV1] = Schema.derived[UserProfileV1]
}

case class UserProfileV2(@Modifier.id id: String, name: String, email: String)
object UserProfileV2 {
  implicit val schema: Schema[UserProfileV2] = Schema.derived[UserProfileV2]
}

val hashV1 = SchemaHash.compute[UserProfileV1]
val hashV2 = SchemaHash.compute[UserProfileV2]
// hashV1 != hashV2 because V2 has an extra "email" field
```

### Lazy rebuild

Set `lazyRebuild = true` in `ProjectionEngineConfig` to defer rebuilds.
The engine marks specs needing rebuild but doesn't block startup. The first
query to a spec triggers its rebuild:

```scala
import zio.blocks.projection.ProjectionEngineConfig

val config = ProjectionEngineConfig(
  batchSize = 100,
  batchTimeout = zio.Duration.fromMillis(50),
  ringCapacity = 4096,
  rebuildParallelism = 4,
  lazyRebuild = true
)
```

### Migration shortcut

For simple `AddField` migrations (adding columns with defaults), the engine
uses `ALTER TABLE ADD COLUMN` instead of a full rebuild. This is much faster
for large projections:

```scala
import zio.blocks.schema.migration.Migration

// Assumes UserProfileV1 and UserProfileV2 are defined as above
val migration = Migration
  .newBuilder[UserProfileV1, UserProfileV2]
  .addField(_.email, "")
  .build

// Engine detects this is a simple AddField migration
// and uses ALTER TABLE instead of full rebuild
```

## Event Tagging and Migration

Event tags identify variant cases for serialization. By default, the tag is
the case class name (e.g., `"UserCreated"`). You can override with numeric
tags or rename cases across versions.

### String tags (default)

For a sealed trait `UserEvent` with cases `UserCreated` and `UserDeleted`,
the tags are `"UserCreated"` and `"UserDeleted"`.

### Numeric tags

Use the `@eventTag` annotation for stable numeric identifiers:

```scala
import zio.blocks.projection.eventTag
import zio.blocks.schema.{Modifier, Schema}

sealed trait UserEvent
object UserEvent {
  @eventTag(1)
  case class Created(name: String) extends UserEvent
  object Created {
    implicit val schema: Schema[Created] = Schema.derived[Created]
  }

  @eventTag(2)
  case class Deleted(userId: String) extends UserEvent
  object Deleted {
    implicit val schema: Schema[Deleted] = Schema.derived[Deleted]
  }

  implicit val schema: Schema[UserEvent] = Schema.derived[UserEvent]
}
```

### Tag aliases via Migration

When you rename a case, old events in the database still have the old tag.
Use `Migration.renameCase` to create an alias:

```scala
import zio.blocks.schema.migration.Migration
import zio.blocks.projection.TagResolver
import zio.blocks.schema.{Modifier, Schema}

sealed trait LoginEvent
object LoginEvent {
  case class UserLoggedIn(userId: String) extends LoginEvent
  object UserLoggedIn {
    implicit val loggedInSchema: Schema[UserLoggedIn] = Schema.derived
  }
  case class UserAuthenticated(userId: String) extends LoginEvent
  object UserAuthenticated {
    implicit val authSchema: Schema[UserAuthenticated] = Schema.derived
  }
  implicit val loginEventSchema: Schema[LoginEvent] = Schema.derived
}

val migration = Migration
  .newBuilder[LoginEvent, LoginEvent]
  .renameCase("UserLoggedIn", "UserAuthenticated")
  .build

val tagInfo = TagResolver.resolve[LoginEvent](migration)
// tagInfo.aliases == Map("UserLoggedIn" -> "UserAuthenticated")
// tagInfo.allTags == Set("UserLoggedIn", "UserAuthenticated")
```

The `TagInfo` object provides:

- `expandRequested(tags)` — returns the union of requested tags plus all
  their old aliases, so selective reads fetch both old and new events
- `isOldTag(tag)` — true if the tag was renamed
- `currentTagFor(oldTag)` — returns the current tag for an old tag
- `normalize(tag)` — maps old tags to current tags

### Transitive chains

If you rename `A → B` then `B → C`, the resolver collapses this to `A → C`.
All three tags are queryable.

### Startup warning

On startup, the `SQLiteEventStore` checks all distinct tags in the database
against the known tag set. Unknown tags produce a warning log, not an error.
This catches typos or events from a different schema version without crashing
the application.

## Testing

The projection module ships with in-memory implementations for unit tests.
No SQLite required.

### InMemoryProjectionStore

A `ProjectionStore[A]` backed by `Ref[Map[String, A]]`. Supports all
operations: insert, upsert, updateFields, delete, truncate, schema hash
tracking.

```scala
import zio.*
import zio.blocks.projection.*
import zio.blocks.projection.testing.InMemoryProjectionStore
import zio.blocks.schema.{Modifier, Schema}

case class UserProfile(@Modifier.id id: String, name: String, email: String)
object UserProfile {
  implicit val schema: Schema[UserProfile]         = Schema.derived[UserProfile]
  implicit val entityPath: EntityPath[UserProfile] = EntityPath.derived[UserProfile]
}

val test: ZIO[Any, Throwable, Unit] = for {
  store <- InMemoryProjectionStore.make[UserProfile]
  _     <- store.insert(UserProfile("u1", "Alice", "alice@example.com"))
  alice <- store.findById("u1")
  // alice == Some(UserProfile("u1", "Alice", "alice@example.com"))
} yield ()
```

### TestEngine

A simple test helper that auto-creates `InMemoryProjectionStore` per
projection and `Hub` per source. No manual `makeWithStores` wiring:

```scala
import zio.*
import zio.blocks.projection.*
import zio.blocks.projection.testing.TestEngine
import zio.blocks.schema.{Modifier, Schema}

case class UserProfile(@Modifier.id id: String, name: String, email: String)
object UserProfile {
  implicit val schema: Schema[UserProfile]         = Schema.derived[UserProfile]
  implicit val entityPath: EntityPath[UserProfile] = EntityPath.derived[UserProfile]
}
case class UserCreated(name: String, email: String)
object UserCreated {
  implicit val schema: Schema[UserCreated] = Schema.derived[UserCreated]
}

val projection = Projection[UserProfile]("userProfiles")
  .from("users").routeToSelf
  .on[UserCreated]
  .insert((e, ctx) => UserProfile(ctx.entityId, e.name, e.email))

val test: ZIO[Scope, Throwable, Unit] = for {
  engine <- TestEngine.make(projection)
  _      <- engine.append("user-1", UserCreated("Alice", "alice@example.com"))
  result <- engine.query(projection, "user-1")
  // result == Some(UserProfile("user-1", "Alice", "alice@example.com"))
} yield ()
```

### TestContext

Factory for `ProjectionContext` values in tests:

```scala
import zio.blocks.projection.testing.TestContext
import java.time.Instant

val ctx1 = TestContext.make(entityId = "user-1")
// ProjectionContext("user-1", now, 0, None)

val ctx2 = TestContext.makeWithSource(entityId = "user-1", sourceEntityId = "src-1")
// ProjectionContext("user-1", now, 0, Some("src-1"))

val ctx3 = TestContext.withSeq(entityId = "user-1", seq = 42L)
// ProjectionContext("user-1", now, 42, None)
```

## Configuration

### ProjectionEngineConfig

Controls batching, ring buffer size, rebuild behavior, and schema
evolution:

```scala
import zio.*
import zio.blocks.projection.ProjectionEngineConfig

val config = ProjectionEngineConfig(
  batchSize         = 100,       // events per batch in catch-up
  batchTimeout      = 50.millis, // max wait before flushing partial batch
  ringCapacity      = 4096,      // Hub capacity for live events
  rebuildParallelism = 4,        // concurrent rebuild fibers
  lazyRebuild       = false      // true = defer rebuilds to first query
)
```

| Parameter | Default | Description |
|-----------|---------|-------------|
| `batchSize` | 100 | Number of events processed per batch during catch-up |
| `batchTimeout` | 50ms | Maximum wait before flushing a partial batch |
| `ringCapacity` | 4096 | Capacity of the Hub ring buffer for live events |
| `rebuildParallelism` | 4 | Number of concurrent rebuild fibers |
| `lazyRebuild` | false | Defer schema rebuilds to first query |
| `evolution` | default | `SchemaEvolutionConfig` sub-config |

### TransactorCacheConfig

Controls the SQLite transactor cache:

```scala
import zio.blocks.projection.TransactorCacheConfig

val cacheConfig = TransactorCacheConfig(maxSize = 256)
```

| Parameter | Default | Description |
|-----------|---------|-------------|
| `maxSize` | 256 | Maximum number of cached SQLite connections |

The cache uses LRU eviction. Each cached transactor sets `PRAGMA journal_mode=WAL`
and `PRAGMA synchronous=NORMAL` on first open. The cache is `Scope`-managed:
all transactors close when the scope exits.

### Scope lifecycle

The `ProjectionEngine.make` and `TransactorCache.make` methods require
`ZIO[Scope, ...]`. This ensures resources are cleaned up when your
application shuts down:

```scala
import zio.*
import zio.blocks.projection.*

val app: ZIO[Scope, Throwable, Unit] = for {
  cache  <- TransactorCache.make()
  engine <- ProjectionEngine.makeWithConfig(
              ProjectionEngineConfig.default,
              /* specs... */
            )
  _ <- engine.start
} yield ()
// cache and engine cleaned up when scope closes
```

## API Reference

| Type | Package | Description |
|------|---------|-------------|
| `@Modifier.id` | `zio.blocks.schema.Modifier` | Annotation marking the entity ID field |
| `EntityPath[A]` | `zio.blocks.projection` | Folder name + ID field for a projection entity |
| `@path(name)` | `zio.blocks.projection` | Annotation to override the derived folder name |
| `@eventTag(n)` | `zio.blocks.projection` | Annotation for numeric event tags |
| `Projection[A]` | `zio.blocks.projection` | Defines a projection: name, schema, handlers, routing |
| `ProjectionAction[+A]` | `zio.blocks.projection` | Enum: Insert, Upsert, Update, Delete, Truncate, Noop |
| `FieldUpdate` | `zio.blocks.projection` | Enum: Set, Increment, Decrement, Max, Min |
| `ProjectionContext` | `zio.blocks.projection` | Event metadata: entityId, timestamp, seq, sourceEntityId |
| `EventEnvelope[+E]` | `zio.blocks.projection` | Event wrapper: seq, tag, event, timestamp, entityId |
| `EventStore[E]` | `zio.blocks.projection` | Trait: append, readFrom, readAll, subscribe |
| `SQLiteEventStore[E]` | `zio.blocks.projection` | SQLite-backed EventStore with tag migration |
| `ProjectionStore[A]` | `zio.blocks.projection` | Trait: insert, upsert, updateFields, delete, truncate, findById |
| `InMemoryProjectionStore[A]` | `zio.blocks.projection.testing` | In-memory ProjectionStore for tests |
| `TransactorCache` | `zio.blocks.projection` | LRU cache of SQLite Transactors |
| `TransactorCacheConfig` | `zio.blocks.projection` | Configuration for TransactorCache |
| `ProjectionEngine` | `zio.blocks.projection` | Orchestrates catch-up + live processing |
| `ProjectionEngineConfig` | `zio.blocks.projection` | Engine configuration (batching, rebuild, etc.) |
| `SchemaHash` | `zio.blocks.projection` | SHA-256 hash of Schema structure for evolution detection |
| `SchemaEvolution` | `zio.blocks.projection` | Hash check, rebuild, migration shortcut |
| `SchemaEvolutionConfig` | `zio.blocks.projection` | Evolution config (parallelism, lazy, migration shortcut) |
| `TagResolver` | `zio.blocks.projection` | Builds TagInfo from Schema + optional Migration |
| `TagInfo` | `zio.blocks.projection` | Alias map, all tags, old tag detection, value migration |
| `AggregateProjection` | `zio.blocks.projection` | Helpers for global aggregate specs with counters |
| `TestEngine` | `zio.blocks.projection.testing` | Simple test engine with auto-created stores and hubs |
| `TestProjectionEngine` | `zio.blocks.projection.testing` | Synchronous test engine (deprecated, use TestEngine) |
| `TestContext` | `zio.blocks.projection.testing` | Factory for ProjectionContext in tests |

### Projection methods

| Method | Description |
|--------|-------------|
| `Projection[A](name)` | Create a per-entity spec (requires `Schema[A]` + `EntityPath[A]`) |
| `Projection.global[A](name)` | Create a global spec (requires `Schema[A]`) |
| `.from(sourceName)` | Bind to a named event source |
| `.on[E]` | Register a handler for event type `E` |
| `.insert((E, ProjectionContext) => A)` | Handler: insert a new entity |
| `.update((E, ProjectionContext) => A)` | Handler: replace the entity |
| `.updateWithField(fieldName, (E, ProjectionContext) => Any)` | Handler: update a single field |
| `.delete` | Handler: delete the entity |
| `.custom((E, ProjectionContext) => ProjectionAction[A])` | Handler: arbitrary action |
| `.aggregate(FieldUpdate)` | Handler: atomic counter update |
| `.routedBy[E](E => String)` | Route events by extracted key |
| `.routeToSelf` | Route events by `ctx.entityId` |
| `.routeToAll` | Send all events to same store |
| `spec.scope` | Derived scope: `PerEntity`, `CrossEntity`, or `Global` |

### ProjectionEngine methods

| Method | Description |
|--------|-------------|
| `ProjectionEngine.make(specs*)` | Create engine with default config (requires `Scope`) |
| `ProjectionEngine.makeWithConfig(config, specs*)` | Create engine with custom config |
| `engine.start` | Start catch-up + live processing (requires `Scope`) |
| `engine.query(spec, entityId)` | Query an entity by ID |
| `engine.queryByName(specName, entityId)` | Query by spec name string |
| `engine.registerMigration(spec, migration)` | Register a migration for schema evolution |
| `engine.transactorCache` | Access the underlying TransactorCache |
