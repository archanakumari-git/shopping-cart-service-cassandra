package shopping.cart.repository

import akka.Done

import scala.concurrent.Future

trait ItemPopularityRepository {
  def update(itemId: String, delta: Int) : Future[Done]

  def getItem(itemId: String): Future[Option[Long]]
}
