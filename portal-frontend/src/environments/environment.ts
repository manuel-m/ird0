export const environment = {
  production: false,
  // Empty string for local dev - the proxy handles routing to BFF
  // Generated API paths already include /api/portal/v1/...
  apiUrl: '',
  auth: {
    issuer: 'http://localhost:8180/realms/ird0',
    clientId: 'ird0-portal',
    redirectUri: 'http://localhost:4200',
    scope: 'openid profile email',
    responseType: 'code',
    requireHttps: false,
    showDebugInformation: true,
  }
};
