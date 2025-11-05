---
id: task-023
title: Implement SCRAM credential generator
status: Done
assignee:
  - '@claude'
created_date: '2025-11-04 18:35'
updated_date: '2025-11-05 04:36'
labels:
  - backend
  - crypto
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Create a service that generates SCRAM-SHA-256 and SCRAM-SHA-512 verifiers (storedKey, serverKey, salt, iterations) from plaintext passwords. This is required for creating Kafka SCRAM credentials from Keycloak user passwords.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 ScramCredentialGenerator service created with methods for both SHA-256 and SHA-512
- [x] #2 Implementation follows RFC 5802 SCRAM specification
- [x] #3 Generates salt using SecureRandom (32 bytes)
- [x] #4 Computes SaltedPassword using PBKDF2 with configurable iterations (default 4096)
- [x] #5 Computes ClientKey = HMAC(SaltedPassword, "Client Key")
- [x] #6 Computes StoredKey = H(ClientKey)
- [x] #7 Computes ServerKey = HMAC(SaltedPassword, "Server Key")
- [x] #8 Returns ScramCredential object with storedKey, serverKey, salt, iterations in Base64
- [x] #9 Unit tests validate against known test vectors
- [x] #10 Unit tests verify both SHA-256 and SHA-512 mechanisms
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
SCRAM credential generator service has been successfully implemented following RFC 5802 specification.

**Implementation Details:**
- Created ScramCredentialGenerator service with methods for both SHA-256 and SHA-512
- Implementation strictly follows RFC 5802 SCRAM specification
- Generates cryptographically secure random salt using SecureRandom (32 bytes)
- Computes SaltedPassword using PBKDF2 with configurable iterations (default 4096)
- Computes ClientKey = HMAC(SaltedPassword, "Client Key")
- Computes StoredKey = H(ClientKey) using SHA-256 or SHA-512
- Computes ServerKey = HMAC(SaltedPassword, "Server Key")
- Returns ScramCredential object with storedKey, serverKey, salt, and iterations encoded in Base64
- Comprehensive unit tests validate generation against known test vectors
- All tests verify both SHA-256 and SHA-512 mechanisms

**Files Created/Modified:**
- src/main/java/com/miimetiq/keycloak/sync/crypto/ScramCredentialGenerator.java
- src/main/java/com/miimetiq/keycloak/sync/domain/ScramCredential.java
- src/test/java/com/miimetiq/keycloak/sync/crypto/ScramCredentialGeneratorTest.java

**Security Features:**
- Uses SecureRandom for salt generation
- Properly clears password from memory after use (PBEKeySpec.clearPassword())
- Sensitive keys redacted in toString() method
- All cryptographic operations use standard Java security APIs
<!-- SECTION:NOTES:END -->
