#!/bin/bash
set -e

KEYCLOAK_URL="${KEYCLOAK_URL:-https://localhost:57003}"
KEYCLOAK_ADMIN="${KEYCLOAK_ADMIN:-admin}"
KEYCLOAK_ADMIN_PASSWORD="${KEYCLOAK_ADMIN_PASSWORD:-The2password.}"
REALM="${KEYCLOAK_REALM:-master}"
WEBHOOK_URL="${WEBHOOK_URL:-http://sync-agent:57010/api/webhook/password}"

echo "Configuring Keycloak realm: $REALM"
echo "Webhook URL: $WEBHOOK_URL"

# Get admin token
echo "Getting admin token..."
TOKEN=$(curl -k -s -X POST "$KEYCLOAK_URL/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=admin-cli" \
  -d "username=$KEYCLOAK_ADMIN" \
  -d "password=$KEYCLOAK_ADMIN_PASSWORD" | jq -r '.access_token')

if [ "$TOKEN" = "null" ] || [ -z "$TOKEN" ]; then
  echo "ERROR: Failed to get admin token"
  exit 1
fi

echo "Token obtained successfully"

# Get current realm configuration
echo "Fetching current realm configuration..."
REALM_CONFIG=$(curl -k -s -X GET "$KEYCLOAK_URL/admin/realms/$REALM" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json")

# Check if password-sync-listener is already configured
if echo "$REALM_CONFIG" | jq -e '.eventsListeners[] | select(. == "password-sync-listener")' > /dev/null 2>&1; then
  echo "✓ password-sync-listener is already configured"
else
  echo "Adding password-sync-listener to eventsListeners..."

  # Add password-sync-listener to eventsListeners array
  UPDATED_CONFIG=$(echo "$REALM_CONFIG" | jq '.eventsListeners += ["password-sync-listener"]')

  # Update realm configuration
  curl -k -s -X PUT "$KEYCLOAK_URL/admin/realms/$REALM" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "$UPDATED_CONFIG"

  echo "✓ password-sync-listener added to realm configuration"
fi

# Configure realm attributes for webhook URL
echo "Setting webhook URL in realm attributes..."
REALM_CONFIG=$(curl -k -s -X GET "$KEYCLOAK_URL/admin/realms/$REALM" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json")

UPDATED_CONFIG=$(echo "$REALM_CONFIG" | jq --arg url "$WEBHOOK_URL" \
  '.attributes["password-sync-webhook-url"] = $url')

curl -k -s -X PUT "$KEYCLOAK_URL/admin/realms/$REALM" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "$UPDATED_CONFIG"

echo "✓ Webhook URL configured"

echo ""
echo "✅ Keycloak realm configuration complete!"
echo "   Realm: $REALM"
echo "   Event Listener: password-sync-listener (enabled)"
echo "   Webhook URL: $WEBHOOK_URL"
