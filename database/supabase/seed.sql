insert into public.startups (
  id,
  slug,
  name,
  tagline,
  description,
  sector,
  city,
  stage,
  status,
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
    'Sostenibilidad',
    'Cochabamba',
    'MVP',
    'PUBLISHED',
    timezone('utc', now()) - interval '2 minutes',
    timezone('utc', now()) - interval '2 minutes'
  ),
  (
    '22222222-2222-2222-2222-222222222222',
    'ande-solar',
    'AndeSolar',
    'Sistemas solares modulares para comunidades rurales.',
    'AndeSolar diseña kits de energía solar que crecen con cada comunidad.',
    'Energía limpia',
    'La Paz',
    'MVP',
    'PUBLISHED',
    timezone('utc', now()) - interval '1 minute',
    timezone('utc', now()) - interval '1 minute'
  )
on conflict (id) do update set
  slug = excluded.slug,
  name = excluded.name,
  tagline = excluded.tagline,
  description = excluded.description,
  sector = excluded.sector,
  city = excluded.city,
  stage = excluded.stage,
  status = excluded.status,
  updated_at = excluded.updated_at;
