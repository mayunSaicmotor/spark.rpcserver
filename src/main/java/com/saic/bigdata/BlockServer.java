package com.saic.bigdata;

import java.util.List;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.BlockingService;
import com.google.protobuf.ExtensionRegistry;
import com.googlecode.protobuf.pro.duplex.CleanShutdownHandler;
import com.googlecode.protobuf.pro.duplex.PeerInfo;
import com.googlecode.protobuf.pro.duplex.RpcClientChannel;
import com.googlecode.protobuf.pro.duplex.RpcConnectionEventNotifier;
import com.googlecode.protobuf.pro.duplex.execute.RpcServerCallExecutor;
import com.googlecode.protobuf.pro.duplex.execute.ThreadPoolCallExecutor;
import com.googlecode.protobuf.pro.duplex.listener.RpcConnectionEventListener;
import com.googlecode.protobuf.pro.duplex.logging.CategoryPerServiceLogger;
import com.googlecode.protobuf.pro.duplex.server.DuplexTcpServerPipelineFactory;
import com.googlecode.protobuf.pro.duplex.util.RenamingThreadFactoryProxy;
import com.saic.bigdata.rpc.Message;
import com.saic.bigdata.rpc.Message.RpcService;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

public class BlockServer {
	private static Logger log = LoggerFactory.getLogger(BlockServer.class);

	public static void main(String[] args) {
		String server = "127.0.0.1";
		int port = 7777;
		
		if(args!=null && args.length >=2){
			
			server = args[0];
			port = Integer.parseInt(args[1]);
		}
		
		startRpcServer(server, port);
	}

	public static void startRpcServer(String server, int port) {
		PeerInfo serverInfo = new PeerInfo(server, port);

		// RPC payloads are uncompressed when logged - so reduce logging
		CategoryPerServiceLogger logger = new CategoryPerServiceLogger();
		logger.setLogRequestProto(false);
		logger.setLogResponseProto(false);

		// Configure the server.
		DuplexTcpServerPipelineFactory serverFactory = new DuplexTcpServerPipelineFactory(serverInfo);

		//扩展
		ExtensionRegistry r = ExtensionRegistry.newInstance();
		Message.registerAllExtensions(r);
		serverFactory.setExtensionRegistry(r);

		RpcServerCallExecutor rpcExecutor = new ThreadPoolCallExecutor(10, 100);
		serverFactory.setRpcServerCallExecutor(rpcExecutor);
		serverFactory.setLogger(logger);

		// setup a RPC event listener - it just logs what happens
		RpcConnectionEventNotifier rpcEventNotifier = new RpcConnectionEventNotifier();
		
		RpcConnectionEventListener listener = new RpcConnectionEventListener() {
			//@Override
			public void connectionReestablished(RpcClientChannel clientChannel) {
				log.info("connectionReestablished " + clientChannel);
			}

			//@Override
			public void connectionOpened(RpcClientChannel clientChannel) {
				log.info("connectionOpened " + clientChannel);
			}

			//@Override
			public void connectionLost(RpcClientChannel clientChannel) {
				log.info("connectionLost " + clientChannel);
			}

			//@Override
			public void connectionChanged(RpcClientChannel clientChannel) {
				log.info("connectionChanged " + clientChannel);
			}
		};
		rpcEventNotifier.setEventListener(listener);
		serverFactory.registerConnectionEventListener(rpcEventNotifier);
		
       //注册服务 阻塞RPC服务
  		BlockingService blockingService = RpcService.newReflectiveBlockingService(new BlockRpcService());
  	    serverFactory.getRpcServiceRegistry().registerService(true, blockingService);
      	    
	    ServerBootstrap bootstrap = new ServerBootstrap();
        EventLoopGroup boss = new NioEventLoopGroup(10,new RenamingThreadFactoryProxy("boss", Executors.defaultThreadFactory()));
        EventLoopGroup workers = new NioEventLoopGroup(50,new RenamingThreadFactoryProxy("worker", Executors.defaultThreadFactory()));
        bootstrap.group(boss,workers);
        bootstrap.channel(NioServerSocketChannel.class);
        bootstrap.option(ChannelOption.SO_SNDBUF, 1048576);
        bootstrap.option(ChannelOption.SO_RCVBUF, 1048576);
        bootstrap.childOption(ChannelOption.SO_RCVBUF, 1048576);
        bootstrap.childOption(ChannelOption.SO_SNDBUF, 1048576);
        bootstrap.option(ChannelOption.TCP_NODELAY, true);
        bootstrap.childHandler(serverFactory);
        bootstrap.localAddress(serverInfo.getPort());

		CleanShutdownHandler shutdownHandler = new CleanShutdownHandler();
        shutdownHandler.addResource(boss);
        shutdownHandler.addResource(workers);
        shutdownHandler.addResource(rpcExecutor);
        
    	// Bind and start to accept incoming connections.
        bootstrap.bind();
        log.info("Serving " + bootstrap);
        
        //TODO add shutdownhook
        Runtime.getRuntime().addShutdownHook(new ShutDownHook(shutdownHandler));
        
        while ( true ) {
            List<RpcClientChannel> clients = serverFactory.getRpcClientRegistry().getAllClients();
            log.info("Number of clients="+ clients.size());
        	try {
				Thread.sleep(100000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
        }
	}
}
