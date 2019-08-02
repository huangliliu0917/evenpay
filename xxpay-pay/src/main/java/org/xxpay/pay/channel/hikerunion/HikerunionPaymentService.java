package org.xxpay.pay.channel.hikerunion;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSONObject;
import org.springframework.stereotype.Service;
import org.xxpay.core.common.constant.PayConstant;
import org.xxpay.core.common.util.MyLog;
import org.xxpay.core.common.util.PayDigestUtil;
import org.xxpay.core.entity.PayOrder;
import org.xxpay.pay.channel.BasePayment;
import org.xxpay.pay.channel.swiftpay.util.XmlUtils;

import java.util.HashMap;
import java.util.Map;


/**
 * @author: gf
 * @date: 2019-08-01 10:05:17
 * @description: HIKERUNION支付接口
 */
@Service
public class HikerunionPaymentService extends BasePayment {
    private static final MyLog _log = MyLog.getLog(HikerunionPaymentService.class);

    @Override
    public String getChannelName() {
        return PayConstant.CHANNEL_NAME_HIKERUNION;
    }


    /**
     * 支付
     * @param payOrder
     * @return
     */
    @Override
    public JSONObject pay(PayOrder payOrder) {
        String channelId = payOrder.getChannelId();
        HikerunionConfig payChannelConfig = new HikerunionConfig(getPayParam(payOrder));
        JSONObject retObj = new JSONObject();

        //对方只支持纯数字订单号
        String orderId = payOrder.getPayOrderId().replace("P","");

        Map<String, Object> post=new HashMap<String, Object>();
        post.put("CLIENTREF",orderId); //商户订单号
        post.put("CLIENTKEY", payChannelConfig.getClientKey());
        post.put("AMOUNT", String.valueOf(payOrder.getAmount()));// 总金额,单位分
        //post.put("siteid", payChannelConfig.getMchId());//商户号
        post.put("CURRCODE","840");
        post.put("FUSEACTION", "main.cardEntry");
        post.put("LANGUAGECODE", "EN");
        post.put("REMARKS"," CategoryID:15");
        post.put("FAILURL",payConfig.getNotifyUrl(getChannelName()));
        post.put("SUCCESSURL",payConfig.getNotifyUrl(getChannelName()));
        post.put("VERSION", "1.0.0");
        //加签
        String temp = PayDigestUtil.md5(payChannelConfig.getMchId()+"|"
                +orderId+"|"
                +post.get("AMOUNT")+"|"
                +post.get("LANGUAGECODE")+"|"
                +post.get("CURRCODE")+"|"
                +post.get("VERSION")+"|"
                +PayDigestUtil.md5(payChannelConfig.getKey(),"utf-8"),"utf-8");
        post.put("SIGNATURE", temp);
        String reqUrl = payChannelConfig.getReqUrl();
        _log.info("Hikerunion支付请求地址:{}", reqUrl);
        _log.info("Hikerunion支付请求数据:{}", post.toString());
        try{
            HttpResponse result = HttpRequest.post(reqUrl)
                    .form(post)
                    .execute();
            if (result.isOk()) {
                int resultDB = rpcCommonService.rpcPayOrderService.updateStatus4Ing(payOrder.getPayOrderId(), null,result.body());
                _log.info("[{}] Hikerunion 更新订单状态为支付中:payOrderId={},prepayId={},result={}", getChannelName(), payOrder.getPayOrderId(), "", resultDB);
                //支付网页返回给下级
                retObj.put("html", result.body());
                retObj.put(PayConstant.RETURN_PARAM_RETCODE, PayConstant.RETURN_VALUE_SUCCESS);
            }else{
                retObj.put("errDes", "查询操作失败!");
                retObj.put(PayConstant.RETURN_PARAM_RETCODE, PayConstant.RETURN_VALUE_FAIL);
            }
        } catch (Exception e) {
            retObj.put("errDes", "查询操作失败!");
            retObj.put(PayConstant.RETURN_PARAM_RETCODE, PayConstant.RETURN_VALUE_FAIL);
        }

        return retObj;
    }

    /**
     * 查询订单
     * @param payOrder
     * @return
     */
    @Override
    public JSONObject query(PayOrder payOrder) {
        //String channelId = payOrder.getChannelId();
        HikerunionConfig payChannelConfig = new HikerunionConfig(getPayParam(payOrder));
        JSONObject retObj = new JSONObject();
        //对方只支持纯数字订单号
        String orderId = payOrder.getPayOrderId().replace("P","");

        Map<String, Object> post=new HashMap<String, Object>();
        post.put("CLIENTREF",orderId); //商户订单号
        post.put("CLIENTKEY", payChannelConfig.getClientKey());
        post.put("FUSEACTION", "main.inquiryApi");
        post.put("VERSION", "1.0.0");

        //加签
        String temp = PayDigestUtil.md5(payChannelConfig.getMchId()+"|"
                +post.get("CLIENTREF")+"|"
                +post.get("VERSION")+"|"
                +PayDigestUtil.md5(payChannelConfig.getKey(),"utf-8"),"utf-8");
        post.put("SIGNATURE", temp);
        String reqUrl = payChannelConfig.getReqUrl();
        _log.info("Hikerunion查询订单请求地址:{}", reqUrl);
        _log.info("Hikerunion查询订单请求数据:{}", post.toString());

        try{
            HttpResponse result = HttpRequest.post(reqUrl)
                    .form(post)
                    .execute();
            if (result.isOk()) {
                Map<String,String> resultMap = XmlUtils.toMap(result.bodyBytes(),"utf-8");
                String resultFlag = resultMap.get("resultFlag");
                //0: General failure, 1: Payment succeeded, 2: Payment failed, 3: Payment pending.
                if("1".equals(resultFlag)){
                    retObj.put("status", "0");
                }else {
                    retObj.put("status", "1");
                }
                retObj.put(PayConstant.RETURN_PARAM_RETCODE, PayConstant.RETURN_VALUE_SUCCESS);
            }else{
                retObj.put("errDes", "查询操作失败!");
                retObj.put(PayConstant.RETURN_PARAM_RETCODE, PayConstant.RETURN_VALUE_FAIL);
            }
        } catch (Exception e) {
            retObj.put("errDes", "查询操作失败!");
            retObj.put(PayConstant.RETURN_PARAM_RETCODE, PayConstant.RETURN_VALUE_FAIL);
        }
        return retObj;
    }

}
