# pull-request-review-agent

An AI code reviewer that diffs two git refs (or your uncommitted working tree) and asks a model to review the
changes, with read-only access back into the repository so it isn't limited to the diff hunks it was handed.

The system is a **single-agent, tool-calling, bounded-autonomy architecture**: a deterministic Java pipeline
computes and sanitizes the diff, then hands control to one LLM agent that autonomously decides which files to
read, list, or search before producing a structured verdict — bounded by a hard per-review tool-call budget
rather than a fixed number of steps.

## How it works

1. **Diff** — `GitRepositoryService` opens the repo; `GitDiffService` diffs `baseBranch` against `reviewBranch`,
   or against the working tree if no review branch is given.
2. **Sanitize** — `DiffSanitizer` strips content that shouldn't reach the model before anything is sent out.
3. **Prompt** — `ReviewPromptFactory` builds the system/user prompts describing the changed files.
4. **Agent loop** — `PullRequestReviewAgent` calls the model (Spring AI `ChatClient`) with `RepositoryTools`
   (`readFile`, `listFiles`, `searchCode`) bound as tools. The model reasons over the diff and its own prior tool
   results, deciding each turn whether to call another tool or answer. This is the autonomous part: the model,
   not the Java code, chooses what to look at and when it has enough context.
5. **Guardrails** — `RepositoryTools` caps tool calls at 20 per review (`ToolBudgetExceededException` once
   exceeded, telling the model to answer with what it already has); `AiChatServiceImpl` separately retries the
   whole exchange up to 3 times on failure.
6. **Result** — the model's final turn is coerced into a structured `ReviewResult` (summary, recommendation,
   findings), streamed to the client over Server-Sent Events (`progress` updates, terminal `result` or `error`).

It's a single agent, not a multi-agent system: no planner/critic split, no sub-agents, and no memory carried
between reviews — a fresh `RepositoryTools` instance is created per request and discarded after.

## Running it

### Backend

Requires an OpenAI API key.

```bash
export OPENAI_API_KEY=sk-...
./mvnw -pl backend spring-boot:run
```

Config lives in `backend/src/main/resources/application.properties` (model, max tokens, etc).

### Frontend

```bash
cd frontend
npm install
npm run dev
```

React + Vite client that starts a review and renders the live SSE progress stream and final result.

## API

```
POST /api/reviews
Content-Type: application/json

{
  "repositoryPath": "/path/to/repo",
  "baseBranch": "main",
  "reviewBranch": "feature-branch"   // omit/blank to review the working tree instead
}
```

Response is `text/event-stream`:

- `progress` — plain-text status updates (diffing, tool calls, ...), fired any number of times
- `result` — terminal JSON payload: `{ summary, recommendation, findings[] }`
- `error` — terminal plain-text failure message
