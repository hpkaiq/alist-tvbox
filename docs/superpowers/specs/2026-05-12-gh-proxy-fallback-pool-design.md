# GitHub Proxy Benchmark Fallback Pool Design

**Date:** 2026-05-12

**Goal:** Expand the benchmark script’s fallback behavior so that when dynamic node discovery fails, it benchmarks a fixed built-in pool of proxy domains instead of only `gh.llkk.cc`.

## Scope

This change applies to:
- `scripts/gh_proxy_bench.sh`
- the Bash test harness for node discovery fallback

Out of scope:
- changing the primary discovery API
- scraping additional websites when the API fails
- adding user-configurable fallback flags

## Current Context

The script currently:
- tries `https://api.akams.cn/github` first
- parses domains from the API response
- falls back to a single built-in host `gh.llkk.cc` when discovery fails

This is functionally correct but too narrow. If the discovery API is temporarily unavailable, the script should still test a small known-good fallback pool.

## Functional Requirements

### Discovery Priority

Keep the current discovery priority:

1. dynamic discovery from `https://api.akams.cn/github`
2. built-in fallback pool when dynamic discovery fails

Dynamic discovery is considered failed when:
- the HTTP request fails
- the API returns no usable nodes
- parsing yields an empty node list

### Built-In Fallback Pool

Use this fixed fallback domain list:

- `gh.llkk.cc`
- `gh-proxy.org`
- `hk.gh-proxy.org`
- `cdn.gh-proxy.org`
- `edgeone.gh-proxy.org`
- `gh.felicity.ac.cn`

These are stored as proxy domains only, not full benchmark URLs.

### Target URL Construction

All fallback domains must use the same existing fixed target construction:

```text
https://<proxy-host>/https://github.com/har01d5/tvbox/raw/refs/heads/master/spiders_v2.json
```

Do not preserve the special-case `raw.githubusercontent.com` form that may work on some proxies. The user explicitly chose the “proxy domain + fixed target URL” model.

### Output Behavior

The script should continue to:
- print terminal results
- write the fixed JSON result file
- sort successful nodes the same way as before

When fallback is used, the script should benchmark the whole fallback pool rather than silently narrowing to a single default host.

## Implementation Design

### Data Model

Represent fallback nodes as built-in rows of:
- label
- host

Suggested labels:
- `默认节点` for `gh.llkk.cc`
- `备用节点` for the other built-in fallback hosts

This keeps fallback output distinct from API-derived labels without inventing too much taxonomy.

### Function Boundary

Keep fallback behavior isolated in `fallback_nodes()`.

`discover_nodes()` should continue to:
- try API discovery
- return API results when available
- otherwise delegate to `fallback_nodes()`

This keeps the rest of the benchmark flow unchanged.

## Verification

Verification should include:

1. force discovery failure in a controlled test
2. confirm all 6 fallback hosts are returned
3. confirm `gh.llkk.cc` keeps the `默认节点` label
4. confirm the others use the fallback label consistently
5. confirm runtime benchmark still produces terminal output and JSON output using fallback hosts

## Decision Summary

- keep dynamic API discovery as the primary path
- expand fallback from one host to a fixed built-in pool of six domains
- store only proxy domains and keep one fixed target URL construction rule
