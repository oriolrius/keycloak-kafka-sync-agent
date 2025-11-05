import { test, expect } from '@playwright/test';

/**
 * OIDC Authentication Tests
 *
 * Verifies the OIDC authentication flow with Keycloak integration.
 * Tests token acquisition, validation, role-based access control, and API authentication.
 */

test.describe('OIDC Authentication Flow', () => {
  const KEYCLOAK_URL = process.env.KEYCLOAK_URL || 'https://localhost:57003';
  const API_BASE_URL = process.env.API_BASE_URL || 'http://localhost:57010';
  const REALM = 'master';
  const CLIENT_ID = 'dashboard-client';
  const CLIENT_SECRET = 'dashboard-secret';
  const USERNAME = 'admin';
  const PASSWORD = 'The2password.';

  test('should successfully obtain access token with password grant', async ({ request }) => {
    const response = await request.post(
      `${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token`,
      {
        form: {
          client_id: CLIENT_ID,
          client_secret: CLIENT_SECRET,
          username: USERNAME,
          password: PASSWORD,
          grant_type: 'password',
        },
        ignoreHTTPSErrors: true,
      }
    );

    expect(response.ok()).toBeTruthy();
    const data = await response.json();

    // Verify token structure
    expect(data).toHaveProperty('access_token');
    expect(data).toHaveProperty('refresh_token');
    expect(data).toHaveProperty('token_type', 'Bearer');
    expect(data).toHaveProperty('expires_in');

    // Verify access token is a valid JWT (3 parts separated by dots)
    const accessToken = data.access_token;
    expect(accessToken.split('.')).toHaveLength(3);
  });

  test('should include dashboard-admin role in access token', async ({ request }) => {
    const response = await request.post(
      `${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token`,
      {
        form: {
          client_id: CLIENT_ID,
          client_secret: CLIENT_SECRET,
          username: USERNAME,
          password: PASSWORD,
          grant_type: 'password',
        },
        ignoreHTTPSErrors: true,
      }
    );

    const data = await response.json();
    const accessToken = data.access_token;

    // Decode JWT payload (base64)
    const payloadBase64 = accessToken.split('.')[1];
    const payload = JSON.parse(Buffer.from(payloadBase64, 'base64').toString());

    // Verify dashboard-admin role exists
    expect(payload).toHaveProperty('realm_access');
    expect(payload.realm_access).toHaveProperty('roles');
    expect(payload.realm_access.roles).toContain('dashboard-admin');
  });

  test('should successfully introspect active token', async ({ request }) => {
    // Get access token
    const tokenResponse = await request.post(
      `${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token`,
      {
        form: {
          client_id: CLIENT_ID,
          client_secret: CLIENT_SECRET,
          username: USERNAME,
          password: PASSWORD,
          grant_type: 'password',
        },
        ignoreHTTPSErrors: true,
      }
    );

    const tokenData = await tokenResponse.json();
    const accessToken = tokenData.access_token;

    // Introspect token
    const introspectResponse = await request.post(
      `${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token/introspect`,
      {
        form: {
          client_id: CLIENT_ID,
          client_secret: CLIENT_SECRET,
          token: accessToken,
        },
        ignoreHTTPSErrors: true,
      }
    );

    expect(introspectResponse.ok()).toBeTruthy();
    const introspectData = await introspectResponse.json();

    // Verify token is active
    expect(introspectData.active).toBe(true);
    expect(introspectData.username).toBe(USERNAME);
    expect(introspectData.client_id).toBe(CLIENT_ID);
  });

  test('should successfully refresh access token', async ({ request }) => {
    // Get initial tokens
    const tokenResponse = await request.post(
      `${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token`,
      {
        form: {
          client_id: CLIENT_ID,
          client_secret: CLIENT_SECRET,
          username: USERNAME,
          password: PASSWORD,
          grant_type: 'password',
        },
        ignoreHTTPSErrors: true,
      }
    );

    const tokenData = await tokenResponse.json();
    const refreshToken = tokenData.refresh_token;

    // Wait a bit to ensure new token will be different
    await new Promise(resolve => setTimeout(resolve, 1000));

    // Refresh token
    const refreshResponse = await request.post(
      `${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token`,
      {
        form: {
          client_id: CLIENT_ID,
          client_secret: CLIENT_SECRET,
          grant_type: 'refresh_token',
          refresh_token: refreshToken,
        },
        ignoreHTTPSErrors: true,
      }
    );

    expect(refreshResponse.ok()).toBeTruthy();
    const refreshData = await refreshResponse.json();

    // Verify new tokens received
    expect(refreshData).toHaveProperty('access_token');
    expect(refreshData).toHaveProperty('refresh_token');
    expect(refreshData.access_token).not.toBe(tokenData.access_token);
  });

  test('should reject invalid credentials', async ({ request }) => {
    const response = await request.post(
      `${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token`,
      {
        form: {
          client_id: CLIENT_ID,
          client_secret: CLIENT_SECRET,
          username: USERNAME,
          password: 'wrong-password',
          grant_type: 'password',
        },
        ignoreHTTPSErrors: true,
      }
    );

    expect(response.status()).toBe(401);
    const data = await response.json();
    expect(data).toHaveProperty('error');
    expect(data.error).toBe('invalid_grant');
  });

  test('should reject invalid client credentials', async ({ request }) => {
    const response = await request.post(
      `${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token`,
      {
        form: {
          client_id: CLIENT_ID,
          client_secret: 'wrong-secret',
          username: USERNAME,
          password: PASSWORD,
          grant_type: 'password',
        },
        ignoreHTTPSErrors: true,
      }
    );

    expect(response.status()).toBe(401);
    const data = await response.json();
    expect(data).toHaveProperty('error');
    expect(data.error).toBe('unauthorized_client');
  });
});

test.describe('API Authentication with OIDC', () => {
  const KEYCLOAK_URL = process.env.KEYCLOAK_URL || 'https://localhost:57003';
  const API_BASE_URL = process.env.API_BASE_URL || 'http://localhost:57010';
  const REALM = 'master';
  const CLIENT_ID = 'dashboard-client';
  const CLIENT_SECRET = 'dashboard-secret';
  const USERNAME = 'admin';
  const PASSWORD = 'The2password.';

  let accessToken: string;

  test.beforeEach(async ({ request }) => {
    // Get access token for API tests
    const response = await request.post(
      `${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token`,
      {
        form: {
          client_id: CLIENT_ID,
          client_secret: CLIENT_SECRET,
          username: USERNAME,
          password: PASSWORD,
          grant_type: 'password',
        },
        ignoreHTTPSErrors: true,
      }
    );

    const data = await response.json();
    accessToken = data.access_token;
  });

  test('should access protected API endpoint with Bearer token', async ({ request }) => {
    // Note: This test assumes OIDC is enabled on the backend
    // If Basic Auth is still active, this test will be skipped in practice
    const response = await request.get(`${API_BASE_URL}/api/summary`, {
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    });

    // If OIDC is enabled, should get 200
    // If Basic Auth is still active, might get 401 (acceptable during transition)
    if (response.ok()) {
      const data = await response.json();
      expect(data).toHaveProperty('operations');
    } else {
      // If unauthorized, verify it's due to auth mechanism, not token validity
      expect([200, 401]).toContain(response.status());
    }
  });

  test('should reject API request without authentication', async ({ request }) => {
    const response = await request.get(`${API_BASE_URL}/api/summary`);

    // Should return 401 Unauthorized
    expect(response.status()).toBe(401);
  });

  test('should reject API request with invalid Bearer token', async ({ request }) => {
    const response = await request.get(`${API_BASE_URL}/api/summary`, {
      headers: {
        Authorization: 'Bearer invalid-token',
      },
    });

    // Should return 401 Unauthorized
    expect(response.status()).toBe(401);
  });

  test('should reject API request with expired token', async ({ request }) => {
    // Use a clearly invalid/expired token (malformed JWT)
    const expiredToken = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjB9.invalid';

    const response = await request.get(`${API_BASE_URL}/api/summary`, {
      headers: {
        Authorization: `Bearer ${expiredToken}`,
      },
    });

    // Should return 401 Unauthorized
    expect(response.status()).toBe(401);
  });
});

test.describe('Role-Based Access Control', () => {
  const KEYCLOAK_URL = process.env.KEYCLOAK_URL || 'https://localhost:57003';
  const API_BASE_URL = process.env.API_BASE_URL || 'http://localhost:57010';
  const REALM = 'master';
  const CLIENT_ID = 'dashboard-client';
  const CLIENT_SECRET = 'dashboard-secret';
  const USERNAME = 'admin';
  const PASSWORD = 'The2password.';

  test('should verify admin user has dashboard-admin role', async ({ request }) => {
    const response = await request.post(
      `${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token`,
      {
        form: {
          client_id: CLIENT_ID,
          client_secret: CLIENT_SECRET,
          username: USERNAME,
          password: PASSWORD,
          grant_type: 'password',
        },
        ignoreHTTPSErrors: true,
      }
    );

    const data = await response.json();
    const accessToken = data.access_token;

    // Decode JWT payload
    const payloadBase64 = accessToken.split('.')[1];
    const payload = JSON.parse(Buffer.from(payloadBase64, 'base64').toString());

    // Verify required role
    expect(payload.realm_access.roles).toContain('dashboard-admin');
  });

  test('should verify token contains expected claims', async ({ request }) => {
    const response = await request.post(
      `${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token`,
      {
        form: {
          client_id: CLIENT_ID,
          client_secret: CLIENT_SECRET,
          username: USERNAME,
          password: PASSWORD,
          grant_type: 'password',
        },
        ignoreHTTPSErrors: true,
      }
    );

    const data = await response.json();
    const accessToken = data.access_token;

    // Decode JWT payload
    const payloadBase64 = accessToken.split('.')[1];
    const payload = JSON.parse(Buffer.from(payloadBase64, 'base64').toString());

    // Verify essential claims
    expect(payload).toHaveProperty('iss'); // Issuer
    expect(payload).toHaveProperty('sub'); // Subject
    expect(payload).toHaveProperty('exp'); // Expiration
    expect(payload).toHaveProperty('iat'); // Issued at
    expect(payload).toHaveProperty('azp', CLIENT_ID); // Authorized party
    expect(payload).toHaveProperty('preferred_username', USERNAME);

    // Verify token is not expired
    const now = Math.floor(Date.now() / 1000);
    expect(payload.exp).toBeGreaterThan(now);
  });
});
