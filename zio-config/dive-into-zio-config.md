# Dive Into ZIO Config

> __Note that this documentation is for 1.x series. For newer versions, please refer to [docs](https://github.com/zio/zio-config/tree/master/docs) section in GitHub.__

__Note that this documentation is for 1.x series. For newer versions, please refer to [docs](https://github.com/zio/zio-config/tree/master/docs) section in GitHub.__

## Describe the config by hand

We must fetch the configuration from the environment to a case class (product) in scala. Let it be `MyConfig`

```scala

```

```scala
case class MyConfig(ldap: String, port: Int, dburl: String)
```
To perform any action using zio-config, we need a configuration description.
Let's define a simple one.

```scala
val myConfig: Config[MyConfig] =
  (string("LDAP") zip int("PORT") zip string("DB_URL")).to[MyConfig]

 // Config[MyConfig]
```

To get a tuple,

```scala
val myConfigTupled: Config[(String, Int, String)] =
  (string("LDAP") zip int("PORT") zip string("DB_URL"))
```

## Fully automated Config Description

If you don't like describing your configuration manually, and rely on the names of the parameter in the case class (or sealed trait),
there is a separate module called `zio-config-magnolia`.

Note:  `zio-config-shapeless` is an alternative to `zio-config-magnolia` to support scala 2.11 projects.
It will be deprecated once we find users have moved on from scala 2.11.

```scala

val myConfigAutomatic = deriveConfig[MyConfig]
```

`myConfig` and `myConfigAutomatic` are same description, and is of the same type.

Refer to API docs for more explanations on [descriptor](https://javadoc.io/static/dev.zio/zio-config-magnolia_2.13/1.0.0-RC31-1/zio/config/magnolia/index.html#descriptor[A](implicitconfig:zio.config.magnolia.package.Descriptor[A]):zio.Config[A])
More examples on automatic derivation is in examples module of [zio-config](https://github.com/zio/zio-config)

## Read config from various sources

There are more information on various sources in [here](read-from-various-sources.md).

Below given is a simple example.

```scala
val map =
  Map(
    "LDAP" -> "xyz",
    "PORT" -> "8888",
    "DB_URL" -> "postgres"
  )

val source = ConfigProvider.fromMap(map)

source.load(myConfig)

```

### Documentations using Config

```scala
generateDocs(myConfig)
//Creates documentation (automatic)

val betterConfig =
  (string("LDAP") ?? "Related to auth" zip int("PORT") ?? "Database port" zip
    string("DB_URL") ?? "url of database"
   ).to[MyConfig]

generateDocs(betterConfig).toTable.toGithubFlavouredMarkdown
// Custom documentation along with auto generated docs
```

### Accumulating all errors

For any misconfiguration, the ReadError collects all of them with proper semantics: `AndErrors` and `OrErrors`.
Instead of directly printing misconfigurations, the `ReadError.prettyPrint` shows the path, detail of collected misconfigurations.

1. All misconfigurations of `AndErrors` are put in parallel lines.
```text
╥
╠══╗ 
║  ║ FormatError
║ MissingValue
``` 
2. `OrErrors` are in the same line which indicates a sequential misconfiguration

```text
╥
╠MissingValue
║
╠FormatError
```

Here is a complete example:

```text
   ReadError:
   ╥
   ╠══╦══╗
   ║  ║  ║
   ║  ║  ╠─MissingValue
   ║  ║  ║ path: var2
   ║  ║  ║ Details: value of type string
   ║  ║  ║ 
   ║  ║  ╠─MissingValue path: envvar3
   ║  ║  ║ path: var3
   ║  ║  ║ Details: value of type string
   ║  ║  ║ 
   ║  ║  ▼
   ║  ║
   ║  ╠─FormatError
   ║  ║ cause: Provided value is wrong, expecting the type int
   ║  ║ path: var1
   ║  ▼
   ▼
```

### Example of mapping keys

Now on, the only way to change keys is as follows:

```scala
  // mapKey is just a function in `Config` that pre-existed

  val config = deriveConfig[Config].mapKey(_.toUpperCase)
```

## Inbuilt support for pure-config

Many users make use of the label `type` in HOCON files to annotate the type of the coproduct.
Now on, zio-config has inbuilt support for reading such a file/string using `descriptorForPureConfig`.

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

ConfigProvider.fromHoconString(str).load(deriveConfig[AppConfig])

```

## The `to` method for easy manual configurations

```scala

final case class AppConfig(port: Int, url: String)

val config = Config.int("PORT").zip(Config.string("URL")).to[AppConfig]

```

## A few handy methods

### CollectAll

```scala

  final case class Variables(variable1: Int, variable2: Option[Int])

  val listOfConfig: List[Config[Variables]] =
    List("GROUP1", "GROUP2", "GROUP3", "GROUP4")
      .map(group => (Config.int(s"${group}_VARIABLE1") zip Config.int(s"${group}_VARIABLE2").optional).to[Variables])

  val configOfList: Config[List[Variables]] =
    Config.collectAll(listOfConfig.head, listOfConfig.tail: _*)

```

### orElseEither && Constant

```scala

sealed trait Greeting

case object Hello extends Greeting
case object Bye extends Greeting

val configSource = 
  ConfigProvider.fromMap(Map("greeting" -> "Hello"))

val config: Config[Greeting] = 
  Config.constant("Hello").orElseEither(Config.constant("Bye")).map(_.merge)

```
