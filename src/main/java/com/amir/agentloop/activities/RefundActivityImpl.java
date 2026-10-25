package com.amir.agentloop.activities;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Activity 至少执行一次（at-least-once）：worker 在“支付成功”和“结果写回 Temporal”之间崩溃，
 * 就会重试。所以副作用本身必须幂等。
 *
 * 生产做法（二选一或都做）：
 *  1. 把 idempotencyKey 作为下游的 Idempotency-Key 请求头（Stripe 等都支持）；
 *  2. 本地表 refunds(idempotency_key UNIQUE, refund_id)，先查后写，同一事务。
 */
@Component
public class RefundActivityImpl implements RefundActivity {

  // 演示用。重启即丢，生产必须换成数据库唯一约束。
  private final Map<String, String> ledger = new ConcurrentHashMap<>();

  @Override
  public String issueRefund(String idempotencyKey, String orderId, long amountCents) {
    if (amountCents <= 0) {
      throw new IllegalArgumentException("amount must be positive"); // 不重试
    }
    return ledger.computeIfAbsent(idempotencyKey, k -> callPaymentProvider(k, orderId, amountCents));
  }

  private String callPaymentProvider(String idempotencyKey, String orderId, long amountCents) {
    // TODO: paymentClient.refund(orderId, amountCents, headers("Idempotency-Key", idempotencyKey))
    return "rf_" + Integer.toHexString(idempotencyKey.hashCode());
  }
}
