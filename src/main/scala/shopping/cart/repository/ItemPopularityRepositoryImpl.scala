package shopping.cart.repository

import akka.Done
import akka.stream.alpakka.cassandra.scaladsl.CassandraSession
import scala.concurrent.{ExecutionContext, Future}

class ItemPopularityRepositoryImpl(cassandraSession: CassandraSession, keyspace: String) extends ItemPopularityRepository {
  val popularityTable = "item_popularity"
  implicit val ec = ExecutionContext.Implicits.global

  override def update(itemId: String,
                      delta: Int): Future[Done] = {
    cassandraSession.executeWrite(
      s"UPDATE $keyspace.$popularityTable SET count = count + ? WHERE item_id = ?",
      delta.asInstanceOf[AnyRef],
      itemId)
  }

  override def getItem(itemId: String): Future[Option[Long]] = {
    val option = cassandraSession
      .selectOne(
        s"SELECT item_id, count FROM $keyspace.$popularityTable WHERE item_id = ?",
        itemId)
    option.map(opt => opt.map(row => row.getLong("count").longValue()))
  }
}
