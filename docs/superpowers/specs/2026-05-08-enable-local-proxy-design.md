# Cloud Drive Proxy Config Design

**Date:** 2026-05-08

**Goal:** Add a global proxy configuration dialog for built-in spiders on the netdisk account page, store per-`CloudDriveType` proxy settings in `local_proxy_config`, and pass that configuration through `SubscriptionService#buildSite` using `ext`.

## Scope

This change applies to:
- The netdisk account configuration UI in `web-ui/src/views/DriverAccountView.vue`
- Setting persistence and retrieval through the existing `/api/settings` flow
- Site `ext` generation in `src/main/java/cn/har01d/alist_tvbox/service/SubscriptionService.java`
- Targeted subscription service tests

Out of scope:
- Spider-side consumption logic
- Per-account proxy configuration for built-in spiders
- Changes to non-built-in site definitions
- Refactoring unrelated settings or driver account storage

## Current Behavior

Built-in site `ext` currently carries:
- `api`
- `token`
- `uid`
- `enable_local_proxy`

The current local proxy UI entry is being moved under the netdisk account page.

Driver accounts already store per-account proxy-related fields such as:
- `useProxy`
- `concurrency`
- `addition.chunk_size`

But the new spider-facing requirement is not account-based. It is a global configuration organized by cloud drive type.

## Requirements

### Functional

- Remove the global `enable_local_proxy` switch from the spider-facing design.
- Add a configuration dialog on the netdisk account page.
- In that dialog, maintain per-drive-type proxy settings that are independent from individual accounts.
- Only show drive types that currently support proxy tuning for this purpose.
- Merge `QUARK` and `QUARK_TV` into one `QUARK` configuration.
- Merge `UC` and `UC_TV` into one `UC` configuration.
- Pass the per-type proxy configuration to built-in spiders through `ext`.

### Supported Drive Types

The dialog and `ext` payload will use the following `CloudDriveType` values:
- `ALI`
- `QUARK`
- `UC`
- `PAN115`
- `PAN123`
- `PAN139`
- `BAIDU`

Other enum values such as `CLOUD189`, `THUNDER`, and `UNKNOWN` are out of scope for this change.

### Per-Type Configuration Shape

Each supported type exposes:
- `enabled`
- `concurrency`
- `chunk_size`

`enabled` is explicit. `concurrency = 1` does **not** mean disabled.
`chunk_size` is expressed in `KB` and is passed to `site.ext` as `KB` directly.

### Compatibility

- Missing per-type proxy configuration must be treated as absent, not as malformed.
- Existing built-in site generation must remain backward compatible except for replacing `enable_local_proxy` with `local_proxy_config`.

## Design

### Frontend

Use `DriverAccountView.vue` as the sole UI entry point for spider proxy settings.

Implementation details:
- Keep the existing top toolbar on the netdisk account page.
- Add a `配置` button that opens a dialog.
- The dialog contains a per-type configuration section for the supported `CloudDriveType` values.
- Each type row contains:
  - Type label
  - `启用` switch
  - `并发数` input
  - `分片大小` input

Persistence:
- Per-type proxy configuration is stored in a dedicated JSON setting named `local_proxy_config`.

Recommended frontend data shape:

```json
{
  "ALI": {"enabled": true, "concurrency": 20, "chunk_size": 1024},
  "QUARK": {"enabled": true, "concurrency": 20, "chunk_size": 1024},
  "UC": {"enabled": true, "concurrency": 10, "chunk_size": 256},
  "PAN115": {"enabled": true, "concurrency": 2, "chunk_size": 1024},
  "PAN123": {"enabled": true, "concurrency": 4, "chunk_size": 256},
  "PAN139": {"enabled": true, "concurrency": 4, "chunk_size": 256},
  "BAIDU": {"enabled": true, "concurrency": 5, "chunk_size": 2048}
}
```

### Backend

`SubscriptionService#buildSite` will resolve the JSON setting `local_proxy_config`, parse it, and include it in the encoded `ext` payload.

The built-in site `ext` map will become:
- `api`
- `token`
- `uid`
- `local_proxy_config`

Recommended emitted `ext` shape:

```json
{
  "api": "http://127.0.0.1:4567",
  "token": "test-token",
  "uid": "test-uid",
  "local_proxy_config": {
    "ALI": {"enabled": true, "concurrency": 20, "chunk_size": 1024},
    "QUARK": {"enabled": true, "concurrency": 20, "chunk_size": 1024},
    "UC": {"enabled": true, "concurrency": 10, "chunk_size": 256},
    "PAN115": {"enabled": true, "concurrency": 2, "chunk_size": 1024},
    "PAN123": {"enabled": true, "concurrency": 4, "chunk_size": 256},
    "PAN139": {"enabled": true, "concurrency": 4, "chunk_size": 256},
    "BAIDU": {"enabled": true, "concurrency": 5, "chunk_size": 2048}
  }
}
```

The backend should treat invalid or missing `local_proxy_config` as an empty object rather than failing site generation, and it should not convert `chunk_size` between `KB` and bytes.

### Source of Defaults

The per-type configuration is global and does not derive from existing account rows.

Default values:
- `DEFAULT_PROXY_CONCURRENCY = 1`
- `DEFAULT_PROXY_CHUNK_SIZE = 256`
- `ALI_PROXY_CONCURRENCY = 20`
- `ALI_PROXY_CHUNK_SIZE = 1024`
- `QUARK_PROXY_CONCURRENCY = 20`
- `QUARK_PROXY_CHUNK_SIZE = 1024`
- `UC_PROXY_CONCURRENCY = 10`
- `UC_PROXY_CHUNK_SIZE = 256`
- `PAN115_PROXY_CONCURRENCY = 2`
- `PAN115_PROXY_CHUNK_SIZE = 1024`
- `PAN123_PROXY_CONCURRENCY = 4`
- `PAN123_PROXY_CHUNK_SIZE = DEFAULT_PROXY_CHUNK_SIZE`
- `PAN139_PROXY_CONCURRENCY = 4`
- `PAN139_PROXY_CHUNK_SIZE = DEFAULT_PROXY_CHUNK_SIZE`
- `BAIDU_PROXY_CONCURRENCY = 5`
- `BAIDU_PROXY_CHUNK_SIZE = 2048`

The dialog should synthesize default rows from these values when the setting is absent or incomplete.

## Data Flow

1. The netdisk account page loads `local_proxy_config`.
2. The frontend merges stored values with synthesized defaults for supported drive types.
3. The user edits values in the configuration dialog and saves.
4. The frontend persists the per-type JSON through `/api/settings`.
5. `SubscriptionService#buildSite` reads `local_proxy_config`.
6. The service injects the value into `ext` without unit conversion.
7. Built-in spiders decode `ext` and consume the per-type config.

## Error Handling

- Missing `local_proxy_config` falls back to `{}` on the backend and a synthesized default dialog state on the frontend.
- Malformed `local_proxy_config` should not break subscription generation; the backend should log and emit `{}`.
- Incomplete `local_proxy_config` should be merged with defaults on the frontend before save.
- Existing saved `chunk_size` values are not migrated; once this change ships, `chunk_size` is interpreted as `KB`.

## Testing

### Backend

Add focused `SubscriptionService` tests that decode the Base64 `ext` payload and verify:
- A stored `local_proxy_config` JSON object is emitted into `ext`
- Missing `local_proxy_config` produces an empty object consistently
- `enable_local_proxy` is no longer emitted in `ext`

### Frontend

Verify via build/type-check that:
- `DriverAccountView.vue` renders the new config dialog structure
- The dialog can load and save `local_proxy_config`
- The dialog shows only supported `CloudDriveType` values
- The old `ConfigView.vue` entry is removed if still present
- The dialog labels or inputs make the `KB` unit explicit

## Acceptance Criteria

- The netdisk account page has a `配置` button.
- The dialog shows only `ALI`, `QUARK`, `UC`, `PAN115`, `PAN123`, `PAN139`, and `BAIDU`.
- `QUARK/QUARK_TV` share one `QUARK` configuration.
- `UC/UC_TV` share one `UC` configuration.
- Each supported type exposes `enabled`, `concurrency`, and `chunk_size`.
- `chunk_size` is edited and transmitted in `KB`.
- Built-in site `ext` includes `local_proxy_config`.
- Built-in site `ext` no longer includes `enable_local_proxy`.
- Targeted backend tests cover the new `ext` payload structure.
