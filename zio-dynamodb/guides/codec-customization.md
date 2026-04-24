# Codec Customization

> ```scala
sealed trait TrafficLight
final case class Green(rgb: Int) extends TrafficLight 
final case class Red(rgb: Int) extends TrafficLight
final case class Box(trafficLightColour: TrafficLight)
```

## Sealed trait members that are case classes

```scala
sealed trait TrafficLight
final case class Green(rgb: Int) extends TrafficLight 
final case class Red(rgb: Int) extends TrafficLight
final case class Box(trafficLightColour: TrafficLight)
```

The default encoding for `Box(Green(42))` is:

`Map(trafficLightColour -> Map(String(Green) -> Map(String(rgb) -> Number(42))))`

Here an intermediate map is used to identify the member of `TrafficLight` using the member class name ie `Map(String(Green) -> Map(...))`

## Sealed trait members that are case objects

```scala
sealed trait TrafficLight
case object GREEN extends TrafficLight 
case object RED extends TrafficLight
final case class Box(trafficLightColour: TrafficLight)
```

The default encoding for `Box(GREEN)` is:

`Map(trafficLightColour -> Map(String(GREEN) -> Null))`

Here an intermediate map is used to identify the member of `TrafficLight` ie `Map(String(GREEN) -> Null)`
Note that the `Null` is used as in this case we do not care about the value.

# Customising encodings via annotations
Encodings can be customised through the use of the following annotations `@discriminatorName` and `@fieldName`.
These annotations are useful when working with a legacy DynamoDB database.

The `@discriminatorName` encodings does not introduce another map for the purposes of identification but rather adds another 
discriminator field to the attribute Map.

Concrete examples of using the `@discriminatorName` and `@field` annotations can be seen below.

## Sealed trait members that are case classes

```scala
@discriminatorName("light_type")
sealed trait TrafficLight
final case class Green(rgb: Int) extends TrafficLight
@caseName("red_traffic_light")
final case class Red(rgb: Int) extends TrafficLight
final case class Amber(@fieldName("red_green_blue") rgb: Int) extends TrafficLight
final case class Box(trafficLightColour: TrafficLight)
```

encoding for an instance of `Box(Green(42))` would be:

`Map(trafficLightColour -> Map(String(rgb) -> Number(42), String(light_type) -> String(Green)))`

We can specify the field name used to identify the case class through the `@discriminatorName` annotation. The discriminator
encoding removes the intermediate map and inserts a new field with a name specified by discriminator annotation and a
value that identifies the member which defaults to the class name.

This can be further customised using the `@caseName` annotation - encoding for an instance of `Box(Red(42))` would be:

`Map(trafficLightColour -> Map(String(rgb) -> Number(42), String(light_type) -> String(red_traffic_light)))`

The encoding for case class field names can also be customised via `@fieldName` - encoding for an instance of `Box(Amber(42))` would be:

`Map(trafficLightColour -> Map(String(red_green_blue) -> Number(42), String(light_type) -> String(Amber)))`

## Sealed trait members that are all case objects

```scala
sealed trait TrafficLight
case object GREEN extends TrafficLight 
@caseName("red_traffic_light")
case object RED extends TrafficLight
final case class Box(trafficLightColour: TrafficLight)
```

We can get a more compact and intuitive encoding of trait members that are case objects by using the `@simpleEnum`
annotation which encodes to just a value that is the member name. This annotation doesn't need to be added explicitly 
since this is done by the ZIO Schema macro automatically.
Encoding for an instance of `Box(GREEN)` would be:

`Map(trafficLightColour -> String(GREEN))`

This can be further customised by using the `@caseName` annotation again - encoding for `Box(RED)` would be

`Map(trafficLightColour -> String(red_traffic_light))`

## No disriminator codecs using @noDiscriminator annotation

```scala
@noDiscriminator
sealed trait TrafficLight
@caseName("blue")
final case object Blue extends TrafficLight
final case object Purple extends TrafficLight 
final case class Green(rgb: Int, i: Int) extends TrafficLight
@caseName("red_traffic_light") // this annotation is ignored in the context of @noDiscriminator
final case class Red(rgb: Int, j: Int) extends TrafficLight
final case class Amber(@fieldName("red_green_blue") rgb: Int) extends TrafficLight
final case class Box(trafficLightColour: TrafficLight)
```

This primarily useful when working with legacy DynamoDB databases where a discriminator field is not present (some DynamoDB mapping libraries allow users to create codecs with no discriminators or tags to disambiguate each sum type case). 

WARNING! - this leads to the inefficiency of having to try each case and checking for success, and also forces the dangerous assumption that all the sum type cases will be different when encoded. When decoding if there are ambiguities amongst the codecs for the sum type instances this is handled gracefully by returning a Left of a DynamoDBError.DecodingError

Mapping for `Box(Blue)` would be `Map(trafficLightColour -> String(blue))`

Mapping for `Box(Amber(42))` would be `Map(trafficLightColour -> Map(String(red_green_blue) -> Number(42))`

## Recommendations
For greenfield development it is recommended to use:
- the default encoding which uses an intermediate map (see the above default encoding) - (note this mapping does not work with top level sum types as it requires an intermediate map and partition keys must be scalar values) *or*
- `@discriminatorName` encoding ([see above](#customising-encodings-via-annotations)) if you would like a more compact encoding - note this *must* be used for top level sum types
- for deeply nested hierarchies avoid a top level sum type altogether and have a concrete top level class that uses only product and sum types - see [High Level API Data Modelling and Limitations](data-modelling#deep-oo-style-hierarchies-are-not-supported---modelling-data-using-product-and-sum-types)
