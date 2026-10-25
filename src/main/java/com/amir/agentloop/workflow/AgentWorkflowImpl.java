package com.amir.agentloop.workflow;

import com.amir.agentloop.activities.OrderLookupActivity;
import com.amir.agentloop.activities.RefundActivity;
import com.amir.agentloop.tools.RefundTools;
import com.amir.agentloop.workflow.Models.*;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ActivityFailure;
import io.temporal.failure.ApplicationFailure;
import io.temporal.failure.TimeoutFailure;
import io.temporal.springai.chat.TemporalChatClient;
import io.temporal.springai.model.ActivityChatModel;
import io.temporal.workflow.Workflow;
import io.temporal.workflow.WorkflowInit;
import java.time.Duration;
import org.slf4j.Logger;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.retry.NonTransientAiException;

public class AgentWorkflowImpl implements AgentWorkflow {

  private static final Logger log = Workflow.getLogger(AgentWorkflowImpl.class);

  /** 挂起后多久自动再试一次（也可以发 resume Signal 立即续跑）。 */
  private static final Duration AUTO_RESUME_AFTER = Duration.ofHours(1);
  private static final int MAX_SUSPENDS = 24;

  private final ChatClient chatClient;
  private final ApprovalGate gate = new ApprovalGate();

  private State state = State.RUNNING;
  private boolean resumeRequested = false;
  private int suspendCount = 0;
  private String lastError;

  @WorkflowInit
  public AgentWorkflowImpl(AgentRequest request) {
    // 1. 模型调用：每次调用都是一个 Activity，结果写进事件历史。
    //    限额 / 过载（429、529、5xx，已在 application.yaml 标成 Transient）
    //    在同一个 Activity 内指数退避重试，最长等 6 小时。
    //    已经完成的工具 Activity 不会因此重跑。
    ActivityChatModel model =
        ActivityChatModel.forDefault(
            ActivityOptions.newBuilder(ActivityChatModel.defaultActivityOptions())
                .setStartToCloseTimeout(Duration.ofMinutes(2))
                .setScheduleToCloseTimeout(Duration.ofHours(6))
                .setRetryOptions(
                    RetryOptions.newBuilder()
                        .setInitialInterval(Duration.ofSeconds(5))
                        .setBackoffCoefficient(2.0)
                        .setMaximumInterval(Duration.ofMinutes(10))
                        .setMaximumAttempts(0) // 不限次数，由 scheduleToClose 兜底
                        .setDoNotRetry(
                            NonTransientAiException.class.getName(),
                            IllegalArgumentException.class.getName())
                        .build())
                .build());

    // 2. 只读工具：@ActivityInterface + @Tool，模型可直接调用，Temporal 负责重试。
    OrderLookupActivity orderLookup =
        Workflow.newActivityStub(
            OrderLookupActivity.class,
            ActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofSeconds(30))
                .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(5).build())
                .build());

    // 3. 有副作用的 Activity：接口上【没有】@Tool，模型碰不到它；
    //    只能经过 RefundTools（普通工具，跑在 Workflow 线程上）的审批闸门。
    RefundActivity refundActivity =
        Workflow.newActivityStub(
            RefundActivity.class,
            ActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofSeconds(30))
                .setRetryOptions(
                    RetryOptions.newBuilder()
                        .setMaximumAttempts(10)
                        .setDoNotRetry(IllegalArgumentException.class.getName())
                        .build())
                .build());

    RefundTools refundTools =
        new RefundTools(gate, refundActivity, Workflow.getInfo().getWorkflowId(), this::setState);

    this.chatClient =
        TemporalChatClient.builder(model)
            .defaultSystem(request.systemPrompt())
            .defaultTools(orderLookup, refundTools)
            .build();
  }

  @Override
  public String run(AgentRequest request) {
    while (true) {
      try {
        state = State.RUNNING;
        String answer = chatClient.prompt().user(request.task()).call().content();
        state = State.SUCCESS;
        return answer;
      } catch (RuntimeException e) {
        if (!isInfraInterruption(e) || suspendCount >= MAX_SUSPENDS) {
          state = State.FAILED;
          lastError = rootMessage(e);
          throw e; // business_failed / 不可恢复：让 Workflow 失败
        }
        // infra_error：挂起，不算成功也不算业务失败。
        // 重新 prompt 时，RefundTools 会按幂等键复用已完成结果，不会重复退款。
        suspendCount++;
        state = State.SUSPENDED;
        lastError = rootMessage(e);
        log.warn("Agent suspended ({}): {}", suspendCount, lastError);
        Workflow.await(AUTO_RESUME_AFTER, () -> resumeRequested);
        resumeRequested = false;
      }
    }
  }

  /** 模型 Activity 用完 scheduleToClose 仍失败（长时间限额）= 基础设施中断。 */
  private static boolean isInfraInterruption(Throwable e) {
    for (Throwable t = e; t != null; t = t.getCause()) {
      if (t instanceof TimeoutFailure) return true;
      if (t instanceof ApplicationFailure af
          && af.getType() != null
          && af.getType().endsWith("TransientAiException")
          && !af.getType().endsWith("NonTransientAiException")) {
        return true;
      }
      if (t instanceof ActivityFailure && t.getCause() instanceof TimeoutFailure) return true;
    }
    return false;
  }

  private static String rootMessage(Throwable e) {
    Throwable t = e;
    while (t.getCause() != null) t = t.getCause();
    return t.getClass().getSimpleName() + ": " + t.getMessage();
  }

  private void setState(State s) {
    this.state = s;
  }

  @Override
  public void approve(ApprovalDecision decision) {
    gate.onDecision(decision);
  }

  @Override
  public void resume() {
    resumeRequested = true;
  }

  @Override
  public AgentStatus status() {
    return new AgentStatus(state, gate.pendingList(), gate.completed.size(), suspendCount, lastError);
  }
}
