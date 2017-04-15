package htsjdk

import java.nio.file.{Files, Path}

import org.scalatest.{FlatSpec, Matchers}

/** Base class for all Scala tests. */
class UnitSpec extends FlatSpec with Matchers {
  /** Make a temporary file that will get cleaned up at the end of testing. */
  protected def makeTempFile(prefix: String, suffix: String): Path = {
    val path = Files.createTempFile(prefix, suffix)
    path.toFile.deleteOnExit()
    path
  }

  /** Implicit conversion from Java to Scala iterator. */
  implicit def javaIteratorAsScalaIterator[A](iter: java.util.Iterator[A]): Iterator[A] = {
    scala.collection.JavaConverters.asScalaIterator(iter)
  }

  /** Implicit conversion from Java to Scala iterator. */
  implicit def javaIterableAsScalaIterable[A](iterable: java.lang.Iterable[A]): Iterable[A] = {
    scala.collection.JavaConverters.iterableAsScalaIterable(iterable)
  }
}
