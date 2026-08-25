# Pulso agent guidance

## Domain and database references

When a task changes database structure, domain entities, onboarding,
discovery, opportunities, messaging, or their relationships:

1. Read [`database/pulso.dbml`](database/pulso.dbml) for the current logical
   MVP model and its explicit foreign-key relationships.
2. Read [`docs/diagrams/pulso-class-diagram.mmd`](docs/diagrams/pulso-class-diagram.mmd)
   for the conceptual class and relationship overview.
3. Read the relevant SQL files under
   [`database/supabase/migrations/`](database/supabase/migrations/) before
   changing implemented persistence.

The DBML is the design reference, the Mermaid file is a readability aid, and
the ordered Supabase migrations are the source of truth for the deployed
schema. Keep the three aligned when a schema change is intentional; state any
temporary difference explicitly.
