# Writing Our First Test

> Any object that implements the `ZIOSpecDefault` trait is a runnable test. So to start writing tests we need to extend `ZIOSpecDefault`, which requires a `Spec`:

Any object that implements the `ZIOSpecDefault` trait is a runnable test. So to start writing tests we need to extend `ZIOSpecDefault`, which requires a `Spec`:

```scala

object HelloWorldSpec extends ZIOSpecDefault {
  def spec = 
    suite("HelloWorldSpec")(
      ??? // all tests go here
    )
}
```

:::note
In order to have runnable tests, the `ZIOSpecDefault` trait must be extended by an **object** that implements the `spec` method. If we extend this trait in a class, the test runner will not be able to find the tests.
:::

`ZIOSpecDefault` is very similar in its logic of operations to `ZIOAppDefault`. Instead of providing one `ZIO` application at the end of the world, we provide a suite that can be a tree of other suites and tests. 

```scala

object HelloWorld {
  def sayHello: ZIO[Any, IOException, Unit] =
    Console.printLine("Hello, World!")
}

object HelloWorldSpec extends ZIOSpecDefault {
  def spec = suite("HelloWorldSpec")(
    test("sayHello correctly displays output") {
      for {
        _      <- sayHello
        output <- TestConsole.output
      } yield assertTrue(output == Vector("Hello, World!\n"))
    }
  )
}
```

In the example above, our test involved the effect of printing to the console, but we didn't have to do anything differently in our test. Also note that the `helloWorld` method in the above program does not actually print a string to the console instead writes it to a buffer for testing.
