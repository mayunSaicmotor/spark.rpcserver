package com.saic.bigdata;

import java.io.IOException;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.BlockingService;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.ServiceException;
import com.googlecode.protobuf.pro.duplex.CleanShutdownHandler;
import com.googlecode.protobuf.pro.duplex.ClientRpcController;
import com.googlecode.protobuf.pro.duplex.PeerInfo;
import com.googlecode.protobuf.pro.duplex.RpcClientChannel;
import com.googlecode.protobuf.pro.duplex.RpcConnectionEventNotifier;
import com.googlecode.protobuf.pro.duplex.client.DuplexTcpClientPipelineFactory;
import com.googlecode.protobuf.pro.duplex.client.RpcClientConnectionWatchdog;
import com.googlecode.protobuf.pro.duplex.execute.RpcServerCallExecutor;
import com.googlecode.protobuf.pro.duplex.execute.ThreadPoolCallExecutor;
import com.googlecode.protobuf.pro.duplex.listener.RpcConnectionEventListener;
import com.googlecode.protobuf.pro.duplex.logging.CategoryPerServiceLogger;
import com.googlecode.protobuf.pro.duplex.util.RenamingThreadFactoryProxy;
import com.saic.bigdata.rpc.Message;
import com.saic.bigdata.rpc.Message.ReplyService;
import com.saic.bigdata.rpc.Message.Request;
import com.saic.bigdata.rpc.Message.RpcService;
import com.saic.bigdata.rpc.Message.SaicRecord;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;

public class BlockClient {
	
	private  RpcClientChannel channel = null;
	private  RpcService.BlockingInterface blockingService;

	private static Logger log = LoggerFactory.getLogger(BlockClient.class);

	public BlockClient(){
		
		log.info("create BlockClient");
	}
	
	public static void main(String[] args)  {
		BlockClient client = new BlockClient();
		//default
		String server = "127.0.0.1";
		int port = 7777;
		String sql = "select vin  from ep11data_parquet_all limit 100";
		
		if(args!=null && args.length >=2){
			
			server = args[0];
			port = Integer.parseInt(args[1]);
		}
		
		if(args!=null && args.length >=3){
			
			sql = args[2];
		}
		CleanShutdownHandler shutdownHandler = null;
		try{
			shutdownHandler = client.setupClient(server, port);
			
			client.requestSqlQuery(sql);
		}catch(Exception e){
			log.error("error happen: ", e);
		}finally{
			client.close(shutdownHandler);
		}
	}


	public  void close(CleanShutdownHandler shutdownHandler) {
		// close channel
		
		if (channel != null) {
			channel.close();
		}
		
		if(shutdownHandler != null){
			shutdownHandler.shutdown();
		}
	

		
		System.exit(0);
	}


	public  Message.Response requestSqlQuery(String sql) throws IOException, ServiceException, InterruptedException {
		

		//while (true && channel != null) {
			blockingService = RpcService.newBlockingStub(channel);
			final ClientRpcController controller = channel.newRpcController();
			controller.setTimeoutMs(30000);

			//Params params = Params.newBuilder().setKey("name").setValue("jack").build();
			
			Request request = Request.newBuilder().setSql(sql).build();
			
			long start = System.currentTimeMillis();
			//block call
			Message.Response response = blockingService.call(controller, request);			
			
			print(response);
			
			long end = System.currentTimeMillis();
			log.info("request time: " + (end -start));
			
			return response;
			//Thread.sleep(100000);
		//}
	}


	public  CleanShutdownHandler setupClient(String serverName, int port) throws IOException {
		//PeerInfo client = new PeerInfo("10.129.96.19", 54321);
		PeerInfo server = new PeerInfo(serverName, port);

		DuplexTcpClientPipelineFactory clientFactory = new DuplexTcpClientPipelineFactory();
		// force the use of a local port
		// - normally you don't need this
		//clientFactory.setClientInfo(client);

		ExtensionRegistry r = ExtensionRegistry.newInstance();
		Message.registerAllExtensions(r);
		clientFactory.setExtensionRegistry(r);

		clientFactory.setConnectResponseTimeoutMillis(60000);
		RpcServerCallExecutor rpcExecutor = new ThreadPoolCallExecutor(3, 10);
		clientFactory.setRpcServerCallExecutor(rpcExecutor);

		// RPC payloads are uncompressed when logged - so reduce logging
		CategoryPerServiceLogger logger = new CategoryPerServiceLogger();
		logger.setLogRequestProto(false);
		logger.setLogResponseProto(false);
		clientFactory.setRpcLogger(logger);

		// Set up the event pipeline factory.
		// setup a RPC event listener - it just logs what happens
		RpcConnectionEventNotifier rpcEventNotifier = new RpcConnectionEventNotifier();

		final RpcConnectionEventListener listener = new RpcConnectionEventListener() {

			//@Override
			public void connectionReestablished(RpcClientChannel clientChannel) {
				log.info("connectionReestablished " + clientChannel);
				channel = clientChannel;
			}

			//@Override
			public void connectionOpened(RpcClientChannel clientChannel) {
				log.info("connectionOpened " + clientChannel);
				channel = clientChannel;
			}

			//@Override
			public void connectionLost(RpcClientChannel clientChannel) {
				log.info("connectionLost " + clientChannel);
			}

			//@Override
			public void connectionChanged(RpcClientChannel clientChannel) {
				log.info("connectionChanged " + clientChannel);
				channel = clientChannel;
			}
		};
		rpcEventNotifier.addEventListener(listener);
		clientFactory.registerConnectionEventListener(rpcEventNotifier);
		//注册服务 reply阻塞服务，用于反馈
		BlockingService blockingReplyService = ReplyService.newReflectiveBlockingService(new BlockReplyService());
		clientFactory.getRpcServiceRegistry().registerService(blockingReplyService);	

		Bootstrap bootstrap = new Bootstrap();
		EventLoopGroup workers = new NioEventLoopGroup(16, new RenamingThreadFactoryProxy("workers", Executors.defaultThreadFactory()));

		bootstrap.group(workers);
		bootstrap.handler(clientFactory);
		bootstrap.channel(NioSocketChannel.class);
		bootstrap.option(ChannelOption.TCP_NODELAY, true);
		bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000);
		bootstrap.option(ChannelOption.SO_SNDBUF, 1048576);
		bootstrap.option(ChannelOption.SO_RCVBUF, 1048576);

		RpcClientConnectionWatchdog watchdog = new RpcClientConnectionWatchdog(clientFactory, bootstrap);
		rpcEventNotifier.addEventListener(watchdog);
		watchdog.start();

		CleanShutdownHandler shutdownHandler = new CleanShutdownHandler();
		shutdownHandler.addResource(workers);
		shutdownHandler.addResource(rpcExecutor);

		clientFactory.peerWith(server, bootstrap);
		
		//get blockingService
		blockingService = RpcService.newBlockingStub(channel);
		
	    //TODO add shutdownhook
        Runtime.getRuntime().addShutdownHook(new ShutDownHook(shutdownHandler));
        return shutdownHandler;
	}
	
	
	  static void print(Message.Response response) {

		   
		    	log.info("col names: " + response.getColsList());
		
		    
		    for (SaicRecord sr: response.getDataList()) {
		    	log.info("record values: " + sr.getRecordsList());
		    	//System.out.println("vin: " + rx5Data.getVin());
		      //System.out.println("  Latitude: " + rx5Data.getLatitude());
		      //System.out.println("  Longitude: " + rx5Data.getLongitude());
		    }
		    
		    log.info("total count: " + response.getDataList().size());
		  }

}
