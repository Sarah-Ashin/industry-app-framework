package com.cosmoplat.bussiness.consumer;

import com.alibaba.fastjson.JSONObject;
import com.cosmoplat.bussiness.controller.AssetHealthController;
import com.cosmoplat.bussiness.websocket.WebSocketServer;
import com.cosmoplat.example.dao.IRegisterInfoDao;
import com.cosmoplat.influx.util.InfluxDBUtils;
import com.cosmoplat.redis.util.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.influxdb.dto.QueryResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

import javax.jms.JMSException;
import javax.websocket.EncodeException;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class JmsConsumer {
    @Autowired(required = false)
    private InfluxDBUtils influxDBUtils;
    @Autowired
    private RedisUtil redisUtil;
    @Autowired(required = false)
    private IRegisterInfoDao iRegisterInfoDao;
    @Autowired
    private WebSocketServer webSocketServer;

    @JmsListener(destination = "topickkk")
    public void receiveTopic(String message) throws JMSException, IOException, EncodeException {
        if (null != message) {
            //String messageString=ASCIIUtil.asciiToString(message);
            String messageString=message;
            log.info("------------获取到边缘层信息-----------："+messageString);
            //json数据解析
            JSONObject jsonObject=JSONObject.parseObject(messageString);
            List<Map<String,Object>> listData= (List<Map<String, Object>>) jsonObject.get("data");
            Map<String, String> tags = new HashMap<>();
            Map<String, Object> fields = new HashMap<>();
            for(int i=0;i<listData.size();i++){
                String machineNumber= String.valueOf(listData.get(i).get("machineNumber"));
                Map<String,Object> map= (Map<String, Object>) listData.get(i).get("message");
                String n=JSONObject.toJSONString(map);
                //封装时序数据库参数
                tags.put("machineNumber", machineNumber);
                fields.put("message", n);
                influxDBUtils.insert("machine_info", tags, fields,System.currentTimeMillis(), TimeUnit.MILLISECONDS);
                log.info("-------------插入时序数据库-----------tag:"+machineNumber);
                //封装redis参数
//                redisUtil.set(machineNumber+"-"+String.valueOf(System.currentTimeMillis()),map);
//                redisUtil.expire(machineNumber+"-"+String.valueOf(System.currentTimeMillis()),30000);
//                log.info("-------------插入redis-----------key:"+machineNumber+"-"+String.valueOf(System.currentTimeMillis()));
            }
            if(WebSocketServer.getOnlineCount()!=0) {
                String machineNumeber= AssetHealthController.getMachineNumber();
                String queryCommand="SELECT * FROM \"machine_info\"  where machineNumber='"+machineNumeber+"' order by time desc limit 10";
                QueryResult result=influxDBUtils.query(queryCommand);
                String resultString=JSONObject.toJSONString(result);
                WebSocketServer.sendInfo(resultString);
            }
        } else {
            log.info("------------获取到边缘层信息为空-----------");
        }
    }
}
