# Connection conversations

## Current scope

PUL-27 implements the backend boundary that turns an accepted contextual
project request into one private conversation. It does not yet expose
profile-block management, message sending, conversation lists, or an
Angular acceptance screen.

Request creation is provided by PUL-25 through the same `ConnectionIntent`
domain and canonical `connection_intents` table. Acceptance restores the full
persisted context without changing the creation endpoint's response contract.

## Migration order after integrating PUL-25

Apply `20261103133534_create_connection_intents.sql` first, followed by the
PUL-27 migrations `20261103150000`, `20261103150100`, and `20261103150200`.
These extend the canonical table rather than creating it again, preserving
PUL-25's pending-context unique indexes and opportunity/project foreign key.

Local databases that already applied the previous, unmerged PUL-27 migration
versions (`20261030212210`, `20261031001305`, `20261031002402`) require schema
and migration-history reconciliation before running the migrator. Merely
renaming the history entries is insufficient: those databases lack PUL-25's
composite foreign key and pending-context indexes. Back up local data and
compare the schema first; do not reset a database containing data to retain.
Fresh databases and databases on current master use the ordered chain above.

The persisted aggregate uses four tables:

- `connection_intents` stores the initiator, project, optional opportunity,
  reason, lifecycle state, and responder.
- `conversations` stores the private context and has a unique nullable
  `connection_intent_id`.
- `conversation_participants` stores the actual profiles in that context.
- `profile_blocks` stores directed blocks; contact policy evaluates both
  directions.

All four tables have RLS enabled and direct `anon` and `authenticated` grants
revoked. Product access is owned by the backend API.

## Acceptance transaction

`POST /api/v1/connection-intents/{connectionIntentId}/accept` derives the actor
from the validated JWT. The `AcceptConnectionIntent` application service opens
one transaction and the JDBC adapter locks the request with `FOR UPDATE`.

For a pending request, acceptance requires:

1. The responder has `ACTIVE` membership in the request's project. The project
   role may be `OWNER`, `ADMIN`, or `MEMBER`.
2. The responder is not the initiating profile.
3. Neither profile has blocked the other.

The membership row is locked while these conditions are evaluated. Acceptance
and writes to `profile_blocks` also take the same transaction-level advisory
lock for the unordered profile pair, so a concurrent block cannot pass between
the policy check and conversation creation.

The transaction then changes the request to `ACCEPTED`, records the responder
and response time, inserts one `PROJECT_CONTACT` conversation, and inserts the
initiator and responder as `INITIATOR` and `PROJECT_MEMBER`. Any failure rolls
back all of these writes.

## Duplicate and retry behavior

The database unique constraint on `conversations.connection_intent_id` is the
last defense against duplicate conversations. The locked request serializes
concurrent acceptance attempts before they reach that constraint.

A retry by the profile that already accepted returns the existing conversation
with its persisted status, even if that profile is no longer an active project
member, including after the intent advances to `CONVERTED`. An attempt by
another member after acceptance is a conflict and does not add that profile as
a participant. Other terminal request states are also conflicts.

Database triggers additionally require a `PROJECT_CONTACT` conversation to
reference its accepted intent and matching project and initiator. Deferred
constraint triggers require exactly the intent initiator and responder with
roles `INITIATOR` and `PROJECT_MEMBER` when the transaction commits.

## Error boundary

- `401` — invalid or missing authenticated subject.
- `403 connection_intent_forbidden` — active project membership is absent, or
  the profiles are blocked.
- `404` — the request does not exist.
- `409 connection_intent_conflict` — the lifecycle state or responder is
  incompatible with the operation.
- `503` — sanitized persistence failure.

Message history, notifications, read state, muting, conversation closure, and
confirmation of a successful real-world outcome remain separate capabilities.
