package com.amir.agentloop.activities;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/** 只读工具：@ActivityInterface + @Tool，会被自动识别为 durable 工具。 */
@ActivityInterface
public interface OrderLookupActivity {

  @Tool(description = "Look up an order's status, amount paid (cents) and refund eligibility.")
  @ActivityMethod
  String lookupOrder(@ToolParam(description = "Order ID") String orderId);
}
