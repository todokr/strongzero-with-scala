package example

object JsonFormats {
  // import the default encoders for primitive types (Int, String, Lists etc)

  implicit val userJsonFormat  = jsonFormat3(User)
  implicit val usersJsonFormat = jsonFormat1(Users)

  implicit val actionPerformedJsonFormat = jsonFormat1(ActionPerformed)
}
