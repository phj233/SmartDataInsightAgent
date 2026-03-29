-- Unified ID default initialization for PostgreSQL tables managed by Jimmer.

CREATE SEQUENCE IF NOT EXISTS public.user_id_seq;
ALTER SEQUENCE public.user_id_seq OWNED BY public."user".id;
ALTER TABLE public."user"
    ALTER COLUMN id SET DEFAULT nextval('public.user_id_seq');
SELECT setval(
    'public.user_id_seq',
    COALESCE((SELECT MAX(id) FROM public."user"), 1),
    (SELECT MAX(id) IS NOT NULL FROM public."user")
);

CREATE SEQUENCE IF NOT EXISTS public.data_source_id_seq;
ALTER SEQUENCE public.data_source_id_seq OWNED BY public.data_source.id;
ALTER TABLE public.data_source
    ALTER COLUMN id SET DEFAULT nextval('public.data_source_id_seq');
SELECT setval(
    'public.data_source_id_seq',
    COALESCE((SELECT MAX(id) FROM public.data_source), 1),
    (SELECT MAX(id) IS NOT NULL FROM public.data_source)
);

CREATE SEQUENCE IF NOT EXISTS public.analysis_tasks_id_seq;
ALTER SEQUENCE public.analysis_tasks_id_seq OWNED BY public.analysis_tasks.id;
ALTER TABLE public.analysis_tasks
    ALTER COLUMN id SET DEFAULT nextval('public.analysis_tasks_id_seq');
SELECT setval(
    'public.analysis_tasks_id_seq',
    COALESCE((SELECT MAX(id) FROM public.analysis_tasks), 1),
    (SELECT MAX(id) IS NOT NULL FROM public.analysis_tasks)
);

