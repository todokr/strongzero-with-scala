package scalangzero.pump

import org.zeromq.{SocketType, ZContext, ZMQ, ZMsg}
import scalangzero.{Topic, ZeroMessage}
import wvlet.airframe.codec.MessageCodec
import wvlet.log.LogSupport

import java.util.concurrent.atomic.AtomicLong
import javax.sql.DataSource
import scala.collection.mutable
import scala.util.{Failure, Success, Using}

class ScalangZeroPump(
    backendAddress: String,
    notificationAddress: String,
    dataSource: DataSource
) extends Runnable
    with LogSupport {
  import ScalangZeroPump._

  private val ctx          = new ZContext()
  private val backend      = ctx.createSocket(SocketType.DEALER)
  private val notification = ctx.createSocket(SocketType.SUB)
  notification.subscribe(ZMQ.SUBSCRIPTION_ALL)

  override def run(): Unit = {
    backend.connect(backendAddress)
    notification.connect(notificationAddress)
    backend.send("READY")

    val poller = ctx.createPoller(1)
    poller.register(notification, ZMQ.Poller.POLLIN)

    logger.info("Worker start")
    while (!Thread.currentThread().isInterrupted) {
      val request    = ZMsg.recvMsg(backend)
      val consumerId = request.pop().getData
      val lastId     = request.popString

      val reply        = new ZMsg()
      val pollWaitTime = new AtomicLong(10_000)
      reply.add(consumerId)
      Using.Manager { use =>
        val conn = use(dataSource.getConnection)
        val stmt = use(conn.prepareStatement(SelectMessage + " LIMIT " + (BatchSize + 1)))
        stmt.setString(1, lastId)
        val res = mutable.ListBuffer.empty[ZeroMessage]
        var cnt = 0
        val messages =
          try {
            val rs = stmt.executeQuery()
            while (cnt < BatchSize && rs.next()) {
              val message = ZeroMessage(
                id = rs.getString(1),
                topic = Topic(rs.getString(2)),
                rawMessage = rs.getBytes(3)
              )
              res += message
              cnt = cnt + 1
            }

            if (cnt == 0) {
              if (poller.poll(pollWaitTime.get()) != 0) {
                ZMsg.recvMsg(notification, false)
              }
            } else if (cnt > 0 && !rs.isAfterLast) {
              pollWaitTime.set(0)
            }
            res.toSeq
          }

        val replyBody = codec.toMsgPack(messages)
        reply.add("SUCCESS")
        reply.add(replyBody)
        logger.info(s"fetch: ${messages.mkString(", ")}")
        reply.send(backend)
        if (poller.poll(pollWaitTime.get()) != 0) {
          ZMsg.recvMsg(notification, false)
          logger.info("Receive an update notification")
        }
      } match {
        case Failure(e) =>
          reply.add("ERROR")
          reply.add(e.getMessage)
          reply.send(backend)
        case Success(_) => ()
      }
    }
  }
}

object ScalangZeroPump {

  private val SelectMessage = "SELECT id, type, message FROM produced_zero WHERE id > ? ORDER BY id"
  private val BatchSize     = 100
  private val codec         = MessageCodec.of[Seq[ZeroMessage]]
}
