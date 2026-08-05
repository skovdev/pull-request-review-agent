# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

An AI code reviewer that fetches a GitHub pull request and asks an LLM to review the changes, with read-only
tool access back into the repository so the model isn't limited to the diff hunks it was handed.

Architecture: a deterministic Java pipeline fetches and sanitizes the diff via the GitHub API, then hands
control to one LLM agent (Spring AI `ChatClient`) that autonomously decides which files to read, list, or
search before producing a structured verdict — bounded by a hard per-review tool-call budget rather than a
fixed number of steps. It is a **single agent**, not multi-agent: no planner/critic split, no sub-agents, and
no memory carried between reviews (a fresh `RepositoryTools` instance, backed by a fresh `GitHubWorkspace`, is
created per request and discarded after).

There is no local clone: the diff comes from GitHub's compare API, and file/directory/search tool calls are
served from a per-review temp directory that's lazily populated by downloading and extracting GitHub's zipball
archive for whichever commit SHA (base or head) the model actually asks about.

Two modules: `backend` (Spring Boot / Java 25, Maven multi-module root + `backend` submodule) and `frontend`
(React 19 + TypeScript + Vite).

## Commands

### Backend (run from repo root; requires `OPENAI_API_KEY` and `GITHUB_TOKEN`)

```bash
export OPENAI_API_KEY=sk-...
export GITHUB_TOKEN=ghp_...        # needs read access to pull requests + contents on the target repo
./mvnw -pl backend spring-boot:run        # run the server (port 8080)
./mvnw -pl backend test                   # run all backend tests
./mvnw -pl backend test -Dtest=GitHubDiffServiceTest              # single test class
./mvnw -pl backend test -Dtest=GitHubDiffServiceTest#methodName   # single test method
./mvnw -pl backend package                # build jar
```

Config lives in `backend/src/main/resources/application.properties` (model, max tokens, `github.token`) and
`ReviewProperties` (`review.*` prefix — diff size limits, tool-call budget, retry count, SSE timeout; see
`config/ReviewProperties.java` for defaults). GitHub API access (token, API base URL for GitHub Enterprise) is
`GitHubProperties` (`github.*` prefix).

### Frontend (run from `frontend/`)

```bash
npm install
npm run dev       # Vite dev server, proxies /api to http://localhost:8080 (see vite.config.ts)
npm run build     # tsc -b && vite build
npm run lint      # oxlint
```

No frontend test runner is configured.

## Architecture

Request flow (`ReviewController` → `ReviewService` → `PullRequestReviewAgent`):

1. **Fetch PR metadata** — `ReviewService` calls `GitHubClient.getPullRequest` (`GET
   /repos/{owner}/{repo}/pulls/{number}`) to resolve the PR's base/head refs (branch names, shown to the
   reviewer/model) and base/head SHAs (what the diff and content lookups are actually pinned to).
2. **Diff** — `GitHubDiffService` calls GitHub's compare API (`GET
   /repos/{owner}/{repo}/compare/{baseSha}...{headSha}`), which resolves the merge-base and detects
   renames/copies server-side. Its per-file `patch` is mapped straight onto `ChangedFile`.
3. **Sanitize** — `DiffSanitizer` strips/truncates diff content before anything reaches the model: skips
   lockfiles and build/vendor output (`node_modules`, `dist`, `target`, ...), collapses diffs GitHub omitted
   (binary or too large), and enforces per-file and total character budgets (`ReviewProperties`).
4. **Prompt** — `ReviewPromptFactory` builds the system/user prompts describing the changed files.
5. **Agent loop** — `PullRequestReviewAgent` calls `AiChatServiceImpl` (wraps the Spring AI `ChatClient`) with a
   fresh `RepositoryTools` instance bound as tools (`readFile`, `listFiles`, `searchCode`, each parameterized by
   `side` = `"base"` or `"review"`). Each tool call resolves `side` to a commit SHA and asks the review's
   `GitHubWorkspace` for that SHA's extraction directory — downloading and unzipping GitHub's zipball archive
   the first time that side is touched, and reusing the extracted files for every call after. The model reasons
   over the diff and its own prior tool results, deciding each turn whether to call another tool or answer —
   this is the autonomous part; the Java code never chooses what to look at.
6. **Guardrails** — `RepositoryTools.remainingCalls` caps tool calls at `review.maxToolCallsPerReview` (default
   20) for the whole review, throwing `ToolBudgetExceededException` once exceeded so the model is told to
   answer with what it has. `AiChatServiceImpl` separately retries the whole chat exchange up to
   `review.maxChatAttempts` (default 3) times on failure.
7. **Result** — the model's final turn is coerced into a structured `ReviewResult` (summary, recommendation,
   findings) via Spring AI's structured output, then streamed to the client over SSE. `GitHubWorkspace` is
   closed in a `finally`, deleting its temp directory regardless of outcome.

### Streaming (SSE)

`ReviewController` runs the review on `reviewExecutor` (see `AsyncConfig`) and returns an `SseEmitter`
immediately. `ReviewProgressPublisher` (bound to a per-request emitter callback in the controller) is threaded
through `ReviewService` and `RepositoryTools` so every diff step and tool call fires a `progress` event before
the terminal `result` (JSON `ReviewResponse`) or `error` (plain text) event. Use
`ReviewProgressPublisher.NO_OP` when progress updates aren't needed (e.g. in tests).

The frontend cannot use `EventSource` because the request is a POST with a body, so
`frontend/src/api/reviewApi.ts` does a manual `fetch` + streaming reader that parses `event:`/`data:` frames
itself.

### Package layout (`backend/src/main/java/local/agent/pullrequestreviewagent/`)

- `github/` — GitHub-backed repository access: `GitHubClient` (REST calls: PR metadata, compare diff, zipball
  download), `GitHubDiffService` (compare API → `ChangedFile`), `GitHubContentService` (read/list/search over
  an extracted zipball directory), `GitHubWorkspace`/`GitHubWorkspaceFactory` (lazy per-SHA zipball extraction
  and cleanup), `ChangedFile`, `DiffSanitizer`.
- `tools/` — `RepositoryTools` (the `@Tool`-annotated methods bound to the model) and its per-request factory.
- `agent/` — the agent itself and prompt construction.
- `ai/` — thin abstraction (`AiChatService`) over the Spring AI `ChatClient` call with retry logic, so the
  agent isn't coupled directly to Spring AI's API.
- `review/` — `ReviewService` (orchestrates the pipeline) and the result/finding/recommendation model types.
- `api/` — `ReviewController` and request/response DTOs.
- `progress/` — the SSE progress publisher abstraction.
- `config/` — `ReviewProperties` (diff/tool-call/retry tunables), `GitHubProperties` (token, API base URL),
  `AiConfig` (`ChatClient` bean), `AsyncConfig` (review executor), `WebConfig`.

### GitHub API notes

- Auth is a bearer token (`github.token`, from `GITHUB_TOKEN`) needing read access to pull requests and
  contents — a classic PAT's `repo` scope, or a fine-grained PAT/GitHub App with those two permissions.
- The zipball download (`GitHubClient.downloadZipball`) is done with a raw `java.net.http.HttpClient` rather
  than `RestClient`: GitHub answers with a redirect to a signed `codeload.github.com` URL, and that hop is
  followed as an explicit second request carrying the same auth header, rather than relying on
  undocumented automatic-redirect header-forwarding behavior.
- Fork PRs work without knowing the fork's URL: GitHub mirrors `refs/pull/{n}/head` onto the base repo, and the
  compare/zipball endpoints accept the PR's head SHA directly against the base repo.
- The compare API returns at most 300 files and omits the `patch` field for very large per-file diffs;
  `DiffSanitizer`/`GitHubDiffService` treat an omitted patch the same as a binary file.

### API contract

```
POST /api/reviews
Content-Type: application/json

{
  "owner": "spring-projects",
  "repo": "spring-framework",
  "pullNumber": 123
}
```

Response is `text/event-stream` with events `progress` (plain text, repeatable), `result` (terminal JSON:
`{ summary, recommendation, findings[] }`), `error` (terminal plain text).
