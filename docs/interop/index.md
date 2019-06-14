---
id: interop_index
title:  "Summary"
---

ZIO provides the ability to interoperate with other parts of the broader ecosystem, including:

 - **Cats & Cats Effect** — In a separate module that is not part of core, ZIO has instances for the Cats and Cats Effect libraries, which allow you to use ZIO with any libraries that rely on Cats and Cats Effect (including FS2, Doobie, Http4s).
 - **Future** — ZIO has built-in conversion between ZIO data types (like `ZIO` and `Fiber`) and Scala concurrent data types like `Future`.
 - **JavaScript** — ZIO has first-class support for Scala.js.
 - **Monix** — In a separate module that is not part of core, ZIO has conversion between ZIO data types and Monix data types.
 - **Reactive Streams** - In a separate module that is not part of core, ZIO has conversion from ZIO Streams and Sinks to Reactive Streams Producers and Consumers.
 - **Scalaz 7.x** — In a separate module that is not part of core, ZIO has instances of `Monad` and other type classes for the ZIO data types.
 - **Scalaz 8** — Scalaz 8 depends on ZIO, and contains instances for all ZIO data types inside the library. No additional modules are needed.

Explore the sections above to learn how easy it is to integrate ZIO with whatever libraries or platforms you are already using. 
