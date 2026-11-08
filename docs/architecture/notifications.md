# Notifications

## Current scope

S5.5 adds the essential notices that tell a person when to review a connection
request or a conversation: a new connection request, the answer to a request,
and a new message. A person can list their own notifications, separate the read
from the unread ones, and mark one as read.

Preferences, muting, email or push delivery, expiry policies, and grouping of
many messages into one notice are separate capabilities. The Angular inbox is
not part of this change.

## Persistence

`public.notifications` stores the recipient, the optional actor, the related
project, conversation and connection request, the type, a stable title, and the
read and creation times. Migration `20261108120000_create_notifications.sql`
enforces, independently of the API:

- the type is one of the known values. This change adds `CONNECTION_REQUEST` and
  `CONNECTION_RESPONSE` to the types already in the model;
- each type carries its context: `NEW_MESSAGE` needs a conversation,
  `CONNECTION_REQUEST` a connection request and a project, and
  `CONNECTION_RESPONSE` a connection request;
- a recipient receives at most one notice per connection request and type
  (partial unique index), so a retry cannot duplicate it;
- the recipient, type, context and creation time cannot change, and `read_at`
  keeps the first read time.

Like the other messaging tables, `notifications` has row level security enabled
and direct `anon` and `authenticated` grants revoked. Product access is owned by
the backend API.

`payload` holds identifiers only (`messageId`, `response`). The text of a message
is never copied into a notification, so deleting or editing a message cannot
leave a stale copy behind.

## Where notices are created

The use cases that produce the events call the `NotificationPublisher` port
inside their own transaction. The event and its notices are stored together, and
a failure in either rolls back both. The JDBC adapter computes recipients with
one SQL statement per event:

| Event | Created by | Recipients |
| --- | --- | --- |
| `CONNECTION_REQUEST` | `CreateConnectionIntent` | Active members of the project, except the sender and anyone separated from the sender by a block in either direction |
| `CONNECTION_RESPONSE` | `AcceptConnectionIntent` | The person who sent the request |
| `NEW_MESSAGE` | `SendTextMessage` | The other active participants of the conversation |

Retrying an acceptance that was already completed returns the existing
conversation without creating another notice. Only acceptance exists as a
response today, so `CONNECTION_RESPONSE` carries `response: ACCEPTED`; declining
can reuse the type with a different response when it exists.

## Listing and reading

`GET /api/v1/me/notifications?status=ALL&offset=0&limit=20` returns only the
authenticated person's notifications, most recent first (`created_at`, then `id`
as a tie-breaker). `status` is `ALL` (default), `UNREAD` or `READ`, which is how
the query separates the two groups. The response carries `meta.offset`,
`meta.limit`, `meta.hasMore` and `meta.unreadCount`, the number of unread
notices regardless of the filter, so a badge needs no second request. The limits
come from `pulso.notifications.*`.

`POST /api/v1/me/notifications/{notificationId}/read` marks one notification as
read and returns it. It is idempotent: marking it again keeps the first read
time. Expired notices (`expires_at` in the past) are left out of the list and the
counter; nothing sets an expiry yet.

## Access boundary

The recipient is always taken from the validated JWT; there is no recipient in
any path or body. Both the list and the update filter by that recipient in SQL,
so a person can neither read nor mark another person's notifications. Marking a
notification that belongs to someone else and marking one that does not exist
are answered with the same `404 notification_not_found`.

## Error boundary

- `400` — a `status` other than `ALL`, `UNREAD` or `READ`, an offset or limit
  outside the configured range, or a malformed notification id.
- `401` — invalid or missing authenticated subject.
- `404 notification_not_found` — the notification does not exist or belongs to
  someone else.
- `503` — sanitized persistence failure.

## Not covered

- No delete or archive endpoint, and no "mark all as read".
- One notice per message, with no grouping, so a long exchange produces many.
- The unread counter and the page are read in separate statements, so a notice
  created between them can make the counter one step ahead of the page.
