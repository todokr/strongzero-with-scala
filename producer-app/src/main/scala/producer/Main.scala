package producer

import akka.actor.typed.ActorSystem
import akka.actor.typed.javadsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives.{complete, get, path}
import scalikejdbc._

import scala.concurrent.ExecutionContext
import scala.io.StdIn

object Main extends App {
  implicit val system: ActorSystem[Nothing] = ActorSystem(Behaviors.empty, "producer-app")
  implicit val ec: ExecutionContext         = system.executionContext

  val ServerPort = 8000
  val DBUrl      = "jdbc:h2:tcp://localhost/producer"
  ConnectionPool.singleton(DBUrl, "sa", "")

  val route =
    path("hello") {
      get {
        complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>Hello</h1>"))
      }
    }

  val bindingFuture = Http().newServerAt("localhost", ServerPort).bind(route)

  println(s"Server now online. Please navigate to http://localhost:${ServerPort}/hello\nPress RETURN to stop...")
  StdIn.readLine()
  bindingFuture
    .flatMap(_.unbind())
    .onComplete(_ => system.terminate())
}
