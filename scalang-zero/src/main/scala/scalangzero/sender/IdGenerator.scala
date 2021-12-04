package scalangzero.sender

import wvlet.airframe.ulid.ULID

class IdGenerator(serverId: String) {

  def generateId(): String = s"$serverId-${ULID.newULIDString}"
}
