# Organize product work by feature with inward dependencies

**Status: accepted.** Product capabilities will be organized as feature modules, with transport and infrastructure adapters around application use cases and domain policy. We choose this over repository-wide technical layers because feature ownership keeps change local, lets teams work vertically, and gives each module a small interface; technical layers remain useful inside a feature but must not become the primary ownership model.

## Consequences

- Frontend features own routes, state, data access, domain rules, and UI modules until reuse is proven.
- Backend features own HTTP adapters, use cases, domain policy, and persistence adapters with dependencies pointing inward.
- Shared modules are allowed only for stable, genuinely cross-feature behavior; `common`, `utils`, and `misc` are not architecture.
- The current prototype and health endpoint are bootstrap exceptions. New product code should follow these boundaries from its first commit.
