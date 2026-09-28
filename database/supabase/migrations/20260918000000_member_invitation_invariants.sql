begin;

-- Invitation rows stay on project_members. Persist a normalized invitee email,
-- keep profile_id empty while INVITED, and reject a second active invitation
-- for the same project and email. The accept token remains digest-only.
alter table public.project_members
  add constraint project_members_invitee_email_normalized
  check (
    invitee_email is null
    or (
      invitee_email = lower(trim(invitee_email))
      and char_length(invitee_email) between 3 and 254
    )
  );

alter table public.project_members
  add constraint project_members_invited_shape
  check (
    membership_status <> 'INVITED'
    or (
      profile_id is null
      and invitee_email is not null
      and invite_token_digest is not null
      and role = 'MEMBER'
    )
  );

create unique index project_members_active_invite_email_key
  on public.project_members (project_id, invitee_email)
  where membership_status = 'INVITED' and invitee_email is not null;

comment on column public.project_members.invitee_email is
  'Normalized email that identifies an invitation while profile_id is empty.';
comment on column public.project_members.invite_token_digest is
  'SHA-256 digest of the accept token. Never store or return the plaintext token.';
comment on column public.project_members.created_by_profile_id is
  'Profile that created the membership or invitation; required for every row.';

commit;
