package cdc.storm;

import backtype.storm.Config;
import backtype.storm.StormSubmitter;
import backtype.storm.spout.SchemeAsMultiScheme;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.BasicOutputCollector;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.TopologyBuilder;
import backtype.storm.topology.base.BaseBasicBolt;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Tuple;
import backtype.storm.tuple.Values;
import org.apache.commons.lang.StringEscapeUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;
import org.elasticsearch.storm.EsBolt;
import storm.kafka.*;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;

public class KafkaEsTopology {
  private static final Logger LOG = Logger.getLogger(KafkaEsTopology.class);

  public static class BinlogPayloadExpander extends BaseBasicBolt {
    private ObjectMapper mapper;
    private TypeReference<HashMap<String,Object>> typeRef;
    private SimpleDateFormat mysqlDateParser;
    private SimpleDateFormat esDateFormater;


    @Override
    public void prepare(Map stormConf, TopologyContext context) {
      mapper = new ObjectMapper();
      typeRef = new TypeReference<HashMap<String,Object>>() {};

      mysqlDateParser = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
      esDateFormater = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    }

    @Override
    public void execute(Tuple tuple, BasicOutputCollector collector) {
      String binlog = tuple.getString(0);

      HashMap<String, Object> binlogEntry = null;
      try {
        binlogEntry = mapper.readValue(binlog, typeRef);
      } catch (IOException e) {
        LOG.error(e.getLocalizedMessage(), e);
      }

      Map data = (Map)binlogEntry.get("data");
      String table = (String)binlogEntry.get("table");


      // Pass through

      String binlogExpanded = "{\"error\": true}";
      try {
        binlogExpanded = mapper.writeValueAsString(binlogEntry);
      } catch (IOException e) {
        LOG.error(e.getLocalizedMessage(), e);
      }

      collector.emit(new Values(binlogExpanded));
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
      declarer.declare(new Fields("binlog"));
    }

    private String mysqlToEsDate(String date) {
      try {
        return esDateFormater.format(mysqlDateParser.parse(date));
      } catch (ParseException e) {
        return date;
      }
    }
  }

  public static void main(String[] args) throws Exception {

    TopologyBuilder builder = new TopologyBuilder();

    Map<String, String> env = System.getenv();
    for (String envName : env.keySet()) {
      LOG.info(envName + "=" + env.get(envName));
    }

    String esHost = "elasticsearch";
    LOG.info("Elasticsearch Host: " + esHost);

    Map esConf = new HashMap();
    esConf.put("es.nodes", esHost);
    esConf.put("es.storm.bolt.flush.entries.size", "100");
    esConf.put("es.batch.size.entries", "100");
    esConf.put("es.input.json", "true");

    String zkConnString = "kafka:2181";
    String topicName = "maxwell";
    BrokerHosts hosts = new ZkHosts(zkConnString);
    SpoutConfig spoutConfig = new SpoutConfig(hosts, topicName, "", "storm");
    spoutConfig.scheme = new SchemeAsMultiScheme(new StringScheme());
    KafkaSpout kafkaSpout = new KafkaSpout(spoutConfig);

    builder.setSpout("kafka", kafkaSpout, 1);
    builder.setBolt("binlog-expander", new BinlogPayloadExpander(), 1).shuffleGrouping("kafka");
    builder.setBolt("es-bolt", new EsBolt("maxwell/BINLOG", esConf), 1).shuffleGrouping("binlog-expander");

    Config conf = new Config();
    conf.put(Config.TOPOLOGY_DEBUG, true);
    conf.setNumWorkers(1);

    StormSubmitter.submitTopologyWithProgressBar(args[0], conf, builder.createTopology());
  }
}
