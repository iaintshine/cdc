# CDC (Change Data Capture) 
Change Data Capture platform example using MySQL, Maxwell, Storm and Elasticsearch.


## Environment Setup

I assume you have locally installed storm, docker-machine, docker-compose and MySQL running on port 3306.

```
docker-machine create --driver virtualbox --virtualbox-memory 4096 --virtualbox-cpu-count 2 cluster-cdc
```

```
MYSQL_HOST=10.0.2.2 MYSQL_PORT=3306 MYSQL_USER=root MYSQL_PASSWORD=root ADVERTISED_HOST=$(docker-machine ip cluster-cdc) docker-compose up
```

## Build Storm topology

To manage java versions I use jenv. 

```
jenv local 1.6
jenv exec mvn package -DskipTests=true
```

The example topology works in a pass through mode, where you can easily plug in and experiment with custom analysis and data format manipulation. 

## Run Storm topology

```
jenv exec storm jar target/cdc-topology-1.0.0-jar-with-dependencies.jar  cdc.storm.KafkaEsTopology KafkaEs -c nimbus.host=$(docker-machine ip cluster-cdc) -c nimbus.thrift.port=49627
```

## Exposed ports

### Storm UI

```
open http://$(docker-machine ip cluster-cdc):49080
```

### Kibana

```
open http://$(docker-machine ip cluster-cdc):5601
```

## Cleanup

```
docker-machine stop cluster-cdc
```
