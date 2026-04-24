# Automatic Derivation of Config

> By bringing in `zio-config-magnolia` we  avoid all the boilerplate required to define the config. With a single import, `Config` is automatically derived.

By bringing in `zio-config-magnolia` we  avoid all the boilerplate required to define the config. With a single import, `Config` is automatically derived.

### Example

#### Config
```scala
sealed trait X

object X {
  case object A extends X
  case object B extends X
  case object C extends X
  case class  DetailsWrapped(detail: Detail) extends X
  
  case class Detail(firstName: String, lastName: String, region: Region)
  case class Region(suburb: String, city: String)
}

case class MyConfig(x: X)
```

#### AutoDerivation

```scala
// Setting up imports

```

```scala
// Defining different possibility of HOCON source

val aHoconSource =
  ConfigProvider
    .fromHoconString("x = A")
```

```scala
val bHoconSource =
  ConfigProvider
    .fromHoconString("x = B")
```

```scala
val cHoconSource =
  ConfigProvider
    .fromHoconString("x = C")
```

```scala
val dHoconSource =
  ConfigProvider
    .fromHoconString(
      s"""
         | x {
         |   DetailsWrapped {
         |    detail  {
         |      firstName : ff
         |      lastName  : ll
         |      region {
         |        city   : syd
         |        suburb : strath
         |     }
         |   }
         |  }
         |}
         |""".stripMargin
    )
```

```scala
// Let's try automatic derivation

aHoconSource.load(deriveConfig[MyConfig])
// res0: Right(MyConfig(A))

bHoconSource.load(deriveConfig[MyConfig])
// res0: Right(MyConfig(B))

cHoconSource.load(deriveConfig[MyConfig])
// res0: Right(MyConfig(C))

dHoconSource.load(deriveConfig[MyConfig])
// res0: Right(MyConfig(DetailsWrapped(Detail("ff", "ll", Region("strath", "syd")))))
```

**NOTE**

The fieldNames and class-names remain the same as that of case-classes and sealed-traits.

If you want custom names for your fields, use `name` annotation.

```

@name("detailsWrapped")
case class  DetailsWrapped(detail: Detail) extends X
```

```scala

deriveConfig[MyConfig].mapKey(toKebabCase)
```

With the above change`firstName` and `lastName` in the above HOCON example can be `first-name` and `last-name` 
respectively.

There are various ways in which you can customise the derivation of sealed traits. 
This is a bit involving, and more documentations will be provided soon.

### Documentation while automatic derivation

With `describe` annotation you can still document your config while automatically generating the config

```scala

@describe("This config is about aws")
case class Aws(region: String, dburl: DbUrl)
case class DbUrl(value: String)
```

This will be equivalent to the manual configuration of:

```scala
string("region").zip(string("dburl").to[DbUrl]).to[Aws] ?? "This config is about aws"
```

You could provide `describe` annotation at field level

Example:

```scala
case class Aws(@describe("AWS region") region: String, dburl: DbUrl)
```

This will be equivalent to the manual configuration of:

```scala
(string("region") ?? "AWS region" zip string("dburl").to[DbUrl]).to[Aws] ?? "This config is about aws"
```

### Custom Config

Every field in a case class should have an instance of `DeriveConfig` (and not `Config`) in order for the automatic derivation to work.
This doesn't mean the entire design of zio-config is typeclass based. For the same reason, the typeclass
`DeriveConfig` exists only in zio-config-magnolia.

As an example, given below is a case class where automatic derivation won't work, and result in a compile time error:
Assume that, `AwsRegion` is a type that comes from AWS SDK.

```

case class Execution(time: AwsRegion, id: Int)
```

In this case, `deriveConfig[Execution]` will give us the following descriptive compile error.

```
magnolia: could not find Descriptor.Typeclass for type <outside.library.package>.AwsRegion
  in parameter 'time' of product type <your.packagename>.Execution
```

This is because zio-config-magnolia failed to derive an instance of Descriptor for AwsRegion.

In order to provide implicit instances, following choices are there

```

implicit val awsRegionConfig: DeriveConfig[Aws.Region] =
  DeriveConfig[String].map(string => AwsRegion.from(string))

```

Now `deriveConfig[Execution]` compiles.

Custom descriptors are also needed in case you use value classes to describe your configuration. You can use them
together with automatic derivation and those implicit custom descriptors will be taken automatically into account

```scala

final case class AwsRegion(value: String) extends AnyVal {
  override def toString: String = value
}

object AwsRegion {
  implicit val descriptor: DeriveConfig[AwsRegion] = 
    DeriveConfig[String].map(AwsRegion(_))
}
```

### Where to place these implicits ?

If the types are owned by us, then the best place to keep implicit instance is the companion object of that type.

```scala
final case class MyAwsRegion(value: AwsRegion)

object MyAwsRegion {
  implicit val awsRegionDescriptor: DeriveConfig[MyAwsRegion] =
    DeriveConfig[String]
      .map(
        string => MyAwsRegion(AwsRegion.from(string))
      ) ?? "value of type AWS.Region"
}
```

However, sometimes, the types are owned by an external library.

In these situations, better off place the implicit closer to where we call the automatic derivation.
Please find the example in `magnolia` package in examples module.

### Change Keys (CamelCase, kebab-case etc)

Please find the examples in ChangeKeys.scala in magnolia module to find how to manipulate keys in an automatic derivation such as being able to specify keys as camelCase, kebabCase or snakeCase in the source config.

## Scala3 Autoderivation
Works just like scala-2.12 and scala-2.13.
If possible, we will make this behaviour consistent in scala-2.12 and scala-2.13 in future versions of zio-config.

#### Example:

The name of the sealed trait itself is skipped completely by default.
However, if you put a `name` annotation on top of the sealed-trait itself,
then it becomes part of the config. 

The name of the case-class should be available in config-source, 
and by default it should the same name as that of the case-class.

```scala
sealed trait A
case class B(x: String) extends A
case class C(y: String) extends A

case class Config(a: A) 
```

With the above config, `deriveConfig[A]` can read following source.

```scala
{
  "a" : {
   "B" : {
      "x" : "abc"
     }
   }
  }
}

// or a.B.x="abc", if your source is just property file
```

However, if you give `name` annotation for A, the name of the sealed trait
should be part of the config too. This is rarely used.

```scala
@name("aaaaa")
sealed trait A
case class B(x: String) extends A
case class C(y: String) extends A

case class Config(a: A) 
```

With the above config, `deriveConfig[A]` can read following source.

```scala
{
  "a" : {
    "aaaaa" : 
      "B" : {
        "x" : "abc"
     }
   }
  }
}
```
Similar to scala-2.x, you can give name annotations to any case-class as well (similar to scala 2.x)

#### Example:

```scala
sealed trait A

@name("b")
case class B(x: String) extends A
case class C(y: String) extends A

case class Config(a: A) 
```

In this case, the config should be

```scala
{
  "a" : {
   "b" : {
      "x" : "abc"
     }
   }
  }
}
```

### Config derivation via `derives` keyword

Scala 3 has introduced `derives` keyword to derive typeclasses without the need of explicitely declaring the implicit / given.
This syntax can be enabled for `zio.Config` by importing `zio.config.magnolia.*` and then using the `derives` keyword on the type that needs to be derived:

```scala 3

sealed trait A
case class B(x: String) extends A
case class C(y: String) extends A

case class MyConfig(a: A) derives Config

// And then simply summon the `Config[MyConfig]` instance as you would normally:
val cfg = ZIO.config[MyConfig]
```

### No guaranteed behavior for scala-3 enum yet
With the current release, there is no guaranteed support of scala-3 enum. 
Use `sealed trait` and `case class` pattern.

### No support for recursive config in auto-derivation, but we can make it work

There is no support for auto-deriving recursive config with scala-3.
Please refer to examples in magnolia package (not in the main examples module)

### Custom Keys

With zio-config-3.x, the only way to change keys is as follows:

```scala
// mapKey is just a function in `Config` that pre-existed

val config = deriveConfig[Config].mapKey(_.toUpperCase)
```

## Inbuilt support for pure-config

Many users make use of the label `type` in HOCON files to annotate the type of the coproduct. 
Just put `@nameWithLabel()` in sealed trait name. By default the `label` name is `type`, but you can provide
any custom name `@nameWithLabel("foo")`

```scala

@nameWithLabel("type")
sealed trait X
case class A(name: String) extends X
case class B(age: Int) extends X

case class AppConfig(x: X)

val str =
  s"""
   x : {
     type = A
     name = jon
   }
  """

read(deriveConfig[AppConfig] from ConfigProvider.fromHoconString(str))
```
