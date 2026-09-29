# Deployment

## Free Render preview

The root render.yaml prepares a Docker web service with a generated server token and public search.
It has not been deployed by this work. Free hosting does not grant model-provider access.

After creation, copy the service HTTPS URL and MARAN_ACCESS_TOKEN into Android More → Connection.
Configure OPENCODE_API_KEY in server secrets only if you have authorized Zen access.
A blank MARAN_OPENCODE_ZEN_MODEL selects from currently active zero-cost models; a selected model
must also be free in the live catalog. Billing, authentication and quota errors stop requests.

Run Test connections. The standalone search tool can work without a language model.
Missions now require OpenCode for planning, drafts, research synthesis and review.

Free Render storage is ephemeral and services sleep when idle. Export mission backups before
redeploying. Restore adds missing missions only and rejects overwrites.

## Operator-hosted service

Set MARAN_ACCESS_TOKEN to a long random token, then run docker compose up -d --build.
The Compose example stores mission data in a named volume. Put HTTPS in front of the API
before connecting a phone with an access token. Run one Uvicorn worker.

## OpenCode access

MARAN uses the official OpenCode Zen model gateway, not an embedded copy of the OpenCode application.
Both direct Android chat and backend reasoning check the free-model catalog and stop on access,
billing or rate-limit failures. Android keys and backend keys are separate; neither is committed.

Live keyless API and official CLI probes returned HTTP 403 restricting the free tier to OpenCode.
This integration does not bypass that restriction. A Zen key is optional configuration, not a
guarantee of free access. Existing CLI/Ollama adapter code is no longer registered by the runtime.

## Sources
- https://opencode.ai/docs/zen/
- https://models.dev/api.json
- https://exa.ai/docs/get-started/exa-mcp
- https://render.com/docs/free
