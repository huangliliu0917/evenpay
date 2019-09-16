package org.xxpay.pay.channel;

import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.xxpay.core.common.Exception.ServiceException;
import org.xxpay.core.common.constant.MchConstant;
import org.xxpay.core.common.constant.PayConstant;
import org.xxpay.core.common.constant.RetEnum;
import org.xxpay.core.common.util.MyLog;
import org.xxpay.core.entity.PayOrder;
import org.xxpay.core.entity.PayPassageAccount;
import org.xxpay.pay.channel.hikerpay.HikerpayPayNotifyService;
import org.xxpay.pay.mq.BaseNotify4MchPay;
import org.xxpay.pay.service.RpcCommonService;
import org.xxpay.pay.util.Util;

import java.util.Random;

/**
 * @author: dingzhiwei
 * @date: 17/12/24
 * @description:
 */
@Component
public abstract class BasePayNotify extends BaseService implements PayNotifyInterface {


    private static final MyLog _log = MyLog.getLog(HikerpayPayNotifyService.class);

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    public RpcCommonService rpcCommonService;

    @Autowired
    public PayConfig payConfig;

    @Autowired
    public BaseNotify4MchPay baseNotify4MchPay;

    public abstract String getChannelName();

    public JSONObject doNotify(Object notifyData) {
        return null;
    }

    public JSONObject doReturn(Object notifyData) {
        return new JSONObject();
    }

    /**
     * 获取三方支付配置信息
     * 如果是平台账户,则使用平台对应的配置,否则使用商户自己配置的渠道
     * @param payOrder
     * @return
     */
    public String getPayParam(PayOrder payOrder) {
        String payParam = "";
        PayPassageAccount payPassageAccount = rpcCommonService.rpcPayPassageAccountService.findById(payOrder.getPassageAccountId());
        if(payPassageAccount != null && payPassageAccount.getStatus() == MchConstant.PUB_YES) {
            payParam = payPassageAccount.getParam();
        }
        if(StringUtils.isBlank(payParam)) {
            throw new ServiceException(RetEnum.RET_MGR_PAY_PASSAGE_ACCOUNT_NOT_EXIST);
        }
        return payParam;
    }

    //是否需要扣量，根据商户ID查询redis变量值。mch.20000000 商户ID
    public boolean isDeduction(PayOrder payOrder,String channelOrderNo) {
        // 判断redis中是否有扣量比例值
        String key = "mch."+payOrder.getChannelMchId();
        if(stringRedisTemplate.hasKey(key)) {
            String value = stringRedisTemplate.opsForValue().get(key);
            int percentage =  Integer.parseInt(value);;// 根据 redis 里面的值来决定命中百分比 0-100的数字为扣量百分比
            Random random = new Random();
            int i = random.nextInt(99);
            if(i>=0&&i<percentage) {
                //命中处理
                int updatePayOrderRows = rpcCommonService.rpcPayOrderService.updateStatus4Deduction(payOrder.getPayOrderId(),channelOrderNo,"");
                if (updatePayOrderRows != 1) {
                    _log.error("商户 {} 扣量成功,payOrderId= {}", payOrder.getChannelMchId(), payOrder.getPayOrderId());
                    return true;
                }else{
                    _log.error("商户 {} 扣量失败,payOrderId= {}", payOrder.getChannelMchId(), payOrder.getPayOrderId());
                }
            }
        }
        return false;
    }
}
