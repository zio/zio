---
id: fiberid
title: "FiberId"
---

`FiberId` is the identity of a [Fiber](fiber.md), described by a globally unique sequence number and the time when it began life:
  * `id` — unique monotonically increasing sequence number `0,1,2,...`, derived from an atomic counter
  * `startTimeSeconds` — UTC time seconds, derived from `java.lang.System.currentTimeMillis / 1000`
