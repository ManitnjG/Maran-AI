"""Use OpenCode's own CLI and advertised free models, with every tool denied."""
import asyncio
import json
import os
import shutil
import signal
import tempfile
from .router import ProviderError

class OpenCodeCLIProvider:
    def __init__(self, model: str):
        self.model = model
        self.name = 'opencode-cli:' + model

    async def complete(self, prompt: str) -> str:
        binary = shutil.which('opencode')
        if not binary:
            raise ProviderError('OpenCode CLI is not installed')
        # Keep host credentials and project instructions out of this text-only worker.
        allowed = ('PATH','LANG','SSL_CERT_FILE','SSL_CERT_DIR','HTTP_PROXY','HTTPS_PROXY','ALL_PROXY','NO_PROXY',
                   'http_proxy','https_proxy','all_proxy','no_proxy')
        env = {k:os.environ[k] for k in allowed if k in os.environ}
        with tempfile.TemporaryDirectory(prefix='maran-model-') as workspace:
            env.update({
                'XDG_CONFIG_HOME': workspace + '/config', 'XDG_DATA_HOME': workspace + '/data',
                'XDG_CACHE_HOME': workspace + '/cache', 'XDG_STATE_HOME': workspace + '/state',
                'OPENCODE_CONFIG_DIR': workspace + '/config',
                'OPENCODE_CONFIG_CONTENT': json.dumps({'permission':{'*':'deny'},'share':'disabled','autoupdate':False}),
                'OPENCODE_PERMISSION': json.dumps({'*':'deny'}),
                'OPENCODE_DISABLE_AUTOUPDATE':'true', 'OPENCODE_DISABLE_DEFAULT_PLUGINS':'true',
                'OPENCODE_DISABLE_CLAUDE_CODE':'true', 'OPENCODE_DISABLE_LSP_DOWNLOAD':'true',
            })
            try:
                proc = await asyncio.create_subprocess_exec(
                    binary, 'run', '--format', 'json', '--model', self.model, '--', prompt,
                    cwd=workspace, env=env, start_new_session=True,
                    stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.PIPE)
            except OSError as exc:
                raise ProviderError('OpenCode could not start') from exc
            try:
                out, _ = await asyncio.wait_for(proc.communicate(), timeout=50)
            except (asyncio.TimeoutError, asyncio.CancelledError) as exc:
                if proc.returncode is None:
                    try: os.killpg(proc.pid, signal.SIGKILL)
                    except ProcessLookupError: pass
                await proc.communicate()
                if isinstance(exc, asyncio.CancelledError): raise
                raise ProviderError('OpenCode model timed out') from exc
            if proc.returncode != 0:
                raise ProviderError('OpenCode model unavailable; no paid fallback was enabled')
            parts = []
            for line in out.decode(errors='replace').splitlines():
                try: event = json.loads(line)
                except ValueError: continue
                if event.get('type') == 'error':
                    raise ProviderError('OpenCode provider returned an error')
                if event.get('type') == 'text' and isinstance(event.get('part',{}).get('text'),str):
                    parts.append(event['part']['text'])
            if not parts: raise ProviderError('OpenCode returned no text')
            return '\n'.join(parts)
