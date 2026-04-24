# ZIO Prelude Abstraction Diagrams

> ```mermaid
classDiagram
  Absorption~A~ <|-- DistributiveAbsorption~A~
  Absorption~A~ <|-- Noncontradiction~A~
  Absorption~A~ <|-- ExcludedMiddle~A~
  ExcludedMiddle~A~ <|-- Involution~A~
  Noncontradiction~A~ <|-- Involution~A~
  class Absorption~A~{
    () or(=> A, => A): A
    () and(=> A, => A): A
  }
  class DistributiveAbsorption~A~{
    Boolean
    Set[A]
  }
  class Noncontradiction~A~{
    () complement(=> A): A
    () bottom: A
  }
  class ExcludedMiddle~A~{
    () complement(=> A): A
    () top: A
  }
  class Involution~A~{
    Boolean
  }
```

## Absorption

```mermaid
classDiagram
  Absorption~A~ <|-- DistributiveAbsorption~A~
  Absorption~A~ <|-- Noncontradiction~A~
  Absorption~A~ <|-- ExcludedMiddle~A~
  ExcludedMiddle~A~ <|-- Involution~A~
  Noncontradiction~A~ <|-- Involution~A~
  class Absorption~A~{
    () or(=> A, => A): A
    () and(=> A, => A): A
  }
  class DistributiveAbsorption~A~{
    Boolean
    Set[A]
  }
  class Noncontradiction~A~{
    () complement(=> A): A
    () bottom: A
  }
  class ExcludedMiddle~A~{
    () complement(=> A): A
    () top: A
  }
  class Involution~A~{
    Boolean
  }
```

## Associative

```mermaid
classDiagram
  Associative~A~ <|-- Commutative~A~
  Associative~A~ <|-- Idempotent~A~
  Associative~A~ <|-- Identity~A~
  Identity~A~ <|-- PartialInverse~A~
  PartialInverse~A~ <|-- Inverse~A~
  class Associative~A~{
    Either[E, A: Associative]
    F[A: Associative]: Derive[_, Associative]
    First[A]
    Last[A]
    NonEmptyChunk[A]
    NonEmptyList[A]
    These[A: Associative, B: Associative]
    ❨T1: Associative, ..., T22: Associative❩
    Validation[E, A: Associative]
    ZNonEmptySet[A, B: Associative]

    () combine(=> A, => A): A
  }
  class Commutative~A~{
    And
    F[A: Commutative]: Derive[_, Commutative]
    Either[E: Commutative, A: Commutative]
    Or
    Map[K, V: Commutative]
    Max[A: Ord]
    Max[Boolean]
    Max[Byte/Char/Double/Float/Int/Long/Short]
    Min[A: Ord]
    Min[Boolean]
    Min[Byte/Char/Double/Float/Int/Long/Short]
    NonEmptySet[A]
    Option[A: Commutative]
    Prod[Boolean]
    Prod[Byte/Char/Double/Float/Int/Long/Short]
    Set[A]
    Sum[Boolean]
    Sum[Byte/Char/Double/Float/Int/Long/Short]
    These[A: Commutative, B: Commutative]
    ❨T1: Commutative, ..., T22: Commutative❩
    Validation[E, A: Commutative]
    ZSet[A, B: Commutative]
    ZNonEmptySet[A, B: Commutative]

    () commute: Commutative[A]
  }
  class Idempotent~A~{
    And
    F[A: Idempotent]: Derive[_, Idempotent]
    Or
    Map[K, V: Idempotent]
    Max[Boolean]
    Max[Byte/Char/Double/Float/Int/Long/Short]
    Min[Boolean]
    Min[Byte/Char/Double/Float/Int/Long/Short]
    NonEmptySet[A]
    Option[A: Idempotent]
    Prod[Boolean]
    Set[A]
    Sum[Boolean]
    These[A: Idempotent, B: Idempotent]
    ❨T1: Idempotent, ..., T22: Idempotent❩
    Validation[E, A: Idempotent]
    ZSet[A, B: Idempotent]
    ZNonEmptySet[A, B: Idempotent]

    () combineIdempotent(=> A, => A)(Equal[A]): A
    () idempotent(Equal[A]): Idempotent[A]
  }
  class Identity~A~{
    F[A: Identity]: Derive[_, Identity]
    Chunk[A]
    Either[E, A: Identity]
    List[A]
    Map[K, V: Associative]
    Max[Boolean]
    Max[Byte/Char/Double/Float/Int/Long/Short]
    Min[Boolean]
    Min[Byte/Char/Double/Float/Int/Long/Short]
    Option[A: Associative]
    String
    ❨T1: Identity, ..., T22: Identity❩
    Validation[E, A: Identity]
    Vector[A]
    ZSet[A, B: Associative]

    () identity: A
  }
  class PartialInverse~A~{
    F[A: PartialInverse]: Derive[_, PartialInverse]
    Prod[Byte/Char/Double/Float/Int/Long/Short]
    ❨T1: PartialInverse, ..., T22: PartialInverse❩
    () inverseOption(=> A, => A): Option[A]
  }
  class Inverse~A~{
    And
    F[A: Inverse]: Derive[_, Inverse]
    Or
    Prod[Boolean]
    Set[A]
    Sum[Boolean]
    Sum[Byte/Char/Double/Float/Int/Long/Short]
    ❨T1: Inverse, ..., T22: Inverse❩

    () inverse(=> A, => A): A
  }
```

## AssociativeBoth

```mermaid
classDiagram
  AssociativeBoth~F<_>~ <|-- CommutativeBoth~F<_>~
  AssociativeBoth~F<_>~ <|-- IdentityBoth~F<_>~
  class AssociativeBoth~F<_>~{
    Exit[E, +*]
    Fiber[E, +*]
    NonEmptyChunk[+*]
    ZSink[R, E, I, L, +*]
    ZStream[R, E, +*]

    () both[A,B](=> F[A], => F[B]): F[(A,B)]
  }
  class CommutativeBoth~F<_>~{
    Chunk[+*]
    Exit[E, +*]
    Id[+*]
    List[+*]
    NonEmptyChunk[+*]
    Option[+*]
    AndF[Schedule[R, E, +*]]
    OrF[Schedule[R, E, +*]]
    Vector[+*]
    ZIO[R, E, +*]
    Failure[ZIO[R, E, +*]]
    ZLayer[R, E, +*]
    ZManaged[R, E, +*]
    Failure[ZManaged[R, E, +*]]
    ZSink[R, E, I, L, +*]
    ZStream[R, E, +*]
  }
  class IdentityBoth~F<_>~{
    Chunk[+*]
    Config[+*]
    Either[L, +*]
    Failure[Either[+*, R]]
    Option[+*]
    Optional[+*]
    Future[+*]
    Id[+*]
    List[+*]
    STM[R, E, +*]
    Try[+*]
    Vector[+*]

    () any: F[Any]
  }
```

## AssociativeCompose

```mermaid
classDiagram
  AssociativeCompose~=>:[-_, +_]~ <|-- IdentityCompose~=>:[-_, +_]~
  AssociativeCompose~=>:[-_, +_]~ <|-- BothCompose~=>:[-_, +_]~
  AssociativeCompose~=>:[-_, +_]~ <|-- EitherCompose~=>:[-_, +_]~
  BothCompose <|-- ApplicationCompose~=>:[-_, +_]~
  class AssociativeCompose~=>:[-_, +_]~{
    () compose[A, B, C](B =>: C, A =>: B): A =>: C
  }
  class IdentityCompose~=>:[-_, +_]~{
    Function[-*, +*]
    URIO[-*, +*]

    () identity[A]: A =>: A
  }
  class BothCompose~=>:[-_, +_]~{
    URIO[-*, +*] [ :*: = Tuple2 ]
  
    (type) :*:[+_, +_]
    () fromFirst[A]: (A :*: Any) =>: A
    () fromSecond[B]: (Any :*: B) =>: B
    () toBoth[A, B, C](A =>: B)(A =>: C): A =>: (B :*: C)
  }
  class ApplicationCompose~=>:[-_, +_]~{
    Function[-*, +*] [ :*: = Tuple2 ; -->: = Function ]
    
    (type) -->:[-_, +_]
    () application[A, B]: ((A -->: B) :*: A) =>: B
    () curry[A, B, C]((A :*: B) =>: C): A =>: (B -->: C)
    () uncurry[A, B, C](A =>: (B -->: C)): (A :*: B) =>: C
  }
  class EitherCompose~=>:[-_, +_]~{
    Function[-*, +*] [ :+: = Either ]
    URIO[-*, +*] [ :+: = Either ]
    
    (type) :+:[+_, +_]
    () toLeft[A]: A =>: (A :+: Nothing)
    () toRight[B]: B =>: (Nothing :+: B)
    () fromEither[A, B, C](=> A =>: C)(=> B =>: C): (A :+: B) =>: C
  }
```

## AssociativeEither

```mermaid
classDiagram
  AssociativeEither~F<_>~ <|-- CommutativeEither~F<_>~
  AssociativeEither~F<_>~ <|-- IdentityEither~F<_>~
  class AssociativeEither~F<_>~{
    Either[L, +*]
    Exit[E, +*]
    Fiber[E, +*]
    Schedule[R, E, +*]
    Try[+*]
    ZLayer[R, E, +*]
    ZManaged[R, E, +*]
    
    () either[A, B](=> F[A], => F[B]): F[Either[A, B]]
  }
  class CommutativeEither~F<_>~{
    Future[+*]
    ZIO[R, E, +*] 
    ZSink[R, E, I, L, +*]
    ZStream[R, E, +*]
    Equal[-*]
  }
  class IdentityEither~F<_>~{
    Option[+*]
    Equal[-*]
    Hash[-*]
    Ord[-*]

    () none: F[Nothing]
  }
```

## AssociativeFlatten

```mermaid
classDiagram
  AssociativeFlatten~F<+_>~ <|-- IdentityFlatten~F<+_>~
  class AssociativeFlatten~F<+_>~{
    Map[K, +*]
    
    () flatten[A](F[F[A]]): F[A]
  }
  class IdentityFlatten~F<+_>~{
    Cause[+*]
    Chunk[+*]
    Either[L, +*]
    Exit[E, +*]
    Future[+*]
    Id[+*]
    List[+*]
    NonEmptyChunk[+*]
    Option[+*]
    Try[+*]
    Vector[+*]
    ZIO[R, E, +*]
    ZManaged[R, E, +*]
    ZStream[R, E, +*]

    () any: F[Any]
  }
```

# DistributiveProd

```mermaid
classDiagram
  DistributiveProd~A~ <|-- Annihilation~A~
  class DistributiveProd~A~{
    Cause[A]
    ParSeq[Unit, A]
    
    () Sum: Associative[Sum[A]]
    () Prod: Associative[Prod[A]]
    () sum(=> A, => A): A
    () prod(=> A, => A): A
  }
  class Annihilation~A~{
    Byte/Char/Double/Float/Int/Long/Short
  }
```

## Equal

```mermaid
classDiagram
  Equal~-A~ <|-- Hash~-A~
  Equal~-A~ <|-- PartialOrd~-A~
  PartialOrd~-A~ <|-- Ord~-A~
  class Equal~A~{
    Chunk[A: Equal]
    Either[A: Equal, B: Equal]
    Exit[E: Equal, A: Equal]
    F[A: Equal]: DeriveEqual[_]
    List[A: Equal]
    NonEmptyChunk[A: Equal]
    NonEmptyList[A: Equal]
    Option[A: Equal]
    ParSeq[A: Equal]
    These[A: Equal, B: Equal]
    Try[A: Equal]
    ❨T1: Equal, ..., T22: Equal❩
    Validation[E, A: Equal]
    Vector[A: Equal]
    ZNonEmptySet[A, B: Equal]
    ZSet[A, B: Equal]

    () both[B](=> Equal[B]): Equal[(A, B)]
    () bothWith[B, C](=> Equal[B])(C => (A, B)): Equal[C]
    () contramap[B](B => A): Equal[B]
    () either[B](=> Equal[B]): Equal[Either[A, B]]
    () eitherWith[B, C](=> Equal[B])(C => Either[A, B]): Equal[C]
    () equal(A, A): Boolean
    () notEqual(A, A): Boolean
    () toScala[A1 <: A]: scala.math.Equiv[A1]
  }
  class Hash~-A~ {
    Boolean
    Byte
    Cause[A]
    Char
    Chunk[A: Hash]
    Class[_]
    Double
    Either[A: Hash, B: Hash]
    F[A: Hash]: Derive[_, Hash]
    Fiber.Id
    Float
    Int
    List[A: Hash]
    Long
    Map[A, B: Hash]
    NonEmptyChunk[A: Hash]
    NonEmptyList[A: Hash]
    NonEmptySet[A]
    Nothing
    Option[A: Hash]
    Ordering
    ParMap[A, B: Hash]
    ParSeq[A: Hash]
    ParSet[A]
    PartialOrdering
    Set[A]
    Short
    String
    These[A: Hash, B: Hash]
    ❨T1: Hash, ..., T22: Hash❩
    Unit
    Validation[E: Hash, A: Hash]
    Vector[A: Hash]
    ZNonEmptySet[A, B: Hash]
    ZSet[A, B: Hash]
    ZTrace

    () both[B](Hash[B]): Hash[(A, B)]
    () bothWith[B, C](Hash[B])(C => (A, B)): Hash[C]
    () contramap[B](B => A): Hash[B]
    () either[B](Hash[B]): Hash[Either[A, B]]
    () eitherWith[B, C](Hash[B])(C => Either[A, B]): Hash[C]
    () hash(A): Int
  }
  class PartialOrd~-A~ {
    Chunk[A: PartialOrd]
    Either[A: PartialOrd, B: PartialOrd]
    F[A: PartialOrd]: Derive[_, PartialOrd]
    List[A: PartialOrd]
    Map[A, B: Equal]
    NonEmptyChunk[A: PartialOrd]
    NonEmptyList[A: PartialOrd]
    NonEmptySet[A]
    Option[A: PartialOrd]
    ParMap[A, B: Equal]
    ParSeq[A: PartialOrd]
    ParSet[A]
    PartialOrdering
    Set[A]
    ❨T1: PartialOrd, ..., T22: PartialOrd❩
    Vector[A: PartialOrd]
    ZNonEmptySet[A, B: PartialOrd]
    ZSet[A, B: PartialOrd]

    () both[B](=> PartialOrd[B]): PartialOrd[(A, B)]
    () bothWith[B, C](=> PartialOrd[B])(C => (A, B)): PartialOrd[C]
    () compare(A, A): PartialOrdering
    () contramap[B](B => A): PartialOrd[B]
    () either[B](=> PartialOrd[B]): PartialOrd[Either[A, B]]
    () eitherWith[B, C](=> PartialOrd[B])(C => Either[A, B]): PartialOrd[C]
    () mapPartialOrdering(PartialOrdering => PartialOrdering): PartialOrd[A]

  }
  class Ord~-A~ {
    Boolean
    Byte
    Char
    Chunk[A: Ord]
    Double
    Either[A: Ord, B: Ord]
    F[A: Ord]: Derive[_, Ord]
    Fiber.Id
    Float
    Int
    List[A: Ord]
    Long
    NonEmptyChunk[A: Ord]
    NonEmptyList[A: Ord]
    Nothing
    Option[A: Ord]
    ParSeq[A: Ord]
    Ordering
    Short
    String
    ❨T1: Ord, ..., T22: Ord❩
    Unit
    Vector[A: Ord]

    () both[B](=> Ord[B]): Ord[(A, B)]
    () bothWith[B, C](=> Ord[B])(C => (A, B)): Ord[C]
    () compare(A, A): Ordering
    () contramap[B](B => A): Ord[B]
    () either[B](=> Ord[B]): Ord[Either[A, B]]
    () eitherWith[B, C](=> Ord[B])(C => Either[A, B]): Ord[C]
    () mapOrdering(Ordering => Ordering): Ord[A]
    () reverse: Ord[A]
    () toScala[A1 <: A]: scala.math.Ordering[A1]
  }
```
