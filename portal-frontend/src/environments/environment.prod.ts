export const environment = {
  production: true,
  // Empty string - API paths are relative, served from same origin or behind reverse proxy
  // Generated API paths already include /api/portal/v1/...
  apiUrl: '',
  auth: {
    issuer: '/realms/ird0',
    clientId: 'ird0-portal',
    redirectUri: window.location.origin,
    scope: 'openid profile email',
    responseType: 'code',
    requireHttps: true,
    showDebugInformation: false,
  }
};
