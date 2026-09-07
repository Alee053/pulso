-- Local development fixtures. Keep this file idempotent so it can be rerun
-- after changing the seed without deleting data created by a developer.

insert into public.sectors (
  slug,
  name,
  description,
  sort_order
)
values
  (
    'sostenibilidad',
    'Sostenibilidad',
    'Soluciones que reducen residuos y mejoran el uso de recursos.',
    1
  ),
  (
    'energia-limpia',
    'Energía limpia',
    'Tecnologías y servicios para una transición energética responsable.',
    2
  )
on conflict (slug) do update set
  name = excluded.name,
  description = excluded.description,
  sort_order = excluded.sort_order,
  is_active = true,
  updated_at = timezone('utc', now());

insert into public.projects (
  id,
  slug,
  name,
  tagline,
  description,
  sector_id,
  city,
  stage,
  status,
  published_at,
  created_at,
  updated_at
)
values
  (
    '11111111-1111-1111-1111-111111111111',
    'eco-pack',
    'EcoPack Bolivia',
    'Envases biodegradables hechos con residuos agrícolas.',
    'EcoPack convierte fibras agrícolas en envases compostables para restaurantes y marcas locales.',
    (select id from public.sectors where slug = 'sostenibilidad'),
    'Cochabamba',
    'MVP',
    'PUBLISHED',
    timezone('utc', now()) - interval '2 minutes',
    timezone('utc', now()) - interval '2 minutes',
    timezone('utc', now()) - interval '2 minutes'
  ),
  (
    '22222222-2222-2222-2222-222222222222',
    'ande-solar',
    'AndeSolar',
    'Sistemas solares modulares para comunidades rurales.',
    'AndeSolar diseña kits de energía solar que crecen con cada comunidad.',
    (select id from public.sectors where slug = 'energia-limpia'),
    'La Paz',
    'MVP',
    'PUBLISHED',
    timezone('utc', now()) - interval '1 minute',
    timezone('utc', now()) - interval '1 minute',
    timezone('utc', now()) - interval '1 minute'
  )
on conflict (id) do update set
  slug = excluded.slug,
  name = excluded.name,
  tagline = excluded.tagline,
  description = excluded.description,
  sector_id = excluded.sector_id,
  city = excluded.city,
  stage = excluded.stage,
  status = excluded.status,
  published_at = excluded.published_at,
  updated_at = excluded.updated_at;
