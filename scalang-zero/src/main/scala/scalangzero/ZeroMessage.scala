package scalangzero

case class Topic(value: String) extends AnyVal

case class ZeroMessage(
    id: String,
    topic: Topic,
    rawMessage: Seq[Byte]
)
