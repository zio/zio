# Environment

> FtpClient & Blocking
---

FtpClient & Blocking
---

ZIO Ftp use Environment type parameter to required a blocking context. Since (s)ftp java client don't provide any async client, 
we are lifting all blocking (effectful) functions into a blocking execution context by using `zio.blocking.effectBlocking()`.

Since we never expose the underlying ftp client, you can only use a ZIO wrapper `FtpAccessors[_]`

``` scala 
trait FtpAccessors[+A] {
 def stat(path: String): ZIO[Blocking, IOException, Option[FtpResource]]
 def ls(path: String): ZStream[Blocking, IOException, FtpResource]
}
```

All provided functions require a `Blocking` context.

There is already predefined Blocking context defined in ZIO `zio.blocking.Blocking.live`

How to provide Blocking Environment ?
---

Your main entrypoint should extend `zio.App` 

```scala

// Provide default environment(s) context such as (Blocking, Clock, Console,...) since it is provided by zio.App which extend zio.DefaultRuntime
object MyApp extends App {
    val program: ZIO[Blocking, IOException, Unit] = ???
    
    final def run(args: List[String]): ZIO[ZEnv, Nothing, Int] = program
  }
```

or you can simply provide your own blocking context 

```scala

val program: ZIO[Blocking, IOException, Unit] = ???
// Blocking environment is not anymore required
val run: ZIO[Any, IOException, Unit] = program.provideLayer(Blocking.live)
```

Resources
---
- [ZIO Effect Blocking](https://zio.dev/docs/overview/overview_creating_effects#blocking-synchronous-side-effects)
- [ZIO environment by John de goes](http://degoes.net/articles/zio-environment)
- [ZIO environment meets constructor based-dependency injection](https://blog.softwaremill.com/zio-environment-meets-constructor-based-dependency-injection-6a13de6e000)
