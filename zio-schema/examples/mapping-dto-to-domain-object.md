# Mapping DTO to Domain Object

> When we write layered applications, where different layers are decoupled from each other, we need to transfer data between layers. For example, assume we have a layer that has `Person` data type and it receives JSON string of type `PersonDTO` from another layer. We need to convert `PersonDTO` to `Person` and maybe vice versa.

When we write layered applications, where different layers are decoupled from each other, we need to transfer data between layers. For example, assume we have a layer that has `Person` data type and it receives JSON string of type `PersonDTO` from another layer. We need to convert `PersonDTO` to `Person` and maybe vice versa.

One way to do this is to write codec for `PersonDTO` and convert the JSON String to the `PersonDTO` and then convert `PersonDTO` to `Person`. This approach is not very convenient and we need to write some boilerplate code. With ZIO Schema we can simplify this process and write a codec for `Person` that uses a specialized schema for `Person`, i.e. `personDTOMapperSchema`, which describes `Person` data type in terms of transformation from `PersonDTO` to `Person` and vice versa. With this approach, we can directly convert the JSON string to `Person` in one step:

```scala

object MainApp extends ZIOAppDefault {

  case class PersonDTO(
                        firstName: String,
                        lastName: String,
                        birthday: (Int, Int, Int)
                      )

  object PersonDTO {
    implicit val schema: Schema[PersonDTO] = DeriveSchema.gen[PersonDTO]

    implicit val codec: JsonCodec[PersonDTO] = jsonCodec[PersonDTO](schema)
  }

  case class Person(name: String, birthdate: LocalDate)

  object Person {
    implicit val schema: Schema[Person] = DeriveSchema.gen[Person]

    val personDTOMapperSchema: Schema[Person] =
      PersonDTO.schema.transform(
        f = dto => {
          val (year, month, day) = dto.birthday
          Person(
            dto.firstName + " " + dto.lastName,
            birthdate = LocalDate.of(year, month, day)
          )
        },
        g = (person: Person) => {
          val fullNameArray = person.name.split(" ")
          PersonDTO(
            fullNameArray.head,
            fullNameArray.last,
            (
              person.birthdate.getYear,
              person.birthdate.getMonthValue,
              person.birthdate.getDayOfMonth
            )
          )
        }
      )
    implicit val codec: JsonCodec[Person] = jsonCodec[Person](schema)

    val personDTOJsonMapperCodec: JsonCodec[Person] =
      jsonCodec[Person](personDTOMapperSchema)
  }

  val json: String =
    """
      |{
      |   "firstName": "John",
      |   "lastName": "Doe",
      |   "birthday": [[1981, 07], 13]
      |}
      |""".stripMargin

  def run: zio.ZIO[Any, String, Any] =
    for {
      // Approach 1: Decode JSON String to PersonDTO and then Transform it into the Person object
      personDTO <- ZIO.fromEither(JsonCodec[PersonDTO].decodeJson(json))
      (year, month, day) = personDTO.birthday
      person1 = Person(
        name = personDTO.firstName + " " + personDTO.lastName,
        LocalDate.of(year, month, day)
      )
      _ <- ZIO.debug(
        s"person: $person1"
      )

      // Approach 2: Decode JSON string in one step into the Person object
      person2 <- ZIO.fromEither(
        JsonCodec[Person](Person.personDTOJsonMapperCodec).decodeJson(json)
      )
      _ <- ZIO.debug(
        s"person: $person2"
          )
    } yield assert(person1 == person2)
}
```

As we can see in the example above, the second approach is much simpler and more convenient than the first one.

The problem we solved in previous example is common in microservices architecture, where we transfer DTOs across the network. So we need to serialize and deserialize the data transfer objects.

In the next example, we will see how we can use schema migration, when we need to map data transfer object to domain object and vice versa. In this example, we do not require to serialize/deserialize any object, but the problem of mapping DTO to domain object persists.

In this example, similar to the previous one, we will define the schema for `Person` in terms of schema transformation from `PersonDTO` to `Person` and vice versa. The only difference is that we will utilize the `Schema#migrate` method to map `PersonDTO` to `Person`. This method returns `Either[String, PersonDTO => Either[String, Person]]`. If the migration is successful, we will receive `Right` with a function that converts `PersonDTO` to `Either[String, Person]`. Otherwise, if there is an error, we will receive `Left` along with an error message:

```scala

object MainApp extends ZIOAppDefault {

  case class PersonDTO(
                        firstName: String,
                        lastName: String,
                        birthday: (Int, Int, Int)
                      )

  object PersonDTO {
    implicit val schema: Schema[PersonDTO] = DeriveSchema.gen[PersonDTO]
  }

  case class Person(name: String, birthdate: LocalDate)

  object Person {
    implicit val schema: Schema[Person] = DeriveSchema.gen[Person]

    val personDTOMapperSchema: Schema[Person] =
      PersonDTO.schema.transform(
        f = dto => {
          val (year, month, day) = dto.birthday
          Person(
            dto.firstName + " " + dto.lastName,
            birthdate = LocalDate.of(year, month, day)
          )
        },
        g = (person: Person) => {
          val fullNameArray = person.name.split(" ")
          PersonDTO(
            fullNameArray.head,
            fullNameArray.last,
            (
              person.birthdate.getYear,
              person.birthdate.getMonthValue,
              person.birthdate.getDayOfMonth
            )
          )
        }
      )

    def fromPersonDTO(p: PersonDTO): IO[String, Person] =
      ZIO.fromEither(
        PersonDTO.schema
          .migrate(personDTOMapperSchema)
          .flatMap(_(p))
      )
  }

  def run: zio.ZIO[Any, String, Any] =
    for {
      personDTO <- ZIO.succeed(PersonDTO("John", "Doe", (1981, 7, 13)))
      person <- Person.fromPersonDTO(personDTO)
      _ <- ZIO.debug(s"person: $person")
    } yield ()

}
```
