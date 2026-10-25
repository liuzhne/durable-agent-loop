package com.amir.agentloop.workflow;

import com.amir.agentloop.workflow.Models.ApprovalDecision;
import com.amir.agentloop.workflow.Models.PendingApproval;
import java.util.Map;

/** 让 tools 包访问 ApprovalGate 的包内状态，同时不把字段公开给其他代码。 */
public final class ApprovalGateAccess {
  private ApprovalGateAccess() {}

  public static Map<String, PendingApproval> pending(ApprovalGate g) {
    return g.pending;
  }

  public static Map<String, ApprovalDecision> decisions(ApprovalGate g) {
    return g.decisions;
  }

  public static Map<String, String> completed(ApprovalGate g) {
    return g.completed;
  }
}
