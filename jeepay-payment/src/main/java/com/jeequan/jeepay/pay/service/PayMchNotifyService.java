/*
 * Copyright (c) 2021-2031, 河北计全科技有限公司 (https://www.jeequan.com & jeequan@126.com).
 * <p>
 * Licensed under the GNU LESSER GENERAL PUBLIC LICENSE 3.0;
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.gnu.org/licenses/lgpl.html
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jeequan.jeepay.pay.service;

import com.alibaba.fastjson.JSONObject;
import com.jeequan.jeepay.core.entity.MchInfo;
import com.jeequan.jeepay.core.entity.MchNotifyRecord;
import com.jeequan.jeepay.core.entity.PayOrder;
import com.jeequan.jeepay.core.utils.JeepayKit;
import com.jeequan.jeepay.core.utils.StringKit;
import com.jeequan.jeepay.pay.mq.queue.MqQueue4PayOrderMchNotify;
import com.jeequan.jeepay.pay.rqrs.QueryPayOrderRS;
import com.jeequan.jeepay.service.impl.MchInfoService;
import com.jeequan.jeepay.service.impl.MchNotifyRecordService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/*
* 商户通知 service
*
* @author terrfly
* @site https://www.jeepay.vip
* @date 2021/6/8 17:43
*/
@Slf4j
@Service
public class PayMchNotifyService {

    @Autowired private MchNotifyRecordService mchNotifyRecordService;
    @Autowired private MchInfoService mchInfoService;
    @Autowired private MqQueue4PayOrderMchNotify mqPayOrderMchNotifyQueue;


    /** 商户通知信息， 只有订单是终态，才会发送通知， 如明确成功和明确失败 **/
    public void payOrderNotify(PayOrder dbPayOrder){

        try {
            // 通知地址为空
            if(StringUtils.isEmpty(dbPayOrder.getNotifyUrl())){
                return ;
            }

            //获取到通知对象
            MchNotifyRecord mchNotifyRecord = mchNotifyRecordService.findByPayOrder(dbPayOrder.getPayOrderId());

            if(mchNotifyRecord != null){

                log.info("当前已存在通知消息， 不再发送。");
                return ;
            }

            //构建数据
            MchInfo mchInfo = mchInfoService.getById(dbPayOrder.getMchNo());
            // 封装通知url
            String notifyUrl = createNotifyUrl(dbPayOrder, mchInfo.getPrivateKey());
            mchNotifyRecord = new MchNotifyRecord();
            mchNotifyRecord.setOrderId(dbPayOrder.getPayOrderId());
            mchNotifyRecord.setOrderType(MchNotifyRecord.TYPE_PAY_ORDER);
            mchNotifyRecord.setMchNo(dbPayOrder.getMchNo());
            mchNotifyRecord.setMchOrderNo(dbPayOrder.getMchOrderNo()); //商户订单号
            mchNotifyRecord.setIsvNo(dbPayOrder.getIsvNo());
            mchNotifyRecord.setNotifyUrl(notifyUrl);
            mchNotifyRecord.setResResult("");
            mchNotifyRecord.setNotifyCount(0);
            mchNotifyRecord.setState(MchNotifyRecord.STATE_ING); // 通知中
            mchNotifyRecordService.save(mchNotifyRecord);

            //推送到MQ
            Long notifyId = mchNotifyRecord.getNotifyId();
            mqPayOrderMchNotifyQueue.send(notifyId + "");

        } catch (Exception e) {
            log.error("推送失败！", e);
        }
    }


    /**
     * 创建响应URL
     */
    public String createNotifyUrl(PayOrder payOrder, String mchKey) {

        QueryPayOrderRS queryPayOrderRS = QueryPayOrderRS.buildByPayOrder(payOrder);
        JSONObject jsonObject = (JSONObject)JSONObject.toJSON(queryPayOrderRS);
        jsonObject.put("reqTime", System.currentTimeMillis()); //添加请求时间

        // 报文签名
        jsonObject.put("sign", JeepayKit.getSign(jsonObject, mchKey));

        // 生成通知
        return StringKit.appendUrlQuery(payOrder.getNotifyUrl(), jsonObject);
    }


    /**
     * 创建响应URL
     */
    public String createReturnUrl(PayOrder payOrder, String mchKey) {

        if(StringUtils.isEmpty(payOrder.getReturnUrl())){
            return "";
        }

        QueryPayOrderRS queryPayOrderRS = QueryPayOrderRS.buildByPayOrder(payOrder);
        JSONObject jsonObject = (JSONObject)JSONObject.toJSON(queryPayOrderRS);
        jsonObject.put("reqTime", System.currentTimeMillis()); //添加请求时间

        // 报文签名
        jsonObject.put("sign", JeepayKit.getSign(jsonObject, mchKey));   // 签名

        // 生成跳转地址
        return StringKit.appendUrlQuery(payOrder.getReturnUrl(), jsonObject);

    }

}
