---
layout: docs
position: 7
section: overview
title:  "Basic Concurrency"
---

# {{page.title}}

ZIO's concurrency is built on _fibers_, which are lightweight green threads implemented by the ZIO runtime.

Unlike operating system threads, fibers consume almost no memory, have growable and shrinkable stacks, consume resources beyond memory only when doing active work, and will be garbage collected automatically if they are inactive and unreachable.

Fibers are scheduled by the ZIO runtime and will cooperatively yield to each other, which enables multitasking even when operating in a single-threaded environment (like Javascript, or even the JVM when configured with one thread).