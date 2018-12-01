package scalaz.zio.java.data;

public final class Tuple<L, R> {
    public final L left;
    public final R right;

    public Tuple(L left, R right) {
        this.left = left;
        this.right = right;
    }

    public static <L, R> Tuple<L, R> fromScala(scala.Tuple2<L, R> tuple) {
        return new Tuple<>(tuple._1, tuple._2);
    }
}
