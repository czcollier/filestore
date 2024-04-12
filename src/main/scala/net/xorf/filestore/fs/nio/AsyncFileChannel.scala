package net.xorf.filestore.fs.nio

import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.{AsynchronousFileChannel, CompletionHandler}
import java.nio.file.StandardOpenOption
import java.util.Collections
import java.util.concurrent.{AbstractExecutorService, ExecutorService, TimeUnit}

import scala.collection.JavaConverters._
import scala.concurrent._
import scala.util.{Failure, Success}

/**
 * Heavily influenced by class of the same name from [[https://github.com/alexandru/shifter shifter]].
 * Did not use entire shifter library as dependency because it does not look to be under active development and
 * builds for recent (2.11) Scala versions are not available.  Just needed the functionality here so stole the idea
 * and tweaked it to my needs.
 *
 * Wrapper around
 * [[http://openjdk.java.net/projects/nio/javadoc/java/nio/channels/AsynchronousFileChannel.html java.nio.channels.AsynchronousFileChannel]]
 * (class available since Java 7 for doing async I/O on files).
 *
 * @param file - to open
 * @param options - specify the mode for opening the file
 * @param ctx - implicit ExecutionContext
 *
 * @example {{{
 *
 *   val out = new AsyncFileChannel(File.createTempFile, StandardOpenOption.CREATE)
 *
 *   val bytes = ByteBuffer.wrap("Hello world!".getBytes("UTF-8"))
 *   val future = out.write(bytes, 0)
 *
 *   future.onComplete {
 *     case Success(nr) =>
 *       println("Bytes written: %d".format(nr))
 *
 *     case Failure(exc) =>
 *       println(s"ERROR: " + exc.getMessage)
 *   }
 *
 * }}}
 */
final class AsyncFileChannel(file: File, options: StandardOpenOption*)(implicit ctx: ExecutionContext) {
  import ExecutorServiceWrapper._
  /**
   * Wraps {{{<A> write(ByteBuffer, int, A, CompletionHandler)}}} method in [[java.nio.channels.AsynchronousFileChannel]].
   * Writes a sequence of bytes to this channel from the given buffer,
   * starting at the given file position.
   *
   * @param source - the sequence of bytes to write
   * @param positionInFile - the position in file where to write.
   * @return - a future value containing the number of bytes written
   */
  def write(source: ByteBuffer, positionInFile: Long): Future[Int] = {
    val promise = Promise[Int]()
    instance.write(source, positionInFile, promise, writeCompletionHandler)
    promise.future
  }

  /**
   * Wraps {{{<A> read(ByteBuffer, long, A, CompletionHandler)}}} method in [[java.nio.channels.AsynchronousFileChannel]].
   * Reads a sequence of bytes from this channel into the given buffer,
   * starting at the given file position.
   *
   * @param dest - the buffer holding the bytes read on completion
   * @param positionInFile - the position in file from where to read
   * @return - the number of bytes read or -1 if the given position is
   *         greater than or equal to the file's size at the time the read
   *         is attempted
   */
  def read(dest: ByteBuffer, positionInFile: Long): Future[Int] = {
    val promise = Promise[Int]()
    instance.read(dest, positionInFile, promise, readCompletionHandler)
    promise.future
  }

  /**
   * Returns the current size of this channel's file.
   */
  def size = instance.size()

  /**
   * Wraps {{{force(boolean)}}} method in [[java.nio.channels.AsynchronousFileChannel]]
   *
   * Forces any updates to this channel's file to be written to the storage device that contains it.
   *
   * @param metadata - can be used to limit the number of I/O operations that this method is
   *                 required to perform. Passing false for this parameter indicates that only
   *                 updates to the file's content need be written to storage; passing true
   *                 indicates that updates to both the file's content and metadata must be written,
   *                 which generally requires at least one more I/O operation. Whether this parameter
   *                 actually has any effect is dependent upon the underlying operating system and
   *                 is therefore unspecified.
   */
  def force(metadata: Boolean) {
    instance.force(metadata)
  }

  /**
   * Wraps {{{close()}}} method in [[java.nio.channels.AsynchronousFileChannel]].
   * Closes this channel.
   *
   * Any outstanding asynchronous operations upon this channel will complete with the exception
   * `AsynchronousCloseException`.
   */
  def close() {
    instance.close()
  }

  private[this] val readCompletionHandler = new CompletionHandler[Integer, Promise[Int]] {
    def completed(result: Integer, promise: Promise[Int]) {
      promise.complete(Success(result))
    }

    def failed(exc: Throwable, promise: Promise[Int]) {
      promise.complete(Failure(exc))
    }
  }

  private[this] val writeCompletionHandler = new CompletionHandler[Integer, Promise[Int]] {
    def completed(result: Integer, promise: Promise[Int]) {
      promise.complete(Success(result))
    }

    def failed(exc: Throwable, promise: Promise[Int]) {
      try {
        promise.complete(Failure(exc))
      }
      finally {
        ctx.reportFailure(exc)
      }
    }
  }

  private[this] val instance =
    AsynchronousFileChannel.open(file.toPath, options.toSet.asJava, ctx)


  object ExecutorServiceWrapper {
    def apply(ec: ExecutionContext): ExecutionContextExecutorService = ec match {
      case null => throw null
      case eces: ExecutionContextExecutorService => eces
      case other => new AbstractExecutorService with ExecutionContextExecutorService {
        override def prepare(): ExecutionContext = other
        override def isShutdown = false
        override def isTerminated = false
        override def shutdown() = ()
        override def shutdownNow() = Collections.emptyList[Runnable]
        override def execute(runnable: Runnable): Unit = other execute runnable
        override def reportFailure(t: Throwable): Unit = other reportFailure t
        override def awaitTermination(length: Long,unit: TimeUnit): Boolean = false
      }
    }

    /**
     * Transforms this Scala `ExecutionContext` into a Java
     * [[http://docs.oracle.com/javase/7/docs/api/java/util/concurrent/ExecutorService.html ExecutorService]]
     */
    implicit def toExecutorServiceWrapper(ec: ExecutionContext): ExecutorService = ec match {
      case null => throw null
      case executor: ExecutionContextExecutorService => executor
      case _ => ExecutorServiceWrapper(ec)
    }
  }
}
