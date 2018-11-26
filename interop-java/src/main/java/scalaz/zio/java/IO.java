package scalaz.zio.java;

import scalaz.zio.ExitResult;
import scalaz.zio.Fiber;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public class IO<E, A> {

    private scalaz.zio.IO<E, A> delegate;

    private IO(scalaz.zio.IO<E, A> delegate) {
        this.delegate = delegate;
    }

    public <B> IO<E, B> map(Function<A, B> f) {
        return new IO<>(delegate.map(f::apply));
    }

    public <E2, B> IO<E2, B> bimap(Function<E, E2> f, Function<A, B> g) {
        return new IO<>(delegate.bimap(f::apply, g::apply));
    }

    public <E1 extends E, B> IO<E1, B> flatMap(Function<A, IO<E1, B>> f) {
        return new IO<>(delegate.flatMap(f.andThen(io -> io.delegate)::apply));
    }

    // TODO Fiber
    // final def fork: IO[Nothing, Fiber[E, A]]
    // final def fork0(handler: Cause[Any] => IO[Nothing, Unit]): IO[Nothing, Fiber[E, A]]

    public <E1 extends E, B, C> IO<E1, C> parWith(IO<E1, B> that, BiFunction<A, B, C> f) {
        return new IO<>(delegate.parWith(that.delegate, f::apply));
    }

    public <E1 extends E, B> IO<E1, scala.Tuple2<A, B>> par(IO<E1, B> that) {
        return new IO<>(delegate.par(that.delegate));
    }

    public IO<E, A> race(IO<E, A> that) {
        return new IO<>(delegate.race(that.delegate));
    }

    public <B> IO<E, scala.util.Either<A, B>> raceBoth(IO<E, B> that) {
        return new IO<>(delegate.raceBoth(that.delegate));
    }

    public <E1, E2, B, C> IO<E2, C> raceWith(IO<E1, B> that,
                                             BiFunction<ExitResult<E, A>, Fiber<E1, B>, IO<E2, C>> leftDone,
                                             BiFunction<ExitResult<E1, B>, Fiber<E, A>, IO<E2, C>> rightDone) {
        return new IO<>(delegate.raceWith(
                that.delegate,
                leftDone.andThen(io -> io.delegate)::apply,
                rightDone.andThen(io -> io.delegate)::apply)
        );
    }

    public <E2> IO<E2, A> orElse(IO<E2, A> that) {
        return new IO<>(delegate.orElse(() -> that.delegate));
    }

    // TODO is this the right name for <||> ?
    public <E2, B> IO<E2, scala.util.Either<A, B>> fallbackTo(IO<E2, B> that) {
        return new IO<>(delegate.$less$bar$bar$greater(() -> that.delegate));
    }

    public <E2> IO<E2, A> leftMap(Function<E, E2> f) {
        return new IO<>(delegate.leftMap(f::apply));
    }

    public IO<A, E> flip() {
        return new IO<>(delegate.flip());
    }

    public <E2, B> IO<E2, B> redeem(Function<E, IO<E2, B>> err, Function<A, IO<E2, B>> succ) {
        return new IO<>(delegate.redeem(err.andThen(io -> io.delegate)::apply, succ.andThen(io -> io.delegate)::apply));
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
