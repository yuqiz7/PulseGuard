# PulseGuard

COMS W4156 team project – a time-series anomaly detection service.

Client programs register numeric metrics, submit data points, and receive an anomaly verdict for
each point in real time. The service stores every metric's history, rolling statistics, detection
rule and detected anomalies. Detection methods: rolling z-score, EWMA and static thresholds.

**Team:** Rohit Biswas (rb3908), Jaxson Robinson (jnr2154), Sidharth Sharma (ss7202),
Yuqi Zhang (yz5072)

**Project board:** _link added once the GitHub project board is public_

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

<!-- Each area owner documents their endpoints below: inputs, outputs, side effects,
     status codes, an example request/response, and bad-usage examples. -->

### registerMetric

`POST /metrics` – registers a metric and its detection rule for the calling client.

**Request body**

| Field           | Type    | Required | Notes                                                                   |
|-----------------|---------|----------|-------------------------------------------------------------------------|
| `name`          | string  | yes      | Not blank, at most 100 characters, unique per client (surrounding spaces are trimmed) |
| `unit`          | string  | no       | At most 50 characters                                                   |
| `method`        | string  | yes      | `ZSCORE`, `EWMA` or `THRESHOLD`                                         |
| `params`        | object  | depends  | Numbers keyed as below; omitted keys get the default; unknown keys are rejected |
| `warmUpSamples` | integer | no       | 0–10000; the first N points are stored but never flagged. Default 10 (0 for `THRESHOLD`) |

| Method      | `params`                                                                                    |
|-------------|---------------------------------------------------------------------------------------------|
| `ZSCORE`    | `windowSize` – whole number 2–10000, default 30; `zThreshold` – greater than 0, default 3   |
| `EWMA`      | `alpha` – strictly between 0 and 1, default 0.3; `tolerance` – greater than 0, default 3    |
| `THRESHOLD` | `lowerBound` and `upperBound` – both required, `lowerBound` < `upperBound`                  |

**Response `201`** – the stored metric with the effective rule (defaults filled in). Use `id` in
all other endpoints.

**Side effects:** creates the metric and an empty statistics row. Nothing else changes.

**Errors:** `400` invalid or unknown parameter, blank/overlong name, unknown method, malformed JSON;
`401` missing `X-Client-Id`; `409` the client already has a metric with this name (other clients
may use the same name).

```bash
curl -X POST http://localhost:8080/metrics \
  -H "X-Client-Id: billing-service" -H "Content-Type: application/json" \
  -d '{"name": "latency", "unit": "ms", "method": "ZSCORE", "params": {"windowSize": 20}}'
```

```json
{ "id": 1, "name": "latency", "unit": "ms", "method": "ZSCORE",
  "params": { "windowSize": 20.0, "zThreshold": 3.0 }, "warmUpSamples": 10,
  "createdAt": "2026-10-20T12:00:00Z" }
```

### submitDataPoints

`POST /metrics/{metricId}/datapoints` – ingests a batch of points and returns a verdict for each.

**Request body**

```json
{ "dataPoints": [ { "timestamp": "2026-10-20T12:00:00Z", "value": 42.5 } ] }
```

- 1 to 1000 points per request; `timestamp` is ISO-8601 (UTC instant), `value` a finite number.
- Timestamps must be strictly increasing within the batch and strictly later than the latest point
  already stored for the metric. The first point of a new metric may have any timestamp.
- Each point is judged against the statistics *before* it is incorporated, so an outlier cannot
  hide itself. Points inside the warm-up count are stored but never flagged.

**Response `200`**

```json
{ "accepted": 2, "anomalies": 1,
  "results": [
    { "timestamp": "2026-10-20T12:00:00Z", "value": 10.0, "anomaly": false, "score": 0.0,
      "expectedLow": 0.0, "expectedHigh": 100.0 },
    { "timestamp": "2026-10-20T12:01:00Z", "value": 150.0, "anomaly": true, "score": 50.0,
      "expectedLow": 0.0, "expectedHigh": 100.0 } ] }
```

**Side effects:** stores the points with their verdicts, stores an anomaly record for each flagged
point, and updates the metric's statistics (sample count, EWMA state, latest timestamp). All of
this happens in one transaction: if the request fails, nothing from the batch is stored. The
metric's statistics row is locked for the duration, so concurrent batches for the same metric are
processed one after the other.

**Errors:** `400` empty or oversized batch, missing/invalid `timestamp` or `value`, non-finite
value, non-numeric `metricId`; `401` missing `X-Client-Id`; `404` metric does not exist or belongs
to another client; `409` a timestamp is not later than the previous one (in the batch or already
stored). Resend only points newer than the latest stored timestamp.

### queryAnomalies

_To be documented by the Area 4 owner._

### getMetricSummary

_To be documented by the Area 4 owner._

### updateDetectionRule

_To be documented by the Area 4 owner._

### Call-order constraints

- A metric must be registered (`registerMetric`) before any other operation can use its ID.
- Data points for a metric must be submitted in strictly increasing timestamp order.

### Example usage scenarios

_To be added once all endpoints are merged._

### Bad-usage examples

_To be added once all endpoints are merged._

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
| API tests                   | Postman                                        | `postman/` (run top to bottom against a running service) |
| Branch coverage             | JaCoCo                                         | `pom.xml`; report in `reports/` at release |
| Style checker               | Checkstyle, Google Java Style (`google_checks.xml`) | `pom.xml`; report in `reports/` at release |
| Continuous integration      | GitHub Actions (`./mvnw verify` on every PR)   | `.github/workflows/ci.yml`                 |
| Local database              | PostgreSQL 16 in Docker                        | `docker-compose.yml`                       |
| Project management          | GitHub Projects + Issues                       | link at the top of this README             |

## Project structure

```
src/main/java/edu/columbia/pulseguard/
  PulseGuardApplication.java   Spring Boot entry point
  api/                         Error types, error JSON body, global exception handler, header names
  detection/                   Detection engine contract (AnomalyDetector, MetricRule, DetectorState,
                               DetectionResult) and parameter keys (RuleParams)
  model/                       JPA entities: Metric, DataPoint, MetricStatistics, Anomaly
  repository/                  Spring Data repositories
  service/                     Services; MetricLookupService performs the shared client/ownership checks
src/test/java/...              Tests, mirroring the main package structure
```

## Third-party code

No third-party source code is included in this repository. All libraries are downloaded by Maven
as declared in `pom.xml`. The Maven Wrapper scripts (`mvnw`, `mvnw.cmd`,
`.mvn/wrapper/maven-wrapper.properties`) are the standard Apache Maven Wrapper 3.3.4
(https://maven.apache.org/wrapper/), copied from the course template repository.

## AI usage

Code generated or debugged with AI is marked with a comment directly above the class or method:

```java
// AI-assisted: <tool>, <generated | debugged>, reviewed by <name>
```

| Member      | Tool                                   | Purpose                                                                                                      | Representative prompts                                                                                                  | Tokens        |
|-------------|----------------------------------------|--------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------|---------------|
| Yuqi Zhang  | Claude (Anthropic, claude.ai)          | Generated the Area 1 project skeleton: build configuration, entities, repositories, error handling, shared lookup service, tests, CI workflow and this README's setup sections | "Implement Area 1 (setup & persistence) of the attached T2 plan for our PulseGuard proposal, following Google Java Style, with unit and repository tests" | _to be filled_ |
| Sidharth Sharma | Claude Code (Anthropic, Sonnet 5.5) | Generated the Area 3 registration and ingestion code (DTOs, rule validator, services, controllers), its unit/integration tests, the Postman collection and the registerMetric/submitDataPoints README sections | "Implement registerMetric and submitDataPoints (Area 3) from the T2 plan in four commits: registration service, registration endpoint, ingestion service, ingestion endpoint with Postman and docs" | 50,208 output; 102,066 input (+2,910,795 cache reads) |
