package scalaz.zio.java.data;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode
@AllArgsConstructor
public final class Pair<A, B> {
    public final A first;
    public final B second;

    public static <L, R> Pair<L, R> fromScala(scala.Tuple2<L, R> tuple) {
        return new Pair<>(tuple._1, tuple._2);
    }

}
