package scalangzero.consumer

import org.zeromq.{SocketType, ZContext, ZFrame, ZMQ, ZMQException, ZMsg}
import scalangzero.{Topic, ZeroMessage}
import wvlet.airframe.codec.MessageCodec
import wvlet.log.LogSupport
import wvlet.airframe.control.Retry

import java.sql.{Connection, SQLException}
import java.time.Clock
import java.util.concurrent.{Callable, Executors}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import javax.sql.DataSource
import scala.collection.mutable
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try, Using}

class ScalangZeroConsumer(
    producerId: String,
    producerAddress: String,
    dataSource: DataSource
) extends LogSupport {
  import ScalangZeroConsumer._
  private val running  = new AtomicBoolean(false)
  private val lastId   = new AtomicReference[String]
  private val handlers = mutable.HashMap.empty[Topic, CheckedConsumer]

  private val InitialDelayMs = 2 * 1000
  private val MaxRetry       = 5

  private val codec = MessageCodec.of[Seq[ZeroMessage]]

  def registerHandler(topic: Topic, consumer: CheckedConsumer): Unit = {
    handlers.put(topic, consumer)
  }

  def start(): Unit = {
    lastId.set(getLastId(producerId))
    running.set(true)
    consumer()
  }

  def consumed(id: String): Unit = {
    val connection = dataSource.getConnection
    Using.Manager { use =>
      val conn = use(connection)
      val stmt = use(conn.prepareStatement(UpdateSql))
      stmt.setString(1, id)
      stmt.setString(2, producerId)
      stmt.executeUpdate()
    } match {
      case Failure(e) =>
        connection.rollback()
        throw new RuntimeException(e)
      case Success(_) =>
        connection.commit()
    }
  }

  def stop(): Unit = running.compareAndSet(true, false)

  private def getLastId(producerId: String): String =
    Using
      .Manager { use =>
        val conn = use(dataSource.getConnection)
        val stmt = use(conn.prepareStatement(SelectSql))
        stmt.setString(1, producerId)
        val rs = use(stmt.executeQuery())
        if (rs.next()) rs.getString(1)
        else {
          setupDefaultValue(conn)
          DummyMessageId
        }
      } match {
      case Failure(e)      => throw new IllegalStateException("Can't start consumer", e)
      case Success(lastId) => lastId
    }

  private def setupDefaultValue(conn: Connection): Unit = {
    val stmt = conn.prepareStatement(InsertSql)
    try {
      stmt.setString(1, producerId)
      stmt.setString(2, DummyMessageId)
      stmt.executeUpdate()
    } catch {
      case e: SQLException =>
        conn.rollback()
        throw e
    } finally stmt.close()
  }

  private def consumer(): Unit = {
    val ctx: ZContext      = new ZContext()
    val socket: ZMQ.Socket = ctx.createSocket(SocketType.REQ)
    socket.connect(producerAddress)
    socket.monitor(MonitorAddr, ZMQ.EVENT_DISCONNECTED)

    implicit val ec: ExecutionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(2))
    val futures = Future {
      while (!ctx.isClosed && running.get()) {

        val msg = Retry
          .withBackOff(maxRetry = MaxRetry, initialIntervalMillis = InitialDelayMs)
          .retryOn { case e: Exception =>
            logger.warn(e)
            Retry.retryableFailure(e)
          }.run {
            val request: ZMsg = new ZMsg()
            request.push(new ZFrame(lastId.get()))
            request.send(socket)
            ZMsg.recvMsg(socket)
          }
        val control = msg.popString()
        if (control == "SUCCESS") {
          val messages = codec.unpack(msg.pop().getData)
          messages.foreach { msg =>
            handlers
              .collect {
                case (topic, consumer) if msg.topic == topic =>
                  logger.debug(s"consumer found=$consumer")
                  consumer.consume(msg.id, msg.rawMessage)
              }
          }
        }
      }
    }
    val monitorSocket: ZMQ.Socket = ctx.createSocket(SocketType.PAIR)
    monitorSocket.connect(MonitorAddr)
    val event: ZMQ.Event = ZMQ.Event.recv(monitorSocket)
    logger.info(s"SOCKET EVENT=${event.getEvent} VALUE=${event.getValue}")
    Await.result(futures, Duration.Inf)
    ctx.destroy()
  }

}
object ScalangZeroConsumer {
  private val DefaultTableName = "consumed_zero"
  private val UpdateSql        = s"UPDATE $DefaultTableName SET last_id=? WHERE producer_id=?"
  private val InsertSql        = s"INSERT INTO $DefaultTableName (producer_id,last_id) VALUES(?,?)"
  private val SelectSql        = s"SELECT last_id FROM $DefaultTableName WHERE producer_id = ?"
  private val DummyMessageId   = "0000000000000000"
  private val MonitorAddr      = "inproc://socket.monitor"
}

trait CheckedConsumer {
  def consume(id: String, rawMessage: Seq[Byte]): Unit
}
