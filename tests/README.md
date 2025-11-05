# API Tests

This directory contains Playwright-based end-to-end tests for the Keycloak Kafka Sync Agent API endpoints.

## Overview

These tests verify the functionality of the REST API endpoints that the dashboard UI consumes, including:
- OpenAPI/Swagger documentation
- Dashboard summary statistics
- Operations timeline with filtering
- Batch history

## Test Structure

```
tests/
├── api/
│   ├── openapi-documentation.spec.ts    # OpenAPI/Swagger UI tests
│   ├── operations-endpoint.spec.ts      # GET /api/operations tests
│   └── summary-endpoint.spec.ts         # GET /api/summary tests
├── screenshots/                          # Generated test screenshots
├── playwright.config.ts                  # Playwright configuration
└── README.md                            # This file
```

## Test Suites

### 1. OpenAPI Documentation Tests (`openapi-documentation.spec.ts`)

Tests the OpenAPI/Swagger UI documentation interface.

**What it checks:**
- ✅ Swagger UI loads successfully at `/q/swagger-ui`
- ✅ API title and version are displayed correctly
- ✅ Dashboard endpoints section is present and complete
  - GET /api/summary
  - GET /api/operations
  - GET /api/batches
- ✅ Configuration endpoints section is present
  - GET /api/config/retention
  - PUT /api/config/retention
- ✅ All DTO schemas are documented
  - SummaryResponse
  - OperationResponse
  - OperationsPageResponse
  - BatchResponse
  - BatchesPageResponse
  - RetentionConfigResponse
  - RetentionConfigUpdateRequest
  - ErrorResponse
- ✅ Full-page screenshot of OpenAPI overview is captured

**Tests:**
1. `should load Swagger UI successfully`
2. `should display Dashboard endpoints section`
3. `should display Configuration endpoints section`
4. `should display all expected schemas`
5. `should take screenshot of OpenAPI overview`

---

### 2. Operations Endpoint Tests (`operations-endpoint.spec.ts`)

Tests the GET /api/operations endpoint for retrieving paginated operation history.

**What it checks:**
- ✅ Endpoint returns 200 OK with valid response
- ✅ Response structure matches expected schema:
  ```json
  {
    "operations": [...],
    "page": 0,
    "pageSize": 20,
    "total": number,
    "totalPages": number
  }
  ```
- ✅ Each operation contains required fields:
  - id, correlationId, occurredAt
  - realm, clusterId, principal
  - opType, result, durationMs
- ✅ Default pagination works (page=0, pageSize=20)
- ✅ All query parameters are documented:
  - page, pageSize
  - startTime, endTime
  - principal, opType, result
- ✅ opType filter accepts valid values (SCRAM_UPSERT, etc.)
- ✅ result filter accepts valid values (SUCCESS, ERROR, SKIPPED)
- ✅ Screenshot of successful response is captured

**Tests:**
1. `should return paginated operations with default parameters`
2. `should display operations endpoint documentation`
3. `should accept and validate opType filter`
4. `should accept and validate result filter`
5. `should take screenshot of operations response`

---

### 3. Summary Endpoint Tests (`summary-endpoint.spec.ts`)

Tests the GET /api/summary endpoint for dashboard statistics.

**What it checks:**
- ✅ Endpoint returns 200 OK with valid response
- ✅ Response structure matches expected schema:
  ```json
  {
    "opsPerHour": number,
    "errorRate": number,
    "latencyP95": number,
    "latencyP99": number,
    "dbUsageBytes": number,
    "timestamp": "ISO-8601 datetime"
  }
  ```
- ✅ All fields have correct data types
- ✅ Value ranges are valid:
  - opsPerHour ≥ 0
  - errorRate between 0-100
  - dbUsageBytes ≥ 0
  - timestamp is valid ISO-8601 format
- ✅ Endpoint requires no parameters
- ✅ Statistics are calculated in real-time (timestamp updates)
- ✅ Response codes are documented (200, 500)
- ✅ Screenshot of successful response is captured

**Tests:**
1. `should return dashboard summary statistics`
2. `should display summary endpoint documentation`
3. `should calculate real-time statistics`
4. `should take screenshot of summary response`

---

## Prerequisites

### 1. Install Dependencies

```bash
npm install --save-dev @playwright/test
npx playwright install chromium
```

### 2. Start the Backend

The tests expect the Quarkus backend to be running on `http://localhost:57010`.

**Option A: Automatic (via Playwright config)**
```bash
# Playwright will start Quarkus automatically
npm run test:api
```

**Option B: Manual**
```bash
# Terminal 1: Start Quarkus
./mvnw quarkus:dev

# Terminal 2: Run tests
npm run test:api
```

---

## Running the Tests

### Run All Tests

```bash
npx playwright test --config=tests/playwright.config.ts
```

### Run Specific Test Suite

```bash
# OpenAPI documentation tests
npx playwright test tests/api/openapi-documentation.spec.ts

# Operations endpoint tests
npx playwright test tests/api/operations-endpoint.spec.ts

# Summary endpoint tests
npx playwright test tests/api/summary-endpoint.spec.ts
```

### Run in UI Mode (Interactive)

```bash
npx playwright test --ui --config=tests/playwright.config.ts
```

### Run in Headed Mode (See Browser)

```bash
npx playwright test --headed --config=tests/playwright.config.ts
```

### Debug Mode

```bash
npx playwright test --debug --config=tests/playwright.config.ts
```

---

## Test Reports

After running tests, reports are generated in:

- **HTML Report**: `tests/test-results/html/index.html`
  ```bash
  npx playwright show-report tests/test-results/html
  ```

- **JSON Report**: `tests/test-results/results.json`

- **Screenshots**: `tests/screenshots/`
  - `openapi-overview.png` - Full OpenAPI documentation
  - `api-operations-response.png` - Operations endpoint response
  - `api-summary-response.png` - Summary endpoint response

---

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `API_BASE_URL` | `http://localhost:57010` | Base URL for the API server |
| `CI` | `false` | Set to `true` in CI environments for stricter testing |

Example:
```bash
API_BASE_URL=http://localhost:8080 npx playwright test --config=tests/playwright.config.ts
```

---

## CI/CD Integration

### GitHub Actions Example

```yaml
name: API Tests

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-node@v3
        with:
          node-version: '20'

      - name: Install dependencies
        run: |
          npm install
          npx playwright install --with-deps chromium

      - name: Run Playwright tests
        run: npx playwright test --config=tests/playwright.config.ts
        env:
          CI: true

      - name: Upload test results
        if: always()
        uses: actions/upload-artifact@v3
        with:
          name: playwright-report
          path: tests/test-results/
```

---

## Adding New Tests

1. Create a new `.spec.ts` file in `tests/api/`
2. Import Playwright test utilities:
   ```typescript
   import { test, expect } from '@playwright/test';
   ```
3. Use the `BASE_URL` pattern for consistency:
   ```typescript
   const BASE_URL = process.env.API_BASE_URL || 'http://localhost:57010';
   ```
4. Group related tests using `test.describe()`
5. Add descriptive test names and comments

Example:
```typescript
test.describe('GET /api/batches', () => {
  const BASE_URL = process.env.API_BASE_URL || 'http://localhost:57010';

  test('should return paginated batches', async ({ page }) => {
    // Your test code here
  });
});
```

---

## Troubleshooting

### Tests Fail with "Connection Refused"

**Solution**: Ensure Quarkus is running on port 57010
```bash
./mvnw quarkus:dev
curl http://localhost:57010/q/health/ready
```

### Tests Timeout

**Solution**: Increase timeout in `playwright.config.ts`:
```typescript
use: {
  actionTimeout: 30000, // 30 seconds
  navigationTimeout: 60000, // 60 seconds
}
```

### Screenshots Not Generated

**Solution**: Ensure screenshots directory exists:
```bash
mkdir -p tests/screenshots
```

---

## Best Practices

1. **Keep tests independent** - Each test should work in isolation
2. **Use meaningful assertions** - Check response structure, not just status codes
3. **Clean test data** - Don't rely on specific database state
4. **Take screenshots** - Visual verification helps debugging
5. **Document what you test** - Add comments explaining complex test logic
6. **Use type safety** - TypeScript helps catch errors early

---

## Related Documentation

- [Playwright Documentation](https://playwright.dev/)
- [OpenAPI Specification](http://localhost:57010/q/openapi)
- [Swagger UI](http://localhost:57010/q/swagger-ui)
- [Task 047 Implementation Notes](../backlog/tasks/task-047%20-%20Implement-backend-API-endpoints-for-UI-data.md)

---

## Maintenance

These tests should be updated when:
- ✅ New API endpoints are added
- ✅ Response schemas change
- ✅ New query parameters are introduced
- ✅ OpenAPI documentation is modified
- ✅ Error handling behavior changes

Last Updated: 2025-11-05
