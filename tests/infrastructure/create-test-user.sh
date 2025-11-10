#!/bin/bash
set -e

KEYCLOAK_URL="${KEYCLOAK_URL:-https://localhost:57003}"
KEYCLOAK_ADMIN="${KEYCLOAK_ADMIN:-admin}"
KEYCLOAK_ADMIN_PASSWORD="${KEYCLOAK_ADMIN_PASSWORD:-The2password.}"
REALM="${KEYCLOAK_REALM:-master}"

TEST_USERNAME="${1:-demo-user}"
TEST_PASSWORD="${2:-DemoPassword123!}"

echo "Creating test user: $TEST_USERNAME"

# Get admin token
TOKEN=$(curl -k -s -X POST "$KEYCLOAK_URL/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=admin-cli" \
  -d "username=$KEYCLOAK_ADMIN" \
  -d "password=$KEYCLOAK_ADMIN_PASSWORD" | jq -r '.access_token')

# Create user
curl -k -s -X POST "$KEYCLOAK_URL/admin/realms/$REALM/users" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"username\": \"$TEST_USERNAME\",
    \"enabled\": true,
    \"emailVerified\": true,
    \"email\": \"${TEST_USERNAME}@test.local\"
  }"

# Get user ID
USER_ID=$(curl -k -s -X GET "$KEYCLOAK_URL/admin/realms/$REALM/users?username=$TEST_USERNAME&exact=true" \
  -H "Authorization: Bearer $TOKEN" | jq -r '.[0].id')

# Set password
curl -k -s -X PUT "$KEYCLOAK_URL/admin/realms/$REALM/users/$USER_ID/reset-password" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"type\": \"password\",
    \"value\": \"$TEST_PASSWORD\",
    \"temporary\": false
  }"

echo "✅ User created successfully!"
echo "   Username: $TEST_USERNAME"
echo "   Password: $TEST_PASSWORD"
echo "   Realm: $REALM"
echo ""
echo "You can now see this user in the Keycloak admin console:"
echo "https://keycloak.example:57003/admin/master/console/#/master/users"
