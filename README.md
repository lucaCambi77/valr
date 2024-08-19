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

## Endpoint with Postman :

Account and Buy/Sell endpoints are available as Postman collections [here](/postman/). Basic authentication is required for all endpoints except for user creation.
