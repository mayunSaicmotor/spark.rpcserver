package com.saic.bigdata;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.googlecode.protobuf.pro.duplex.CleanShutdownHandler;

public class ShutDownHook extends Thread {

    private Log logger = LogFactory.getLog(getClass());

    private CleanShutdownHandler cleanShutdownHandler;

    public ShutDownHook(CleanShutdownHandler applicationContext ){
        super();
        this.cleanShutdownHandler = applicationContext;
    }

    @Override  
    public void run() {
        logger.info("Start clean the login info.");
       // cleanShutdownHandler.shutdown();
        logger.info("Socket server shutdown");
    }
}
