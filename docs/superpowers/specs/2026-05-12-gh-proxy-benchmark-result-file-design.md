# GitHub Proxy Benchmark Result File Design

**Date:** 2026-05-12

**Goal:** Extend the GitHub proxy benchmark script so each run overwrites a fixed JSON result file while preserving the existing terminal table output.

## Scope

This change applies to:
- `scripts/gh_proxy_bench.sh`
- its lightweight Bash test coverage

Out of scope:
- adding CLI flags
- supporting multiple output formats
- timestamped history files
- replacing the terminal output with file-only output

## Current Context

The benchmark script already:
- discovers nodes from `https://api.akams.cn/github`
- benchmarks a fixed `spiders_v2.json` target
- prints successful and failed nodes to the terminal
- sorts successful nodes by first-byte time, then total time

The missing capability is persistence of the benchmark result to a stable file path.

## Functional Requirements

### Output File

Add a fixed output file:

- `scripts/gh_proxy_bench_result.json`

Every run overwrites this file.

### Output Behavior

The script must:
- keep the current terminal output
- additionally write a JSON file
- write the file once after all results are collected

The script should avoid writing partial results incrementally. Build the full document first, then replace the file contents in one pass.

### JSON Shape

The JSON must include at least:

- `generated_at`
- `target_url`
- `discovery_api`
- `success_nodes`
- `failed_nodes`

Recommended shape:

```json
{
  "generated_at": "2026-05-12T18:00:00+08:00",
  "target_url": "https://github.com/har01d5/tvbox/raw/refs/heads/master/spiders_v2.json",
  "discovery_api": "https://api.akams.cn/github",
  "success_nodes": [
    {
      "label": "默认节点",
      "host": "gh.llkk.cc",
      "http_status": 200,
      "ttfb_seconds": 4.421,
      "total_seconds": 4.422,
      "benchmark_url": "https://gh.llkk.cc/https://github.com/har01d5/tvbox/raw/refs/heads/master/spiders_v2.json"
    }
  ],
  "failed_nodes": [
    {
      "host": "ghpr.cc",
      "reason": "http_404"
    }
  ]
}
```

### Ordering

`success_nodes` must preserve the same sorted order as the terminal output.

`failed_nodes` may remain in encounter order.

### Empty Cases

If no successful nodes exist:
- `success_nodes` must be an empty array

If no failed nodes exist:
- `failed_nodes` must be an empty array

The file should still be written in both cases.

## Implementation Design

### Path Handling

Resolve the output path relative to the script file so the JSON lands in the repository `scripts/` directory regardless of the caller’s current working directory.

### Serialization

Prefer generating JSON through `python3` rather than hand-escaping strings in Bash.

Suggested flow:
- collect tab-separated success rows
- collect tab-separated failure rows
- pass them into a short embedded Python serializer
- write the final JSON string to `scripts/gh_proxy_bench_result.json`

This is safer than manual shell string escaping because hosts, labels, and reasons may contain characters that are awkward to escape in pure Bash.

### Atomicity

Write to a temporary file first, then move it into place.

This avoids leaving a truncated JSON file if serialization fails.

## Verification

Verification should include:

1. run the Bash test script
2. run the benchmark script
3. confirm `scripts/gh_proxy_bench_result.json` exists
4. confirm the file is valid JSON
5. confirm the first success node in JSON matches the first terminal-ranked success node
6. confirm rerunning the script overwrites the same file instead of creating a new one

## Decision Summary

- keep terminal output unchanged
- always overwrite one fixed JSON result file
- write the file only after full collection completes
- serialize with Python for correctness and simplicity
