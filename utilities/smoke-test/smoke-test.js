#!/usr/bin/env node

import { glob } from 'glob';
import { readFileSync, existsSync } from 'fs';
import yaml from 'js-yaml';
import { join } from 'path';

// Get root directory - use cwd since Makefile runs from project root
const ROOT_DIR = process.cwd();

// Port mapping for services with relative URLs or special handling
const SERVICE_PORT_MAP = {
  'portal-bff': process.env.PORTAL_BFF_HOST_PORT || '7777',
  'directory': process.env.POLICYHOLDERS_HOST_PORT || '8081',
};

// Load .env file if it exists
function loadEnvFile() {
  const envPath = join(ROOT_DIR, '.env');
  if (existsSync(envPath)) {
    const content = readFileSync(envPath, 'utf8');
    for (const line of content.split('\n')) {
      const trimmed = line.trim();
      if (trimmed && !trimmed.startsWith('#')) {
        const eqIndex = trimmed.indexOf('=');
        if (eqIndex > 0) {
          const key = trimmed.substring(0, eqIndex);
          const value = trimmed.substring(eqIndex + 1);
          if (!process.env[key]) {
            process.env[key] = value;
          }
        }
      }
    }
  }
}

/**
 * Parse an OpenAPI spec and extract testable GET endpoints.
 * Testable = GET method + no required path parameters.
 */
function parseOpenApiSpec(filePath) {
  const content = readFileSync(filePath, 'utf8');
  const spec = yaml.load(content);

  // Extract service name from file path
  const pathParts = filePath.split('/');
  const microservicesIdx = pathParts.indexOf('microservices');
  const serviceName = microservicesIdx >= 0 ? pathParts[microservicesIdx + 1] : 'unknown';

  // Get base URL from servers
  let baseUrl = spec.servers?.[0]?.url || 'http://localhost:8080';

  // Handle relative URLs (portal-bff uses url: /)
  if (baseUrl === '/' || baseUrl.startsWith('/')) {
    const port = SERVICE_PORT_MAP[serviceName] || process.env[`${serviceName.toUpperCase().replace(/-/g, '_')}_HOST_PORT`] || '8080';
    baseUrl = `http://localhost:${port}`;
  }

  const endpoints = [];

  if (!spec.paths) {
    return { serviceName, baseUrl, endpoints };
  }

  for (const [path, methods] of Object.entries(spec.paths)) {
    // Skip paths with required path parameters (contain {})
    if (path.includes('{')) {
      continue;
    }

    const getMethod = methods.get;
    if (getMethod) {
      // Check if there are any required parameters that would make this untestable
      const params = getMethod.parameters || [];
      const hasRequiredParams = params.some(p => p.required && p.in !== 'query');

      if (!hasRequiredParams) {
        endpoints.push({
          path,
          operationId: getMethod.operationId || path,
          summary: getMethod.summary || '',
        });
      }
    }
  }

  return { serviceName, baseUrl, endpoints };
}

/**
 * Test a single endpoint with HTTP GET.
 */
async function testEndpoint(baseUrl, endpoint, timeout = 5000) {
  const url = `${baseUrl}${endpoint.path}`;
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), timeout);

  try {
    const response = await fetch(url, {
      method: 'GET',
      signal: controller.signal,
      headers: {
        'Accept': 'application/json',
      },
    });

    clearTimeout(timeoutId);

    return {
      url,
      success: response.ok,
      status: response.status,
      statusText: response.statusText,
    };
  } catch (error) {
    clearTimeout(timeoutId);

    if (error.name === 'AbortError') {
      return {
        url,
        success: false,
        status: 0,
        statusText: `Timeout after ${timeout}ms`,
      };
    }

    return {
      url,
      success: false,
      status: 0,
      statusText: error.message,
    };
  }
}

/**
 * Main function - scan OpenAPI files and test endpoints.
 */
async function main() {
  console.log('Scanning OpenAPI files...\n');

  // Load .env file for port configuration
  loadEnvFile();

  // Find all OpenAPI spec files
  const pattern = join(ROOT_DIR, 'microservices', '*', 'openapi', '*.yaml');
  const specFiles = await glob(pattern);

  if (specFiles.length === 0) {
    console.log('No OpenAPI spec files found.');
    process.exit(0);
  }

  // Parse all specs and collect testable endpoints
  const allSpecs = [];
  let totalEndpoints = 0;

  for (const specFile of specFiles) {
    const spec = parseOpenApiSpec(specFile);
    if (spec.endpoints.length > 0) {
      allSpecs.push(spec);
      totalEndpoints += spec.endpoints.length;
    }
  }

  console.log(`Found ${specFiles.length} spec files, ${totalEndpoints} testable GET endpoints\n`);

  if (totalEndpoints === 0) {
    console.log('No testable GET endpoints found (endpoints without required path parameters).');
    process.exit(0);
  }

  // Test each endpoint sequentially, stop on first failure
  let testedCount = 0;
  let failedEndpoint = null;

  for (const spec of allSpecs) {
    for (const endpoint of spec.endpoints) {
      testedCount++;
      const result = await testEndpoint(spec.baseUrl, endpoint);

      process.stdout.write(`Testing: GET ${result.url}\n`);

      if (result.success) {
        console.log(`  \x1b[32m✓\x1b[0m ${result.status} ${result.statusText}\n`);
      } else {
        console.log(`  \x1b[31m✗\x1b[0m ${result.status || 'ERR'} ${result.statusText}\n`);
        failedEndpoint = result;
        break;
      }
    }

    if (failedEndpoint) {
      break;
    }
  }

  // Summary
  console.log('---');
  if (failedEndpoint) {
    console.log(`\x1b[31mFAILED:\x1b[0m ${testedCount - 1}/${totalEndpoints} passed, stopped at first failure`);
    console.log(`  Failed URL: ${failedEndpoint.url}`);
    process.exit(1);
  } else {
    console.log(`\x1b[32mSUCCESS:\x1b[0m All ${totalEndpoints} endpoints returned 2xx responses`);
    process.exit(0);
  }
}

main().catch(error => {
  console.error('Unexpected error:', error);
  process.exit(1);
});
