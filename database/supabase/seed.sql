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
  ),
  (
    'agroindustria',
    'Agroindustria',
    'Tecnologías y servicios para mejorar la producción y transformación de alimentos locales.',
    3
  ),
  (
    'educacion',
    'Educación',
    'Herramientas para ampliar el acceso y la calidad del aprendizaje.',
    4
  ),
  (
    'salud',
    'Salud',
    'Servicios y soluciones para el cuidado de la salud en comunidades bolivianas.',
    5
  ),
  (
    'tecnologia',
    'Tecnología',
    'Productos digitales e infraestructura para resolver problemas cotidianos.',
    6
  )
on conflict (slug) do update set
  name = excluded.name,
  description = excluded.description,
  sort_order = excluded.sort_order,
  is_active = true,
  updated_at = timezone('utc', now());

insert into public.tags (
  slug,
  name,
  tag_type
)
values
  (
    'innovacion',
    'Innovación',
    'TOPIC'
  ),
  (
    'impacto-social',
    'Impacto social',
    'TOPIC'
  ),
  (
    'economia-circular',
    'Economía circular',
    'TOPIC'
  ),
  (
    'inteligencia-artificial',
    'Inteligencia artificial',
    'TOPIC'
  ),
  (
    'comercio-electronico',
    'Comercio electrónico',
    'INDUSTRY'
  ),
  (
    'inclusion-financiera',
    'Inclusión financiera',
    'INDUSTRY'
  ),
  (
    'agricultura',
    'Agricultura',
    'INDUSTRY'
  ),
  (
    'talento-local',
    'Talento local',
    'OTHER'
  )
on conflict (slug) do update set
  name = excluded.name,
  tag_type = excluded.tag_type,
  is_active = true,
  updated_at = timezone('utc', now());

insert into public.skills (
  slug,
  name,
  category
)
values
  (
    'desarrollo-web',
    'Desarrollo web',
    'DESARROLLO'
  ),
  (
    'desarrollo-movil',
    'Desarrollo móvil',
    'DESARROLLO'
  ),
  (
    'analisis-de-datos',
    'Análisis de datos',
    'DATOS'
  ),
  (
    'marketing-digital',
    'Marketing digital',
    'NEGOCIOS'
  ),
  (
    'ventas',
    'Ventas',
    'NEGOCIOS'
  ),
  (
    'gestion-de-proyectos',
    'Gestión de proyectos',
    'GESTION'
  ),
  (
    'diseno-ux',
    'Diseño UX',
    'DISENO'
  ),
  (
    'agricultura-sostenible',
    'Agricultura sostenible',
    'AGRO'
  )
on conflict (slug) do update set
  name = excluded.name,
  category = excluded.category,
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

-- Quince proyectos ficticios adicionales para demostraciones locales.
-- UUIDs y slugs estables permiten repetir este seed sin duplicar proyectos.
-- Las fechas de los proyectos demo mantienen los dos proyectos originales al inicio del recorrido.
do $$
begin
  if exists (
    select 1 from (values
      ('30000000-0000-4000-8000-000000000001'::uuid, 'bio-carbono-andino'),
      ('30000000-0000-4000-8000-000000000002'::uuid, 'seca-sol-andino'),
      ('30000000-0000-4000-8000-000000000003'::uuid, 'qhatu'),
      ('30000000-0000-4000-8000-000000000004'::uuid, 'kawsay-foods'),
      ('30000000-0000-4000-8000-000000000005'::uuid, 'aquasense'),
      ('30000000-0000-4000-8000-000000000006'::uuid, 'ruta'),
      ('30000000-0000-4000-8000-000000000007'::uuid, 'revivo'),
      ('30000000-0000-4000-8000-000000000008'::uuid, 'tutoria'),
      ('30000000-0000-4000-8000-000000000009'::uuid, 'muyu-salud'),
      ('30000000-0000-4000-8000-000000000010'::uuid, 'inti-agua'),
      ('30000000-0000-4000-8000-000000000011'::uuid, 'warmi-textil'),
      ('30000000-0000-4000-8000-000000000012'::uuid, 'altura-cacao'),
      ('30000000-0000-4000-8000-000000000013'::uuid, 'suma-logistica'),
      ('30000000-0000-4000-8000-000000000014'::uuid, 'yachay-lab'),
      ('30000000-0000-4000-8000-000000000015'::uuid, 'bosque-vivo')
    ) as demo(id, slug)
    join public.projects project on project.id = demo.id and project.slug <> demo.slug
  ) or exists (
    select 1 from (values
      ('30000000-0000-4000-8000-000000000001'::uuid, 'bio-carbono-andino'),
      ('30000000-0000-4000-8000-000000000002'::uuid, 'seca-sol-andino'),
      ('30000000-0000-4000-8000-000000000003'::uuid, 'qhatu'),
      ('30000000-0000-4000-8000-000000000004'::uuid, 'kawsay-foods'),
      ('30000000-0000-4000-8000-000000000005'::uuid, 'aquasense'),
      ('30000000-0000-4000-8000-000000000006'::uuid, 'ruta'),
      ('30000000-0000-4000-8000-000000000007'::uuid, 'revivo'),
      ('30000000-0000-4000-8000-000000000008'::uuid, 'tutoria'),
      ('30000000-0000-4000-8000-000000000009'::uuid, 'muyu-salud'),
      ('30000000-0000-4000-8000-000000000010'::uuid, 'inti-agua'),
      ('30000000-0000-4000-8000-000000000011'::uuid, 'warmi-textil'),
      ('30000000-0000-4000-8000-000000000012'::uuid, 'altura-cacao'),
      ('30000000-0000-4000-8000-000000000013'::uuid, 'suma-logistica'),
      ('30000000-0000-4000-8000-000000000014'::uuid, 'yachay-lab'),
      ('30000000-0000-4000-8000-000000000015'::uuid, 'bosque-vivo')
    ) as demo(id, slug)
    join public.projects project on project.slug = demo.slug and project.id <> demo.id
  ) then
    raise exception 'demo project fixture UUID or slug collides with an existing project';
  end if;
end
$$;

insert into public.projects (
  id, slug, name, tagline, short_description, description, problem, solution,
  value_proposition, product_or_service, sector_id, city, department, stage,
  status, published_at, created_at, updated_at, media, external_links
)
values
  ('30000000-0000-4000-8000-000000000001'::uuid,
   'bio-carbono-andino',
   'BioCarbono Andino',
   'Biochar y mezclas de suelo a partir de residuos agrícolas.',
   'Biochar y mezclas de suelo a partir de residuos agrícolas.',
   'BioCarbono Andino aprovecha podas y residuos agrícolas para producir biochar y mezclas de suelo. Coordina la recolección con asociaciones productoras, registra cada lote y ofrece guías de aplicación para huertos y viveros.

El piloto contempla un horno de pequeña escala, fichas de lote y parcelas de prueba para comparar mezclas. El equipo busca productores y especialistas en suelos para evaluar rendimiento y operación.

Proyecto ficticio de demostración de Pulso.',
   'En el sector sostenibilidad, las personas y organizaciones necesitan alternativas claras, cercanas y fáciles de probar.',
   'BioCarbono Andino aprovecha podas y residuos agrícolas para producir biochar y mezclas de suelo. Coordina la recolección con asociaciones productoras, registra cada lote y ofrece guías de aplicación para huertos y viveros.',
   'Convertir residuos agrícolas en insumos de suelo trazables y útiles para productores locales.',
   'Biochar y mezclas de suelo a partir de residuos agrícolas.',
   (select id from public.sectors where slug = 'sostenibilidad'),
   'Cochabamba',
   'Cochabamba',
   'MVP',
   'PUBLISHED',
   (timestamp with time zone '2026-08-19 12:00:00+00' - interval '1 seconds'),
   (timestamp with time zone '2026-08-19 12:00:00+00' - interval '1 seconds'),
   (timestamp with time zone '2026-08-19 12:00:00+00' - interval '1 seconds'),
   '[{"type":"COVER","url":"https://images.unsplash.com/photo-1542601906990-b4d3fb778b09?auto=format&fit=crop&w=1200&h=800&q=80","alt":"Imagen ilustrativa del proyecto BioCarbono Andino","source":"Unsplash"}]'::jsonb,
   '[]'::jsonb),
  ('30000000-0000-4000-8000-000000000002'::uuid,
   'seca-sol-andino',
   'SecaSol Andino',
   'Secadores solares modulares para cosechas y alimentos locales.',
   'Secadores solares modulares para cosechas y alimentos locales.',
   'SecaSol Andino diseña secadores solares modulares para conservar frutas, hierbas y cacao con control de temperatura y circulación de aire. Las cooperativas pueden ajustar la capacidad según su cosecha y consultar guías de operación y limpieza.

La demostración incluye módulos para fruta, hierbas y granos, además de un registro de ciclos de secado. El equipo busca asociaciones productoras y especialistas en alimentos para validar calidad y mantenimiento.

Proyecto ficticio de demostración de Pulso.',
   'En el sector energia-limpia, las personas y organizaciones necesitan alternativas claras, cercanas y fáciles de probar.',
   'SecaSol Andino diseña secadores solares modulares para conservar frutas, hierbas y cacao con control de temperatura y circulación de aire. Las cooperativas pueden ajustar la capacidad según su cosecha y consultar guías de operación y limpieza.',
   'Reducir pérdidas poscosecha con equipos reparables que aprovechan energía solar y conocimiento local.',
   'Secadores solares modulares para cosechas y alimentos locales.',
   (select id from public.sectors where slug = 'energia-limpia'),
   'El Alto',
   'La Paz',
   'MVP',
   'PUBLISHED',
   (timestamp with time zone '2026-08-19 12:00:00+00' - interval '2 seconds'),
   (timestamp with time zone '2026-08-19 12:00:00+00' - interval '2 seconds'),
   (timestamp with time zone '2026-08-19 12:00:00+00' - interval '2 seconds'),
   '[{"type":"COVER","url":"https://images.unsplash.com/photo-1509391366360-2e959784a276?auto=format&fit=crop&w=1200&h=800&q=80","alt":"Imagen ilustrativa del proyecto SecaSol Andino","source":"Unsplash"}]'::jsonb,
   '[]'::jsonb),
  ('30000000-0000-4000-8000-000000000003'::uuid,
   'qhatu',
   'Qhatu',
   'El catálogo digital de los comercios de tu barrio.',
   'El catálogo digital de los comercios de tu barrio.',
   'Qhatu reúne productos, horarios y opciones de entrega de pequeños comercios en un catálogo fácil de actualizar desde el teléfono. Las personas pueden comparar alternativas cercanas y organizar su pedido sin recorrer varios canales.

El escenario de demostración incluye comercios de alimentos, artículos de hogar y regalos. El equipo busca emprendedores que quieran probar la publicación de productos y especialistas en experiencia de usuario para simplificar el primer registro.

Proyecto ficticio de demostración de Pulso.',
   'En el sector tecnologia, las personas y organizaciones necesitan alternativas claras, cercanas y fáciles de probar.',
   'Qhatu reúne productos, horarios y opciones de entrega de pequeños comercios en un catálogo fácil de actualizar desde el teléfono. Las personas pueden comparar alternativas cercanas y organizar su pedido sin recorrer varios canales.',
   'Dar visibilidad digital a negocios de barrio sin exigir una tienda en línea compleja ni conocimientos técnicos.',
   'El catálogo digital de los comercios de tu barrio.',
   (select id from public.sectors where slug = 'tecnologia'),
   'La Paz',
   'La Paz',
   'EARLY_STAGE',
   'PUBLISHED',
   (timestamp with time zone '2026-08-19 12:00:00+00' - interval '3 seconds'),
   (timestamp with time zone '2026-08-19 12:00:00+00' - interval '3 seconds'),
   (timestamp with time zone '2026-08-19 12:00:00+00' - interval '3 seconds'),
   '[{"type":"COVER","url":"https://images.unsplash.com/photo-1556742049-0cfed4f6a45d?auto=format&fit=crop&w=1200&h=800&q=80","alt":"Imagen ilustrativa del proyecto Qhatu","source":"Unsplash"}]'::jsonb,
   '[]'::jsonb),
  ('30000000-0000-4000-8000-000000000004'::uuid,
   'kawsay-foods',
   'Kawsay Foods',
   'Alimentos prácticos elaborados con granos andinos.',
   'Alimentos prácticos elaborados con granos andinos.',
   'Desarrollamos mezclas listas para cocinar con quinua, amaranto y otros ingredientes de productores locales. Cada presentación explica su origen, preparación y conservación para facilitar su uso en hogares y cafeterías.

La línea de demostración reúne desayunos, sopas y mezclas para hornear. Buscamos puntos de distribución y profesionales de desarrollo de alimentos que acompañen nuevas pruebas de sabor, empaque y logística.

Proyecto ficticio de demostración de Pulso.',
   'En el sector agroindustria, las personas y organizaciones necesitan alternativas claras, cercanas y fáciles de probar.',
   'Desarrollamos mezclas listas para cocinar con quinua, amaranto y otros ingredientes de productores locales. Cada presentación explica su origen, preparación y conservación para facilitar su uso en hogares y cafeterías.',
   'Acercar ingredientes andinos a la alimentación cotidiana y construir compras planificadas con asociaciones productoras.',
   'Alimentos prácticos elaborados con granos andinos.',
   (select id from public.sectors where slug = 'agroindustria'),
   'Sucre',
   'Chuquisaca',
   'GROWING',
   'PUBLISHED',
   (timestamp with time zone '2026-08-19 12:00:00+00' - interval '4 seconds'),
   (timestamp with time zone '2026-08-19 12:00:00+00' - interval '4 seconds'),
   (timestamp with time zone '2026-08-19 12:00:00+00' - interval '4 seconds'),
   '[{"type":"COVER","url":"https://images.unsplash.com/photo-1542838132-92c53300491e?auto=format&fit=crop&w=1200&h=800&q=80","alt":"Imagen ilustrativa del proyecto Kawsay Foods","source":"Unsplash"}]'::jsonb,
   '[]'::jsonb),
  ('30000000-0000-4000-8000-000000000005'::uuid,
   'aquasense',
   'AquaSense',
   'Riego guiado por datos sencillos para pequeños productores.',
   'Riego guiado por datos sencillos para pequeños productores.',
   'Combinamos sensores de humedad con recomendaciones de riego que se pueden consultar desde un teléfono. El prototipo guarda lecturas aun cuando la conexión es intermitente y muestra el estado de cada parcela con indicadores fáciles de interpretar.

La demostración representa tres cultivos con necesidades distintas y alertas de mantenimiento del sensor. Buscamos productores para validar la instalación y personas con experiencia en electrónica y análisis de datos.

Proyecto ficticio de demostración de Pulso.',
   'En el sector agroindustria, las personas y organizaciones necesitan alternativas claras, cercanas y fáciles de probar.',
   'Combinamos sensores de humedad con recomendaciones de riego que se pueden consultar desde un teléfono. El prototipo guarda lecturas aun cuando la conexión es intermitente y muestra el estado de cada parcela con indicadores fáciles de interpretar.',
   'Ayudar a decidir cuándo regar con información de la propia parcela, evitando calendarios rígidos y recorridos innecesarios.',
   'Riego guiado por datos sencillos para pequeños productores.',
   (select id from public.sectors where slug = 'agroindustria'),
   'Tarija',
   'Tarija',
   'PROTOTYPE',
   'PUBLISHED',
   (timestamp with time zone '2026-08-19 12:00:00+00' - interval '5 seconds'),
   (timestamp with time zone '2026-08-19 12:00:00+00' - interval '5 seconds'),
   (timestamp with time zone '2026-08-19 12:00:00+00' - interval '5 seconds'),
   '[{"type":"COVER","url":"https://images.unsplash.com/photo-1464226184884-fa280b87c399?auto=format&fit=crop&w=1200&h=800&q=80","alt":"Imagen ilustrativa del proyecto AquaSense","source":"Unsplash"}]'::jsonb,
   '[]'::jsonb),
  ('30000000-0000-4000-8000-000000000006'::uuid,
   'ruta',
   'Ruta',
   'Información clara para combinar rutas de transporte urbano.',
   'Información clara para combinar rutas de transporte urbano.',
   'Ruta organiza recorridos, paradas y referencias para que las personas puedan planificar sus desplazamientos cotidianos. El diseño prioriza instrucciones breves, puntos de transbordo y alternativas para caminar el último tramo.

El piloto de demostración recorre conexiones entre mercados, universidades y centros de trabajo. Buscamos colaboradores para revisar recorridos y diseñadores que ayuden a hacer la información más accesible.

Proyecto ficticio de demostración de Pulso.',
   'En el sector tecnologia, las personas y organizaciones necesitan alternativas claras, cercanas y fáciles de probar.',
   'Ruta organiza recorridos, paradas y referencias para que las personas puedan planificar sus desplazamientos cotidianos. El diseño prioriza instrucciones breves, puntos de transbordo y alternativas para caminar el último tramo.',
   'Reducir la incertidumbre al usar transporte público mediante rutas comprensibles y datos revisados por la comunidad.',
   'Información clara para combinar rutas de transporte urbano.',
   (select id from public.sectors where slug = 'tecnologia'),
   'Santa Cruz de la Sierra',
   'Santa Cruz',
   'MVP',
   'PUBLISHED',
   (timestamp with time zone '2026-08-19 12:00:00+00' - interval '6 seconds'),
   (timestamp with time zone '2026-08-19 12:00:00+00' - interval '6 seconds'),
   (timestamp with time zone '2026-08-19 12:00:00+00' - interval '6 seconds'),
   '[{"type":"COVER","url":"https://images.unsplash.com/photo-1544620347-c4fd4a3d5957?auto=format&fit=crop&w=1200&h=800&q=80","alt":"Imagen ilustrativa del proyecto Ruta","source":"Unsplash"}]'::jsonb,
   '[]'::jsonb),
  ('30000000-0000-4000-8000-000000000007'::uuid,
   'revivo',
   'ReVivo',
   'Una segunda vida para muebles y materiales de oficina.',
   'Una segunda vida para muebles y materiales de oficina.',
   'Recuperamos muebles, clasificamos sus materiales y coordinamos su reparación con talleres locales. Cada pieza tiene una ficha de estado para que el comprador conozca las intervenciones realizadas y las recomendaciones de cuidado.

La colección de demostración incluye escritorios, estanterías y mesas recuperadas. Buscamos organizaciones que renueven sus espacios y talleres interesados en reparar, restaurar y diseñar piezas a partir de materiales disponibles.

Proyecto ficticio de demostración de Pulso.',
   'En el sector sostenibilidad, las personas y organizaciones necesitan alternativas claras, cercanas y fáciles de probar.',
   'Recuperamos muebles, clasificamos sus materiales y coordinamos su reparación con talleres locales. Cada pieza tiene una ficha de estado para que el comprador conozca las intervenciones realizadas y las recomendaciones de cuidado.',
   'Extender la vida útil del mobiliario y conectar inventarios en desuso con oficios y compradores locales.',
   'Una segunda vida para muebles y materiales de oficina.',
   (select id from public.sectors where slug = 'sostenibilidad'),
   'Oruro',
   'Oruro',
   'EARLY_STAGE',
   'PUBLISHED',
   (timestamp with time zone '2026-08-19 12:00:00+00' - interval '7 seconds'),
   (timestamp with time zone '2026-08-19 12:00:00+00' - interval '7 seconds'),
   (timestamp with time zone '2026-08-19 12:00:00+00' - interval '7 seconds'),
   '[{"type":"COVER","url":"https://images.unsplash.com/photo-1538688423619-a81d3f23454b?auto=format&fit=crop&w=1200&h=800&q=80","alt":"Imagen ilustrativa del proyecto ReVivo","source":"Unsplash"}]'::jsonb,
   '[]'::jsonb),
  ('30000000-0000-4000-8000-000000000008'::uuid,
   'tutoria',
   'TutorIA',
   'Práctica guiada para aprender al ritmo de cada estudiante.',
   'Práctica guiada para aprender al ritmo de cada estudiante.',
   'Creamos secuencias de ejercicios con pistas graduales y un panel para que docentes identifiquen temas que requieren refuerzo. Las actividades combinan ejemplos cotidianos, explicaciones cortas y revisión de respuestas.

El prototipo de demostración se centra en razonamiento matemático y comprensión lectora. Buscamos docentes para revisar contenidos y especialistas en aprendizaje digital para evaluar la claridad de las explicaciones.

Proyecto ficticio de demostración de Pulso.',
   'En el sector educacion, las personas y organizaciones necesitan alternativas claras, cercanas y fáciles de probar.',
   'Creamos secuencias de ejercicios con pistas graduales y un panel para que docentes identifiquen temas que requieren refuerzo. Las actividades combinan ejemplos cotidianos, explicaciones cortas y revisión de respuestas.',
   'Ofrecer acompañamiento de práctica entre clases, manteniendo a docentes y estudiantes al centro de las decisiones educativas.',
   'Práctica guiada para aprender al ritmo de cada estudiante.',
   (select id from public.sectors where slug = 'educacion'),
   'Cochabamba',
   'Cochabamba',
   'PROTOTYPE',
   'PUBLISHED',
   (timestamp with time zone '2026-08-19 12:00:00+00' - interval '8 seconds'),
   (timestamp with time zone '2026-08-19 12:00:00+00' - interval '8 seconds'),
   (timestamp with time zone '2026-08-19 12:00:00+00' - interval '8 seconds'),
   '[{"type":"COVER","url":"https://images.unsplash.com/photo-1523240795612-9a054b0db644?auto=format&fit=crop&w=1200&h=800&q=80","alt":"Imagen ilustrativa del proyecto TutorIA","source":"Unsplash"}]'::jsonb,
   '[]'::jsonb),
  ('30000000-0000-4000-8000-000000000009'::uuid,
   'muyu-salud',
   'Muyu Salud',
   'Agenda y seguimiento para equipos de salud comunitaria.',
   'Agenda y seguimiento para equipos de salud comunitaria.',
   'Muyu Salud ayuda a organizar turnos, recordatorios y visitas de equipos comunitarios. El proyecto se concentra en coordinación administrativa y continuidad de la atención, sin reemplazar la evaluación de profesionales de salud.

La demostración utiliza exclusivamente casos ficticios para mostrar agendas, listas de seguimiento y recordatorios. Buscamos centros comunitarios y especialistas en accesibilidad para evaluar el recorrido de pacientes y personal administrativo.

Proyecto ficticio de demostración de Pulso.',
   'En el sector salud, las personas y organizaciones necesitan alternativas claras, cercanas y fáciles de probar.',
   'Muyu Salud ayuda a organizar turnos, recordatorios y visitas de equipos comunitarios. El proyecto se concentra en coordinación administrativa y continuidad de la atención, sin reemplazar la evaluación de profesionales de salud.',
   'Facilitar la coordinación de consultas y seguimientos con herramientas simples que se adapten al trabajo comunitario.',
   'Agenda y seguimiento para equipos de salud comunitaria.',
   (select id from public.sectors where slug = 'salud'),
   'Trinidad',
   'Beni',
   'MVP',
   'PUBLISHED',
   (timestamp with time zone '2026-08-19 12:00:00+00' - interval '9 seconds'),
   (timestamp with time zone '2026-08-19 12:00:00+00' - interval '9 seconds'),
   (timestamp with time zone '2026-08-19 12:00:00+00' - interval '9 seconds'),
   '[{"type":"COVER","url":"https://images.unsplash.com/photo-1576091160399-112ba8d25d1d?auto=format&fit=crop&w=1200&h=800&q=80","alt":"Imagen ilustrativa del proyecto Muyu Salud","source":"Unsplash"}]'::jsonb,
   '[]'::jsonb),
  ('30000000-0000-4000-8000-000000000010'::uuid,
   'inti-agua',
   'Inti Agua',
   'Bombeo solar para sistemas comunitarios de agua.',
   'Bombeo solar para sistemas comunitarios de agua.',
   'Integramos bombeo solar, almacenamiento y monitoreo de nivel para pequeñas redes comunitarias. El prototipo permite comparar horarios de operación y planificar tareas de mantenimiento con un registro compartido.

La demostración muestra el abastecimiento de un tanque y avisos de inspección técnica. Buscamos organizaciones de agua y técnicos electromecánicos para revisar el diseño de instalación y las condiciones de operación.

Proyecto ficticio de demostración de Pulso.',
   'En el sector energia-limpia, las personas y organizaciones necesitan alternativas claras, cercanas y fáciles de probar.',
   'Integramos bombeo solar, almacenamiento y monitoreo de nivel para pequeñas redes comunitarias. El prototipo permite comparar horarios de operación y planificar tareas de mantenimiento con un registro compartido.',
   'Aprovechar energía solar para mover agua con equipos mantenibles y una operación coordinada por la comunidad.',
   'Bombeo solar para sistemas comunitarios de agua.',
   (select id from public.sectors where slug = 'energia-limpia'),
   'Villamontes',
   'Tarija',
   'PROTOTYPE',
   'PUBLISHED',
   (timestamp with time zone '2026-08-19 12:00:00+00' - interval '10 seconds'),
   (timestamp with time zone '2026-08-19 12:00:00+00' - interval '10 seconds'),
   (timestamp with time zone '2026-08-19 12:00:00+00' - interval '10 seconds'),
   '[{"type":"COVER","url":"https://images.unsplash.com/photo-1437482078695-73f5ca6c96e2?auto=format&fit=crop&w=1200&h=800&q=80","alt":"Imagen ilustrativa del proyecto Inti Agua","source":"Unsplash"}]'::jsonb,
   '[]'::jsonb),
  ('30000000-0000-4000-8000-000000000011'::uuid,
   'warmi-textil',
   'Warmi Textil',
   'Diseño textil y pedidos colaborativos con talleres locales.',
   'Diseño textil y pedidos colaborativos con talleres locales.',
   'Warmi Textil conecta talleres con marcas que necesitan series cortas de prendas y accesorios. Cada pedido define materiales, medidas y tiempos de entrega para hacer más clara la coordinación entre diseño y confección.

El catálogo de demostración reúne tejidos, bolsos y prendas de trabajo con fichas de producción. Buscamos talleres asociados, diseñadores y comercios para validar la solicitud de muestras y el seguimiento de pedidos.

Proyecto ficticio de demostración de Pulso.',
   'En el sector tecnologia, las personas y organizaciones necesitan alternativas claras, cercanas y fáciles de probar.',
   'Warmi Textil conecta talleres con marcas que necesitan series cortas de prendas y accesorios. Cada pedido define materiales, medidas y tiempos de entrega para hacer más clara la coordinación entre diseño y confección.',
   'Hacer visible la capacidad de talleres locales y facilitar pedidos planificados con especificaciones compartidas.',
   'Diseño textil y pedidos colaborativos con talleres locales.',
   (select id from public.sectors where slug = 'tecnologia'),
   'El Alto',
   'La Paz',
   'EARLY_STAGE',
   'PUBLISHED',
   (timestamp with time zone '2026-08-19 12:00:00+00' - interval '11 seconds'),
   (timestamp with time zone '2026-08-19 12:00:00+00' - interval '11 seconds'),
   (timestamp with time zone '2026-08-19 12:00:00+00' - interval '11 seconds'),
   '[{"type":"COVER","url":"https://images.unsplash.com/photo-1558618666-fcd25c85cd64?auto=format&fit=crop&w=1200&h=800&q=80","alt":"Imagen ilustrativa del proyecto Warmi Textil","source":"Unsplash"}]'::jsonb,
   '[]'::jsonb),
  ('30000000-0000-4000-8000-000000000012'::uuid,
   'altura-cacao',
   'Altura Cacao',
   'Cacao de origen con trazabilidad desde la cosecha.',
   'Cacao de origen con trazabilidad desde la cosecha.',
   'Trabajamos con lotes de cacao identificados por comunidad, proceso de fermentación y perfil de sabor. El proyecto vincula a productores con chocolaterías que buscan compras recurrentes e información clara sobre el origen.

La demostración presenta fichas de tres lotes y propuestas de degustación para compradores. Buscamos aliados en transformación de alimentos, empaque y comercialización para ampliar las pruebas de producto.

Proyecto ficticio de demostración de Pulso.',
   'En el sector agroindustria, las personas y organizaciones necesitan alternativas claras, cercanas y fáciles de probar.',
   'Trabajamos con lotes de cacao identificados por comunidad, proceso de fermentación y perfil de sabor. El proyecto vincula a productores con chocolaterías que buscan compras recurrentes e información clara sobre el origen.',
   'Conectar el valor del cacao de origen con compradores que puedan reconocer su proceso y planificar compras a largo plazo.',
   'Cacao de origen con trazabilidad desde la cosecha.',
   (select id from public.sectors where slug = 'agroindustria'),
   'Riberalta',
   'Beni',
   'GROWING',
   'PUBLISHED',
   (timestamp with time zone '2026-08-19 12:00:00+00' - interval '12 seconds'),
   (timestamp with time zone '2026-08-19 12:00:00+00' - interval '12 seconds'),
   (timestamp with time zone '2026-08-19 12:00:00+00' - interval '12 seconds'),
   '[{"type":"COVER","url":"https://images.unsplash.com/photo-1511381939415-e44015466834?auto=format&fit=crop&w=1200&h=800&q=80","alt":"Imagen ilustrativa del proyecto Altura Cacao","source":"Unsplash"}]'::jsonb,
   '[]'::jsonb),
  ('30000000-0000-4000-8000-000000000013'::uuid,
   'suma-logistica',
   'Suma Logística',
   'Entregas agrupadas para pequeños comercios.',
   'Entregas agrupadas para pequeños comercios.',
   'Agrupamos pedidos cercanos para que los comercios coordinen recolecciones y entregas en ventanas horarias claras. El tablero muestra el estado de cada envío y permite informar cambios sin depender de conversaciones dispersas.

La demostración simula pedidos de tres barrios y una ruta compartida entre varios comercios. Buscamos negocios y operadores de reparto para revisar el flujo de confirmación, entrega y resolución de incidencias.

Proyecto ficticio de demostración de Pulso.',
   'En el sector tecnologia, las personas y organizaciones necesitan alternativas claras, cercanas y fáciles de probar.',
   'Agrupamos pedidos cercanos para que los comercios coordinen recolecciones y entregas en ventanas horarias claras. El tablero muestra el estado de cada envío y permite informar cambios sin depender de conversaciones dispersas.',
   'Simplificar la logística de pequeños negocios con pedidos organizados, entregas agrupadas y seguimiento comprensible.',
   'Entregas agrupadas para pequeños comercios.',
   (select id from public.sectors where slug = 'tecnologia'),
   'Santa Cruz de la Sierra',
   'Santa Cruz',
   'MVP',
   'PUBLISHED',
   (timestamp with time zone '2026-08-19 12:00:00+00' - interval '13 seconds'),
   (timestamp with time zone '2026-08-19 12:00:00+00' - interval '13 seconds'),
   (timestamp with time zone '2026-08-19 12:00:00+00' - interval '13 seconds'),
   '[{"type":"COVER","url":"https://images.unsplash.com/photo-1586528116311-ad8dd3c8310d?auto=format&fit=crop&w=1200&h=800&q=80","alt":"Imagen ilustrativa del proyecto Suma Logística","source":"Unsplash"}]'::jsonb,
   '[]'::jsonb),
  ('30000000-0000-4000-8000-000000000014'::uuid,
   'yachay-lab',
   'Yachay Lab',
   'Ciencia y tecnología mediante talleres prácticos itinerantes.',
   'Ciencia y tecnología mediante talleres prácticos itinerantes.',
   'Diseñamos experiencias de aprendizaje con materiales reutilizables para explorar circuitos, mediciones y pensamiento computacional. Cada taller propone un reto que se resuelve en equipo y deja una guía para continuar practicando.

La propuesta de demostración incluye una estación meteorológica, un circuito luminoso y un reto de clasificación de materiales. Buscamos docentes, voluntarios y espacios comunitarios para revisar la primera secuencia de talleres.

Proyecto ficticio de demostración de Pulso.',
   'En el sector educacion, las personas y organizaciones necesitan alternativas claras, cercanas y fáciles de probar.',
   'Diseñamos experiencias de aprendizaje con materiales reutilizables para explorar circuitos, mediciones y pensamiento computacional. Cada taller propone un reto que se resuelve en equipo y deja una guía para continuar practicando.',
   'Acercar experiencias de ciencia a comunidades educativas mediante actividades portátiles, colaborativas y fáciles de repetir.',
   'Ciencia y tecnología mediante talleres prácticos itinerantes.',
   (select id from public.sectors where slug = 'educacion'),
   'Potosí',
   'Potosí',
   'IDEA',
   'PUBLISHED',
   (timestamp with time zone '2026-08-19 12:00:00+00' - interval '14 seconds'),
   (timestamp with time zone '2026-08-19 12:00:00+00' - interval '14 seconds'),
   (timestamp with time zone '2026-08-19 12:00:00+00' - interval '14 seconds'),
   '[{"type":"COVER","url":"https://images.unsplash.com/photo-1581092921461-eab62e97a780?auto=format&fit=crop&w=1200&h=800&q=80","alt":"Imagen ilustrativa del proyecto Yachay Lab","source":"Unsplash"}]'::jsonb,
   '[]'::jsonb),
  ('30000000-0000-4000-8000-000000000015'::uuid,
   'bosque-vivo',
   'Bosque Vivo',
   'Seguimiento colaborativo para restauración de espacios verdes.',
   'Seguimiento colaborativo para restauración de espacios verdes.',
   'Organizamos jornadas de restauración con fichas de especies, mapas de plantación y registros de seguimiento. Las organizaciones pueden coordinar responsables y documentar el cuidado posterior a cada actividad.

El prototipo de demostración reúne tres áreas de intervención y un calendario de visitas. Buscamos viveros, organizaciones vecinales y especialistas forestales para revisar los registros y el acompañamiento de voluntarios.

Proyecto ficticio de demostración de Pulso.',
   'En el sector sostenibilidad, las personas y organizaciones necesitan alternativas claras, cercanas y fáciles de probar.',
   'Organizamos jornadas de restauración con fichas de especies, mapas de plantación y registros de seguimiento. Las organizaciones pueden coordinar responsables y documentar el cuidado posterior a cada actividad.',
   'Dar continuidad al cuidado de espacios restaurados con información compartida y responsabilidades visibles después de cada jornada.',
   'Seguimiento colaborativo para restauración de espacios verdes.',
   (select id from public.sectors where slug = 'sostenibilidad'),
   'Cobija',
   'Pando',
   'PROTOTYPE',
   'PUBLISHED',
   (timestamp with time zone '2026-08-19 12:00:00+00' - interval '15 seconds'),
   (timestamp with time zone '2026-08-19 12:00:00+00' - interval '15 seconds'),
   (timestamp with time zone '2026-08-19 12:00:00+00' - interval '15 seconds'),
   '[{"type":"COVER","url":"https://images.unsplash.com/photo-1441974231531-c6227db76b6e?auto=format&fit=crop&w=1200&h=800&q=80","alt":"Imagen ilustrativa del proyecto Bosque Vivo","source":"Unsplash"}]'::jsonb,
   '[]'::jsonb)
on conflict (id) do update set
  name = excluded.name,
  tagline = excluded.tagline,
  short_description = excluded.short_description,
  description = excluded.description,
  problem = excluded.problem,
  solution = excluded.solution,
  value_proposition = excluded.value_proposition,
  product_or_service = excluded.product_or_service,
  sector_id = excluded.sector_id,
  city = excluded.city,
  department = excluded.department,
  stage = excluded.stage,
  status = excluded.status,
  published_at = excluded.published_at,
  media = excluded.media,
  external_links = excluded.external_links,
  updated_at = excluded.updated_at;

insert into public.project_tags (project_id, tag_id)
values
('30000000-0000-4000-8000-000000000001'::uuid, (select id from public.tags where slug = 'economia-circular')),
('30000000-0000-4000-8000-000000000001'::uuid, (select id from public.tags where slug = 'agricultura')),
('30000000-0000-4000-8000-000000000001'::uuid, (select id from public.tags where slug = 'innovacion')),
('30000000-0000-4000-8000-000000000002'::uuid, (select id from public.tags where slug = 'agricultura')),
('30000000-0000-4000-8000-000000000002'::uuid, (select id from public.tags where slug = 'talento-local')),
('30000000-0000-4000-8000-000000000002'::uuid, (select id from public.tags where slug = 'innovacion')),
('30000000-0000-4000-8000-000000000003'::uuid, (select id from public.tags where slug = 'comercio-electronico')),
('30000000-0000-4000-8000-000000000003'::uuid, (select id from public.tags where slug = 'inclusion-financiera')),
('30000000-0000-4000-8000-000000000003'::uuid, (select id from public.tags where slug = 'talento-local')),
('30000000-0000-4000-8000-000000000004'::uuid, (select id from public.tags where slug = 'agricultura')),
('30000000-0000-4000-8000-000000000004'::uuid, (select id from public.tags where slug = 'talento-local')),
('30000000-0000-4000-8000-000000000004'::uuid, (select id from public.tags where slug = 'innovacion')),
('30000000-0000-4000-8000-000000000005'::uuid, (select id from public.tags where slug = 'agricultura')),
('30000000-0000-4000-8000-000000000005'::uuid, (select id from public.tags where slug = 'innovacion')),
('30000000-0000-4000-8000-000000000005'::uuid, (select id from public.tags where slug = 'inteligencia-artificial')),
('30000000-0000-4000-8000-000000000006'::uuid, (select id from public.tags where slug = 'impacto-social')),
('30000000-0000-4000-8000-000000000006'::uuid, (select id from public.tags where slug = 'innovacion')),
('30000000-0000-4000-8000-000000000006'::uuid, (select id from public.tags where slug = 'talento-local')),
('30000000-0000-4000-8000-000000000007'::uuid, (select id from public.tags where slug = 'economia-circular')),
('30000000-0000-4000-8000-000000000007'::uuid, (select id from public.tags where slug = 'comercio-electronico')),
('30000000-0000-4000-8000-000000000007'::uuid, (select id from public.tags where slug = 'talento-local')),
('30000000-0000-4000-8000-000000000008'::uuid, (select id from public.tags where slug = 'inteligencia-artificial')),
('30000000-0000-4000-8000-000000000008'::uuid, (select id from public.tags where slug = 'impacto-social')),
('30000000-0000-4000-8000-000000000008'::uuid, (select id from public.tags where slug = 'innovacion')),
('30000000-0000-4000-8000-000000000009'::uuid, (select id from public.tags where slug = 'impacto-social')),
('30000000-0000-4000-8000-000000000009'::uuid, (select id from public.tags where slug = 'innovacion')),
('30000000-0000-4000-8000-000000000009'::uuid, (select id from public.tags where slug = 'talento-local')),
('30000000-0000-4000-8000-000000000010'::uuid, (select id from public.tags where slug = 'impacto-social')),
('30000000-0000-4000-8000-000000000010'::uuid, (select id from public.tags where slug = 'innovacion')),
('30000000-0000-4000-8000-000000000010'::uuid, (select id from public.tags where slug = 'agricultura')),
('30000000-0000-4000-8000-000000000011'::uuid, (select id from public.tags where slug = 'talento-local')),
('30000000-0000-4000-8000-000000000011'::uuid, (select id from public.tags where slug = 'comercio-electronico')),
('30000000-0000-4000-8000-000000000011'::uuid, (select id from public.tags where slug = 'impacto-social')),
('30000000-0000-4000-8000-000000000012'::uuid, (select id from public.tags where slug = 'agricultura')),
('30000000-0000-4000-8000-000000000012'::uuid, (select id from public.tags where slug = 'talento-local')),
('30000000-0000-4000-8000-000000000012'::uuid, (select id from public.tags where slug = 'comercio-electronico')),
('30000000-0000-4000-8000-000000000013'::uuid, (select id from public.tags where slug = 'comercio-electronico')),
('30000000-0000-4000-8000-000000000013'::uuid, (select id from public.tags where slug = 'innovacion')),
('30000000-0000-4000-8000-000000000013'::uuid, (select id from public.tags where slug = 'talento-local')),
('30000000-0000-4000-8000-000000000014'::uuid, (select id from public.tags where slug = 'impacto-social')),
('30000000-0000-4000-8000-000000000014'::uuid, (select id from public.tags where slug = 'innovacion')),
('30000000-0000-4000-8000-000000000014'::uuid, (select id from public.tags where slug = 'talento-local')),
('30000000-0000-4000-8000-000000000015'::uuid, (select id from public.tags where slug = 'economia-circular')),
('30000000-0000-4000-8000-000000000015'::uuid, (select id from public.tags where slug = 'impacto-social')),
('30000000-0000-4000-8000-000000000015'::uuid, (select id from public.tags where slug = 'talento-local'))
on conflict (project_id, tag_id) do nothing;

-- Oportunidades de demostración (PUL-18, PUL-22 y PUL-24).
--
-- Cubre todos los tipos de oportunidad, ubicaciones presenciales y remotas, los
-- estados del ciclo de vida y habilidades REQUIRED y PREFERRED, para que el
-- detalle público, la gestión del equipo y la coincidencia con un perfil tengan
-- datos reales que mostrar. El seed no crea cuentas para iniciar sesión.
--
-- Identidad creadora: una cuenta de equipo sin contraseña ni correo utilizable,
-- necesaria porque project_opportunities.created_by_profile_id es obligatorio y
-- cada profile pertenece a auth.users. No puede iniciar sesión y no es miembro
-- de ningún proyecto.
--
-- Reglas del seed: UUIDs fijos con el prefijo 40000000 (oportunidades) y
-- 50000000 (creadora) para repetirlo sin duplicar; el contenido, el estado y las
-- habilidades de estas filas se restauran al volver a ejecutarlo, y las
-- oportunidades que cree una persona desarrolladora no se tocan.

insert into public.skills (slug, name, category)
values
  ('ingenieria-electrica', 'Ingeniería eléctrica', 'INGENIERIA'),
  ('logistica-y-distribucion', 'Logística y distribución', 'OPERACIONES'),
  ('finanzas-y-contabilidad', 'Finanzas y contabilidad', 'NEGOCIOS'),
  ('procesamiento-de-alimentos', 'Procesamiento de alimentos', 'AGRO')
on conflict (slug) do update set
  name = excluded.name,
  category = excluded.category,
  is_active = true,
  updated_at = timezone('utc', now());

insert into auth.users (
  id,
  aud,
  role,
  email,
  encrypted_password,
  raw_app_meta_data,
  raw_user_meta_data,
  created_at,
  updated_at
)
values (
  '50000000-0000-4000-8000-000000000001'::uuid,
  'authenticated',
  'authenticated',
  'equipo-demo@pulso.invalid',
  '',
  '{"provider":"seed","providers":[]}'::jsonb,
  '{"display_name":"Equipo demo de Pulso"}'::jsonb,
  timezone('utc', now()),
  timezone('utc', now())
)
on conflict (id) do nothing;

do $$
begin
  if not exists (
    select 1 from public.profiles
    where id = '50000000-0000-4000-8000-000000000001'::uuid
  ) then
    raise exception 'the demo opportunity creator has no profile; check the auth.users trigger';
  end if;
end
$$;

insert into public.project_opportunities (
  id, project_id, opportunity_type, title, description, status,
  city, department, remote_allowed, created_by_profile_id,
  closed_at, created_at, updated_at
)
select
  demo.id,
  project.id,
  demo.opportunity_type,
  demo.title,
  demo.description,
  demo.status,
  demo.city,
  demo.department,
  demo.remote_allowed,
  '50000000-0000-4000-8000-000000000001'::uuid,
  case when demo.status = 'CLOSED' then demo.created_at + interval '20 days' end,
  demo.created_at,
  demo.created_at
from (
  values
    -- EcoPack Bolivia
    ('40000000-0000-4000-8000-000000000001'::uuid, 'eco-pack', 'TALENT',
     'Desarrollador/a web para el portal de pedidos',
     'Construye el portal donde restaurantes y marcas locales piden envases compostables, siguen sus entregas y repiten pedidos. Trabajarás con el equipo de operaciones desde Cochabamba.',
     'OPEN', 'Cochabamba', 'Cochabamba', false, timestamptz '2026-10-20 12:00:00+00'),
    ('40000000-0000-4000-8000-000000000002'::uuid, 'eco-pack', 'BETA_TESTER',
     'Restaurantes piloto para probar los envases compostables',
     'Buscamos restaurantes y cafeterías de Cochabamba que prueben los envases durante cuatro semanas y compartan cómo resisten el calor, la humedad y el transporte.',
     'OPEN', 'Cochabamba', 'Cochabamba', false, timestamptz '2026-10-18 12:00:00+00'),
    ('40000000-0000-4000-8000-000000000003'::uuid, 'eco-pack', 'SUPPLIER',
     'Proveedores de fibra agrícola y residuos de caña',
     'Necesitamos asociaciones productoras que entreguen de forma regular residuos de caña, paja y otras fibras limpias para convertirlos en envases.',
     'OPEN', null, 'Cochabamba', false, timestamptz '2026-10-15 12:00:00+00'),
    -- AndeSolar
    ('40000000-0000-4000-8000-000000000004'::uuid, 'ande-solar', 'TALENT',
     'Ingeniero/a eléctrico/a para kits solares modulares',
     'Dimensiona, prueba y documenta los kits solares que se instalan en comunidades rurales del altiplano. Incluye visitas de campo planificadas.',
     'OPEN', 'La Paz', 'La Paz', false, timestamptz '2026-10-19 12:00:00+00'),
    ('40000000-0000-4000-8000-000000000005'::uuid, 'ande-solar', 'PARTNERSHIP',
     'Aliados para instalar kits en comunidades rurales',
     'Buscamos organizaciones y cooperativas con presencia en el altiplano que coordinen la instalación, la capacitación de las familias y el mantenimiento.',
     'OPEN', null, 'La Paz', false, timestamptz '2026-10-14 12:00:00+00'),
    ('40000000-0000-4000-8000-000000000006'::uuid, 'ande-solar', 'INVESTMENT',
     'Inversión semilla para llegar a 200 hogares',
     'Abrimos una ronda semilla pequeña para financiar la fabricación de la siguiente serie de kits y el seguimiento durante el primer año.',
     'OPEN', null, null, true, timestamptz '2026-10-10 12:00:00+00'),
    -- BioCarbono Andino
    ('40000000-0000-4000-8000-000000000007'::uuid, 'bio-carbono-andino', 'MENTOR',
     'Mentoría en suelos y agricultura sostenible',
     'Buscamos una persona con experiencia en suelos andinos que revise nuestras parcelas de prueba y oriente el protocolo para medir resultados.',
     'OPEN', null, null, true, timestamptz '2026-10-17 12:00:00+00'),
    ('40000000-0000-4000-8000-000000000008'::uuid, 'bio-carbono-andino', 'TALENT',
     'Analista de datos para la trazabilidad de lotes',
     'Diseña los tableros y las consultas que muestran el origen, la mezcla y el rendimiento de cada lote de biochar. Puedes trabajar a distancia.',
     'OPEN', null, null, true, timestamptz '2026-10-12 12:00:00+00'),
    -- SecaSol Andino
    ('40000000-0000-4000-8000-000000000009'::uuid, 'seca-sol-andino', 'COLLABORATION',
     'Asociaciones productoras para pilotos de secado',
     'Invitamos a asociaciones de productores de fruta, hierbas y cacao a probar los secadores y registrar sus ciclos de secado junto a nuestro equipo.',
     'OPEN', 'El Alto', 'La Paz', false, timestamptz '2026-10-16 12:00:00+00'),
    -- Qhatu
    ('40000000-0000-4000-8000-000000000010'::uuid, 'qhatu', 'TALENT',
     'Desarrollador/a móvil para vendedores de mercado',
     'Crea la aplicación móvil con la que comerciantes de mercado registran ventas, cobran y reciben pedidos. Prioriza conexiones lentas y uso con una sola mano.',
     'OPEN', null, null, true, timestamptz '2026-10-21 12:00:00+00'),
    ('40000000-0000-4000-8000-000000000011'::uuid, 'qhatu', 'TALENT',
     'Marketing digital para las primeras cohortes de comerciantes',
     'Diseña y mide las campañas que atraen a las primeras cohortes de comerciantes en La Paz y El Alto, y propón cómo explicar el producto en mercados.',
     'OPEN', 'La Paz', 'La Paz', false, timestamptz '2026-10-13 12:00:00+00'),
    ('40000000-0000-4000-8000-000000000012'::uuid, 'qhatu', 'TALENT',
     'Diseñador/a UX para el flujo de cobro',
     'Rediseña el flujo de cobro con pruebas en mercados. La búsqueda está en pausa mientras se define el presupuesto del trimestre.',
     'PAUSED', 'La Paz', 'La Paz', false, timestamptz '2026-09-30 12:00:00+00'),
    -- Kawsay Foods
    ('40000000-0000-4000-8000-000000000013'::uuid, 'kawsay-foods', 'TALENT',
     'Ejecutivo/a de ventas para comercios locales',
     'Abre cuentas en tiendas de barrio, restaurantes y cooperativas de Sucre, y construye una agenda de visitas con metas mensuales claras.',
     'OPEN', 'Sucre', 'Chuquisaca', false, timestamptz '2026-10-19 08:00:00+00'),
    ('40000000-0000-4000-8000-000000000014'::uuid, 'kawsay-foods', 'PARTNERSHIP',
     'Distribuidores en ciudades intermedias',
     'Buscamos distribuidores con frío y transporte propios para llevar los productos a Potosí, Tarija y Cochabamba con entregas semanales.',
     'OPEN', null, 'Chuquisaca', true, timestamptz '2026-10-11 12:00:00+00'),
    ('40000000-0000-4000-8000-000000000015'::uuid, 'kawsay-foods', 'TALENT',
     'Jefe/a de planta de procesamiento',
     'Coordina el arranque de la segunda línea de producción. La búsqueda se cerró al incorporarse la persona seleccionada.',
     'CLOSED', 'Sucre', 'Chuquisaca', false, timestamptz '2026-08-25 12:00:00+00'),
    -- AquaSense
    ('40000000-0000-4000-8000-000000000016'::uuid, 'aquasense', 'BETA_TESTER',
     'Agricultores con riego por goteo para probar los sensores',
     'Buscamos agricultores de Tarija con riego por goteo que instalen los sensores de humedad en una parcela y nos cuenten cómo cambian sus decisiones de riego.',
     'OPEN', 'Tarija', 'Tarija', false, timestamptz '2026-10-18 08:00:00+00'),
    ('40000000-0000-4000-8000-000000000017'::uuid, 'aquasense', 'TALENT',
     'Analista de datos para modelos de humedad del suelo',
     'Limpia las lecturas de los sensores y prueba modelos sencillos que recomienden cuándo regar. Trabajo remoto con reuniones semanales.',
     'OPEN', null, null, true, timestamptz '2026-10-09 12:00:00+00'),
    ('40000000-0000-4000-8000-000000000018'::uuid, 'aquasense', 'TALENT',
     'Técnico/a de campo para instalación de sensores',
     'Borrador: perfil aún sin publicar mientras se define el calendario de instalaciones.',
     'DRAFT', 'Tarija', 'Tarija', false, timestamptz '2026-10-22 12:00:00+00'),
    -- Ruta
    ('40000000-0000-4000-8000-000000000019'::uuid, 'ruta', 'CUSTOMER',
     'Flotas de transporte interesadas en optimizar sus rutas',
     'Buscamos empresas de transporte y distribución en Santa Cruz que prueben la planificación de rutas con sus propios camiones durante un mes.',
     'OPEN', 'Santa Cruz de la Sierra', 'Santa Cruz', false, timestamptz '2026-10-17 08:00:00+00'),
    ('40000000-0000-4000-8000-000000000020'::uuid, 'ruta', 'TALENT',
     'Desarrollador/a web para el panel de despacho',
     'La vacante ya fue cubierta. Se conserva en el historial del proyecto.',
     'FILLED', 'Santa Cruz de la Sierra', 'Santa Cruz', false, timestamptz '2026-09-15 12:00:00+00'),
    -- TutorIA
    ('40000000-0000-4000-8000-000000000021'::uuid, 'tutoria', 'COLLABORATION',
     'Colegios para probar la tutoría con inteligencia artificial',
     'Invitamos a colegios de Cochabamba a usar el prototipo con un curso durante un bimestre y revisar con nosotros qué ayuda de verdad a las y los estudiantes.',
     'OPEN', 'Cochabamba', 'Cochabamba', false, timestamptz '2026-10-14 08:00:00+00'),
    -- Muyu Salud
    ('40000000-0000-4000-8000-000000000022'::uuid, 'muyu-salud', 'PARTNERSHIP',
     'Centros de salud rurales para el seguimiento de controles',
     'Buscamos centros y brigadas de salud del Beni que prueben el seguimiento de controles prenatales y vacunas en zonas con poca conectividad.',
     'OPEN', 'Trinidad', 'Beni', false, timestamptz '2026-10-13 08:00:00+00'),
    -- Suma Logística
    ('40000000-0000-4000-8000-000000000023'::uuid, 'suma-logistica', 'TALENT',
     'Coordinador/a de operaciones logísticas',
     'Planifica las rutas de recojo y entrega del día, coordina a los conductores y mantiene indicadores simples de puntualidad y costo.',
     'OPEN', 'Santa Cruz de la Sierra', 'Santa Cruz', false, timestamptz '2026-10-16 08:00:00+00'),
    -- Altura Cacao
    ('40000000-0000-4000-8000-000000000024'::uuid, 'altura-cacao', 'INVESTMENT',
     'Capital de trabajo para la próxima cosecha',
     'Buscamos financiamiento de corto plazo para comprar cacao a las asociaciones productoras de Riberalta al inicio de la cosecha y pagarles a tiempo.',
     'OPEN', 'Riberalta', 'Beni', false, timestamptz '2026-10-12 08:00:00+00'),
    -- Bosque Vivo
    ('40000000-0000-4000-8000-000000000025'::uuid, 'bosque-vivo', 'OTHER',
     'Voluntariado para jornadas de reforestación',
     'Convocamos personas de Cobija y alrededores para plantar y cuidar árboles nativos durante las jornadas mensuales del proyecto.',
     'OPEN', 'Cobija', 'Pando', false, timestamptz '2026-10-08 12:00:00+00')
) as demo (
  id, project_slug, opportunity_type, title, description, status,
  city, department, remote_allowed, created_at
)
join public.projects project on project.slug = demo.project_slug
on conflict (id) do update set
  project_id = excluded.project_id,
  opportunity_type = excluded.opportunity_type,
  title = excluded.title,
  description = excluded.description,
  status = excluded.status,
  city = excluded.city,
  department = excluded.department,
  remote_allowed = excluded.remote_allowed,
  closed_at = excluded.closed_at;

do $$
begin
  if (select count(*)
      from public.project_opportunities
      where id::text like '40000000-0000-4000-8000-0000000000%') <> 25 then
    raise exception 'the demo opportunities were not all written; check the demo project slugs';
  end if;
end
$$;

-- Cada oportunidad demo conserva exactamente estas habilidades.
delete from public.opportunity_skills
where opportunity_id::text like '40000000-0000-4000-8000-0000000000%';

insert into public.opportunity_skills (opportunity_id, skill_id, importance)
select demo.opportunity_id, skill.id, demo.importance
from (
  values
    ('40000000-0000-4000-8000-000000000001'::uuid, 'desarrollo-web', 'REQUIRED'),
    ('40000000-0000-4000-8000-000000000001'::uuid, 'diseno-ux', 'PREFERRED'),
    ('40000000-0000-4000-8000-000000000003'::uuid, 'agricultura-sostenible', 'REQUIRED'),
    ('40000000-0000-4000-8000-000000000004'::uuid, 'ingenieria-electrica', 'REQUIRED'),
    ('40000000-0000-4000-8000-000000000004'::uuid, 'gestion-de-proyectos', 'PREFERRED'),
    ('40000000-0000-4000-8000-000000000005'::uuid, 'gestion-de-proyectos', 'REQUIRED'),
    ('40000000-0000-4000-8000-000000000005'::uuid, 'logistica-y-distribucion', 'PREFERRED'),
    ('40000000-0000-4000-8000-000000000006'::uuid, 'finanzas-y-contabilidad', 'PREFERRED'),
    ('40000000-0000-4000-8000-000000000007'::uuid, 'agricultura-sostenible', 'REQUIRED'),
    ('40000000-0000-4000-8000-000000000008'::uuid, 'analisis-de-datos', 'REQUIRED'),
    ('40000000-0000-4000-8000-000000000008'::uuid, 'desarrollo-web', 'PREFERRED'),
    ('40000000-0000-4000-8000-000000000009'::uuid, 'procesamiento-de-alimentos', 'REQUIRED'),
    ('40000000-0000-4000-8000-000000000009'::uuid, 'agricultura-sostenible', 'PREFERRED'),
    ('40000000-0000-4000-8000-000000000010'::uuid, 'desarrollo-movil', 'REQUIRED'),
    ('40000000-0000-4000-8000-000000000010'::uuid, 'diseno-ux', 'PREFERRED'),
    ('40000000-0000-4000-8000-000000000011'::uuid, 'marketing-digital', 'REQUIRED'),
    ('40000000-0000-4000-8000-000000000011'::uuid, 'ventas', 'PREFERRED'),
    ('40000000-0000-4000-8000-000000000012'::uuid, 'diseno-ux', 'REQUIRED'),
    ('40000000-0000-4000-8000-000000000013'::uuid, 'ventas', 'REQUIRED'),
    ('40000000-0000-4000-8000-000000000013'::uuid, 'marketing-digital', 'PREFERRED'),
    ('40000000-0000-4000-8000-000000000014'::uuid, 'logistica-y-distribucion', 'REQUIRED'),
    ('40000000-0000-4000-8000-000000000015'::uuid, 'procesamiento-de-alimentos', 'REQUIRED'),
    ('40000000-0000-4000-8000-000000000015'::uuid, 'gestion-de-proyectos', 'PREFERRED'),
    ('40000000-0000-4000-8000-000000000017'::uuid, 'analisis-de-datos', 'REQUIRED'),
    ('40000000-0000-4000-8000-000000000017'::uuid, 'agricultura-sostenible', 'PREFERRED'),
    ('40000000-0000-4000-8000-000000000018'::uuid, 'agricultura-sostenible', 'PREFERRED'),
    ('40000000-0000-4000-8000-000000000020'::uuid, 'desarrollo-web', 'REQUIRED'),
    ('40000000-0000-4000-8000-000000000023'::uuid, 'logistica-y-distribucion', 'REQUIRED'),
    ('40000000-0000-4000-8000-000000000023'::uuid, 'gestion-de-proyectos', 'PREFERRED'),
    ('40000000-0000-4000-8000-000000000024'::uuid, 'finanzas-y-contabilidad', 'PREFERRED')
) as demo (opportunity_id, skill_slug, importance)
join public.skills skill on skill.slug = demo.skill_slug;
