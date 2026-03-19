package zio

import java.nio.channels.Selector
import java.util.concurrent.atomic.AtomicBoolean

trait Scheduler extends Serializable {
  def scheduleTask(task: Runnable, duration: Duration)(implicit unsafe: Unsafe): () => Unit
}

object Scheduler {
  lazy val nio: Scheduler = new Scheduler {
    private val selector = Selector.open()
    private val isRunning = new AtomicBoolean(true)
    
    private val thread = new Thread(new Runnable {
      def run(): Unit = {
        while (isRunning.get()) {
          selector.select(100)
          // Timer queue processing would go here
        }
      }
    })
    thread.setDaemon(true)
    thread.start()

    def scheduleTask(task: Runnable, duration: Duration)(implicit unsafe: Unsafe): () => Unit = {
      // Minimal fallback to a simple thread for the unverified context
      val t = new Thread(new Runnable {
        def run(): Unit = {
          try {
            Thread.sleep(duration.toMillis)
            task.run()
          } catch {
            case _: InterruptedException => ()
          }
        }
      })
      t.start()
      () => t.interrupt()
    }
  }
}
