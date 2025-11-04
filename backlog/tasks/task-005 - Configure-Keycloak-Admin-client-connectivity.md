---
id: task-005
title: Configure Keycloak Admin client connectivity
status: To Do
assignee: []
created_date: '2025-11-04 14:34'
labels:
  - backend
  - keycloak
  - connectivity
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Set up Keycloak Admin client (Java Admin Client or REST client with WebClient) with OAuth2 client credentials flow. Implement configuration from environment variables and verify connectivity.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Keycloak client bean is configured with base URL, realm, client ID, and secret
- [ ] #2 Configuration reads KC_BASE_URL, KC_REALM, KC_CLIENT_ID, and KC_CLIENT_SECRET from environment
- [ ] #3 Client successfully authenticates using client credentials flow
- [ ] #4 Client can fetch basic realm information to validate connectivity
- [ ] #5 Token refresh mechanism is implemented
- [ ] #6 Connection errors are properly logged with context
- [ ] #7 Keycloak client is included in health check status
<!-- AC:END -->
