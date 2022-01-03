## Caramel Cat (CACA)

Not another dog coin. [CaramelCat](https://caramelcat.org/)

Unlike other cryptos, this one you can mine. It's very easy, even if you're a dog.

## How to run

download latest release then

**java -jar caramelcat-0.8-jar-with-dependencies.jar**

or 

**java -jar caramelcat-0.8-jar-with-dependencies.jar YOUR_ADDRESS**

to instant start mining (replace YOUR_ADDRESS with your address)

## Mining

* **mine** (to stop, call again with "address":"")

```
curl --header "Content-Type: application/json" --request POST --data '{"method":"mine", "address": "miiOu1UjS4GCZ9kTTEHwwX7Fawex3kI9y72axqiDzcM"}' http://localhost:9443/meow
```

## API

* **getnewaddress**

```
curl --header "Content-Type: application/json" --request POST --data '{"method":"getnewaddress"}' http://localhost:9443/meow
```

* **getbalance**

```
curl --header "Content-Type: application/json" --request POST --data '{"method":"getbalance", "address": "miiOu1UjS4GCZ9kTTEHwwX7Fawex3kI9y72axqiDzcM"}' http://localhost:9443/meow
```

* **sendtoaddress**

```
curl --header "Content-Type: application/json" --request POST --data '{"method":"sendtoaddress", "to":"qlKHr8HLdl4WSil4U1Yy3VqnHY3DpKO5KeDw5RYc4RU", "from": "miiOu1UjS4GCZ9kTTEHwwX7Fawex3kI9y72axqiDzcM", "amount":2}' http://localhost:9443/meow
```

## Show all balances

* **all**

```
curl --header "Content-Type: application/json" --request POST --data '{"method":"all"}' https://caramelcat.org/meow
```

