---
id: task-056
title: Fix DashboardAuthConfig registration in backend integration tests
status: In Progress
assignee:
  - '@assistant'
created_date: '2025-11-05 20:49'
updated_date: '2025-11-05 20:55'
labels:
  - bug
  - testing
  - configuration
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
All backend integration tests are currently failing with "Could not find a mapping for com.miimetiq.keycloak.sync.security.DashboardAuthConfig". The @ConfigMapping annotation requires proper registration in the Quarkus test configuration. This blocks all backend integration test execution.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Backend integration tests can start successfully
- [ ] #2 DashboardAuthConfig is properly registered in test environment
- [ ] #3 All previously passing backend integration tests pass again
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Identify the root cause of DashboardAuthConfig registration failure
2. Try adding complete dashboard configuration properties to test resources
3. Test if empty basic-auth property helps register ConfigMapping
4. Research Quarkus @ConfigMapping test configuration patterns
5. Consider alternative solutions (conditional bean, different injection pattern)
6. Document findings and recommended fixes for maintainer
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Investigation Summary

### Root Cause
The `@ConfigMapping(prefix = "dashboard")` annotation on `DashboardAuthConfig` interface is not being properly registered/discovered during Quarkus test initialization. This causes all backend integration tests to fail with:
```
java.util.NoSuchElementException: SRCFG00027: Could not find a mapping for com.miimetiq.keycloak.sync.security.DashboardAuthConfig
```

### Component Analysis

**DashboardAuthConfig.java:**
- Interface with `@ConfigMapping(prefix = "dashboard")`
- Three properties: `basic-auth` (Optional<String>), `oidc-enabled` (boolean), `oidc-required-role` (String)

**DashboardAuthFilter.java:**
- `@Provider` with `@Priority(Priorities.AUTHENTICATION)`  
- Injects `DashboardAuthConfig` via `@Inject`
- Filter is instantiated during application startup, causing early config requirement

### Attempted Fixes

1. ✅ Added `dashboard.oidc-enabled=false` to test properties
2. ✅ Added `dashboard.oidc-required-role=dashboard-admin` to test properties  
3. ✅ Added `dashboard.basic-auth=` (empty value) to test properties
4. ❌ None of these resolved the registration issue

### Analysis

The problem appears to be that:
1. Quarkus `@ConfigMapping` interfaces require special registration during build time
2. In test mode, the config mapping may not be properly discovered/registered
3. The filter's injection happens too early (during startup) before config is fully initialized

### Recommended Solutions

**Option 1: Make DashboardAuthConfig Optional in Filter (Preferred)**
```java
@Inject
Instance<DashboardAuthConfig> configInstance;

// In filter method:
if (!configInstance.isResolvable()) {
    // Skip auth if config not available
    return;
}
DashboardAuthConfig config = configInstance.get();
```

**Option 2: Use @ConfigProperty Instead**
Replace `@ConfigMapping` with individual `@ConfigProperty` injections:
```java
@ConfigProperty(name = "dashboard.oidc-enabled", defaultValue = "false")
boolean oidcEnabled;

@ConfigProperty(name = "dashboard.oidc-required-role", defaultValue = "dashboard-admin")
String oidcRequiredRole;

@ConfigProperty(name = "dashboard.basic-auth")
Optional<String> basicAuth;
```

**Option 3: Conditional Filter Registration**
Use `@IfBuildProperty` to only register the filter when needed:
```java
@Provider
@IfBuildProperty(name = "dashboard.auth.enabled", stringValue = "true")
public class DashboardAuthFilter {
```

**Option 4: Test Profile Exclusion**
Exclude the filter entirely in test profile using `quarkus.arc.exclude-types`.

### Files Modified

- `src/test/resources/application.properties` - Added complete dashboard configuration

### Impact

- All backend integration tests blocked (35+ tests)
- Existing tests worked before DashboardAuthFilter was added
- Frontend Playwright tests unaffected (they don't use backend test mode)

### Next Steps

1. Choose one of the recommended solutions
2. Implement the fix
3. Verify all backend integration tests can start
4. Run full test suite to ensure no regressions
<!-- SECTION:NOTES:END -->
