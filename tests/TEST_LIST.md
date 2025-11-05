# Test List - API Endpoint Tests

This document provides a quick reference of all test files and what they verify.

## Test Files

### 📄 `api/openapi-documentation.spec.ts`
**Purpose**: Verify OpenAPI/Swagger UI documentation

| Test Name | What It Checks |
|-----------|----------------|
| `should load Swagger UI successfully` | ✅ Swagger UI page loads<br>✅ Page title is correct<br>✅ API name and version displayed |
| `should display Dashboard endpoints section` | ✅ Dashboard section visible<br>✅ All 3 Dashboard endpoints listed<br>✅ Section description present |
| `should display Configuration endpoints section` | ✅ Configuration section visible<br>✅ Retention endpoints listed<br>✅ Section description present |
| `should display all expected schemas` | ✅ All 8 DTO schemas documented<br>✅ Schema types shown (object/string) |
| `should take screenshot of OpenAPI overview` | ✅ Full-page screenshot captured |

**Expected Endpoints**:
- GET /api/summary
- GET /api/operations
- GET /api/batches
- GET /api/config/retention
- PUT /api/config/retention

**Expected Schemas**:
- SummaryResponse
- OperationResponse
- OperationsPageResponse
- BatchResponse
- BatchesPageResponse
- RetentionConfigResponse
- RetentionConfigUpdateRequest
- ErrorResponse

---

### 📄 `api/operations-endpoint.spec.ts`
**Purpose**: Verify GET /api/operations endpoint functionality

| Test Name | What It Checks |
|-----------|----------------|
| `should return paginated operations with default parameters` | ✅ Returns HTTP 200<br>✅ Response has `operations` array<br>✅ Response has pagination metadata<br>✅ Default page=0, pageSize=20<br>✅ Operation objects have all required fields |
| `should display operations endpoint documentation` | ✅ Endpoint description visible<br>✅ All 7 query parameters documented |
| `should accept and validate opType filter` | ✅ opType filter works<br>✅ Accepts valid enum values |
| `should accept and validate result filter` | ✅ result filter works<br>✅ Accepts valid enum values |
| `should take screenshot of operations response` | ✅ Screenshot of successful response |

**Query Parameters Tested**:
- `page` (integer, default: 0)
- `pageSize` (integer, default: 20)
- `startTime` (ISO 8601 datetime)
- `endTime` (ISO 8601 datetime)
- `principal` (string)
- `opType` (enum: SCRAM_UPSERT, SCRAM_DELETE, ACL_CREATE, ACL_DELETE)
- `result` (enum: SUCCESS, ERROR, SKIPPED)

**Response Fields Verified**:
```json
{
  "operations": [
    {
      "id": number,
      "correlationId": string,
      "occurredAt": datetime,
      "realm": string,
      "clusterId": string,
      "principal": string,
      "opType": string,
      "mechanism": string | null,
      "result": string,
      "errorCode": string | null,
      "errorMessage": string | null,
      "durationMs": number
    }
  ],
  "page": number,
  "pageSize": number,
  "total": number,
  "totalPages": number
}
```

---

### 📄 `api/summary-endpoint.spec.ts`
**Purpose**: Verify GET /api/summary endpoint functionality

| Test Name | What It Checks |
|-----------|----------------|
| `should return dashboard summary statistics` | ✅ Returns HTTP 200<br>✅ All 6 fields present<br>✅ Correct data types<br>✅ Valid value ranges<br>✅ Timestamp is valid ISO-8601 |
| `should display summary endpoint documentation` | ✅ Endpoint description visible<br>✅ "No parameters" displayed<br>✅ Response codes documented (200, 500) |
| `should calculate real-time statistics` | ✅ Statistics are computed in real-time<br>✅ Timestamp updates on each request<br>✅ Database usage is consistent |
| `should take screenshot of summary response` | ✅ Screenshot of successful response |

**Response Fields Verified**:
```json
{
  "opsPerHour": number (≥ 0),
  "errorRate": number (0-100),
  "latencyP95": number | null,
  "latencyP99": number | null,
  "dbUsageBytes": number (≥ 0),
  "timestamp": "ISO-8601 datetime"
}
```

**Validations**:
- ✅ `opsPerHour` ≥ 0
- ✅ `errorRate` between 0 and 100
- ✅ `dbUsageBytes` ≥ 0
- ✅ `timestamp` is valid ISO-8601 format
- ✅ All numeric fields are numbers, not strings
- ✅ Timestamp updates on subsequent requests

---

## Test Statistics

| Metric | Count |
|--------|-------|
| **Total Test Files** | 3 |
| **Total Tests** | 12 |
| **Endpoints Covered** | 3 |
| **Screenshots Generated** | 3 |

## Coverage Summary

### ✅ Fully Tested Endpoints
1. **GET /api/summary** - Dashboard statistics
   - Response structure ✓
   - Data types ✓
   - Value ranges ✓
   - Real-time calculation ✓

2. **GET /api/operations** - Operation timeline
   - Response structure ✓
   - Pagination ✓
   - Filtering (opType, result) ✓
   - Query parameters ✓

3. **OpenAPI Documentation** - Swagger UI
   - Page loading ✓
   - Endpoint documentation ✓
   - Schema documentation ✓

### ⏳ Not Yet Tested (Future Work)
- GET /api/batches
- GET /api/config/retention
- PUT /api/config/retention
- Error scenarios (400, 500 responses)
- Edge cases (empty results, invalid filters)

---

## Running Tests

```bash
# Install dependencies first
npm install
npm run test:install

# Run all tests
npm run test:api

# Run specific test file
npx playwright test tests/api/openapi-documentation.spec.ts

# Run in UI mode
npm run test:api:ui

# Run in debug mode
npm run test:api:debug

# View test report
npm run test:api:report
```

---

## Screenshots Generated

1. **`screenshots/openapi-overview.png`**
   - Full OpenAPI documentation page
   - Shows all endpoints and schemas

2. **`screenshots/api-operations-response.png`**
   - GET /api/operations successful response
   - Shows real data with pagination

3. **`screenshots/api-summary-response.png`**
   - GET /api/summary successful response
   - Shows calculated statistics

---

## Maintenance Notes

**When to Update Tests**:
- ✅ New API endpoints added → Add new test file
- ✅ Response schema changes → Update field assertions
- ✅ New query parameters → Add parameter tests
- ✅ OpenAPI documentation changes → Update documentation tests

**Test Data Requirements**:
- Tests assume backend is running with some historical data
- At least 1 operation should exist in the database
- Database should have some size > 0 bytes

---

Last Updated: 2025-11-05
Test Framework: Playwright v1.49.0
Language: TypeScript
