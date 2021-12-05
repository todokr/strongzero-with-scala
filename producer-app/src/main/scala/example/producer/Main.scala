package example.producer

import akka.actor.typed.ActorSystem
import akka.actor.typed.javadsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import scalikejdbc._
import scalikejdbc.config.DBs
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import scalangzero.Topic
import scalangzero.sender.ScalangZeroSender
import scalikejdbc.ConnectionPool.dataSource
import spray.json.DefaultJsonProtocol._
import spray.json.RootJsonFormat
import wvlet.airframe.ulid.ULID

import scala.concurrent.ExecutionContext
import scala.io.StdIn

object Main extends App {
  implicit val system: ActorSystem[Nothing] = ActorSystem(Behaviors.empty, "example.producer-app")
  implicit val ec: ExecutionContext         = system.executionContext
  implicit val createMemberFormat: RootJsonFormat[CreateUserRequest] = jsonFormat1(CreateUserRequest)

  val ServerPort = 8000

  DBs.setupAll()
  val ds     = dataSource()
  val sender = new ScalangZeroSender[UserUpdated]("ipc://notification", "producer", ds)

  val route =
    concat(
      path("hello") {
        get {
          DB.readOnly { implicit session =>
            println(sql"select count(*) from USERS".map(_.long(1)).single().apply())
          }
          complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>Hello</h1>"))
        }
      },
      path("user") {
        post {
          entity(as[CreateUserRequest]) { request =>
            val user    = User.create(request.name)
            val message = UserUpdated("created", user.id, user.name)
            DB.localTx { implicit session =>
              sql"insert into users (id, name) values (${user.id}, ${user.name})".update().apply()
              sender.send(Topic("user"), message)
            }
            sender.updated()
            complete(HttpEntity(ContentTypes.`application/json`, s"""{"created": "${user.id}"}"""))
          }
        }
      }
    )

  val bindingFuture = Http().newServerAt("localhost", ServerPort).bind(route)

  println(s"Server now online. Please navigate to http://localhost:${ServerPort}/hello\nPress RETURN to stop...")
  StdIn.readLine()
  bindingFuture
    .flatMap(_.unbind())
    .onComplete(_ => system.terminate())
}

final case class CreateUserRequest(
    name: String
)

final case class User(
    id: String,
    name: String
)
object User {
  def create(name: String): User = User(ULID.newULIDString, name)
}

final case class UserUpdated(
    action: String,
    id: String,
    name: String
)
