---
id: index
title: "Best Practices"
description: "Recommended patterns for ZIO's typed error channel: model domain errors as ADTs, use union types for precision, keep defects untyped, and avoid reflexive logging."
keywords:
  - "Best Practices"
  - "Algebraic Data Types"
  - "Union Types"
  - "Unexpected Errors"
  - "Error Logging"
---

These pages collect the design principles that make ZIO's typed error channel pay off in real applications. Following them keeps error signatures precise, prevents unexpected errors from leaking into the type system, and avoids logging patterns that hide errors rather than surface them.

- **[Algebraic Data Types](algebraic-data-types.md)** — model domain errors as sealed traits and case classes so the compiler enforces exhaustive handling.
- **[Union Types](union-types.md)** — use Scala 3 union types to compose unrelated error types without a shared supertype, keeping error signatures precise.
- **[Don't Type Unexpected Errors](dont-type-unexpected-errors.md)** — use `orDie` and `refineOrDie` to separate recoverable errors from application-killing defects rather than widening the error type.
- **[Don't Reflexively Log Errors](logging-errors.md)** — rely on ZIO's typed error propagation instead of logging errors at every call site.
