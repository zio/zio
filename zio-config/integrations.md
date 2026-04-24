# Integrations

> `zio-config` is also integrated with `enumeratum`, `cats`, `scalaz`, `aws-sdk`, `zio-aws`, `refined` etc.
Note that only a few of them is documented here. `refined` is already discussed under `automatic-validations`.

## Integration with other libraries

`zio-config` is also integrated with `enumeratum`, `cats`, `scalaz`, `aws-sdk`, `zio-aws`, `refined` etc.
Note that only a few of them is documented here. `refined` is already discussed under `automatic-validations`.

#### Enumeratum

Many applications rely on this beautiful library https://github.com/lloydmeta/enumeratum.
Zio-config can directly load it from enumeratum's `enum` without relying on auto-derivation (and rely on Enumeratum's macro indirectly witha additional features).

```scala

 sealed trait Greeting extends EnumEntry

  object Greeting extends Enum[Greeting] {

    val values = findValues

    case object Hello extends Greeting
    case object GoodBye extends Greeting
    case object Hi extends Greeting
    case object Bye extends Greeting

  }

  // Load using zio-config

  val mapProvider =
    ConfigProvider.fromMap(Map(
      "greeting" -> "Hello"
    ))

  val config =
    `enum`(Greeting).nested("greeting")

  val pgm: IO[Error, Greeting] =
    mapProvider.load(config)
    
  // Returns Hello  
    

```

#### Scalaz/Cats

Highly polymorphic code end up relying on
typeclasses, and zio-config provides instances for `Config`.

This is a simple example to showcase the capability.

```scala

 // Across the application, there can be various effect types, but there is only one addition!
 def add[F[_]: Applicative, A: Monoid](primary: F[A], secondary: F[A]): F[A] =
    primary.<*>(Applicative[F].map(secondary)(secondary => (primary: A) => primary.mappend(secondary)))
    
 // Now even `Config` can take part in this addition given the values of config parameters should be Monoid,
 // instead of using native `zip` and separately implementing addition for various types
 val configResult = add(Config.int("marks1"), Config.int("marks2")))
 
 ConfigProvider.fromMap(Map("marks1" -> "10", "marks2" -> "20")).load(configResult) // returns 30
 

```

In addition to it, it can also load cats/scalaz specific datatypes

```scala

  val config: Config[Maybe[Int]] = maybe(Config.int("age"))
  
```

Have a look at modules of zio-config to know about other integrations such as `aws`, `zio-aws` etc
