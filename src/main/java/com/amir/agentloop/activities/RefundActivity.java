package com.amir.agentloop.activities;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/** 有副作用。故意不加 @Tool：模型不能绕过审批直接调用。 */
@ActivityInterface
public interface RefundActivity {

  @ActivityMethod
  String issueRefund(String idempotencyKey, String orderId, long amountCents);
}
