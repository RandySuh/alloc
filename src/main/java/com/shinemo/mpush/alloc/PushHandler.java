/*
 * (C) Copyright 2015-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     ohun@live.cn (夜色)
 */

package com.shinemo.mpush.alloc;

import com.mpush.api.Constants;
import com.mpush.api.push.*;
import com.mpush.api.router.ClientLocation;
import com.mpush.tools.Jsons;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * Created by ohun on 16/9/7.
 *
 * @author ohun@live.cn (夜色)
 */
/*package*/ final class PushHandler implements HttpHandler {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final PushSender pushSender = PushSender.create();
    private final AtomicInteger idSeq = new AtomicInteger();

    public PushHandler() {
        pushSender.start();
    }

    public void stop() {
        pushSender.stop();
    }

    @SuppressWarnings("unchecked")
    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        String body = new String(readBody(httpExchange), Constants.UTF_8);
        Map<String, String> params = Jsons.fromJson(body, Map.class);
        String userId = params.get("userId");
        String hello = params.get("hello");

        sendPush(userId, hello);

        byte[] data = "服务已经开始推送,请注意查收消息".getBytes(Constants.UTF_8);
        httpExchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        httpExchange.sendResponseHeaders(200, data.length);//200, content-length
        OutputStream out = httpExchange.getResponseBody();
        out.write(data);
        out.close();
    }

    private void sendPush(String userId, String hello) {
        NotificationDO notificationDO = new NotificationDO();
        notificationDO.content = "MPush开源推送消息," + hello;
        notificationDO.title = "MPUSH推送";
        notificationDO.nid = idSeq.get() % 2;
        notificationDO.ticker = "你有一条新的消息,请注意查收";
        PushMsg pushMsg = PushMsg.build(MsgType.NOTIFICATION_AND_MESSAGE, Jsons.toJson(notificationDO));
        pushMsg.setMsgId("msg_" + idSeq.incrementAndGet());

        pushSender.send(PushContext
                .build(pushMsg)
                .setUserId(userId)
                .setCallback(new PushCallback() {
                    @Override
                    public void onResult(PushResult result) {
                        logger.error(result.toString());
                    }
                })
        );
    }

    private byte[] readBody(HttpExchange httpExchange) throws IOException {
        InputStream in = httpExchange.getRequestBody();
        String length = httpExchange.getRequestHeaders().getFirst("content-length");
        if (length != null && !length.equals("0")) {
            byte[] buffer = new byte[Integer.parseInt(length)];
            in.read(buffer);
            in.close();
            return buffer;
        } else {
            ByteArrayOutputStream out = new ByteArrayOutputStream(1024);
            byte[] buffer = new byte[1024];
            int len = 0;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            in.close();
            return out.toByteArray();
        }
    }

    public static final class NotificationDO {
        public String msgId;
        public String title;
        public String content;
        public Integer nid; //主要用于聚合通知，非必填
        public Byte flags; //特性字段。 0x01:声音   0x02:震动 0x03:闪灯
        public String largeIcon; // 大图标
        public String ticker; //和title一样
        public Integer number;
        public Map<String, String> extras;
    }
}
