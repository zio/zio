---
id: runtime
title: "Runtime"
---

A `Runtime[R]` is capable of executing tasks within an environment `R`.

To run an effect, we need a `Runtime`, which is capable of executing effects.
Runtimes bundle a thread pool together with the environment that effects need.
