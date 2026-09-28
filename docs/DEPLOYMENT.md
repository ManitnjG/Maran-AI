# Deployment

## Free Render research preview

The root `render.yaml` deploys the FastAPI service using Docker, generates a server token and enables keyless public search. It does not deploy a language model or enable paid providers.

After creation, copy the service HTTPS URL and the generated `MARAN_ACCESS_TOKEN` into Android **More → Connection**. Keep the token out of the repository, APK and screenshots. Run **Test connections**; a missing model result does not prevent public-source research.

Free Render storage is ephemeral and services sleep when idle. This is a preview, not durable production hosting. Use **Export mission backup → Save file** before redeploying. Restore adds missing missions only and rejects overwrites.

## Operator-hosted model with persistent storage

On a host with Docker and enough resources for the selected model:

```sh
export MARAN_ACCESS_TOKEN="$(python -c 'import secrets; print(secrets.token_urlsafe(32))')"
docker compose up -d --build
docker compose exec ollama ollama pull qwen2.5:3b
```

The Compose example uses the official Ollama image (`latest`); pin a tested image digest for production. Put HTTPS in front of the API before connecting a phone with an access token. Ollama is only exposed on the internal Compose network. Both mission and model data use persistent named volumes.

## OpenCode

`services/orchestrator/Dockerfile.opencode` optionally installs OpenCode CLI 1.18.33. Enable `MARAN_OPENCODE_FREE_ENABLED=true` only after a provider check succeeds. The development smoke test was denied by the free provider, despite using its official CLI, so this is not the default deployment. No API-key-free AI availability is promised. OpenCode Zen with a user-supplied key or a local Ollama model can be configured separately.

## Validation sources
- https://exa.ai/docs/get-started/exa-mcp
- https://opencode.ai/docs/cli/
- https://help.tallysolutions.com/sample-xml/
- https://render.com/docs/free
- https://render.com/docs/blueprint-spec
- https://docs.ollama.com/docker
