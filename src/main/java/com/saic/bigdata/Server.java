package com.saic.bigdata;


import java.util.List;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.RpcCallback;
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

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;  
  
public class Server {  
  
    private static Logger log = LoggerFactory.getLogger(Server.class);  
      
    public static void main(String[] args) {  
  
        PeerInfo serverInfo = new PeerInfo("127.0.0.1", 12345);  
  
        // RPC payloads are uncompressed when logged - so reduce logging  
        // �ر� ������־ ����com.googlecode.protobuf.pro.duplex.logging.nulllogger���Դ���ģ�������¼�κ�categoryperservicelogger��  
        CategoryPerServiceLogger logger = new CategoryPerServiceLogger();  
        logger.setLogRequestProto(false);  
        logger.setLogResponseProto(false);  
          
        // ����server  
        DuplexTcpServerPipelineFactory serverFactory = new DuplexTcpServerPipelineFactory(serverInfo);  
        // �����̳߳�  
        RpcServerCallExecutor rpcExecutor = new ThreadPoolCallExecutor(10, 10);  
        serverFactory.setRpcServerCallExecutor(rpcExecutor);  
        serverFactory.setLogger(logger);  
          
        // �ص�  
        final RpcCallback<Message.Msg> clientResponseCallback = new RpcCallback<Message.Msg>() {  
            //@Override  
            public void run(Message.Msg parameter) {  
                log.info("����  " + parameter);  
            }  
        };  
        // ����rpc�¼�����  
        RpcConnectionEventNotifier rpcEventNotifier = new RpcConnectionEventNotifier();  
        RpcConnectionEventListener listener = new RpcConnectionEventListener() {  
            //@Override  
            public void connectionReestablished(RpcClientChannel clientChannel) {  
                log.info("���½������� " + clientChannel);  
                clientChannel.setOobMessageCallback(Message.Msg.getDefaultInstance(), clientResponseCallback);  
            }  
  
            //@Override  
            public void connectionOpened(RpcClientChannel clientChannel) {  
                log.info("���Ӵ�" + clientChannel);  
                clientChannel.setOobMessageCallback(Message.Msg.getDefaultInstance(), clientResponseCallback);  
            }  
  
           // @Override  
            public void connectionLost(RpcClientChannel clientChannel) {  
                log.info("���ӶϿ�" + clientChannel);  
            }  
  
            //@Override  
            public void connectionChanged(RpcClientChannel clientChannel) {  
                log.info("���Ӹı�" + clientChannel);  
            }  
        };  
  
        rpcEventNotifier.setEventListener(listener);  
        serverFactory.registerConnectionEventListener(rpcEventNotifier);  
        //��ʼ��netty  
        ServerBootstrap bootstrap = new ServerBootstrap();  
        EventLoopGroup boss = new NioEventLoopGroup(2, new RenamingThreadFactoryProxy("boss", Executors.defaultThreadFactory()));  
        EventLoopGroup workers = new NioEventLoopGroup(16, new RenamingThreadFactoryProxy("worker", Executors.defaultThreadFactory()));  
        bootstrap.group(boss, workers);  
        bootstrap.channel(NioServerSocketChannel.class);  
        bootstrap.option(ChannelOption.SO_SNDBUF, 1048576);  
        bootstrap.option(ChannelOption.SO_RCVBUF, 1048576);  
        bootstrap.childOption(ChannelOption.SO_RCVBUF, 1048576);  
        bootstrap.childOption(ChannelOption.SO_SNDBUF, 1048576);  
        //bootstrap.childOption(ChannelOption.SO_KEEPALIVE, true);   
        bootstrap.option(ChannelOption.TCP_NODELAY, true);  
          
        bootstrap.childHandler(serverFactory);  
        bootstrap.localAddress(serverInfo.getPort());  
          
        //�ر��ͷ���Դ  
        CleanShutdownHandler shutdownHandler = new CleanShutdownHandler();  
        shutdownHandler.addResource(boss);  
        shutdownHandler.addResource(workers);  
        shutdownHandler.addResource(rpcExecutor);  
  
        bootstrap.bind();  
        log.info("���������� " + bootstrap);  
  
        //��ʱ��ͻ��˷�����Ϣ  
        while (true) {  
            List<RpcClientChannel> clients = serverFactory.getRpcClientRegistry().getAllClients();  
            for (RpcClientChannel client : clients) {  
                //������Ϣ  
                Message.Msg msg = Message.Msg.newBuilder().setContent("Server "+ serverFactory.getServerInfo() + " OK@" + System.currentTimeMillis()).build();  
                  
                ChannelFuture oobSend = client.sendOobMessage(msg);  
                if (!oobSend.isDone()) {  
                    log.info("Waiting for completion.");  
                    oobSend.syncUninterruptibly();  
                }  
                if (!oobSend.isSuccess()) {  
                    log.warn("OobMessage send failed." + oobSend.cause());  
                }  
  
            }  
 /*           log.info("Sleeping 5s before sending request to all clients.");  
            try {  
                Thread.sleep(5000);  
            } catch (InterruptedException e) {  
                e.printStackTrace();  
            } */ 
        }  
    }  
} 