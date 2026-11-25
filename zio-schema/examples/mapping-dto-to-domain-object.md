# How to Map a DTO to a Domain Object

> Describe a domain type as a transformation of a DTO schema, then decode DTO-shaped JSON straight into the domain type.

In this guide, we read a `Person` from JSON that arrives in the shape of a `PersonDTO`, a data transfer object (DTO) with different fields. We describe `Person` as a transformation of the `PersonDTO` schema once, and then use that one schema to decode DTO-shaped JSON straight into `Person`, to encode a `Person` back into the DTO shape, and to convert `PersonDTO` values that are already in memory.

## The Problem

Layered applications keep the types they send over the network apart from the types their business logic uses. A service might receive people as JSON in the shape of `PersonDTO`, with the name split into two fields and the birthday as a tuple, while its domain type `Person` holds one name and a `LocalDate`. Converting between the two takes a pair of hand-written functions:

```scala
import java.time.LocalDate

final case class PersonDTO(firstName: String, lastName: String, birthday: (Int, Int, Int))
final case class Person(name: String, birthdate: LocalDate)

def toPerson(dto: PersonDTO): Person = {
  val (year, month, day) = dto.birthday
  Person(s"${dto.firstName} ${dto.lastName}", LocalDate.of(year, month, day))
}

def toDTO(person: Person): PersonDTO = {
  val names = person.name.split(" ")
  PersonDTO(
    names.head,
    names.last,
    (person.birthdate.getYear, person.birthdate.getMonthValue, person.birthdate.getDayOfMonth)
  )
}
```

Every place that reads a person then needs two steps: decode a `PersonDTO` with a codec for the DTO, and remember to call `toPerson`. A codec derived for `Person` itself cannot help, because it expects the fields `name` and `birthdate`, not the fields of the DTO.

With ZIO Schema, the conversion becomes part of a schema for `Person`. Any codec built from that schema reads and writes the DTO shape and hands us a `Person` directly.

## Prerequisites

The examples use the JSON codec, which also brings in schema derivation:

```scala
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-schema"      % "1.9.2",
  "dev.zio" %% "zio-schema-json" % "1.9.2"
)
```

On Scala 2, schema derivation also needs `scala-reflect` at compile time, as the [Installation](../index.md#installation) section shows.

## Defining the DTO and the Domain Type

Both types get a derived schema in their companion objects:

```scala
import java.time.LocalDate

import zio.schema.{DeriveSchema, Schema}
import zio.schema.codec.JsonCodec

final case class PersonDTO(firstName: String, lastName: String, birthday: (Int, Int, Int))

object PersonDTO {
  implicit val schema: Schema[PersonDTO] = DeriveSchema.gen[PersonDTO]
}

final case class Person(name: String, birthdate: LocalDate)

object Person {
  implicit val schema: Schema[Person] = DeriveSchema.gen[Person]
}
```

## Describing Person in Terms of PersonDTO

`Schema#transform` turns a schema for one type into a schema for another, given a function in each direction. Applied to the schema of `PersonDTO`, it gives a schema for `Person` that has the structure of the DTO:

```scala
val personFromDTO: Schema[Person] =
  PersonDTO.schema.transform(
    dto => {
      val (year, month, day) = dto.birthday
      Person(s"${dto.firstName} ${dto.lastName}", LocalDate.of(year, month, day))
    },
    (person: Person) => {
      val names = person.name.split(" ")
      PersonDTO(
        names.head,
        names.last,
        (person.birthdate.getYear, person.birthdate.getMonthValue, person.birthdate.getDayOfMonth)
      )
    }
  )
```

We keep `personFromDTO` as a plain value rather than an implicit one, so the derived `Person.schema` stays the default schema for `Person`, and we pass `personFromDTO` explicitly wherever the DTO shape is needed. The reverse function splits the name at spaces, so a name without a space becomes both the first and the last name. [Transforming Schemas](../operations/transforming-schemas.md) covers `Schema#transform` and `Schema#transformOrFail`, which lets a conversion fail with an error message.

## Decoding DTO-Shaped JSON into Person

In the JSON shape of `PersonDTO`, the three-element tuple appears as nested arrays, `[[year, month], day]`:

```scala
val json: String =
  """{"firstName":"John","lastName":"Doe","birthday":[[1981,7],13]}"""
```

A JSON codec built from `personFromDTO` decodes this JSON straight into a `Person`:

```scala
JsonCodec.jsonCodec(personFromDTO).decodeJson(json)
// res1: Either[String, Person] = Right(
//   value = Person(name = "John Doe", birthdate = 1981-07-13)
// )
```

The codec derived from `Person.schema` expects the fields of `Person`, so it rejects the same input:

```scala
JsonCodec.jsonCodec(Person.schema).decodeJson(json)
// res2: Either[String, Person] = Left(value = ".name(missing)")
```

## Encoding Person in the DTO Shape

The same schema works in the other direction. Encoding a `Person` with it applies the reverse function and writes the fields of the DTO:

```scala
println(
  JsonCodec.jsonCodec(personFromDTO).encodeJson(Person("Jane Roe", LocalDate.of(1990, 1, 2)), None)
)
// {"firstName":"Jane","lastName":"Roe","birthday":[[1990,1],2]}
```

## Converting DTO Values in Memory

When a `PersonDTO` value is already in memory, for example because another layer of the application passes it in, no codec is involved. `Schema#migrate` builds a conversion function from one schema to another. It returns a `Left` with an error message when the structures of the two schemas cannot be reconciled, and otherwise a function that converts each value:

```scala
val dtoToPerson: Either[String, PersonDTO => Either[String, Person]] =
  PersonDTO.schema.migrate(personFromDTO)
```

Both schemas describe the same record structure, so the migration step changes nothing, and `personFromDTO` performs the mapping when it reads the result:

```scala
dtoToPerson.flatMap(convert => convert(PersonDTO("John", "Doe", (1981, 7, 13))))
// res4: Either[String, Person] = Right(
//   value = Person(name = "John Doe", birthdate = 1981-07-13)
// )
```

[Schema Migration](../operations/schema-migration.md) explains how migrations handle schemas whose structures differ, such as an older version of a DTO.

## Going Further

- [Transforming Schemas](../operations/transforming-schemas.md) describes `Schema#transform`, `Schema#transformOrFail`, and related operations.
- [JSON Codecs](../derivations/codecs/json.md) covers the JSON codec and its configuration.
- [Combining Different Encoders](combining-different-encoders.md) uses the same schema with several codecs.
