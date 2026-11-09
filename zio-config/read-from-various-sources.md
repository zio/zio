# Read from various Sources

> zio-config supports various sources. Format-specific modules:

| Source | Module | Import |
| --- | --- | --- |
| HOCON / JSON | `zio-config-typesafe` | `zio.config.typesafe._` |
| YAML | `zio-config-yaml` | `zio.config.yaml._` |
| TOML | `zio-config-toml` | `zio.config.toml._` |
| XML (experimental) | `zio-config-xml` | `zio.config.xml.experimental._` |

```scala
import zio._, Config._, ConfigProvider._
import zio.config._, magnolia._
```

```scala
case class MyConfig(ldap: String, port: Int, dburl: String)
```

```scala
val myConfig =
  (string("LDAP") zip int("PORT") zip string("DB_URL")).to[MyConfig]

 // val automatedConfig = deriveConfig[MyConfig]; using zio-config-magnolia
```

## HOCON String

To enable HOCON source, you have to bring in `zio-config-typesafe` module.
There are many examples in examples module in zio-config.

Here is an quick example

```scala
import zio.config.typesafe._
import zio.config.magnolia._
```

```scala
case class SimpleConfig(port: Int, url: String, region: Option[String])

val automaticDescription = deriveConfig[SimpleConfig]

val hoconSource =
  ConfigProvider.fromHoconString(
      """
      {
        port : 123
        url  : bla
        region: useast
      }

      """
    )

val anotherHoconSource =
  ConfigProvider.fromHoconString(
      """
        port=123
        url=bla
        region=useast
      """
  )

hoconSource.load(deriveConfig[SimpleConfig])

// yielding SimpleConfig(123,bla,Some(useast))
```

## HOCON File

```scala
ConfigProvider.fromHoconFile(new java.io.File("fileapth"))
```

## Json

You can use `zio-config-typesafe` module to fetch json as well

```scala
val jsonString =
   """
   {
     "port" : "123"
     "url"  : "bla"
     "region": "useast"
   }

   """

ConfigProvider.fromHoconString(jsonString)
```

## Yaml

To enable YAML source, bring in the `zio-config-yaml` module.

```scala
import zio.config.yaml._

ConfigProvider.fromYamlString(
  """
  port: 123
  url: bla
  region: useast
  """
)
```

## Toml

To enable TOML source, bring in the `zio-config-toml` module.

```scala
import zio.config.toml._

ConfigProvider.fromTomlString(
  """
  port = 123
  url = "bla"
  region = "useast"
  """
)
```

You can also load from a file or path:

```scala
import zio.config.toml._

ConfigProvider.fromTomlFile(new java.io.File("application.toml"))
ConfigProvider.fromTomlPath(java.nio.file.Path.of("application.toml"))
```

TOML tables become nested keys, so the keys under a `[server]` table are read with `Config#nested`. TOML arrays become indexed keys, which `Config.listOf` reads as a list. To describe both, define a `Config` for a table that contains an array:

```scala
final case class TomlServer(host: String, ports: List[Int])

val serverConfig: Config[TomlServer] =
  (Config.string("host") zip Config.listOf("ports", Config.int))
    .nested("server")
    .map(pair => TomlServer(pair._1, pair._2))
```

Next, parse a TOML string that contains that table and load the config from it:

```scala
import zio.config.toml._

val serverToml: ConfigProvider =
  ConfigProvider.fromTomlString(
    """
    [server]
    host = "localhost"
    ports = [8080, 8443]
    """
  )

val server: IO[Config.Error, TomlServer] = serverToml.load(serverConfig)
```

By default, commas inside a string value are not treated as list separators. Pass `enableCommaSeparatedValueAsList = true` to `fromTomlString`, `fromTomlFile`, or `fromTomlPath` to split such values on commas when they are read as lists.

## Xml String

zio-config can read XML strings through the experimental `zio-config-xml` module. The parser is small and native. It does not support XML comments, and it has not been tested with complex data types.

Each attribute and each element's text becomes a key. The key path starts at the root tag, so `<config><aws region="us-east"/></config>` produces the key `config.aws.region`. An attribute and an element's text map to the same path, so `<database port="123">` and `<database><port>123</port></database>` both produce the key `config.database.port`. Repeated sibling elements with the same name produce the same key, so the last one wins. XML lists are not supported.

To read the XML below, define a `Config` for each element and nest them under the root tag:

```scala
import zio.config.xml.experimental._
import zio.Config

final case class Aws(region: String, account: String)

object Aws {
  val config: Config[Aws] = Config.string("region").zip(Config.string("account")).to[Aws]
}

final case class Database(port: Int, url: String)

object Database {
  val config: Config[Database] = Config.int("port").zip(Config.string("url")).to[Database]
}

final case class Configuration(aws: Aws, database: Database)

object Configuration {
  val config: Config[Configuration] =
    Aws.config.nested("aws").zip(Database.config.nested("database")).to[Configuration].nested("config")
}

val config =
  s"""
     |<config>
     |  <aws region="us-east" account="personal"></aws>
     |  <database port="123" url="some url"></database>
     |</config>
     |
     |""".stripMargin

val parsed = ConfigProvider.fromYamlString(config).load(Configuration.config)
```

### Indexed Map, Array datatype, and a some implementation notes

`zio-config` comes up with the idea of `IndexedFlat` allowing you to define indexed configs (see examples below).
However, the constructors of `IndexedFlat` is not exposed to the user for the time being, since it can conflate with some ideas in `zio.core` `Flat`,
and resulted in failures whenever `IndexedFlat` was converted to a `Flat` internally. Example: https://github.com/zio/zio-config/issues/1095

Therefore, some of these ideas around `Indexing` is  pushed back to `ZIO` and incorporated within the `Flat` structure.

See https://github.com/zio/zio/pull/7823 and https://github.com/zio/zio/pull/7891

These changes are to keep the backward compatibility of ZIO library itself.

#### What does it mean to users?
It implies, for sequence (or list) datatypes, you can use either `<nil>` or `""` to represent empty list in a flat structure.
See the below example where it tries to mix indexing into flat structure.
We recommend using `<nil>` over `""` whenever you are trying  to represent a real indexed format

Example:

```scala
import zio.config._, magnolia._

final case class Department(name: String, block: Int)

final case class Employee(departments: List[Department], name: String)
final case class Config(employees: List[Employee])

val map =
  Map(
    "employees[0].name" -> "jon",
    "employees[0].departments[0].name" -> "science",
    "employees[0].departments[0].block" -> "10",
    "employees[0].departments[1].name" -> "maths",
    "employees[0].departments[2].block" -> "11",
    "employees[1].name" -> "foo",
    "employees[1].departments" -> "<nil>",
  )
  

ConfigProvider.fromMap(map).load(deriveConfig[Config])

```

Although we support indexing within Flat, formats such as Json/HOCON/XML is far better to work with indexing,
and zio-config supports these formats making use of the above idea.

#### Another simple example of an indexed format

```scala

import zio.config._, magnolia._

final case class Employee(age: Int, name: String)

 val map = 
   Map(
     "department.employees[0].age" -> "10",
     "department.employees[0].name" -> "foo",
     "department.employees[1].age" -> "11",
     "department.employees[1].name" -> "bar",
     "department.employees[2].age" -> "12",
     "department.employees[2].name" -> "baz",
   )

val provider = ConfigProvider.fromMap(map)
val config = Config.listOf("employees", deriveConfig[Employee]).nested("department")
val result = provider.load(config)

```
