# Scalability guidelines

Scalability is a sequence of measured changes, not a reason to add layers or infrastructure before the product has load and failure evidence. These are the defaults for Pulso.

## Defaults and triggers

| Area | Default now | Add complexity when evidence shows |
| --- | --- | --- |
| Frontend state | Local state inside the owning feature | Multiple routes need the same server state or transitions become difficult to reason about |
| Frontend loading | One Angular application bundle | Route/feature cost materially affects startup or interaction metrics |
| HTTP | Synchronous request/response | A use case is long-running, retryable, or independent enough for a job/event |
| Backend | Stateless application processes | A measured bottleneck requires a specific cache, worker, or horizontal scale strategy |
| Database | PostgreSQL with bounded queries | Query plans, contention, or dataset size show a specific index, read model, partition, or replica need |
| Caching | No cache until a read path is measured | Repeated expensive reads have a clear invalidation and ownership strategy |
| Shared code | Duplicate a small stable type temporarily | The same contract is repeated, stable, and generated/shared ownership reduces real drift |
| Async messaging | No broker in the bootstrap | A documented throughput, latency, or integration requirement cannot be met safely with HTTP |

## Frontend scalability

- Keep feature state and data access local so unrelated screens can ship independently.
- Lazy-load feature routes when bundle analysis shows a meaningful benefit.
- Cache server state at the adapter/data-access layer with explicit freshness and invalidation rules.
- Paginate or incrementally load large collections; never render an unbounded feed by default.
- Keep expensive ranking or filtering out of render paths and measure before optimizing.
- Preserve accessible loading, empty, error, and retry states as the feature scales.

## Backend scalability

- Keep HTTP processes stateless so they can be replicated without session affinity.
- Bound every external call with a timeout; retries must be limited, observable, and safe for the operation's idempotency.
- Paginate collection endpoints and define stable ordering before clients depend on them.
- Measure database queries with realistic data before adding indexes, caching, replicas, or denormalized read models.
- Keep transactions short and avoid remote calls while a database transaction is open.
- Make background work explicit: a job has an owner, lifecycle, retry policy, idempotency key, and operational visibility.
- Treat connection pools, thread pools, queues, and rate limits as finite resources with configuration and metrics.

## Team scalability

- Organize work as vertical feature slices so one owner can change the frontend, backend, contract, and tests coherently.
- Assign ownership by feature and project, not by a shared folder that everyone edits.
- Keep cross-project contracts explicit and reviewable; do not solve coordination with a broad shared package.
- Record load assumptions and measurements in the relevant ADR or feature document.
- Prefer reversible changes behind a small interface; make irreversible data and contract changes additive first.

## A practical evolution path

1. Establish the first feature contract and baseline tests.
2. Add each schema change as a reviewed Supabase migration and add repository
   integration tests when an adapter's query behavior is product-critical.
3. Add structured logs, request identifiers, metrics, and traces before production traffic.
4. Measure the critical frontend and backend paths.
5. Address the measured bottleneck with one change and a before/after check.
6. Revisit the architecture only when a seam no longer provides enough locality or leverage.
