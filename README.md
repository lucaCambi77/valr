# VALR | Assessment | Backend Engineer

Build a working, in-memory order book to place a limit order with order matching including the ability to view all open orders.
The API should have at-least the following endpoints:

1. Get order book: Use this API as a reference for request and response payload
   https://api.valr.com/BTCZAR/orderbook
2. Submit limit order: Use this API as a reference for request and response payload
   https://api.valr.com/v1/orders/limit. This can be a very simple data structure and does not
   need to cater for all the advanced usages. Feel free to ask if you have any questions here.
   (see https://docs.valr.com for the API reference if needed)
3. Recent Trades : Similar to https://api.valr.com/BTCZAR/tradehistory

## Requirements :

* Java17
* Gradle

## Build

* To build the application

```bash
./gradlew clean build
```

## Run

* To run the application

```bash
java -jar build/libs/task.jar
```

or

```bash
./gradlew bootRun
```

The application will start listening on port 8080.

## Solution :

- **The application provides endpoints for creating users and performing basic operations related to asset exchanges and limit order management. For simplicity, the exchange service currently supports only the `USDCBTC` currency pair.**

- **To execute a trade, the exchange service matches a buyer with a seller based on the specified quantity and the given buy or sell price.**

- **When an order is placed, it may not be filled immediately. The order is stored in the order book until it is either fulfilled or cancelled.**

- **Users can monitor the status of their orders.**

- **Limit orders are supported with a GTC (Good Till Cancelled) option. Orders remain in the order book until they are fulfilled or cancelled, and they can be partially refunded if not fully filled.**

- **All trades are recorded in a trade history, which users can access at any time.**

- **Fee and Reward Calculation**
    - **Transaction Fee**: A percentage-based fee is applied to each trade.
    - **Maker Reward**: Users who provide liquidity by placing limit orders that are not immediately matched receive a reward.

## API Endpoints

### Account Endpoints

- **`POST /account/create`**
    - **Description**: Creates a new user account. The request body must include the user's name, email, and password. The password is securely hashed before being stored.
    - **Response**: Returns the unique user ID of the newly created account.
    - **HTTP Status**: `201 Created`

- **`PATCH /account/wallet`**
    - **Description**: Updates the user's wallet with new base and quote balances. The request body should include the balances to be updated.
    - **Response**: Returns the updated user details, including the wallet balances.
    - **HTTP Status**: `200 OK`

### Exchange Endpoints

- **`GET /{currencyPair}/orderbook`**
    - **Description**: Retrieves the current order book for a specified currency pair, showing both buy and sell orders.
    - **Response**: Returns a summary of the order book, including the sequence number and the last change timestamp.
    - **HTTP Status**: `200 OK`

- **`GET /{currencyPair}/tradehistory`**
    - **Description**: Fetches the trade history for a specified currency pair. You can specify the number of recent trades to retrieve using the `limit` parameter.
    - **Response**: Returns a list of recent trades for the specified currency pair.
    - **HTTP Status**: `200 OK`

- **`POST /orders/limit`**
    - **Description**: Places a limit order for buying or selling a specified currency pair. The order includes details such as price, quantity, and order side (buy/sell).
    - **Response**: Returns the ID of the placed order.
    - **HTTP Status**: `202 Accepted`

- **`GET /orders/{currencyPair}/status/{orderId}`**
    - **Description**: Retrieves the status of a specific order for a given currency pair. This endpoint allows monitoring the progress and fulfillment of an order.
    - **Response**: Returns the current status of the specified order.
    - **HTTP Status**: `200 OK`

- **`DELETE /orders/order`**
    - **Description**: Cancels an existing order for a specified currency pair. The request body should include the order ID and currency pair.
    - **Response**: Returns an empty response indicating the order has been successfully canceled.
    - **HTTP Status**: `200 OK`

## Endpoints with Postman :

Account and Buy/Sell endpoints are available as [Postman](/postman) collections. Basic authentication is required for all endpoints except for user creation.