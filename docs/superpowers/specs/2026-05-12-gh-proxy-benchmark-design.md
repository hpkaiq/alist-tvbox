# GitHub Proxy Benchmark Script Design

**Date:** 2026-05-12

**Goal:** Add a long-lived repository script that discovers GitHub proxy nodes from `github.akams.cn`, benchmarks them against a fixed `spiders_v2.json` target, and sorts results by time to first byte and total request time.

## Scope

This change applies to:
- A new script under `scripts/`
- Local command-line benchmarking only
- Runtime discovery of proxy nodes from the public `github.akams.cn` service

Out of scope:
- Changing application runtime behavior
- Adding backend or frontend product features
- Supporting arbitrary target URLs
- Persisting benchmark history

## Current Context

The repository already keeps operational helper scripts in `scripts/`, mostly as Bash scripts. This task fits the existing pattern and does not require application integration.

The `github.akams.cn` website is a Next.js frontend. Its node list is not embedded as static HTML. The frontend fetches node data from:

- `https://api.akams.cn/github`

The returned payload contains node entries in `data[]`, and each entry includes a proxy URL. The frontend extracts the host from that URL and uses it as the node domain.

## Functional Requirements

### Script Location and Invocation

Add:

- `scripts/gh_proxy_bench.sh`

Invocation:

```bash
bash scripts/gh_proxy_bench.sh
```

Optional:
- make it executable for direct invocation

The script does not accept a custom target URL.

### Fixed Benchmark Target

The script benchmarks this fixed resource:

```text
https://github.com/har01d5/tvbox/raw/refs/heads/master/spiders_v2.json
```

Each proxy request is formed as:

```text
https://<proxy-host>/https://github.com/har01d5/tvbox/raw/refs/heads/master/spiders_v2.json
```

This matches how the target site expects proxy URLs to be composed.

### Runtime Node Discovery

Discovery order:

1. Request `https://api.akams.cn/github`
2. Parse JSON payload
3. Read `data[]`
4. Extract the host from each `data[i].url`
5. De-duplicate hosts

Node labels:
- preserve the source tag when available
- normalize common labels the same way the site does:
  - `gh.llkk.cc` -> `默认节点`
  - `donate` -> `公益贡献`
  - otherwise keep the remote tag or fall back to a generic label

### Fallback Behavior

If API discovery fails or returns no usable nodes:

- fall back to a small built-in list

Minimum fallback set:
- `gh.llkk.cc`

Optional extra fallback entries may include stable hosts already known from prior use, but the fallback set should remain small so it does not become a second source of truth.

### Benchmark Method

Use `curl` for each node and capture:

- HTTP status code
- `time_starttransfer` as the primary metric
- `time_total` as the secondary metric

Recommended request behavior:
- `--location`
- `--silent`
- `--show-error`
- `--output /dev/null`
- conservative timeout values so dead nodes do not stall the full run

The script should treat the proxy as failed when:
- DNS fails
- connection fails
- timeout occurs
- no usable HTTP response is received

### Sorting

Sort successful nodes by:

1. `time_starttransfer` ascending
2. `time_total` ascending
3. host ascending as a deterministic tie-breaker

Failed nodes should be listed after successful nodes, without participating in the primary ranking.

### Output

Print a terminal-friendly table containing at least:

- label
- host
- HTTP status
- time to first byte
- total time
- final benchmark URL

Also print a separate failure section containing:

- host
- failure reason

The output should make it easy to copy the fastest host into other scripts or config.

## Implementation Design

### Script Structure

The script should be organized into small shell functions:

- fetch node API payload
- parse nodes from JSON
- build proxy target URL
- benchmark one host
- print results

Because the payload is JSON, prefer a parser that is realistic for the repository environment:

- use `jq` if present
- otherwise fall back to a simple `python3 -c` JSON extraction path

This keeps the script portable without making parsing fragile.

### Concurrency

The first version should run sequentially.

Reasoning:
- simpler and easier to debug
- avoids local network saturation skewing latency numbers
- enough for a modest node list

Parallel benchmarking can be added later if the list grows large.

### Failure Handling

The script must continue after individual node failures.

Failure handling rules:
- node discovery failure triggers fallback list
- a failed node does not abort the full run
- malformed API entries are skipped
- empty or duplicate hosts are skipped

## Verification

Verification for this change should include:

1. Run the script and confirm it discovers nodes from `https://api.akams.cn/github`
2. Confirm output includes successful and failed nodes when applicable
3. Confirm successful rows are sorted by first-byte time, then total time
4. Confirm fallback still works by temporarily forcing API fetch failure in a controlled test

## Risks

- The remote API shape may change
- Some proxy nodes may return HTTP success while still being practically slow or unstable
- Sequential testing makes the overall run slower, but produces cleaner numbers

## Decision Summary

- Use Bash in `scripts/` to match repository conventions
- Discover nodes from the site’s public node API, not by scraping HTML
- Benchmark a fixed TvBox `spiders_v2.json` target
- Rank by time to first byte, then total time
- Keep a minimal fallback list so the script still works when the API is unavailable
