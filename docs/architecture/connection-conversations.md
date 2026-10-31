# Connection conversations

## Current scope

PUL-27 implements the backend boundary that turns an accepted contextual
project request into one private conversation. It does not yet expose request
creation, profile-block management, message sending, conversation lists, or an
Angular acceptance screen.

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

The transaction then changes the request to `ACCEPTED`, records the responder
and response time, inserts one `PROJECT_CONTACT` conversation, and inserts the
initiator and responder as `INITIATOR` and `PROJECT_MEMBER`. Any failure rolls
back all of these writes.

## Duplicate and retry behavior

The database unique constraint on `conversations.connection_intent_id` is the
last defense against duplicate conversations. The locked request serializes
concurrent acceptance attempts before they reach that constraint.

A retry by the profile that already accepted returns the existing active
conversation. An attempt by another member after acceptance is a conflict and
does not add that profile as a participant. Other terminal request states are
also conflicts.

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
