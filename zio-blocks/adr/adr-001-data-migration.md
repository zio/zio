# ADR-001: SQL Data Migration Architecture

> Accepted

## Status

## Context

The `zio-blocks` library needed an online data migration system that allows evolving database schemas from application code. The system builds on two existing foundations:

- `zio.blocks.schema.migration.Migration[A, B]` for typed schema evolution
- `zio.blocks.sql.Repo[E, ID]` for database access

Key requirements:

- No hand-written SQL in migrations
- No XML or Liquibase-style migration files
- Support for both PostgreSQL and SQLite
- Safe cutover for large tables with zero data loss

The module is Scala 3 only.

## Decisions

### Decision 1: Three Execution Models

**Decision:** Provide three execution tiers, each suited to a different scale of change:

- **A-Tiny** (`TinyMigrator`): Simple startup DDL via `Transactor.transact`. For schema-only changes that are fast and non-blocking.
- **A-Small** (`SmallMigrator`): Queue-based batch worker for moderate tables. Lifecycle: `init()` then loop `processBatch()` then `complete()`.
- **B-Large** (`LargeMigrator`): Incremental worker with safe completion protocol. Lifecycle: `init()` then `fence()` then `drain()` then `complete()`.

**Rationale:** Different migration scales need different guarantees. Tiny is lightweight and runs at startup. Small is transactional and batch-oriented. Large has distributed coordination and a safe cutover protocol. A single execution model would either be too heavy for simple DDL changes or too weak for large-table migrations.

### Decision 2: Coalesced Dirty-Key Queue

**Decision:** The queue table is a single shared table with columns `(id, op, payload)` — `id` is the source primary key (TEXT, PRIMARY KEY so duplicate keys coalesce via upsert), `op` records the capturing operation (`I`/`U`/`D`), and `payload` optionally stores the row JSON on deletes. There is no per-migration queue table or `migration_id` column; the source table remains authoritative. Keys enter the queue either manually (`QueueTable.enqueue`) or automatically via opt-in capture triggers (`captureTriggers = true` installs them in `init()`); trigger installation is idempotent and requires PG 14+ on PostgreSQL.

**Rationale:** Bounded queue storage. A single keyed table keeps the worker protocol simple and lets manual enqueue and trigger capture share one mechanism. The source row is always reread at processing time for `I`/`U` entries, avoiding stale data; `D` entries carry the payload needed to resolve the target key. Coalescing on the primary key means multiple updates to the same row between processing cycles collapse into a single queue entry. Manual enqueue keeps the default behavior dependency-free; triggers let producers write normally without application-code changes, but must not be enabled when the migrator writes to the same physical table it captures from (self-requeue loop).

### Decision 3: Source-Authoritative Replay

**Decision:** Workers claim a dirty key and, in one transaction: reread the source row (without taking a source-row lock), apply the migration, write the target, and remove the key. A missing source row at processing time means delete the target row.

**Rationale:** Prevents deadlock with concurrent source writers. The worker holds only the dirty-key claim, not the source row lock. A source writer can proceed without waiting for the worker to finish reading. The source table is the single source of truth, so rereading at processing time guarantees the worker acts on the latest state.

### Decision 4: Target Strategy Orthogonality

**Decision:** `TargetStrategy` is a thin enum (`InPlace` | `ShadowTable(suffix)`) orthogonal to the execution model. ShadowTable uses `CREATE TABLE IF NOT EXISTS ... LIKE ... INCLUDING ALL` and a DDL swap at completion.

**Rationale:** Decouples "how to write" from "when to write." The same worker code works for both strategies. InPlace updates rows directly in the source table. ShadowTable writes to a separate table and swaps it in at cutover. Callers choose the strategy based on whether the schema change is backward compatible.

### Decision 5: Safe Completion Protocol (B-Large)

**Decision:** State machine: `Initialized -> Fenced -> Drained -> Completed`. Fenced state prevents new source writes. Drain processes remaining queue items. Complete swaps the shadow table.

**Rationale:** Guarantees zero data loss during cutover. Queue persistence across restarts enables crash recovery. State is checked at each transition with `require()` guards. The protocol ensures every row modified between `init()` and `complete()` is migrated, even if the application crashes and restarts mid-migration.

### Decision 6: PostgreSQL-Specific Features

**Decision:** `QueueTable.dequeue` uses `SELECT ... FOR UPDATE SKIP LOCKED` for concurrent workers on PostgreSQL. Shadow table uses `CREATE TABLE ... LIKE ... INCLUDING ALL`. DDL swap is atomic.

**Rationale:** PostgreSQL's row-level locking and DDL transactionality enable safe multi-worker migrations. SQLite uses `dequeueSQLite` without `SKIP LOCKED` since SQLite serializes writes via `BEGIN IMMEDIATE` (single-writer model). The dialect split is necessary because the two databases have fundamentally different concurrency models.

### Decision 7: ID Type Parameters with Equality Constraint

**Decision:** `LargeMigrator` and `SmallMigrator` accept separate `ID1` and `ID2` type parameters, but construction requires evidence that `ID1 =:= ID2`. The queue stores V1 IDs.

**Rationale:** The two type parameters document the roles (source-repo ID vs target-repo ID) and keep repo signatures precise, while the `ID1 =:= ID2` constraint makes delete propagation type-safe: when a source row disappears between dequeue and read, the worker deletes the corresponding target row using the same key, reinterpreted through the evidence (`ev.substituteCo`) rather than a cast. Genuinely different key types (for example, `Int` to `Long`) are not supported by the built-in migrators; callers needing that must provide a custom conversion path outside this module.

### Decision 8: Pre-Commit State Enforcement

**Decision:** `complete()` requires `Drained` state. `drain()` requires `Fenced` state. `fence()` requires `Initialized` state. `drain()` only transitions to `Drained` if not paused.

**Rationale:** Prevents misordered lifecycle calls. The pause guard prevents `complete()` from being called when the queue is not empty (paused mid-drain). The stall valve turns an unbounded hot loop (empty batches with pending items, e.g. a stuck concurrent worker holding row locks) into a clear failure after 100 consecutive rounds; `SmallMigrator.complete()` likewise refuses while items are pending. State transitions are enforced at runtime with `require()` checks, making invalid sequences fail fast rather than producing subtle data inconsistencies.

### Decision 9: Failure Policy

**Decision:** On worker transaction failure: rollback, retain the dirty key, return failure to caller. No automatic retry, no dead-letter queue.

**Rationale:** Simple, predictable semantics. The caller decides retry policy. No silent data loss. No configuration surface for retry counts, backoff strategies, or dead-letter routing. The dirty key remains in the queue, so the row can be retried by calling `processBatch()` or `drain()` again.

### Decision 10: Scope Exclusions

**Decision:** The following are explicitly out of scope:

- No schema-diff or automatic migration derivation (Derivation.scala removed per review)
- No CLI (MigrationCLI removed per review)
- No JSON or full-row outbox payloads
- No external brokers or LISTEN/NOTIFY
- No composite keys, configurable retry, configuration files, third-party dialects, or automatic target-schema DDL generation

**Rationale:** Keeps scope narrow. `Migration[A, B]` provides the typed transformation. `Repo` provides the database surface. The data migration module is a thin orchestration layer. Callers supply the migration logic; the module handles queueing, worker coordination, and cutover.

## Consequences

### Positive

- Clean separation of concerns: `Migration[A, B]` (what) vs execution model (how) vs target strategy (where)
- Queue-based dirty-key tracking is storage-bounded and crash-recoverable
- Safe completion protocol prevents data loss during cutover
- ID type flexibility supports realistic migration scenarios
- No hand-written SQL, no XML, no external dependencies beyond the database

### Tradeoffs

- Missing-source-row delete relies on ID type compatibility (`ID1 =:= ID2`). For genuinely different ID types, a conversion function would be needed.
- No automatic retry requires caller-side error handling. Callers must implement their own retry logic if desired.
- PostgreSQL-only `SKIP LOCKED` limits full multi-worker support. SQLite is single-worker due to its write serialization model.
- State is in-memory (not persisted). Restart resets the state machine, though the queue table persists across restarts for crash recovery.
- Per-key writer blocking during worker processing is accepted. A source writer waits while the worker holds the dirty-key claim. No tuning configuration is provided for this.

## References

- [Data Migration Reference Documentation](../reference/data-migration.md)
- PR #1534 (implementation)
