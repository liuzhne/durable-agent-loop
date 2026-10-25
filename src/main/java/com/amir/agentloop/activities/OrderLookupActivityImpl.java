package com.amir.agentloop.activities;

import org.springframework.stereotype.Component;

@Component
public class OrderLookupActivityImpl implements OrderLookupActivity {
  @Override
  public String lookupOrder(String orderId) {
    // TODO: 换成真实的订单服务调用
    return "{\"orderId\":\"" + orderId + "\",\"status\":\"DELIVERED\",\"paidCents\":12900,\"refundable\":true}";
  }
}
