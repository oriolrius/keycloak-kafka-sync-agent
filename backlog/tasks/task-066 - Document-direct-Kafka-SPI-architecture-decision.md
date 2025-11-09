---
id: task-066
title: Document direct Kafka SPI architecture decision
status: To Do
assignee: []
created_date: '2025-11-09 11:19'
labels:
  - documentation
  - architecture
dependencies:
  - task-065
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Create architectural decision record documenting the shift from webhook/cache to direct Kafka SPI. Capture rationale (real-time sync, no cache expiration, simpler architecture), trade-offs (Kafka downtime affects password changes, network dependency), and comparison with original approach. Update README with new architecture diagram and configuration.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Decision document created in backlog/decisions/
- [ ] #2 Document explains why direct SPI approach was chosen
- [ ] #3 Trade-offs and failure scenarios documented
- [ ] #4 Architecture diagram shows direct Keycloak→Kafka flow
- [ ] #5 README updated with new ENV variables for SPI Kafka config
<!-- AC:END -->
