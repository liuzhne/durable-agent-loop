package com.amir.agentloop.tools;

import com.amir.agentloop.activities.RefundActivity;
import com.amir.agentloop.workflow.ApprovalGateAccess;
import com.amir.agentloop.workflow.Models.ApprovalDecision;
import com.amir.agentloop.workflow.Models.PendingApproval;
import com.amir.agentloop.workflow.Models.State;
import com.amir.agentloop.workflow.ApprovalGate;
import io.temporal.workflow.Workflow;
import java.time.Duration;
import java.util.function.Consumer;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 普通工具（不是 Activity stub，也不是 @SideEffectTool），所以跑在 Workflow 线程上，
 * 可以调用 Workflow.await 等 Signal，再调用有副作用的 Activity。
 * 这里的代码必须保持确定性：不要用 System.currentTimeMillis()、UUID.randomUUID()、普通线程等。
 */
public class RefundTools {

  private static final Duration APPROVAL_TIMEOUT = Duration.ofHours(24);

  private final ApprovalGate gate;
  private final RefundActivity refundActivity;
  private final String workflowId;
  private final Consumer<State> stateSink;

  public RefundTools(
      ApprovalGate gate, RefundActivity refundActivity, String workflowId, Consumer<State> stateSink) {
    this.gate = gate;
    this.refundActivity = refundActivity;
    this.workflowId = workflowId;
    this.stateSink = stateSink;
  }

  @Tool(description = "Issue a refund for an order. Requires human approval before money moves.")
  public String issueRefund(
      @ToolParam(description = "Order ID") String orderId,
      @ToolParam(description = "Refund amount in cents") long amountCents,
      @ToolParam(description = "Short reason shown to the approver") String reason) {

    // 幂等键由业务参数决定，而不是随机数：重新 prompt、重放、重试都会算出同一个键。
    String key = workflowId + ":refund:" + orderId + ":" + amountCents;

    // 已完成：直接复用结果（对应“先持久化 tool_result，恢复时复用”）。
    String done = ApprovalGateAccess.completed(gate).get(key);
    if (done != null) {
      return done;
    }

    ApprovalGateAccess.pending(gate)
        .putIfAbsent(
            key,
            new PendingApproval(key, "issueRefund", orderId + " / " + amountCents + " cents / " + reason));
    stateSink.accept(State.WAITING_APPROVAL);

    boolean decided =
        Workflow.await(APPROVAL_TIMEOUT, () -> ApprovalGateAccess.decisions(gate).containsKey(key));
    stateSink.accept(State.RUNNING);

    if (!decided) {
      ApprovalGateAccess.pending(gate).remove(key);
      return "Refund NOT executed: approval timed out after 24h.";
    }

    ApprovalDecision d = ApprovalGateAccess.decisions(gate).get(key);
    ApprovalGateAccess.pending(gate).remove(key);

    String result;
    if (!d.approved()) {
      result = "Refund REJECTED by " + d.approver() + ": " + d.comment();
    } else {
      String refundId = refundActivity.issueRefund(key, orderId, amountCents);
      result = "Refund executed. refundId=" + refundId + ", approvedBy=" + d.approver();
    }
    ApprovalGateAccess.completed(gate).put(key, result);
    return result;
  }
}
