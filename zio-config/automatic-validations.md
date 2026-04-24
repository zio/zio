# Automatic Validations

> By bringing in `zio-config-refined` module, you get validations for your config parameters almost for free. 
`zio-config` elegantly integrates with `Refined` library for you to achieve this with same ergonomics.

By bringing in `zio-config-refined` module, you get validations for your config parameters almost for free. 
`zio-config` elegantly integrates with `Refined` library for you to achieve this with same ergonomics.

If you are not familiar with `refined` library, refer https://github.com/fthomas/refined.

There are various ways that zio-config can interact with refined library. 
Take a look at `zio.config.refined` package.

```scala

```

A few examples are given below.

## Basic Example

```scala

 case class Jdbc(username: NonEmptyString, password: NonEmptyString)

 val jdbc: Config[Jdbc] =
   (refineType[NonEmptyString]("username") zip
     refineType[NonEmptyString]("password")).to[Jdbc]

 ConfigProvider.fromMap(Map("username" -> "", "password" -> "")).load(jdbc)
```

## Direct Interaction with Refined Predicates

If you need to directly interact with `Predicate`s (ex: `NonEmpty`), then
`refine[A, P]` method is useful.

```scala

 type NonEmptyString = String Refined NonEmpty
 
 val refinedConfig: Config[NonEmptyString] = 
   refineType[NonEmptyString]("USERNAME")
  
 // Another way of doing it is
 val urlConfig: Config[Refined[String, Url]] =
   refine[String, Url]("URL")
   
 // refineType takes a fully formed type (String Refined NonEmpty) where as refine allows you to play with the predicate directly (NonEmpty)  
```

## Derive from existing Config

Of various methods available in `zio.config.refined` package, 
the most interesting one is being able to get a refined type out of an already derived Config.
This shows the composable nature of zio-config. 

Take a look at the below example

```scala

 case class MyConfig(url: String, port: Int)

 val configs: Config[List[MyConfig]] =
   Config.listOf("databases", deriveConfig[MyConfig])

 // A list of database configs, such that size should be greater than 2.
 val databaseList: Config[Refined[List[MyConfig], Size[Greater[W.`2`.T]]]] =
   refine[Size[Greater[W.`2`.T]]](configs)
```

## Auto-Derivation and Refined

You can also use auto derivations with refined.

```scala

object RefinedReadConfig extends App {
  case class RefinedProd(
    ldap: Refined[String, NonEmpty],
    port: Refined[Int, GreaterEqual[W.`1024`.T]],
    dbUrl: Option[Refined[String, NonEmpty]]
  )

  val configMap =
    Map(
      "LDAP"     -> "ldap",
      "PORT"     -> "1999",
      "DBURL"   -> "ddd"
    )

  val result =
    ConfigProvider.fromMap(configMap).load(deriveConfig[RefinedProd].mapKey(_.toUpperCase))

  // RefinedProd(ldap,1999,Some(ddd))
}
```
