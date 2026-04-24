# Read from various Sources

> zio-config supports various sources.

zio-config supports various sources.

```scala

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

## Yaml FIle

Similar to Hocon source, we have `ConfigProvider.fromYamlString`

```scala

ConfigProvider.fromYamlString

```

## Xml String

zio-config can read XML strings. Note that it's experimental with a dead simple native xml parser, 
Currently it cannot XML comments, and has not been tested with complex data types, which will be fixed in the near future.

```scala

final case class Configuration(aws: Aws, database: Database)

object Configuration {
  val config: Config[Configuration] =
    Aws.config.nested("aws").zip(Database.config.nested("database")).to[Configuration].nested("config")

  final case class Aws(region: String, account: String)

  object Aws {
    val config: Config[Aws] = Config.string("region").zip(Config.string("account")).to[Aws]
  }
  final case class Database(port: Int, url: String)

  object Database {
    val config: Config[Database] = Config.int("port").zip(Config.string("url")).to[Database]
  }
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
  

ConfigProvider.fromMap(map).load(derivedConfig[Config])

```

Although we support indexing within Flat, formats such as Json/HOCON/XML is far better to work with indexing,
and zio-config supports these formats making use of the above idea.

#### Another simple example of an indexed format

```scala

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
