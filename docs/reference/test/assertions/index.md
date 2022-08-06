---
id: index
title: "Introduction"
---

Assertions are used to make sure that the assumptions on computations are exactly what we expect them to be. They are _executable checks_ for a property that must be true in our code. Also, they can be seen as a _specification of a program_ and facilitate understanding of programs.

We have two types of methods for writing test assertions:
1. **[Smart Assertions](smart-assertions.md)**— This is a unified syntax for asserting both ordinary values and ZIO effects using `assertTrue` method.
2. **[Classic Assertions](classic-assertions.md)**— This one is the classic old-fashioned way of asserting ordinary values (`assert`) and ZIO effects (`assertZIO`).
