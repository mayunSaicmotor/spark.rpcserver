package com.saic.bigdata.spark;


import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.sql.CarbonContext;
//import org.apache.spark.sql.Dataset;
//import org.apache.spark.sql.Row;
//import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.DataFrame;
import org.apache.spark.sql.types.StructType;

import com.saic.bigdata.BlockServer;
import com.saic.bigdata.rpc.Message.SaicRecord;

public class RunSparkRpcSevice {

	  static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(RunSparkRpcSevice.class);
	 static JavaSparkContext jsc = null;
	 public static CarbonContext cc = null;
	 
	public static void main( String[] args )
    {

		//TODO
		SaicRecord.Builder srb= SaicRecord.newBuilder();
		
		//OverrideCatalog
		logger.info("start to run spark rpc service");
		
       
        SparkConf conf=new SparkConf().setMaster("yarn-client").setAppName("carbonDataSevice");
        jsc =new JavaSparkContext(conf);

        
        String storeLocation = "hdfs://nameservice2/carbon/data/";
        String metaLocation = "hdfs://nameservice2/carbon/carbonstore/";
        		
        //String storeLocation = "c:/data/";
        //String metaLocation = "c:/meta/";
        cc = new CarbonContext(JavaSparkContext.toSparkContext(jsc), storeLocation, metaLocation);
        logger.info("run use default");
        cc.sql("use default");
        logger.info("run show tables");
        DataFrame df = cc.sql("show tables");
        //DataFrame df = cc.sql("select vin  from ep11data_parquet_all limit 10");
        df.printSchema();
        df.show();
        
        logger.info("run sql: select vin  from ep11data_parquet_all limit 10");
        df = cc.sql("select vin  from ep11data_parquet_all limit 10");
        
        df.take(10);
        df.show();
        StructType st = df.schema();
        logger.info(st == null ? "":st.toString());
        st.fieldNames();
        //df.javaRDD().ma
        
        
/*         String readme="C:/Users/tansheng-10/Documents/software/spark-2.0.2-bin-hadoop2.7/README.md";
        JavaRDD<String> logData=jsc.textFile(readme).cache();
        Long num=logData.filter(new Function<String,Boolean>(){
        	public Boolean call(String s){
        		return s.contains("a");
        	.
        	
        }).count();
        		
        logger.info("the count of word a is "+num);*/
        
        
        
/*		SparkSession spark = SparkSession.builder()
				  .appName("Java Spark SQL basic example")
				  .config("spark.some.config.option", "some-value")
				  .getOrCreate();*/
		
        //SparkContext sc = new SparkContext(conf);
        //SQLContext sqlContext = new SQLContext(sc);
        
        //Dataset<Row> df = spark.read().json("examples/src/main/resources/people.json");
/*        
        SQLContext sqlContext = new org.apache.spark.sql.SQLContext(jsc);
        DataFrame df = sqlContext.read().parquet("d:\\region.parquet");
        df.show();*/
        

       
        
		//start rpc server;
		String server = "10.129.96.18";
		int port = 7777;
		
		if(args!=null && args.length >=2){
			
			server = args[0];
			port = Integer.parseInt(args[1]);
		}
		
		BlockServer.startRpcServer(server, port);
		logger.info("end to run spark service");
        
    }

	
	
}
