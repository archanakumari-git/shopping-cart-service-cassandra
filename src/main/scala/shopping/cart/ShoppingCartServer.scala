package shopping.cart

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.Behaviors
import akka.grpc.scaladsl.{ServerReflection, ServiceHandler}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpEntity, HttpRequest, HttpResponse, MediaTypes, StatusCodes}
import akka.http.scaladsl.server.ContentNegotiator.Alternative.MediaType
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import akka.util.Timeout
import shopping.cart.proto.{GetItemPopularityRequest, GetItemPopularityResponse, ShoppingCartServiceHandler}
import spray.json.DefaultJsonProtocol.{jsonFormat2, _}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}
import spray.json.{DefaultJsonProtocol, enrichAny}

case class Item(itemId: String, popularityCount:Long)
object JsonProtocol extends DefaultJsonProtocol{
  implicit val itemFormat = jsonFormat2(Item)
  //implicit val getItemResponse = jsonFormat4(shopping.cart.proto.GetItemPopularityResponse)

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
//          .onComplete {
//            case Success(res) => println(res)
//            case Failure(_)   => throw new IllegalArgumentException//("something wrong")
//          }
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
