package org.xxpay.pay.channel.hikerpay;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSONObject;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Service;
import org.xxpay.core.common.constant.PayConstant;
import org.xxpay.core.common.util.MyLog;
import org.xxpay.core.entity.PayOrder;
import org.xxpay.pay.channel.BasePayment;
import org.xxpay.pay.channel.hikerpay.util.HikerUtil;
import java.io.IOException;


/**
 * @author: gf
 * @date: 2019/07/19
 * @description: 海科支付接口
 */
@Service
public class HikerpayPaymentService extends BasePayment {
    private static final MyLog _log = MyLog.getLog(HikerpayPaymentService.class);

    @Override
    public String getChannelName() {
        return PayConstant.CHANNEL_NAME_HKPAY;
    }


    /**
     * 支付
     * @param payOrder
     * @return
     */
    @Override
    public JSONObject pay(PayOrder payOrder) {
        String channelId = payOrder.getChannelId();
        HikerpayConfig hikertpayConfig = new HikerpayConfig(getPayParam(payOrder));
        JSONObject retObj = new JSONObject();
        JSONObject parmObj = new JSONObject();
        // 商品描述
        parmObj.put("description", payOrder.getBody());
        // 总金额,单位分
        parmObj.put("price", String.valueOf(payOrder.getAmount()));
        // 货币
        parmObj.put("currency", "USD");
        // 通知地址 http://gf.dev.honch123.com:8000/notify/%s/notify_res.htm
        //payConfig.getNotifyUrl(getChannelName())
        //String.format("http://gf.dev.honch123.com:8000/notify/%s/notify_res.htm",getChannelName())
        parmObj.put("notify_url", String.format(payConfig.getNotifyUrl(getChannelName()),getChannelName()));

        String urltype = "/gateway";
        switch (channelId) {
            case PayConstant.PAY_CHANNEL_HKPAY_WXPAY_NATIVE :
                parmObj.put("channel","Wechat");
                break;
            case PayConstant.PAY_CHANNEL_HKPAY_ALIPAY_NATIVE :
                parmObj.put("channel","Alipay");
                break;
            case PayConstant.PAY_CHANNEL_HKPAY_WX_MWEB :
                parmObj.put("channel","Wechat");
                urltype = "/h5_payment";
                break;
            case PayConstant.PAY_CHANNEL_HKPAY_ALI_MWEB :
                parmObj.put("channel","Alipay");
                urltype = "/h5_payment";
                break;
            case PayConstant.PAY_CHANNEL_HKPAY_ALI_JSAPI :
                parmObj.put("channel","Wechat");
                parmObj.put("operator","web");
                urltype = "/wechat_jsapi_gateway";
                break;
            default:
                retObj = buildRetObj(PayConstant.RETURN_VALUE_FAIL, "不支持的渠道[channelId="+channelId+"]");
                break;
        }

        String CREDENTIAL_CODE = hikertpayConfig.getKey();
        String PARTNER_CODE = hikertpayConfig.getMchId();
        String orderId = payOrder.getPayOrderId();
        String reqUrl = hikertpayConfig.getReqUrl()
                + urltype + "/partners/"
                + PARTNER_CODE + "/orders/"
                + orderId + "?"
                + HikerUtil.queryParams(System.currentTimeMillis(),PARTNER_CODE,CREDENTIAL_CODE);
        CloseableHttpResponse response;
        CloseableHttpClient client = null;
        try {
            String req = parmObj.toJSONString();
            HttpPut httpPut = new HttpPut(reqUrl);
            _log.info("Hikerpass请求地址:{}", reqUrl);
            _log.info("Hikerpass请求数据:{}", req);
            StringEntity entityParams = new StringEntity(req, "utf-8");
            httpPut.setEntity(entityParams);
            httpPut.setHeader("Content-Type", "application/json");
            client = HttpClients.createDefault();
            response = client.execute(httpPut);
            if(response != null && (response.getStatusLine().getStatusCode() -200 <100 )){
                HttpEntity entity = response.getEntity();
                String result =  EntityUtils.toString(entity);
                _log.info("Hikerpass请求结果:{}", result);
                JSONObject res =  JSONObject.parseObject(result);
                if (StrUtil.equals("SUCCESS",res.getString("return_code"),true)) {
                    String channelOrderNo = res.getString("order_id");//对方商户号
                    //如果保存返回结果集会导致超长
                    int resultDB = rpcCommonService.rpcPayOrderService.updateStatus4Ing(payOrder.getPayOrderId(), channelOrderNo);
                    _log.info("[{}] Hikerpass 更新订单状态为支付中:payOrderId={},prepayId={},result={}", getChannelName(), payOrder.getPayOrderId(), "", resultDB);
                    JSONObject payInfo = new JSONObject();
                    if (channelId.equalsIgnoreCase(PayConstant.PAY_CHANNEL_HKPAY_WXPAY_NATIVE) ||
                            channelId.equalsIgnoreCase(PayConstant.PAY_CHANNEL_HKPAY_ALIPAY_NATIVE)) {
                        payInfo.put("codeUrl", res.get("code_url")); // 二维码支付链接
                        payInfo.put("payMethod", PayConstant.PAY_METHOD_CODE_IMG);
                    }else if(channelId.equalsIgnoreCase(PayConstant.PAY_CHANNEL_HKPAY_ALI_JSAPI) ){
                        payInfo.put("payUrl", res.get("pay_url"));
                        payInfo.put("payMethod", PayConstant.PAY_METHOD_FORM_JUMP);
                    } else{
                        //H5支付的情况下返回支付页
                        payInfo.put("payUrl", res.get("pay_url")+ "?redirect="+payOrder.getReturnUrl()+"&"
                                + HikerUtil.queryParams(System.currentTimeMillis(),PARTNER_CODE,CREDENTIAL_CODE));
                        payInfo.put("payMethod", PayConstant.PAY_METHOD_FORM_JUMP);
                    }

                    retObj.put("payParams", payInfo);
                    retObj.put(PayConstant.RETURN_PARAM_RETCODE, PayConstant.RETURN_VALUE_SUCCESS);
                    return retObj;
                }else{
                    retObj.put("errDes", "支付操作失败!");
                    retObj.put(PayConstant.RETURN_PARAM_RETCODE, PayConstant.RETURN_VALUE_FAIL);
                    return retObj;
                }
            }else{
                _log.info("Hikerpass请求状态码response.getStatusLine().getStatusCode():{}", response.getStatusLine().getStatusCode());
                retObj.put("errDes", "支付操作失败!");
                retObj.put(PayConstant.RETURN_PARAM_RETCODE, PayConstant.RETURN_VALUE_FAIL);
            }
        }catch (Exception e){
            _log.error(e.getMessage());
            retObj.put("errDes", "支付操作失败!");
            retObj.put(PayConstant.RETURN_PARAM_RETCODE, PayConstant.RETURN_VALUE_FAIL);
            return retObj;
        }finally {
            if(client != null){
                try {
                    client.close();
                } catch (IOException e) {
                    _log.error(e, "");
                }
            }
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
        HikerpayConfig hikertpayConfig = new HikerpayConfig(getPayParam(payOrder));
        JSONObject retObj = new JSONObject();
        String CREDENTIAL_CODE = hikertpayConfig.getKey();
        String PARTNER_CODE = hikertpayConfig.getMchId();
        String orderId = payOrder.getPayOrderId();
        String reqUrl = hikertpayConfig.getReqUrl()
                + "/gateway/partners/"
                + PARTNER_CODE + "/orders/"
                + orderId + "?"
                + HikerUtil.queryParams(System.currentTimeMillis(),PARTNER_CODE,CREDENTIAL_CODE);

        CloseableHttpResponse response;
        CloseableHttpClient client = null;
        try {
            HttpGet httpGet = new HttpGet(reqUrl);
            _log.info("HikerQuery请求数据:{}", reqUrl);

            client = HttpClients.createDefault();
            response = client.execute(httpGet);

            if(response != null && (response.getStatusLine().getStatusCode() -200 <100 )){
                HttpEntity entity = response.getEntity();
                String result =  EntityUtils.toString(entity);
                _log.info("Hikerpass请求结果:{}", result);
                JSONObject res =  JSONObject.parseObject(result);
                if (StrUtil.equals("SUCCESS",res.getString("return_code"),true)) {
                    // 交易状态
                    /*
                        • PAYING: Waiting for payment
                        • CREATE_FAIL: Fail to create order
                        • CLOSED: Order closed
                        • PAY_FAIL: Payment failed
                        • PAY_SUCCESS: Payment succeeded
                        • PARTIAL_REFUND: Partial refunded
                        • Full_REFUND: Full refunded
                        Use the same order id to call create order API can renew the order. PAYING,
                        PAY_SUCCESS orders cannot be renewed.
                    */
                    String trade_state = res.getString("result_code");
                    retObj.put("obj", res);
                    if(StrUtil.equals("PAY_SUCCESS",trade_state,true)){
                        retObj.put("status", "2");//支付成功
                    }else {
                        retObj.put("status", "1");
                    }
                    retObj.put(PayConstant.RETURN_PARAM_RETCODE, PayConstant.RETURN_VALUE_SUCCESS);
                }else {
                    retObj.put("errDes", "查询操作失败!");
                    retObj.put(PayConstant.RETURN_PARAM_RETCODE, PayConstant.RETURN_VALUE_FAIL);
                }
            }else{
                retObj.put("errDes", "查询操作失败!");
                retObj.put(PayConstant.RETURN_PARAM_RETCODE, PayConstant.RETURN_VALUE_FAIL);
            }
        } catch (Exception e) {
            _log.error(e, "");
            retObj.put("errDes", "查询操作失败!");
            retObj.put(PayConstant.RETURN_PARAM_RETCODE, PayConstant.RETURN_VALUE_FAIL);
            return retObj;
        } finally {
            if(client != null){
                try {
                    client.close();
                } catch (IOException e) {
                    _log.error(e, "");
                }
            }
        }
        return retObj;
    }


}
