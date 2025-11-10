#!/bin/bash
set -e

KEYCLOAK_URL="${KEYCLOAK_URL:-https://localhost:57003}"
KEYCLOAK_ADMIN="${KEYCLOAK_ADMIN:-admin}"
KEYCLOAK_ADMIN_PASSWORD="${KEYCLOAK_ADMIN_PASSWORD:-The2password.}"
REALM="${KEYCLOAK_REALM:-master}"

USERNAME="${1:-kafka-test-user}"
NEW_PASSWORD="${2:-KafkaTest123!}"

# Get admin token
TOKEN=$(curl -k -s -X POST "$KEYCLOAK_URL/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=admin-cli" \
  -d "username=$KEYCLOAK_ADMIN" \
  -d "password=$KEYCLOAK_ADMIN_PASSWORD" | jq -r '.access_token')

# Get user ID
USER_ID=$(curl -k -s -X GET "$KEYCLOAK_URL/admin/realms/$REALM/users?username=$USERNAME&exact=true" \
  -H "Authorization: Bearer $TOKEN" | jq -r '.[0].id')

# Reset password
curl -k -s -X PUT "$KEYCLOAK_URL/admin/realms/$REALM/users/$USER_ID/reset-password" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"type\": \"password\",
    \"value\": \"$NEW_PASSWORD\",
    \"temporary\": false
  }"

echo "✅ Password reset for user: $USERNAME"
echo "   New password: $NEW_PASSWORD"
