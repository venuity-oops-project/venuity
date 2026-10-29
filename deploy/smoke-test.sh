#!/usr/bin/env bash
# ==============================================================================
# Venuity Deployment Smoke Test Script
# Usage: ./deploy/smoke-test.sh <BASE_URL>
# Example: ./deploy/smoke-test.sh https://venuity.onrender.com
# ==============================================================================
set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
# Trim trailing slash if present
BASE_URL="${BASE_URL%/}"

echo "================================================================="
echo "   VENUITY DEPLOYMENT SMOKE TEST"
echo "   Target: ${BASE_URL}"
echo "================================================================="

# 1. Health Endpoint Check
echo -n "[1/3] Checking platform health check (/api/health)... "
HEALTH_RESPONSE=$(curl -fsS -m 15 "${BASE_URL}/api/health" 2>/dev/null || true)
if echo "${HEALTH_RESPONSE}" | grep -q '"status":"UP"'; then
    echo "OK (status: UP)"
else
    echo "FAILED!"
    echo "Expected status UP but got: ${HEALTH_RESPONSE}"
    exit 1
fi

# 2. Static Web UI Check
echo -n "[2/3] Checking single-page application root (/)... "
ROOT_HTML=$(curl -fsS -m 15 -L "${BASE_URL}/" 2>/dev/null || true)
if echo "${ROOT_HTML}" | grep -qi "venuity"; then
    echo "OK (Venuity HTML served)"
else
    echo "FAILED!"
    echo "Root endpoint did not return valid Venuity HTML."
    exit 1
fi

# 3. Settings Branding Check (Public endpoint)
echo -n "[3/3] Checking public branding endpoint (/api/settings/branding)... "
BRANDING=$(curl -fsS -m 15 "${BASE_URL}/api/settings/branding" 2>/dev/null || true)
if echo "${BRANDING}" | grep -qi "businessName"; then
    echo "OK (Branding metadata returned)"
else
    echo "FAILED!"
    echo "Branding endpoint did not return valid JSON: ${BRANDING}"
    exit 1
fi

echo "================================================================="
echo "   ALL SMOKE TESTS PASSED SUCCESSFULLY!"
echo "================================================================="
