-- Linko : sauvegarde des donnees entre Android et PC
-- A coller dans Supabase > SQL Editor > New query > Run (une seule fois)

create table if not exists public.linko_state (
  user_id    uuid primary key default auth.uid() references auth.users(id) on delete cascade,
  data       jsonb not null default '{}'::jsonb,
  updated_at timestamptz not null default now()
);

alter table public.linko_state enable row level security;

drop policy if exists "linko lecture"      on public.linko_state;
drop policy if exists "linko creation"     on public.linko_state;
drop policy if exists "linko modification" on public.linko_state;
create policy "linko lecture"      on public.linko_state for select using (auth.uid() = user_id);
create policy "linko creation"     on public.linko_state for insert with check (auth.uid() = user_id);
create policy "linko modification" on public.linko_state for update using (auth.uid() = user_id) with check (auth.uid() = user_id);

create or replace function public.linko_touch() returns trigger
language plpgsql as $$
begin
  new.updated_at = now();
  return new;
end $$;

drop trigger if exists linko_touch on public.linko_state;
create trigger linko_touch before insert or update on public.linko_state
for each row execute function public.linko_touch();

-- Chacun ne voit et ne modifie que SA ligne (recettes, categories, titres, listes).
-- Reglage conseille : Authentication > Providers > Email > desactiver "Confirm email" (connexion directe).
