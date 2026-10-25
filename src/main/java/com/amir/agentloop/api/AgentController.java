package com.amir.agentloop.api;

import com.amir.agentloop.workflow.AgentWorkflow;
import com.amir.agentloop.workflow.Models.AgentRequest;
import com.amir.agentloop.workflow.Models.AgentStatus;
import com.amir.agentloop.workflow.Models.ApprovalDecision;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/agents")
public class AgentController {

  private static final String TASK_QUEUE = "agent-loop";
  private final WorkflowClient client;

  public AgentController(WorkflowClient client) {
    this.client = client;
  }

  @PostMapping
  public Map<String, String> start(@RequestBody AgentRequest request) {
    String id = "agent-" + UUID.randomUUID();
    AgentWorkflow wf =
        client.newWorkflowStub(
            AgentWorkflow.class,
            WorkflowOptions.newBuilder().setTaskQueue(TASK_QUEUE).setWorkflowId(id).build());
    WorkflowClient.start(wf::run, request);
    return Map.of("workflowId", id);
  }

  @GetMapping("/{id}")
  public AgentStatus status(@PathVariable String id) {
    return client.newWorkflowStub(AgentWorkflow.class, id).status();
  }

  @PostMapping("/{id}/approvals")
  public void approve(@PathVariable String id, @RequestBody ApprovalDecision decision) {
    client.newWorkflowStub(AgentWorkflow.class, id).approve(decision);
  }

  @PostMapping("/{id}/resume")
  public void resume(@PathVariable String id) {
    client.newWorkflowStub(AgentWorkflow.class, id).resume();
  }

  @GetMapping("/{id}/result")
  public String result(@PathVariable String id) {
    return client.newUntypedWorkflowStub(id).getResult(String.class); // 阻塞直到完成
  }
}
