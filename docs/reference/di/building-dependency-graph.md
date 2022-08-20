---
id: building-dependency-graph
title: "Building Dependency Graph"
---

We have two options to build a dependency graph:
1. [Manual layer construction](manual-layer-construction.md)
2. [Automatic layer construction](automatic-layer-construction.md)

The first method uses ZIO's composition operators such as horizontal (`++`) and vertical (`>>>`) compositions. The second one uses metaprogramming and automatically creates the dependency graph at compile time.
