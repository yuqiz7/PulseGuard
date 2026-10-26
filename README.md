# PulseGuard

COMS W4156 team project – a time-series anomaly detection service.

Client programs register numeric metrics, submit data points, and receive an anomaly verdict for
each point in real time. The service stores every metric's history, rolling statistics, detection
rule and detected anomalies. Detection methods: rolling z-score, EWMA and static thresholds.

**Team:** Rohit Biswas (rb3908), Jaxson Robinson (jnr2154), Sidharth Sharma (ss7202),
Yuqi Zhang (yz5072)

**Project board:** https://github.com/users/yuqiz7/projects/2 (one issue per task, assigned to its owner)

## Contents

1. [Tech stack](#tech-stack)
2. [Build, run and test](#build-run-and-test)
3. [API](#api)
4. [Data model](#data-model)
5. [Tools and configuration files](#tools-and-configuration-files)
6. [Project structure](#project-structure)
7. [Third-party code](#third-party-code)
8. [AI usage](#ai-usage)

## Tech stack

- Java 17, Spring Boot 3.5 (Spring Web, Spring Data JPA, Bean Validation)
- PostgreSQL 16 (local: Docker; cloud: GCP Cloud SQL)
- Maven (via the included Maven Wrapper – no separate Maven installation needed)

## Build, run and test

### Prerequisites

- JDK 17 (`java -version` should print 17)
- Docker Desktop (only needed to run the service against PostgreSQL)

On Windows use `mvnw.cmd` instead of `./mvnw` in all commands below.

### Test

```bash
./mvnw test
```

Runs all unit and repository tests against an in-memory H2 database (no PostgreSQL needed) and
writes the branch coverage report to `target/site/jacoco/index.html`.

### Full check (tests + style)

```bash
./mvnw clean verify
```

Runs the tests, then Checkstyle; the build fails on any style violation. Run this before opening
a pull request.

### API tests (Postman)

The collection `postman/PulseGuard.postman_collection.json` exercises every endpoint, including
error cases. Start the service (see below), then either:

- **Postman:** Import the collection, open it, and click **Run** (run all requests in order), or
- **Newman (command line):** `npx newman run postman/PulseGuard.postman_collection.json`

Each run uses a new client ID, so it can be repeated against the same database. CI runs the
collection on every pull request.

### Run locally with PostgreSQL

```bash
docker compose up -d        # starts PostgreSQL on localhost:5432 (see docker-compose.yml)
./mvnw spring-boot:run      # starts the service on http://localhost:8080
```

Stop the database with `docker compose down`. Tables are created automatically on startup.

Connection settings come from environment variables (defaults match `docker-compose.yml`):

| Variable      | Default                                       |
|---------------|-----------------------------------------------|
| `DB_URL`      | `jdbc:postgresql://localhost:5432/pulseguard` |
| `DB_USER`     | `pulseguard`                                  |
| `DB_PASSWORD` | `pulseguard`                                  |
| `PORT`        | `8080`                                        |

### Run locally without Docker

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=h2
```

Uses a file-based H2 database in `./data` instead of PostgreSQL (for quick local demos only).

## API

All requests must include the header `X-Client-Id: <client program ID>`. Each client only sees its
own metrics; a metric that belongs to another client is reported as `404`.

All errors return a JSON body:

```json
{ "status": 404, "error": "Not Found", "message": "Metric 7 not found" }
```

| Status | Meaning                                                                 |
|--------|-------------------------------------------------------------------------|
| 200    | Success                                                                 |
| 201    | Metric created                                                          |
| 400    | Invalid parameters, invalid or malformed JSON body, wrongly typed value |
| 401    | `X-Client-Id` header missing or blank                                   |
| 404    | Metric not found or owned by another client                             |
| 409    | Duplicate metric name for this client, or out-of-order timestamp       |

| Operation           | Endpoint                                                   |
|---------------------|------------------------------------------------------------|
| registerMetric      | `POST /metrics`                                            |
| submitDataPoints    | `POST /metrics/{metricId}/datapoints`                      |
| queryAnomalies      | `GET /metrics/{metricId}/anomalies?from&to&minScore&limit` |
| getMetricSummary    | `GET /metrics/{metricId}/summary?from&to&bucket`           |
| updateDetectionRule | `PUT /metrics/{metricId}/rule?dryRun=true`                 |

### registerMetric

`POST /metrics` registers a metric and its detection rule.

**Body**

| Field           | Type   | Required | Description                                                        |
|-----------------|--------|----------|--------------------------------------------------------------------|
| `name`          | string | yes      | Unique per client, at most 100 characters                          |
| `unit`          | string | no       | Label only, at most 50 characters                                  |
| `method`        | string | yes      | `ZSCORE`, `EWMA` or `THRESHOLD`                                    |
| `params`        | object | no       | Method parameters (below); missing keys get defaults               |
| `warmUpSamples` | int    | no       | Points stored but never flagged at the start (default 10; 0 for `THRESHOLD`); 0–10000 |

| Method      | Parameters (default)                                    | Flags a value when                         |
|-------------|---------------------------------------------------------|--------------------------------------------|
| `ZSCORE`    | `windowSize` (30, whole number 2–10000), `zThreshold` (3) | it is more than `zThreshold` standard deviations from the mean of the last `windowSize` values |
| `EWMA`      | `alpha` (0.3, between 0 and 1), `tolerance` (3)         | it is more than `tolerance` standard deviations from the exponentially weighted moving average |
| `THRESHOLD` | `lowerBound`, `upperBound` (at least one required)      | it is below `lowerBound` or above `upperBound` |

**Response `201`**

```json
{ "metricId": 1, "name": "cpu", "unit": "%", "method": "ZSCORE",
  "params": { "windowSize": 30.0, "zThreshold": 3.0 }, "warmUpSamples": 10,
  "createdAt": "2026-10-26T14:00:00Z" }
```

**Side effects:** stores the metric and an empty statistics record.
**Errors:** `400` missing/invalid field, unknown method, unknown parameter for the method, or
invalid value; `401` missing `X-Client-Id`; `409` the client already has a metric with this name.

### submitDataPoints

`POST /metrics/{metricId}/datapoints` judges and stores a batch of values.

**Body:** `{ "points": [ { "timestamp": "2026-10-26T14:00:00Z", "value": 42.5 }, ... ] }` with
1–1000 points. Points may be in any order; they are processed oldest first.

**Response `200`**

```json
{ "metricId": 1, "accepted": 2, "anomalies": 1,
  "results": [
    { "timestamp": "2026-10-26T14:00:00Z", "value": 25.0, "anomaly": false, "score": 0.0,
      "expectedLow": -1.7976931348623157E308, "expectedHigh": 30.0 },
    { "timestamp": "2026-10-26T14:01:00Z", "value": 31.5, "anomaly": true, "score": 1.5,
      "expectedLow": -1.7976931348623157E308, "expectedHigh": 30.0 } ] }
```

`score` is the number of standard deviations from the expected level (`ZSCORE`, `EWMA`) or the
distance outside the bounds (`THRESHOLD`, 0 inside them). `expectedLow`/`expectedHigh` is the
range that would not have been flagged (for a `THRESHOLD` without one bound, that side is the
largest double). During warm-up every point is non-anomalous with score 0.

**Side effects:** stores every point and each anomaly, and updates the metric's statistics. The
whole batch is one transaction holding a lock on the metric's statistics, so concurrent batches
for the same metric are processed one after another.
**Errors:** `400` empty or oversized batch, missing timestamp/value, malformed timestamp;
`401` missing `X-Client-Id`; `404` metric not found or owned by another client;
`409` a timestamp is repeated or not after the latest stored timestamp (nothing is stored).

### queryAnomalies

`GET /metrics/{metricId}/anomalies?from=...&to=...&minScore=0&limit=100`

| Parameter  | Required | Description                                  |
|------------|----------|----------------------------------------------|
| `from`, `to` | yes    | ISO-8601 timestamps, inclusive; `from` ≤ `to` |
| `minScore` | no       | Only anomalies with at least this score (default 0, ≥ 0) |
| `limit`    | no       | Maximum anomalies returned, 1–1000 (default 100) |

**Response `200`:** `{ "metricId": 1, "total": 2, "anomalies": [ { "timestamp", "value",
"score", "expectedLow", "expectedHigh", "method" }, ... ] }`, oldest first; `total` ignores
`limit`.
**Side effects:** none.
**Errors:** `400` missing or malformed parameter, `from` after `to`, invalid `minScore` or
`limit`; `401`; `404`.

### getMetricSummary

`GET /metrics/{metricId}/summary?from=...&to=...&bucket=hour`

`bucket` is optional: `hour`/`1h` or `day`/`1d` (UTC).

**Response `200`**

```json
{ "metricId": 1, "from": "...", "to": "...", "bucket": "hour",
  "overall": { "count": 5, "anomalies": 2, "mean": 42.0, "stddev": 25.6, "min": 10.0,
               "max": 80.0, "anomalyRate": 0.4, "trendPerHour": 50.0 },
  "buckets": [ { "start": "2026-10-01T10:00:00Z", "stats": { "count": 4, ... } }, ... ] }
```

`stddev` is the population standard deviation; `trendPerHour` is the slope of a least-squares
line (value units per hour). For an empty range only the counts are set (others are `null`);
`trendPerHour` is `null` with fewer than two distinct timestamps.
**Side effects:** none.
**Errors:** `400` missing/malformed parameter, `from` after `to`, unknown `bucket`; `401`; `404`.

### updateDetectionRule

`PUT /metrics/{metricId}/rule?dryRun=true`

**Body:** same `method`, `params` and `warmUpSamples` fields as registerMetric.

The new rule is replayed over the metric's **most recent 10,000 points** (older points keep their
verdicts). With `dryRun=true` (default) nothing changes; with `dryRun=false` the rule is saved,
the replayed points' verdicts are replaced, and their anomalies are recomputed.

**Response `200`**

```json
{ "metricId": 1, "applied": false, "method": "THRESHOLD", "params": { "upperBound": 30.0 },
  "warmUpSamples": 0, "replayedPoints": 5, "totalPoints": 5, "truncated": false,
  "previousAnomalyCount": 2, "newAnomalyCount": 3, "changedPointCount": 1,
  "changedPoints": [ { "timestamp": "2026-10-01T10:03:00Z", "value": 40.0,
                       "previousAnomaly": false, "newAnomaly": true, "newScore": 10.0 } ] }
```

`changedPoints` lists at most 100 points; `truncated` is true when older points were not replayed.
**Side effects:** none for a dry run; otherwise updates the rule, point verdicts, anomalies and
statistics in one transaction under the metric's lock.
**Errors:** `400` invalid rule; `401`; `404`.

### Call-order constraints

- `registerMetric` must be called first; every other operation needs the returned `metricId` and
  the same `X-Client-Id`.
- For each metric, points must arrive in strictly increasing timestamp order across calls.
- Points submitted after a rule change are judged with the new rule.

### Example usage scenarios

**Server monitoring agent**

1. `POST /metrics` with `{"name":"p95-latency","unit":"ms","method":"ZSCORE"}` → `metricId` 1.
2. Every 10 seconds, `POST /metrics/1/datapoints` with the latest readings; alert when any
   result has `"anomaly": true`.
3. After an incident, `GET /metrics/1/anomalies?from=...&to=...` to reconstruct the timeline.

**Tuning a rule**

1. `PUT /metrics/1/rule` with a candidate rule (dry run) and compare `previousAnomalyCount` with
   `newAnomalyCount`.
2. When satisfied, repeat with `?dryRun=false` to apply it.
3. `GET /metrics/1/summary?from=...&to=...&bucket=day` to check the daily anomaly rate.

### Bad-usage examples

| What the client does                                   | Result | Why |
|--------------------------------------------------------|--------|-----|
| Omits the `X-Client-Id` header                         | `401`  | Every metric belongs to a client; without the ID the service cannot tell whose data it is |
| Uses another client's `metricId`                       | `404`  | Clients are isolated; the service does not reveal that the metric exists |
| Registers `"cpu"` twice for the same client            | `409`  | Names are unique per client (another client may use the same name) |
| Sends `{"method":"EWMA","params":{"alpha":2}}`         | `400`  | `alpha` must be between 0 and 1 |
| Sends `windowSize` to an `EWMA` metric                 | `400`  | Each method only accepts its own parameters |
| Re-sends points it already submitted                   | `409`  | Timestamps must be strictly increasing; nothing from the batch is stored |
| Sends an empty `points` array or more than 1000 points | `400`  | Batches must contain 1–1000 points |
| Queries with `from` after `to`                         | `400`  | Empty, inverted ranges are rejected rather than silently returning nothing |

## Data model

| Table               | Columns                                                                                              |
|---------------------|------------------------------------------------------------------------------------------------------|
| `metric`            | id, client_id, name, unit, method, params (JSON), warm_up_samples, created_at; unique (client_id, name) |
| `data_point`        | id, metric_id, ts, point_value, is_anomaly, score; index (metric_id, ts)                            |
| `metric_statistics` | metric_id (PK), sample_count, ewma, ewma_variance, last_ts                                           |
| `anomaly`           | id, metric_id, ts, point_value, score, expected_low, expected_high, method; index (metric_id, ts)   |

Detection parameter keys and defaults are defined in
`src/main/java/edu/columbia/pulseguard/detection/RuleParams.java`.

**Concurrency:** ingestion and rule updates lock the metric's `metric_statistics` row
(`MetricStatisticsRepository.findByIdForUpdate`, JPA `PESSIMISTIC_WRITE`) inside one transaction,
so concurrent batches for the same metric are processed one at a time.

## Tools and configuration files

| Purpose                     | Tool                                           | Configuration                              |
|-----------------------------|------------------------------------------------|--------------------------------------------|
| Build / dependencies        | Maven (Maven Wrapper)                          | `pom.xml`, `.mvn/wrapper/`                 |
| Unit test runner            | JUnit 5                                        | `pom.xml` (spring-boot-starter-test)       |
| Mocking                     | Mockito                                        | `pom.xml` (spring-boot-starter-test)       |
| Repository tests            | Spring `@DataJpaTest` with in-memory H2        | `src/test/resources/application.properties` |
| API tests                   | Postman (run headless with Newman)             | `postman/PulseGuard.postman_collection.json` |
| Branch coverage             | JaCoCo                                         | `pom.xml`; report: `reports/jacoco/index.html` |
| Style checker               | Checkstyle, Google Java Style (`google_checks.xml`) | `pom.xml`; report: `reports/checkstyle-result.xml` |
| Continuous integration      | GitHub Actions: `./mvnw verify`, then the Postman collection against a running service | `.github/workflows/ci.yml` |
| Local database              | PostgreSQL 16 in Docker                        | `docker-compose.yml`                       |
| Project management          | GitHub Projects + Issues                       | link at the top of this README             |

## Project structure

```
src/main/java/edu/columbia/pulseguard/
  PulseGuardApplication.java   Spring Boot entry point
  api/                         Error types, error JSON body, global exception handler, header names
  controller/                  REST controllers (ingestion: registerMetric, submitDataPoints;
                               queries: queryAnomalies, getMetricSummary, updateDetectionRule)
  dto/                         Request and response bodies
  detection/                   Detection engine (DetectionEngine: z-score, EWMA, threshold), its
                               contract types, parameter keys and rule validation
  model/                       JPA entities: Metric, DataPoint, MetricStatistics, Anomaly
  repository/                  Spring Data repositories
  service/                     One service per operation; MetricLookupService performs the shared
                               client/ownership checks
src/test/java/...              Tests, mirroring the main package structure
```

## Third-party code

No third-party source code is included in this repository. All libraries are downloaded by Maven
as declared in `pom.xml`. The Maven Wrapper scripts (`mvnw`, `mvnw.cmd`,
`.mvn/wrapper/maven-wrapper.properties`) are the standard Apache Maven Wrapper 3.3.4
(https://maven.apache.org/wrapper/), copied from the course template repository.

## AI usage

Code generated or debugged with AI, including test code, is marked with a comment directly above
the class:

```java
// AI-generated: <tool>
```

| Member     | Tool               | Purpose | Representative prompts | Tokens |
|------------|--------------------|---------|------------------------|--------|
| Yuqi Zhang | Claude (claude.ai) | Generated the initial code from our T2 plan (build setup, entities, repositories, detection engine, endpoints, tests, CI, Postman collection, API docs); reviewed, ran through CI and fixed failing tests before merging | "Implement Area N of our T2 plan following Google Java Style, with unit, repository and API tests" | _to be filled_ |
