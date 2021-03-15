## Running the sample code

1. **Start Cassandra and Kafka from the shopping-cart-service-cassandra:**
    ```
    docker-compose up -d
    ```

2. **Run ddl scripts to create tables in Cassandra**
   ```
     docker exec -i shopping-cart-service-cassandra_cassandra_1 cqlsh -t < ddl-scripts/create_tables.cql
     
     docker exec -i shopping-cart-service-cassandra_cassandra_1 cqlsh -t < ddl-scripts/create_user_tables.cql
   ```
3. **Start a first node:**

    ```
    sbt -Dconfig.resource=local1.conf run
    ```

4. **(Optional) Start another node with different ports:**

    ```
    sbt -Dconfig.resource=local2.conf run
    ```
   
5. **Testing the application :-**
   
   *Step-1 : Add 3 socks to a cart:*
 
   ```
   grpcurl -d '{\"cartId\":\"cart1\", \"itemId\":\"socks\", \"quantity\":3}' -plaintext 127.0.0.1:8101 shoppingcart.ShoppingCartService.AddItem
   ```
   *Step-2 : Add 2 t-shirts to the same cart:*
   ```
   grpcurl -d '{\"cartId\":\"cart1\", \"itemId\":\"t-shirt\", \"quantity\":2}' -plaintext 127.0.0.1:8101 shoppingcart.ShoppingCartService.AddItem
   ```
   *Step-3 : Check the quantity of the cart:*
   ```
   grpcurl -d '{\"cartId\":\"cart1\"}' -plaintext 127.0.0.1:8101 shoppingcart.ShoppingCartService.GetCart
   ```
   *Step-4 : Check the popularity of the item:*
   ```
   grpcurl -d '{\"itemId\":\"t-shirt\"}' -plaintext 127.0.0.1:8101 shoppingcart.ShoppingCartService.GetItemPopularity
   ```
   *Step-5 : Check the quantity of the cart:*
   ```
   grpcurl -d '{\"cartId\":\"cart1\"}' -plaintext 127.0.0.1:8101 shoppingcart.ShoppingCartService.Checkout
   ```
   
