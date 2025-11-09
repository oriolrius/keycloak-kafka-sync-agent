---
id: task-063
title: Remove webhook and cache code from SPI branch
status: To Do
assignee: []
created_date: '2025-11-09 11:18'
labels:
  - cleanup
  - spi
dependencies:
  - task-062
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Remove all webhook-related code from the feature branch: remove HTTP POST to sync-agent webhook, remove ThreadLocal password sharing, remove webhook endpoint dependencies. Keep only direct Kafka sync logic in the SPI. Be radical but use common sense - remove everything not needed for direct Kafka approach.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Webhook HTTP client code removed from SPI
- [ ] #2 ThreadLocal password storage removed
- [ ] #3 PasswordWebhookResource reference removed
- [ ] #4 SPI only contains direct Kafka sync logic
- [ ] #5 SPI compiles successfully without webhook dependencies
<!-- AC:END -->
