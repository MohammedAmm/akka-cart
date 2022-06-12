## Akka Cart
Getting my hands on Akka streams, Akka actor(for asyc), event sourcing, ... scala
Getting to know about DDD
### Available Routes

#### Adding a new order

```shell
curl -X POST http://localhost:8080/orders   -H 'Content-Type: application/json'
```

#### Adding Product(price) to existing order 

```shell
curl -X POST http://localhost:8080/orders/{id}   -H 'Content-Type: application/json'   -d '{"price": -100.0}'

```

#### Retrieving the details of an order

```shell
curl -v http://localhost:8080/orders/{id}

```
#### Requirements
sbt version in this project: 1.6.2
Docker-compose

### How to start
In root directory run:
```
docker-compose up -d
```
then:
```
sbt build
```
then:
```
sbt run
```
