package scalangzero.producer

import org.zeromq.util.ZData
import org.zeromq.{SocketType, ZContext, ZMQ, ZMsg}
import wvlet.log.LogSupport

import java.util.concurrent.Executors
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}

class ScalangZeroProducer(
    frontendAddress: String,
    backendAddress: String
) extends LogSupport {
  private val executor = Executors.newFixedThreadPool(2)
  private val ctx      = new ZContext()
  private val frontend = ctx.createSocket(SocketType.ROUTER)
  private val backend  = ctx.createSocket(SocketType.ROUTER)

  private implicit val ec: ExecutionContext = ExecutionContext.fromExecutor(executor)

  def start(): Unit = {
    frontend.bind(frontendAddress)
    frontend.monitor("inproc://frontend.monitor", ZMQ.EVENT_ALL)
    val monitorSocket = ctx.createSocket(SocketType.PAIR)
    monitorSocket.connect("inproc://frontend.monitor")
    Future {
      while (!Thread.currentThread().isInterrupted) {
        val event = ZMQ.Event.recv(monitorSocket)
        logger.debug(s"FRONTEND EVENT=${event.getEvent} ADDRESS=${event.getAddress} VALUE=${event.getValue}")
      }
    }

    backend.bind(backendAddress)
    val items = ctx.createPoller(2)
    // socketがEventの着信を待ち受ける
    items.register(backend, ZMQ.Poller.POLLIN)
    items.register(frontend, ZMQ.Poller.POLLIN)

    val workerQueue = mutable.Queue.empty[Array[Byte]]
    Future {
      while (!Thread.currentThread().isInterrupted) {
        items.poll()

        // backend event
        if (items.pollin(0)) {
          val msg      = ZMsg.recvMsg(backend, 0)
          val workerId = msg.pop().getData
          workerQueue.enqueue(workerId)
          val consumerId = msg.pop().getData
          if (ZData.toString(consumerId) != "READY") {
            val status = msg.popString()
            if (status == "SUCCESS") {
              logger.info("Worker success")
              val reply = new ZMsg()
              reply.add(consumerId)
              reply.add("")
              reply.add("SUCCESS")
              reply.add(msg.pop().getData)
              reply.send(frontend)
            } else {
              logger.error(s"Worker error: ${msg.popString()}")
            }
          } else {
            logger.info(s"Worker connected: ${new String(workerId)}")
          }
        }

        // frontend event
        if (items.pollin(1)) {
          val msg                     = ZMsg.recvMsg(frontend, 0)
          val consumerId: Array[Byte] = msg.pop().getData
          msg.pop() // empty
          val lastId = msg.popString()

          if (workerQueue.isEmpty) {
            logger.info(s"No available workers: ${new String(consumerId)}")
            val reply = new ZMsg()
            reply.add(consumerId)
            reply.add("")
            reply.add("BUSY")
            reply.send(frontend)
          } else {
            logger.info(s"Consumer connected: ${new String(consumerId)}")
            val workerId = workerQueue.dequeue()
            val reply    = new ZMsg()
            reply.add(workerId)
            reply.add(consumerId)
            reply.add(lastId)
            reply.send(backend)
          }
        }
      }
    }
  }

  def stop(): Unit = {
    backend.unbind(backendAddress)
  }
}
