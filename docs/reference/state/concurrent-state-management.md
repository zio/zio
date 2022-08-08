---
id: concurrent
title: "Concurrent State Management"
sidebar_label: "Concurrent"
---

In concurrent programming, we can categorize state management into two general approaches:

1. **[Global Shared State](global-shared-state.md)**- ZIO provides the `Ref` data type for managing global states that are shared across all fibers and can be updated and accessed concurrently.

2. **[Fiber-local State](fiber-local-state.md)**— ZIO provides two data types called `Fiberref` and `ZState` that can be used to maintain the state in a concurrent environment, but each fiber has its own state. Their states are not shared between other fibers. This prevents them from clobbering each other's state.
