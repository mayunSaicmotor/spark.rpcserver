package com.saic.bigdata;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

import org.apache.spark.api.java.function.Function;
import org.apache.spark.sql.DataFrame;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.google.protobuf.RpcController;
import com.google.protobuf.ServiceException;
import com.googlecode.protobuf.pro.duplex.ClientRpcController;
import com.googlecode.protobuf.pro.duplex.RpcClientChannel;
import com.googlecode.protobuf.pro.duplex.execute.ServerRpcController;
import com.saic.bigdata.rpc.Message;
import com.saic.bigdata.rpc.Message.Msg;
import com.saic.bigdata.rpc.Message.ReplyService;
import com.saic.bigdata.rpc.Message.Request;
import com.saic.bigdata.rpc.Message.Response;
import com.saic.bigdata.rpc.Message.RpcService;

import com.saic.bigdata.spark.RunSparkRpcSevice;


/**
 * block interface
 */
public  class BlockRpcService implements RpcService.BlockingInterface, Serializable
{
    private static final long serialVersionUID = 1L;
	
	private Logger log = LoggerFactory.getLogger(getClass());

	//@Override
	public Response call(RpcController controller, Request request) throws ServiceException {
		if ( controller.isCanceled() ) {
			return null;
		}
		log.info("received data: ");
		log.info("sql : "+request.getSql());

		//log.info("serviceName : "+request.getServiceName());
		//log.info("methodName : "+request.getMethodName());
		//log.info("params : "+request.getParams());
		
		RpcClientChannel channel = ServerRpcController.getRpcChannel(controller);
		ReplyService.BlockingInterface clientService = ReplyService.newBlockingStub(channel);
		ClientRpcController clientController = channel.newRpcController();
		clientController.setTimeoutMs(3000);
		//调用过程反馈消息
		Msg msg = Msg.newBuilder().setContent("success.").build();
		clientService.call(clientController, msg);
		
		//List<Rx5DataModel> rx5List = new ArrayList<Rx5DataModel>();
		Response.Builder rsb = Response.newBuilder();
		List<Message.SaicRecord> datas = null;
		List<String> cols = null;
		if (RunSparkRpcSevice.cc != null) {
			DataFrame dataFrame = RunSparkRpcSevice.cc.sql(request.getSql());
			
			// set column names
			StructType st = dataFrame.schema();
			cols = Arrays.asList(st.fieldNames());
			StructField[] sfArr = st.fields();
			log.info("StructField[]: " + sfArr);
			DataType dt = sfArr[0].dataType();
			log.info("dt.typeName: " + dt.typeName());
			
			//Row[] rows = df.collect();

			List<String[]> dataArrList = dataFrame.javaRDD().map(new Function<Row, String[]>() {
				  public String[] call(Row row) {
					  
					 
						String[] dataArr = new String[row.size()];
						for(int i =0; i< row.size();i++){
							Object obj = row.get(i);
							dataArr[i] = obj==null?"":obj.toString();
							
						}
						//srb.addAllRecords(values)
				
						//Rx5DataModel.Builder rx5DataBuilder = Rx5DataModel.newBuilder();
						//rx5DataBuilder.setVin(row.get(0).toString());
						//saicRecords.add(srb.build());
						
				    return dataArr;
				  }
				}).collect();
			
			datas = Lists.newArrayList();
			for(String[] dataArr : dataArrList){
				
				Message.SaicRecord.Builder srb = Message.SaicRecord.newBuilder();
				srb.addAllRecords(Arrays.asList(dataArr));
				datas.add(srb.build());
			}
/*			datas = df.javaRDD().map(new Function<Row, Message.SaicRecord>() {
				  public Message.SaicRecord call(Row row) {
					  
					  Message.SaicRecord.Builder srb = Message.SaicRecord.newBuilder();
						
						for(int i =0; i< row.size();i++){
							Object obj = row.get(i);
							srb.addRecords(obj==null?"":obj.toString());
						}
						//srb.addAllRecords(values)
				
						//Rx5DataModel.Builder rx5DataBuilder = Rx5DataModel.newBuilder();
						//rx5DataBuilder.setVin(row.get(0).toString());
						//saicRecords.add(srb.build());
						
				    return srb.build();
				  }
				}).collect();*/
			
			
		/*	//set values
			for (Row row : rows) {
				SaicRecord.Builder srb = SaicRecord.newBuilder();
				srb.addAllRecords(values)
		
				//Rx5DataModel.Builder rx5DataBuilder = Rx5DataModel.newBuilder();
				//rx5DataBuilder.setVin(row.get(0).toString());
				saicRecords.add(srb.build());
				//rx5List.add(rx5DataBuilder.build());
			}*/
			
		// for test	
		}else{
			cols = Lists.newArrayList();
			datas = Lists.newArrayList();
			cols.add("vin");
			cols.add("data_date");
			
			Message.SaicRecord.Builder srb = Message.SaicRecord.newBuilder();
			srb.addRecords("LSJW2676XFS061997");
			srb.addRecords("2016-10-10");
			
			datas.add(srb.build());
			
			
		}
		rsb.addAllCols(cols);
		rsb.addAllData(datas);
		rsb.setCode(0).setMsg("completed");      
			
	/*	  Rx5DataModel.Builder rx5DataBuilder = Rx5DataModel.newBuilder();
		  rx5DataBuilder.setVin("LSJW2676XFS061997");
		  rx5DataBuilder.setDataDate("2016-07-08");
		   
		  rsb.setCode(0).setMsg("completed");
		  rx5List.add(rx5DataBuilder.build());
		  rsb.addAllCols(cols);
		  rsb.addAllData(values)*/
		  
		Response response = rsb.build();
		return response;
	}
	
}
