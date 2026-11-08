# Conversation messages

## Current scope

S5.4 adds the minimal internal messaging for a private conversation: the
participants of a conversation can send `TEXT` messages and read its history.
It builds on the `conversations` and `conversation_participants` tables created
when a project contact request is accepted (see
[connection conversations](./connection-conversations.md)).

The model already contemplates the `SENT`, `EDITED` and `DELETED` statuses, but
the API only creates `SENT` messages. Editing, deleting, replies, attachments,
video calls, read receipts, and notifications are separate capabilities.

## Persistence

`public.messages` stores the sender, the text, the type (`TEXT` or `SYSTEM`),
the status, and the creation, edit and deletion times. Migration
`20261107120000_create_conversation_messages.sql` enforces, independently of
the API:

- the sender must be an active participant (`left_at is null`) of the
  conversation, checked by a `BEFORE INSERT` trigger;
- a non-deleted message cannot have a blank body, and the stored text is capped
  at 5000 characters;
- the status and timestamps stay consistent: `SENT` has no edit or deletion
  time, `EDITED` has an edit time, `DELETED` has a deletion time;
- the conversation, sender, type and creation time of a message cannot change;
- `reply_to_message_id` can only point to a message of the same conversation.
  The column is persisted for the model but is not exposed by the API yet.

Like the other conversation tables, `messages` has row level security enabled
and direct `anon` and `authenticated` grants revoked. Product access is owned by
the backend API.

The index `(conversation_id, created_at, id)` serves the ordered history read.

## Sending a message

`POST /api/v1/conversations/{conversationId}/messages` derives the sender from
the validated JWT. The body accepts only `body`; `senderProfileId`,
`messageType`, `status` and timestamps are rejected with `400`.

`SendTextMessage` validates the text first (non-blank, trimmed, at most
`pulso.messages.body-max-length` characters, counted as Unicode code points) and
then opens one transaction in which the JDBC adapter:

1. locks the conversation row with `FOR UPDATE` and the sender's participant row
   with `FOR SHARE`, only when the sender is an active participant. This
   serializes sends and status changes of that conversation;
2. requires the conversation status to be `ACTIVE`;
3. takes the same advisory lock per profile pair that block writes use, and
   rejects the send when the sender and any other active participant are
   separated by a block in either direction;
4. inserts the `TEXT`/`SENT` message and advances
   `conversations.last_message_at` to the newest message time.

A failure in any step rolls back the whole send.

## Reading history

`GET /api/v1/conversations/{conversationId}/messages?offset=0&limit=20` returns
the messages from oldest to newest, ordered by `created_at` and then `id` as a
stable tie-breaker. Oldest-first with `offset`/`limit` keeps pages stable while
new messages are appended. The window follows the other collection endpoints:
the limits come from `pulso.messages.*` and the response carries
`meta.offset`, `meta.limit` and `meta.hasMore`.

Authorization and the page are read in a single SQL statement, so the check and
the contents share one PostgreSQL snapshot. A participant of an empty
conversation receives an empty page. A `DELETED` message is returned with its
status and `deletedAt` but never with its body: the adapter does not select it
and the `Message` domain type drops it.

Reading is allowed in any conversation status, so history stays available after
a conversation is archived, blocked or closed. It requires an active
participation: a participant who left can no longer read or send.

## Access boundary

Only an active participant can read or send. A user who is not a participant,
a participant who left, and a conversation that does not exist are all answered
with the same `404 conversation_not_found`, so a conversation id reveals nothing
about its existence. The backend never accepts a client-controlled participant
list or sender.

## Error boundary

- `400` — invalid or unknown body fields, blank or oversized `body`, or a page
  window outside the configured limits.
- `401` — invalid or missing authenticated subject.
- `403 messaging_blocked` — a profile block separates the sender from another
  active participant.
- `404 conversation_not_found` — the conversation does not exist or the caller
  is not an active participant.
- `409 conversation_not_active` — the conversation is `ARCHIVED`, `BLOCKED` or
  `CLOSED`.
- `503` — sanitized persistence failure.

## Configuration

`pulso.messages.*` defines the pagination defaults and limits (the same shape as
the other collections) and `body-max-length`, overridable with
`PULSO_MESSAGES_BODY_MAX_LENGTH`. The database cap of 5000 characters is a
safety net: keep `body-max-length` at or below it.

## Not covered

- No idempotency key: retrying a send after a lost response can create a second
  message. A client key can be added later without changing the contract.
- No edit or delete endpoints, although their statuses are persisted and read.
- No conversation list, unread counters, or push delivery. Notifications for new
  messages are the scope of the next issue.
