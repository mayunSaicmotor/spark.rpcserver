package com.saic.bigdata;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.RpcController;
import com.google.protobuf.ServiceException;
import com.saic.bigdata.rpc.Message.Msg;
import com.saic.bigdata.rpc.Message.ReplyService;

/**
 * 阻塞反馈服务实现
 */
public class BlockReplyService implements ReplyService.BlockingInterface{
	
	private Logger log = LoggerFactory.getLogger(getClass());

	//@Override
	public Msg call(RpcController controller, Msg request) throws ServiceException {
		log.debug("接收反馈消息:"+request.getContent());
		if ( controller.isCanceled() ) {
			return null;
		}
		return Msg.newBuilder().setContent("收到反馈成功.").build();
	}
}
