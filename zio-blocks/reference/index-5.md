# Resource Management & Dependency Injection

> Resource management and dependency injection are fundamental to building reliable, maintainable applications. ZIO Blocks provides three complementary types that work together to eliminate common lifetime bugs while enabling powerful composition patterns: **Scope** provides compile-time safe resource boundaries, **Resource** encapsulates acquisition and cleanup with automatic finalization, and **Wire** describes dependency graphs with type-safe construction recipes. Together, they form a cohesive system for managing object lifecycles, preventing resource leaks, and building dependency-injected architectures.

## Introduction

Resource management and dependency injection are fundamental to building reliable, maintainable applications. ZIO Blocks provides three complementary types that work together to eliminate common lifetime bugs while enabling powerful composition patterns: **Scope** provides compile-time safe resource boundaries, **Resource** encapsulates acquisition and cleanup with automatic finalization, and **Wire** describes dependency graphs with type-safe construction recipes. Together, they form a cohesive system for managing object lifecycles, preventing resource leaks, and building dependency-injected architectures.

## Overview

These three types solve the fundamental problem of managing resources and dependencies in concurrent, long-lived applications:

**[Scope](./scope.md)** is the foundation — it provides a compile-time safe boundary that prevents resources from escaping their intended lifetime. Using path-dependent types, Scope ensures that values allocated in one scope cannot accidentally be used in another scope, catching lifetime violations at compile time rather than causing runtime bugs.

**[Resource](./resource.md)** builds on Scope to describe how to acquire and finalize resources. Rather than executing immediately, a Resource is a lazy recipe that composes naturally with `map`, `flatMap`, and `zip`. When allocated within a scope, finalizers run automatically in LIFO order, ensuring cleanup happens even when errors occur.

**[Wire](./wire.md)** brings it all together by describing how to construct services and their dependencies. The Wire macro automatically handles dependency resolution, cycle detection, and AutoCloseable registration, letting you declaratively specify a dependency graph that the compiler validates.

### How They Work Together

The typical flow is:

1. **Define** dependencies using `Wire.shared[T]` or `Wire.unique[T]` — the macro inspects constructor parameters and generates a wire
2. **Compose** wires together using `Resource.from[App](wire1, wire2, ...)` — builds the dependency graph
3. **Allocate** within a scope using `scope.allocate(resource)` — acquires resources and registers finalizers
4. **Use** scoped values via the `$` accessor — compile-time ensures they can't escape
5. **Cleanup** happens automatically when the scope exits — finalizers run in reverse order (LIFO)

### Common Patterns

**Shared Singletons** — Use `Wire.shared[T]` for expensive resources (database connections, thread pools) that should be created once and reused across the application.

**Per-Request Instances** — Use `Wire.unique[T]` for request-scoped state that should be fresh for each request or operation.

**Manual Construction** — Use `Wire.Shared.fromFunction` or `Wire.Unique.fromFunction` when macro derivation doesn't fit your use case (custom initialization logic, special parameters).

**Resource Composition** — Use `Resource.map`, `Resource.flatMap`, and `Resource.zip` to build complex dependency chains where later resources depend on earlier ones.

### Integration Points

- **Wire** uses **Resource** to manage lifecycles of constructed services
- **Resource** uses **Scope** for finalization and scoped value boundaries
- Both **Wire** and **Resource** produce values that are usable only within a **Scope** context
