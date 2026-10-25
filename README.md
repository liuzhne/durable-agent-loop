# Durable Agent Loop（Spring Boot 3 + Spring AI 1.1 + Temporal）

## 运行
```bash
temporal server start-dev            # 本地 Temporal，UI 在 http://localhost:8233
export OPENAI_API_KEY=sk-...
mvn spring-boot:run
```

## 试一下
```bash
# 1. 启动
curl -s -XPOST localhost:8080/agents -H 'content-type: application/json' \
  -d '{"systemPrompt":"You are a support agent. Look up the order before refunding.","task":"Order A-1001 arrived broken, refund it in full."}'

# 2. 查看状态，拿到 pendingApprovals[0].approvalId
curl -s localhost:8080/agents/<workflowId>

# 3. 审批
curl -s -XPOST localhost:8080/agents/<workflowId>/approvals -H 'content-type: application/json' \
  -d '{"approvalId":"<approvalId>","approved":true,"approver":"amir","comment":"ok"}'

# 4. 结果
curl -s localhost:8080/agents/<workflowId>/result
```

## 测续跑
- 等待审批时 kill 掉进程再启动：状态和待审批项从事件历史重放回来。
- 把 OPENAI_API_KEY 换成会 429 的 key：模型 Activity 在 Temporal UI 里持续退避重试，
  已完成的 lookupOrder 不会重跑。超过 6 小时进入 SUSPENDED，1 小时后自动再试，或 POST /resume。

## 状态对应
| 状态 | 含义 |
|---|---|
| RUNNING | 正在跑 |
| WAITING_APPROVAL | 等人工 Signal |
| SUSPENDED | infra_error（限额/超时），不计成功也不计业务失败 |
| SUCCESS | 成功 |
| FAILED | 不可恢复（NonTransientAiException、参数错误、挂起次数超限） |
