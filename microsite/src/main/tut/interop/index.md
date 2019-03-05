---
layout: docs
position: 5
section: interop
title:  "Interop"
---

# Interop

ZIO provides the ability to interoperate with other parts of the broader ecosystem, including:

 - **Cats Effect** — In a separate module that is not part of core, ZIO has instances for the Cats Effect library, which allow you to use ZIO with any libraries that rely on Cats Effect (including FS2, Doobie, Http4s).
 - **Future** — In a separate module that is not part of core, ZIO has conversion between ZIO data types (like `IO` and `Fiber`) and Scala concurrent data types like `Future`.
 — **Java** — ZIO has an in-development Java facade that allows Java developers to write ZIO-powered applications in a way that's more idiomatic for Java development.
 - **Javascript** — ZIO has full compatibility with Scala.js.
 - **Monix** — In a separate module that is not part of core, ZIO has conversion between ZIO data types and Monix data types.
 - **Reactive Streams** - In a separate module that is not part of core, ZIO has conversion from ZIO Streams and Sinks to Reactive Streams Procucers and Consumers.
 - **Scalaz 7.x** — In a separate module that is not part of core, ZIO has instances of `Monad` and other type classes for the ZIO data types.
 - **Scalaz 8** — Scalaz 8 depends on ZIO, and contains instances for all ZIO data types inside the library. No additional modules are needed.

Explore the sections above to learn how easy it is to integrate ZIO with whatever libraries or platforms you are already using. 
