package com.manu.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.write.DefaultWriteRequest;
import org.apache.mina.core.write.WriteRequest;
import org.apache.mina.filter.codec.ProtocolEncoder;
import org.apache.mina.filter.codec.ProtocolEncoderOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.GeneratedMessage.Builder;
import com.google.protobuf.Message;
import com.google.protobuf.MessageLite;
import com.google.protobuf.MessageOrBuilder;
import com.manu.dynasty.util.ProtobufUtils;
import com.manu.network.msg.ProtobufMsg;

/**
 * @author 康建虎
 * 
 */
public class ProtoBuffEncoder implements ProtocolEncoder {
	private static Logger log = LoggerFactory.getLogger(ProtoBuffEncoder.class);
	private static boolean battleInfoRecord = false;
	private static List<Map<Integer, Integer>> battleInfoList = new ArrayList<Map<Integer,Integer>>();
	
	@Override
	public void dispose(IoSession arg0) throws Exception {
		// TODO Auto-generated method stub

	}

	@Override
	public void encode(IoSession arg0, Object msg, ProtocolEncoderOutput out)
			throws Exception {
		if(msg instanceof ProtobufMsg){
			ProtobufMsg p = (ProtobufMsg) msg;
			encode(out,p.builder.build(), p.id);
		}else if (msg instanceof Builder) {
			Builder mob = (Builder) msg;
			Message b = mob.build();
			encode(arg0, b, out);
			log.warn("应该发送Message，而不是builder {}", msg.getClass().getName());
		}else if (msg instanceof MessageLite) {
			MessageLite lite = (MessageLite) msg;
			Integer protoId = ProtobufUtils.protoClassToIdMap.get(msg.getClass());
			if (protoId == null) {
				log.error("没有找到协议号 {}", msg.getClass().getName());
				return;
			}
			encode(out, lite, protoId);
		}else if(msg instanceof Short){//只发送协议号就够了。
			IoBuffer buf = IoBuffer.allocate(0 + 4 + 2);
			buf.putInt(0 + 2);//数据（协议号和逻辑数据）长度
			buf.putShort((Short)msg);
//			buf.put(body);
			buf.flip();
			out.write(buf);
			log.debug("send protocol id {}", msg);
		} else {
			if (msg instanceof String) {// 为了用java客户端模拟发送藤讯的验证串
				byte[] bytes = ((String) msg).getBytes("UTF-8");
				IoBuffer buffer = IoBuffer.allocate(bytes.length );
				buffer.put(bytes);

				buffer.flip();
				out.write(buffer);
			}else{

			log.warn("not MessageLite:{}", msg == null ? "null" : msg
					.getClass().getName());
			}
		}
	}

	protected void encode(ProtocolEncoderOutput out,
			MessageLite lite, Integer protoId) {
		byte[] body = lite.toByteArray();
		IoBuffer buf = IoBuffer.allocate(body.length + 4 + 2);
		buf.putInt(body.length + 2);//数据（协议号和逻辑数据）长度
		buf.putShort(protoId.shortValue());
		buf.put(body);
		buf.flip();
		synchronized (out) {//防止flush里的bug
			 // Creates an empty writeRequest containing the destination
            //WriteRequest writeRequest = new DefaultWriteRequest(null, null, destination);
			//这个会出IllegalArgumentException，因为message是null
			out.write(buf);
			out.flush();
		}
		if (protoId != PD.Spirite_Move )
			log.debug("发送数据：协议号 {} 数据长度 {} {}",  protoId,
					body.length, lite.getClass().getSimpleName() );
		if(battleInfoRecord) {
			switch(protoId.intValue()) {
			case PD.ZHANDOU_INIT_RESP:
				Map<Integer, Integer> map = new HashMap<Integer, Integer>(1);
				map.put(protoId, body.length);
				battleInfoList.add(map);
				break;
			default:
				break;
			}
		}
	}

	public static boolean isBattleInfoRecord() {
		return battleInfoRecord;
	}

	public static void setBattleInfoRecord(boolean battleInfoRecord) {
		ProtoBuffEncoder.battleInfoRecord = battleInfoRecord;
	}

	public static List<Map<Integer, Integer>> getBattleInfoList() {
		return battleInfoList;
	}
	
}