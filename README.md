# pull-request-review-agent

An AI code reviewer that fetches a GitHub pull request and asks an LLM to review the changes, with read-only
tool access back into the repository so it isn't limited to the diff hunks it was handed.

The system is a **single-agent, tool-calling, bounded-autonomy architecture**: a deterministic Java pipeline
fetches and sanitizes the diff via the GitHub API, then hands control to one LLM agent that autonomously
decides which files to read, list, or search before producing a structured verdict — bounded by a hard
per-review tool-call budget rather than a fixed number of steps.

There is no local clone: the diff comes from GitHub's compare API, and file/directory/search tool calls are
served from a per-review temp directory that's lazily populated by downloading and extracting GitHub's
zipball archive for whichever commit SHA (base or head) the model actually asks about.

## How it works

1. **Fetch PR metadata** — `GitHubClient.getPullRequest` resolves the PR's base/head branches and SHAs.
2. **Diff** — `GitHubDiffService` calls GitHub's compare API, which resolves the merge-base and detects
   renames/copies server-side.
3. **Sanitize** — `DiffSanitizer` strips/truncates diff content before anything reaches the model: skips
   lockfiles and build/vendor output, collapses diffs GitHub omitted (binary or too large), and enforces
   per-file and total character budgets.
4. **Prompt** — `ReviewPromptFactory` builds the system/user prompts describing the changed files.
5. **Agent loop** — `PullRequestReviewAgent` calls the model (Spring AI `ChatClient`) with a fresh
   `RepositoryTools` instance bound as tools (`readFile`, `listFiles`, `searchCode`, each parameterized by
   `side` = `"base"` or `"review"`). Each tool call resolves `side` to a commit SHA and downloads/extracts
   that commit's zipball on first use, reusing it for every call after. The model reasons over the diff and
   its own prior tool results, deciding each turn whether to call another tool or answer — this is the
   autonomous part; the Java code never chooses what to look at.
6. **Guardrails** — `RepositoryTools` caps tool calls at 20 per review (`ToolBudgetExceededException` once
   exceeded, telling the model to answer with what it already has); `AiChatServiceImpl` separately retries
   the whole exchange on transient failures.
7. **Result** — the model's final turn is coerced into a structured `ReviewResult` (summary, recommendation,
   findings), streamed to the client over Server-Sent Events (`progress` updates, terminal `result` or
   `error`). The per-review temp directory is deleted once the review completes, regardless of outcome.
8. **Post to GitHub (opt-in)** — if enabled, `GitHubReviewPublisher` submits the result as a real review on
   the pull request. Findings only become inline comments when their line falls inside that file's diff
   hunks (GitHub rejects anything else); everything else is folded into the review's summary text instead of
   being dropped. This step is best-effort: a review that was already computed successfully still reaches the
   client even if posting to GitHub fails.

It's a single agent, not a multi-agent system: no planner/critic split, no sub-agents, and no memory carried
between reviews — a fresh `RepositoryTools` instance, backed by a fresh per-review workspace, is created per
request and discarded after.

## Running it

### Backend

Requires an OpenAI API key and a GitHub token with read access to pull requests and contents (a classic PAT's
`repo` scope, or a fine-grained PAT/GitHub App with those two permissions). Posting reviews back to GitHub is
off by default; enabling it (`review.postReviewToGitHub=true`) also needs pull request *write* access.

```bash
export OPENAI_API_KEY=sk-...
export GITHUB_TOKEN=ghp_...
./mvnw -pl backend spring-boot:run
```

Config lives in `backend/src/main/resources/application.properties` (model, max tokens, `github.token`) and
`ReviewProperties`/`GitHubProperties` (diff size limits, tool-call budget, retry count, SSE timeout, whether
to post reviews back to GitHub, GitHub API base URL for GitHub Enterprise).

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
  "owner": "spring-projects",
  "repo": "spring-framework",
  "pullNumber": 123
}
```

Response is `text/event-stream`:

- `progress` — plain-text status updates (diffing, tool calls, ...), fired any number of times
- `result` — terminal JSON payload: `{ summary, recommendation, findings[] }`
- `error` — terminal plain-text failure message
