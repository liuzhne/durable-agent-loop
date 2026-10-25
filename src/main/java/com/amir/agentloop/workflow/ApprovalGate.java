package com.amir.agentloop.workflow;

import com.amir.agentloop.workflow.Models.ApprovalDecision;
import com.amir.agentloop.workflow.Models.PendingApproval;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Workflow 内部状态（不是 Spring Bean）。全部状态都由事件历史重放重建，
 * 所以 worker 崩溃或重启后，待审批列表、已完成的副作用结果都会原样回来。
 */
public class ApprovalGate {
  final Map<String, PendingApproval> pending = new LinkedHashMap<>();
  final Map<String, ApprovalDecision> decisions = new LinkedHashMap<>();
  /** 幂等键 -> 工具结果。重新 prompt 时直接复用，不再审批、不再执行。 */
  final Map<String, String> completed = new LinkedHashMap<>();

  void onDecision(ApprovalDecision d) {
    if (pending.containsKey(d.approvalId())) {
      decisions.put(d.approvalId(), d);
    }
  }

  List<PendingApproval> pendingList() {
    return new ArrayList<>(pending.values());
  }
}
