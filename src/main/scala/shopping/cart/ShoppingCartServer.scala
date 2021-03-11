package shopping.cart

import akka.actor.typed.ActorSystem
import akka.grpc.scaladsl.{ServerReflection, ServiceHandler}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import shopping.cart.ShoppingCart.AddItem
import shopping.cart.proto.{GetItemPopularityRequest, ShoppingCartServiceHandler}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success}
import akka.actor.typed.scaladsl.AskPattern._

object ShoppingCartServer {

  def start(
             interface: String,
             port: Int,
             system: ActorSystem[_],
             grpcService: proto.ShoppingCartService): Unit = {
    implicit val sys: ActorSystem[_] = system
    implicit val ec: ExecutionContext =
      system.executionContext
    val httpService: Route = path("getItem") {
      get {
        complete("sample service!!!")
      }
    }

    val service: HttpRequest => Future[HttpResponse] =
      ServiceHandler.concatOrNotFound(
        ShoppingCartServiceHandler.partial(grpcService),
        // ServerReflection enabled to support grpcurl without import-path and proto parameters
        ServerReflection.partial(List(proto.ShoppingCartService))
      )

    val allRoutes = concat(httpService,handle(service))

    val bound =
      Http()
        .newServerAt(interface, port)
        .bind(allRoutes)
        .map(_.addToCoordinatedShutdown(3.seconds))

    bound.onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        system.log.info(
          "Shopping online at gRPC server {}:{}",
          address.getHostString,
          address.getPort)
      case Failure(ex) =>
        system.log.error("Failed to bind gRPC endpoint, terminating system", ex)
        system.terminate()
    }
  }

}
