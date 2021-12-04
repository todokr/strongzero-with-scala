package scalangzero

case class ZeroMessage(
    id: String,
    messageType: String,
    message: Seq[Byte]
)
