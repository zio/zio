# ZIO NioScheduler Implementation Decisions

## Decision Log

### 2026-04-03: Initial Implementation Approach

**Decision**: Implement the NioScheduler as a separate executor class rather than modifying ZScheduler.

**Rationale**:
- Allows users to choose between work-stealing (ZScheduler) and least-loaded (NioScheduler) approaches
- Preserves the existing, well-tested ZScheduler implementation
- Makes it easier to benchmark and compare the two approaches

**Alternatives Considered**:
1. Add a configuration flag to ZScheduler to switch algorithms - Rejected due to complexity
2. Replace ZScheduler entirely - Rejected as too risky for an experimental feature

### 2026-04-03: Parameter Naming

**Decision**: Accept `autoBlocking` parameter in constructor but don't implement it initially.

**Rationale**:
- Maintains API consistency with ZScheduler
- Allows for future implementation without breaking changes
- The core scheduling algorithm is the focus of this implementation

### 2026-04-03: Work Stealing as Fallback

**Decision**: Include work stealing as a fallback mechanism when workers are idle.

**Rationale**:
- Pure least-loaded can leave workers idle when there's work available
- Combines benefits of both approaches:
  - Low contention during normal operation (least-loaded)
  - No starvation when some workers finish early (work stealing)
- The Nio Rust runtime also has a similar hybrid approach

### 2026-04-03: Queue Implementation

**Decision**: Use `RingBufferPow2[Runnable]` for local queues (256 capacity).

**Rationale**:
- Consistent with ZScheduler implementation
- Power-of-2 sizing enables fast modulo via bit masking
- 256 elements provides good balance between memory and capacity

### 2026-04-03: Worker Thread Design

**Decision**: Workers extend `Thread` and implement `BlockContext`.

**Rationale**:
- Consistent with ZScheduler
- `BlockContext` allows Scala's `Await.ready` to work correctly
- When blocking is detected, the worker spawns a replacement
