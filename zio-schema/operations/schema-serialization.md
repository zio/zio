# Serialization of the Schema Itself

> In distributed systems, we often need to move computations to data instead of moving data to computations. The data is big and the network is slow, so moving it is expensive and sometimes impossible due to the volume of data. So in distributed systems, we would like to move our functions to the data and apply the data to the functions and gather the results back.

In distributed systems, we often need to move computations to data instead of moving data to computations. The data is big and the network is slow, so moving it is expensive and sometimes impossible due to the volume of data. So in distributed systems, we would like to move our functions to the data and apply the data to the functions and gather the results back.

So we need a way to serialize our computations and send them through the network. In ZIO Schema, each schema itself has a schema, so we can treat the structure as pure data! we can serialize our schemas by calling the `serializable` method:

```scala
sealed trait Schema[A] {
  def serializable: Schema[Schema[A]]
}
```

By calling this method, we can get the schema of a schema. So we can serialize it and send it across the wire, and it can be deserialized on the other side. After deserializing it, we have a schema that is isomorphic to the original schema. So all the operations that we can perform on the original type `A`, we can perform on any value that is isomorphic to `A` on the other side.
