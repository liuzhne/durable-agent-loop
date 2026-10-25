package com.amir.agentloop.workflow;

import com.amir.agentloop.workflow.Models.AgentRequest;
import com.amir.agentloop.workflow.Models.AgentStatus;
import com.amir.agentloop.workflow.Models.ApprovalDecision;
import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface AgentWorkflow {

  /** 跑完整个 Agent Loop，返回最终回答。 */
  @WorkflowMethod
  String run(AgentRequest request);

  /** 人工审批：批准或拒绝一个有副作用的工具调用。 */
  @SignalMethod
  void approve(ApprovalDecision decision);

  /** 额度恢复后手动唤醒（不发也会按定时器自动重试）。 */
  @SignalMethod
  void resume();

  @QueryMethod
  AgentStatus status();
}
