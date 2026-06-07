# ДЗ 6 Load Test Report

## Scope

- API under test: Scrapper `GET /links`, `POST /links`, `DELETE /links`.
- Homework mapping: `GET /list` maps to this repository's `GET /links`; mutation traffic uses `POST /links` and `DELETE /links`.
- Dataset target: 1000 chats * 100 links per chat = 100000 links.
- Operation mix: about 100 list calls per 1 mutation cycle.

## Test Matrix

|       Mode        |        Scrapper cache config         |       Client-side cache config        |                                  Notes                                  |
|-------------------|--------------------------------------|---------------------------------------|-------------------------------------------------------------------------|
| No cache          | `APP_CACHE_LIST_LINKS_ENABLED=false` | n/a                                   | Repository-only baseline.                                               |
| Valkey cache      | `APP_CACHE_LIST_LINKS_ENABLED=true`  | `APP_CACHE_CLIENT_SIDE_ENABLED=false` | Server-side Valkey cache.                                               |
| Client-side cache | `APP_CACHE_LIST_LINKS_ENABLED=true`  | `APP_CACHE_CLIENT_SIDE_ENABLED=true`  | Not implemented in this MR; Spring Data Redis template path limitation. |

## Apache JMeter Command

```bash
jmeter -n \
  -t load-tests/caching/list-links-cache.jmx \
  -JbaseUrl=http://localhost:8081 \
  -JchatStart=1 \
  -JchatCount=1000 \
  -JlinksPerChat=100 \
  -Jthreads=8 \
  -JrampUp=60 \
  -Jduration=300 \
  -JmutationRatio=101 \
  -l load-tests/caching/results/list-links-cache.jtl \
  -e -o load-tests/caching/results/html
```

## Results Template

|       Mode        |    Request    |     RPS | Avg, ms | p50, ms | p99, ms | 200 count | 502/504 count | 500 count | Frequent errors  |
|-------------------|---------------|--------:|--------:|--------:|--------:|----------:|--------------:|----------:|------------------|
| No cache          | `GET /links`  |     tbd |     tbd |     tbd |     tbd |       tbd |           tbd |       tbd | tbd              |
| No cache          | `POST /links` |     tbd |     tbd |     tbd |     tbd |       tbd |           tbd |       tbd | tbd              |
| Valkey cache      | `GET /links`  |     tbd |     tbd |     tbd |     tbd |       tbd |           tbd |       tbd | tbd              |
| Valkey cache      | `POST /links` |     tbd |     tbd |     tbd |     tbd |       tbd |           tbd |       tbd | tbd              |
| Client-side cache | `GET /links`  | not run | not run | not run | not run |   not run |       not run |   not run | Not implemented. |
| Client-side cache | `POST /links` | not run | not run | not run | not run |   not run |       not run |   not run | Not implemented. |

## Results

Not collected in this workspace because the Apache JMeter CLI is not installed and measured load runs were not executed.

## Conclusions

Pending measured runs. Expected behavior: repeated unpaged `GET /links` calls for the same chat should avoid repository
reads while the Valkey TTL is active; mutation latency includes cache eviction but should remain close to the baseline
because eviction is a single-key delete and fail-open.
