package shopping.cart

import akka.actor.typed.ActorSystem
import akka.grpc.scaladsl.{ServerReflection, ServiceHandler}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpEntity, HttpRequest, HttpResponse, MediaTypes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Route, RouteResult}
import akka.util.Timeout
import shopping.cart.proto.{GetItemPopularityRequest, ShoppingCartServiceHandler}
import spray.json.{DefaultJsonProtocol, enrichAny}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}

case class Item(itemId: String, popularityCount:Long)
object JsonProtocol extends DefaultJsonProtocol{
  implicit val itemFormat = jsonFormat2(Item)

}

object ShoppingCartServer {

  def start(
             interface: String,
             port: Int,
             system: ActorSystem[_],
             grpcService: proto.ShoppingCartService): Unit = {
    implicit val sys: ActorSystem[_] = system
    implicit val ec: ExecutionContext =
      system.executionContext
    implicit val executionContext: ExecutionContextExecutor = sys.executionContext

    def getItems(itemId: String): Future[Item] =
      {
        implicit val timeout: Timeout = 5.seconds
         grpcService.getItemPopularity(GetItemPopularityRequest(itemId)).map(x=>Item(x.itemId,x.popularityCount))

      }

    val httpService: Route = path("getItem") {
      parameter("itemId") {itemId=>
        get {
          import JsonProtocol._
          complete{
              getItems(itemId)
                .map(value=> HttpResponse(entity = HttpEntity(MediaTypes.`application/json`,value.toJson.prettyPrint)))
            }
        }
      }
    }

    val service: HttpRequest => Future[HttpResponse] =
      ServiceHandler.concatOrNotFound(
        ShoppingCartServiceHandler.partial(grpcService),
        // ServerReflection enabled to support grpcurl without import-path and proto parameters
        ServerReflection.partial(List(proto.ShoppingCartService))
      )
//convert akka grpc function to route
    val handlerRoute: Route = { ctx => service(ctx.request).map(RouteResult.Complete) }
    //akka http service simply combined with existing grpc functions
   // val allRoutes = concat(httpService,handle(service))

    //akka http interoperability with akka grpc
    val allRoutes = concat(httpService,handlerRoute)

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
