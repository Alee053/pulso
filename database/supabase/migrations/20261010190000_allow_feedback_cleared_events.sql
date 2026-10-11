begin;

alter table public.interaction_events
  drop constraint interaction_events_event_type;

alter table public.interaction_events
  add constraint interaction_events_event_type check (
    event_type in (
      'PROJECT_VIEWED', 'PROJECT_OPENED', 'MEMBER_VIEWED', 'OPPORTUNITY_VIEWED',
      'SAVED', 'UNSAVED', 'INTERESTED', 'DISMISSED', 'FEEDBACK_CLEARED', 'SHARED',
      'CONTACT_STARTED', 'MESSAGE_STARTED'
    )
  );

commit;
