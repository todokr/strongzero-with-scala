package scalangzero.sender

import org.zeromq.{SocketType, ZContext}
import scalangzero.Topic
import wvlet.airframe.codec.MessageCodec
import wvlet.log.LogSupport

import javax.sql.DataSource
import javax.sql.rowset.serial.SerialBlob
import scala.util.Using

class ScalangZeroSender[Payload](
    notificationAddress: String,
    serverId: String,
    dataSource: DataSource
) extends LogSupport {
  import ScalangZeroSender._
  import scala.util.chaining._

  private val ctx = new ZContext()
  private val notification =
    ctx
      .createSocket(SocketType.PUB)
      .tap(_.setSndHWM(0))
      .tap(_.bind(notificationAddress))

  private val idGenerator = new IdGenerator(serverId)
  private val codec       = MessageCodec.of[Payload]

  def send(topic: Topic, payload: Payload): Unit = {
    val connection = dataSource.getConnection()

    Using(connection) { conn =>
      val stmt = conn.prepareStatement(InsertMessage)
      stmt.setString(1, idGenerator.generateId())
      stmt.setString(2, topic.value)
      val blob = codec.toMsgPack(payload)
      stmt.setBlob(3, new SerialBlob(blob))
      stmt.execute()
    }
  }

  def updated(): Unit = {
    logger.info("Update notification")
    notification.send("update")
  }
}

object ScalangZeroSender {
  private final val InsertMessage = "INSERT INTO produced_zero (id, type, message) VALUES (?, ?, ?)"
}
