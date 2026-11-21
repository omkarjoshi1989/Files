-- Run in Supabase Dashboard -> SQL Editor.
-- Fixes: "new row violates row-level security policy for table Hybrid" (401 / 42501).
-- The app uses the publishable (anon) key, so policies must target role `anon`.

alter table public."Hybrid" enable row level security;

drop policy if exists "hybrid_select_anon" on public."Hybrid";
drop policy if exists "hybrid_insert_anon" on public."Hybrid";
drop policy if exists "hybrid_update_anon" on public."Hybrid";

create policy "hybrid_select_anon" on public."Hybrid"
  for select to anon using (true);

create policy "hybrid_insert_anon" on public."Hybrid"
  for insert to anon with check (true);

create policy "hybrid_update_anon" on public."Hybrid"
  for update to anon using (true) with check (true);

grant usage on schema public to anon;
grant select, insert, update on public."Hybrid" to anon;

