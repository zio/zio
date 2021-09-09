package zio.stm

trait ZTQueue[-RA, -RB, +EA, +EB, -A, +B] extends Serializable { self =>

  def awaitShutdown: USTM[Unit]

  def capacity: Int

  def isShutdown: USTM[Boolean]

  def offer(a: A): ZSTM[RA, EA, Boolean]

  def shutdown: USTM[Unit]

  def size: USTM[Int]

  def take: ZSTM[RB, EB, B]

  final def contramap[C](f: C => A): ZTQueue[RA, RB, EA, EB, C, B] =
    contramapSTM(c => ZSTM.succeedNow(f(c)))

  final def contramapSTM[RC <: RA, EC >: EA, C](f: C => ZSTM[RC, EC, A]): ZTQueue[RC, RB, EC, EB, C, B] =
    dimapSTM(f, ZSTM.succeedNow)

  final def dimap[C, D](f: C => A, g: B => D): ZTQueue[RA, RB, EA, EB, C, D] =
    dimapSTM(c => ZSTM.succeedNow(f(c)), b => ZSTM.succeedNow(g(b)))

  final def dimapSTM[RC <: RA, RD <: RB, EC >: EA, ED >: EB, C, D](
    f: C => ZSTM[RC, EC, A],
    g: B => ZSTM[RD, ED, D]
  ): ZTQueue[RC, RD, EC, ED, C, D] =
    new ZTQueue[RC, RD, EC, ED, C, D] {
      def awaitShutdown: USTM[Unit] =
        self.awaitShutdown
      def capacity: Int =
        self.capacity
      def isShutdown: USTM[Boolean] =
        self.isShutdown
      def offer(c: C): ZSTM[RC, EC, Boolean] =
        f(c).flatMap(self.offer)
      def shutdown: USTM[Unit] =
        self.shutdown
      def size: USTM[Int] =
        self.size
      def take: ZSTM[RD, ED, D] =
        self.take.flatMap(g)
    }

  final def filterInput[A1 <: A](f: A1 => Boolean): ZTQueue[RA, RB, EA, EB, A1, B] =
    filterInputSTM(a => ZSTM.succeedNow(f(a)))

  final def filterInputSTM[RA1 <: RA, EA1 >: EA, A1 <: A](
    f: A1 => ZSTM[RA1, EA1, Boolean]
  ): ZTQueue[RA1, RB, EA1, EB, A1, B] =
    new ZTQueue[RA1, RB, EA1, EB, A1, B] {
      def awaitShutdown: USTM[Unit] =
        self.awaitShutdown
      def capacity: Int =
        self.capacity
      def isShutdown: USTM[Boolean] =
        self.isShutdown
      def offer(a: A1): ZSTM[RA1, EA1, Boolean] =
        f(a).flatMap(b => if (b) self.offer(a) else ZSTM.succeedNow(false))
      def shutdown: USTM[Unit] =
        self.shutdown
      def size: USTM[Int] =
        self.size
      def take: ZSTM[RB, EB, B] =
        self.take
    }

  final def filterOutput(f: B => Boolean): ZTQueue[RA, RB, EA, EB, A, B] =
    filterOutputSTM(b => ZSTM.succeedNow(f(b)))

  final def filterOutputSTM[RB1 <: RB, EB1 >: EB](
    f: B => ZSTM[RB1, EB1, Boolean]
  ): ZTQueue[RA, RB1, EA, EB1, A, B] =
    new ZTQueue[RA, RB1, EA, EB1, A, B] {
      def awaitShutdown: USTM[Unit] =
        self.awaitShutdown
      def capacity: Int =
        self.capacity
      def isShutdown: USTM[Boolean] =
        self.isShutdown
      def offer(a: A): ZSTM[RA, EA, Boolean] =
        self.offer(a)
      def shutdown: USTM[Unit] =
        self.shutdown
      def size: USTM[Int] =
        self.size
      def take: ZSTM[RB1, EB1, B] =
        self.take.flatMap { a =>
          f(a).flatMap { b =>
            if (b) ZSTM.succeedNow(a) else take
          }
        }
    }

  final def map[C](f: B => C): ZTQueue[RA, RB, EA, EB, A, C] =
    mapSTM(b => ZSTM.succeedNow(f(b)))

  final def mapSTM[RC <: RB, EC >: EB, C](f: B => ZSTM[RC, EC, C]): ZTQueue[RA, RC, EA, EC, A, C] =
    dimapSTM(ZSTM.succeedNow, f)
}

object ZTQueue {

  def bounded[A](requestedCapacity: Int): USTM[TQueue[A]] =
    for {
      array <- TArray.fromIterable(List.fill(requestedCapacity)(null.asInstanceOf[A]))
      head  <- TRef.make[Int](0)
      tail  <- TRef.make[Int](0)
      down  <- TRef.make[Boolean](false)
    } yield new TQueue[A] {
      def awaitShutdown: USTM[Unit] =
        down.get.flatMap(b => if (b) ZSTM.unit else ZSTM.retry)
      def capacity: Int =
        requestedCapacity
      def isShutdown: USTM[Boolean] =
        down.get
      def offer(a: A): ZSTM[Any, Nothing, Boolean] =
        ZSTM.Effect { (journal, _, _) =>
          val h = head.unsafeGet(journal)
          val t = tail.unsafeGet(journal)

          if (h - t < capacity) {
            tail.unsafeSet(journal, t + 1)
            array.array(t % capacity).unsafeSet(journal, a)
            true
          } else {
            throw ZSTM.RetryException
          }
        }
      // for {
      //   h <- head.get
      //   t <- tail.get
      //   _ <- if (t - h < capacity) tail.set(t + 1) *> array.update(t % capacity, _ => a) else ZSTM.retry
      // } yield true
      def shutdown: USTM[Unit] =
        down.set(true)
      def size: USTM[Int] =
        for {
          h <- head.get
          t <- tail.get
        } yield t - h
      def take: ZSTM[Any, Nothing, A] =
        ZSTM.Effect { (journal, _, _) =>
          val h = head.unsafeGet(journal)
          val t = tail.unsafeGet(journal)

          if (h < t) {
            head.unsafeSet(journal, h + 1)
            array.array(h % capacity).unsafeGet(journal)
          } else {
            throw ZSTM.RetryException
          }
        }
      // for {
      //   h <- head.get
      //   t <- tail.get
      //   _ <- ZSTM.retry.when(h >= t)
      //   a <- array(h % capacity)
      //   _ <- head.set(h + 1)
      // } yield a
    }

  def bounded2[A](requestedCapacity: Int): USTM[TQueue[A]] =
    for {
      array <- TArray.fromIterable(List.fill(requestedCapacity)(null.asInstanceOf[A]))
      seq   <- TArray.fromIterable(0 until requestedCapacity)
      head  <- TRef.make[Int](0)
      tail  <- TRef.make[Int](0)
      down  <- TRef.make[Boolean](false)
    } yield new TQueue[A] {
      def awaitShutdown: USTM[Unit] =
        down.get.flatMap(b => if (b) ZSTM.unit else ZSTM.retry)
      def capacity: Int =
        requestedCapacity
      def isShutdown: USTM[Boolean] =
        down.get
      def offer(a: A): ZSTM[Any, Nothing, Boolean] =
        ZSTM.Effect { (journal, _, _) =>
          val t          = tail.unsafeGet(journal)
          val index      = t % capacity
          val currentSeq = seq.array(index).unsafeGet(journal)

          if (t == currentSeq) {
            tail.unsafeSet(journal, t + 1)
            seq.array(index).unsafeSet(journal, t + 1)
            array.array(index).unsafeSet(journal, a)
            true
          } else {
            throw ZSTM.RetryException
          }
        }
      // for {
      //   h <- head.get
      //   t <- tail.get
      //   _ <- if (t - h < capacity) tail.set(t + 1) *> array.update(t % capacity, _ => a) else ZSTM.retry
      // } yield true
      def shutdown: USTM[Unit] =
        down.set(true)
      def size: USTM[Int] =
        for {
          h <- head.get
          t <- tail.get
        } yield t - h
      def take: ZSTM[Any, Nothing, A] =
        ZSTM.Effect { (journal, _, _) =>
          val h          = head.unsafeGet(journal)
          val index      = h % capacity
          val currentSeq = seq.array(index).unsafeGet(journal)

          if (h + 1 == currentSeq) {
            head.unsafeSet(journal, h + 1)
            seq.array(index).unsafeSet(journal, h + requestedCapacity)
            array.array(index).unsafeGet(journal)
          } else {
            throw ZSTM.RetryException
          }
        }
      // for {
      //   h <- head.get
      //   t <- tail.get
      //   _ <- ZSTM.retry.when(h >= t)
      //   a <- array(h % capacity)
      //   _ <- head.set(h + 1)
      // } yield a
    }

  def dropping[A](capacity: Int): USTM[TQueue[A]] =
    ???

  def sliding[A](capacity: Int): USTM[TQueue[A]] =
    ???

  def unbounded[A]: USTM[TQueue[A]] =
    ???
}
