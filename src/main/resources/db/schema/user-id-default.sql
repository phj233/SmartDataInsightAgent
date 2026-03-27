CREATE SEQUENCE IF NOT EXISTS public.user_id_seq;

ALTER SEQUENCE public.user_id_seq OWNED BY public."user".id;

ALTER TABLE public."user"
	ALTER COLUMN id SET DEFAULT nextval('public.user_id_seq');

SELECT setval(
	'public.user_id_seq',
	COALESCE((SELECT MAX(id) FROM public."user"), 1),
	(SELECT MAX(id) IS NOT NULL FROM public."user")
);

