package scalaz.zio.java;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public class IO<E, A> {

    private scalaz.zio.IO<E, A> zio;

    private IO(scalaz.zio.IO<E, A> zio) {
        this.zio = zio;
    }

    public <B> IO<E, B> map(Function<A, B> f) {
        return new IO<>(zio.map(f::apply));
    }

    public <E2, B> IO<E2, B> bimap(Function<E, E2> f, Function<A, B> g) {
        return new IO<>(zio.bimap(f::apply, g::apply));
    }

    public <E1 extends E, B> IO<E1, B> flatMap(Function<A, IO<E1, B>> f) {
        return new IO<>(zio.flatMap(f.andThen(io -> io.zio)::apply));
    }

    // TODO Fiber
    // final def fork: IO[Nothing, Fiber[E, A]]
    // final def fork0(handler: Cause[Any] => IO[Nothing, Unit]): IO[Nothing, Fiber[E, A]]

    public <E1 extends E, B, C> IO<E1, C> parWith(IO<E1, B> that, BiFunction<A, B, C> f) {
        return new IO<>(zio.parWith(that.zio, f::apply));
    }

    public <E1 extends E, B> IO<E1, scala.Tuple2<A, B>> par(IO<E1, B> that) {
        return new IO<>(zio.par(that.zio));
    }

    // TODO other transformation/composition methods...

    public static <E, A> IO<E, A> now(A a) {
        return new IO(scalaz.zio.IO$.MODULE$.now(a));
    }

    public static <E, A> IO<E, A> point(Supplier<A> a) {
        return new IO(scalaz.zio.IO$.MODULE$.point(a::get));
    }

    public static <E, A> IO<E, A> fail(E error) {
        return new IO(scalaz.zio.IO$.MODULE$.fail(error));
    }

    // TODO other construction methods...
}
