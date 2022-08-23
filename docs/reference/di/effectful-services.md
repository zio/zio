---
id: effectful-layer-construction
title: "Effectful Layer Construction"
---

We can create `ZLayer` from any `ZIO` effect by using `ZLayer.fromZIO`/`ZLayer.apply` constructor.

For example, assume we have a `ZIO` effect that reads the application config from a file, we can create a layer from that:

```scala mdoc:compile-only
import zio._

case class AppConfig(poolSize: Int)
  
object AppConfig {

  private def loadConfig : Task[AppConfig] = 
    ZIO.attempt(???) // loading config from a file
    
  val layer: TaskLayer[AppConfig] = 
    ZLayer(loadConfig)  // or ZLayer.fromZIO(loadConfig)
} 
```
