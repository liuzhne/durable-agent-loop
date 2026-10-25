package com.amir.agentloop.workflow;

import java.util.List;

/** Workflow 的输入、Signal 载荷和 Query 结果。全部是可被 Jackson 序列化的 record。 */
public final class Models {
  private Models() {}

  public record AgentRequest(String systemPrompt, String task) {}

  /** 人工审批 Signal 的载荷。approvalId 就是工具调用的幂等键。 */
  public record ApprovalDecision(String approvalId, boolean approved, String approver, String comment) {}

  public record PendingApproval(String approvalId, String action, String detail) {}

  public enum State { RUNNING, WAITING_APPROVAL, SUSPENDED, SUCCESS, FAILED }

  public record AgentStatus(
      State state,
      List<PendingApproval> pendingApprovals,
      int completedSideEffects,
      int suspendCount,
      String lastError) {}
}
