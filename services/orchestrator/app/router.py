from dataclasses import dataclass
from typing import Protocol

class ProviderError(Exception): pass
class QuotaError(ProviderError): pass

class Provider(Protocol):
    name: str
    async def complete(self, prompt: str) -> str: ...

@dataclass
class ProviderState:
    provider: Provider
    enabled: bool = True
    failures: int = 0

class ModelRouter:
    """Provider-independent fallback. Mission state lives outside models."""
    def __init__(self, providers: list[Provider] | None=None):
        self.providers=[ProviderState(p) for p in (providers or [])]

    async def complete(self, prompt: str) -> tuple[str,str]:
        errors=[]
        for state in self.providers:
            if not state.enabled: continue
            try:
                return await state.provider.complete(prompt), state.provider.name
            except QuotaError as exc:
                state.failures += 1
                state.enabled = False
                errors.append(f"{state.provider.name}: quota:{exc}")
            except ProviderError as exc:
                state.failures += 1
                if state.failures >= 3: state.enabled = False
                errors.append(f"{state.provider.name}: {exc}")
        raise ProviderError("No model provider available: "+"; ".join(errors))
