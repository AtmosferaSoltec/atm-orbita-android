-- =====================================================================
-- 0001_init.sql — MVP
-- =====================================================================

-- ---------- 1) Utilidades ----------
create or replace function public.set_updated_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

-- ---------- 2) Tablas ----------
create table public.profiles (
  id               uuid primary key references auth.users (id) on delete cascade,
  default_currency text not null default 'PEN' check (default_currency ~ '^[A-Z]{3}$'),
  display_currency text not null default 'PEN' check (display_currency ~ '^[A-Z]{3}$'),
  fx_mode          text not null default 'manual' check (fx_mode in ('manual', 'auto')),
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now()
);

-- Tipo de cambio: rate = unidades de to_currency por 1 unidad de from_currency.
-- user_id NULL = tipo de cambio global (lo cargará una API en el futuro).
create table public.exchange_rates (
  id            uuid primary key default gen_random_uuid(),
  user_id       uuid default auth.uid() references auth.users (id) on delete cascade,
  from_currency text not null check (from_currency ~ '^[A-Z]{3}$'),
  to_currency   text not null check (to_currency ~ '^[A-Z]{3}$'),
  rate          numeric(18, 6) not null check (rate > 0),
  source        text not null default 'manual' check (source in ('manual', 'api')),
  rate_date     date not null default current_date,
  created_at    timestamptz not null default now(),
  check (from_currency <> to_currency)
);

create table public.accounts (
  id                 uuid primary key default gen_random_uuid(),
  user_id            uuid not null default auth.uid() references auth.users (id) on delete cascade,
  name               text not null check (length(trim(name)) between 1 and 60),
  type               text not null default 'debit' check (type in ('cash', 'debit', 'savings', 'other')),
  currency           text not null default 'PEN' check (currency ~ '^[A-Z]{3}$'),
  initial_balance    numeric(14, 2) not null default 0,
  include_in_savings boolean not null default true,
  is_archived        boolean not null default false,
  sort_order         integer not null default 0,
  created_at         timestamptz not null default now(),
  updated_at         timestamptz not null default now(),
  deleted_at         timestamptz,
  unique (id, user_id)
);

create table public.categories (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null default auth.uid() references auth.users (id) on delete cascade,
  name        text not null check (length(trim(name)) between 1 and 60),
  kind        text not null check (kind in ('income', 'expense')),
  icon        text,
  color       text check (color is null or color ~ '^#[0-9A-Fa-f]{6}$'),
  is_archived boolean not null default false,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now(),
  deleted_at  timestamptz,
  unique (id, user_id)
);

create table public.transactions (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null default auth.uid() references auth.users (id) on delete cascade,
  account_id  uuid not null,
  category_id uuid not null,
  kind        text not null check (kind in ('income', 'expense')),
  amount      numeric(14, 2) not null check (amount > 0),
  occurred_on date not null default current_date,
  description text not null default '' check (length(description) <= 500),
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now(),
  deleted_at  timestamptz,
  constraint transactions_account_fk  foreign key (account_id, user_id)
    references public.accounts (id, user_id) on delete cascade,
  constraint transactions_category_fk foreign key (category_id, user_id)
    references public.categories (id, user_id) on delete cascade
);

-- exchange_rate = unidades de la moneda de la cuenta destino por 1 unidad de la cuenta origen.
create table public.transfers (
  id              uuid primary key default gen_random_uuid(),
  user_id         uuid not null default auth.uid() references auth.users (id) on delete cascade,
  from_account_id uuid not null,
  to_account_id   uuid not null,
  from_amount     numeric(14, 2) not null check (from_amount > 0),
  to_amount       numeric(14, 2) not null check (to_amount > 0),
  exchange_rate   numeric(18, 6) check (exchange_rate is null or exchange_rate > 0),
  occurred_on     date not null default current_date,
  note            text not null default '' check (length(note) <= 500),
  created_at      timestamptz not null default now(),
  updated_at      timestamptz not null default now(),
  deleted_at      timestamptz,
  check (from_account_id <> to_account_id),
  constraint transfers_from_account_fk foreign key (from_account_id, user_id)
    references public.accounts (id, user_id) on delete cascade,
  constraint transfers_to_account_fk foreign key (to_account_id, user_id)
    references public.accounts (id, user_id) on delete cascade
);

-- ---------- 3) Triggers ----------
-- 3a) updated_at automático
do $$
declare t text;
begin
  foreach t in array array['profiles', 'accounts', 'categories', 'transactions', 'transfers'] loop
    execute format(
      'create trigger %I before update on public.%I for each row execute function public.set_updated_at()',
      t || '_set_updated_at', t);
  end loop;
end $$;

-- 3b) La categoría debe ser del mismo tipo (ingreso/egreso) que el movimiento
create or replace function public.validate_transaction()
returns trigger
language plpgsql
set search_path = ''
as $$
declare
  cat_kind text;
begin
  select c.kind into cat_kind
  from public.categories c
  where c.id = new.category_id and c.user_id = new.user_id;

  if cat_kind is not null and cat_kind <> new.kind then
    raise exception 'La categoría es de tipo % pero el movimiento es de tipo %', cat_kind, new.kind;
  end if;
  return new;
end;
$$;

create trigger transactions_validate
before insert or update on public.transactions
for each row execute function public.validate_transaction();

-- 3c) Transferencias: misma moneda => mismos montos; distinta moneda => exige tipo de cambio
create or replace function public.validate_transfer()
returns trigger
language plpgsql
set search_path = ''
as $$
declare
  from_cur text;
  to_cur   text;
begin
  select a.currency into from_cur from public.accounts a
   where a.id = new.from_account_id and a.user_id = new.user_id;
  select a.currency into to_cur from public.accounts a
   where a.id = new.to_account_id and a.user_id = new.user_id;

  if from_cur = to_cur and new.to_amount <> new.from_amount then
    raise exception 'Entre cuentas de la misma moneda los montos deben ser iguales';
  end if;
  if from_cur <> to_cur and new.exchange_rate is null then
    raise exception 'Falta el tipo de cambio: las cuentas tienen monedas distintas';
  end if;
  return new;
end;
$$;

create trigger transfers_validate
before insert or update on public.transfers
for each row execute function public.validate_transfer();

-- ---------- 4) Índices ----------
create index accounts_user_idx on public.accounts (user_id) where deleted_at is null;
create index categories_user_idx on public.categories (user_id) where deleted_at is null;
create unique index categories_user_kind_name_uidx
  on public.categories (user_id, kind, lower(name)) where deleted_at is null;
create index transactions_user_date_idx on public.transactions (user_id, occurred_on desc) where deleted_at is null;
create index transactions_account_idx on public.transactions (account_id) where deleted_at is null;
create index transactions_category_idx on public.transactions (category_id) where deleted_at is null;
create index transfers_user_date_idx on public.transfers (user_id, occurred_on desc) where deleted_at is null;
create index transfers_from_idx on public.transfers (from_account_id) where deleted_at is null;
create index transfers_to_idx on public.transfers (to_account_id) where deleted_at is null;
create index exchange_rates_lookup_idx
  on public.exchange_rates (user_id, from_currency, to_currency, rate_date desc);

-- ---------- 5) Row Level Security ----------
alter table public.profiles       enable row level security;
alter table public.exchange_rates enable row level security;

create policy profiles_select_own on public.profiles
  for select to authenticated using (id = (select auth.uid()));
create policy profiles_update_own on public.profiles
  for update to authenticated
  using (id = (select auth.uid())) with check (id = (select auth.uid()));

create policy exchange_rates_select on public.exchange_rates
  for select to authenticated using (user_id is null or user_id = (select auth.uid()));
create policy exchange_rates_insert_own on public.exchange_rates
  for insert to authenticated
  with check (user_id = (select auth.uid()) and source = 'manual');
create policy exchange_rates_update_own on public.exchange_rates
  for update to authenticated
  using (user_id = (select auth.uid()))
  with check (user_id = (select auth.uid()) and source = 'manual');
create policy exchange_rates_delete_own on public.exchange_rates
  for delete to authenticated
  using (user_id = (select auth.uid()) and source = 'manual');

-- Tablas de datos: select / insert / update solo de las filas propias (sin delete: borrado lógico)
do $$
declare t text;
begin
  foreach t in array array['accounts', 'categories', 'transactions', 'transfers'] loop
    execute format('alter table public.%I enable row level security', t);
    execute format(
      'create policy %I on public.%I for select to authenticated using (user_id = (select auth.uid()))',
      t || '_select_own', t);
    execute format(
      'create policy %I on public.%I for insert to authenticated with check (user_id = (select auth.uid()))',
      t || '_insert_own', t);
    execute format(
      'create policy %I on public.%I for update to authenticated using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()))',
      t || '_update_own', t);
  end loop;
end $$;

-- ---------- 6) Vista de saldos ----------
create view public.account_balances
with (security_invoker = true) as
select
  a.id        as account_id,
  a.user_id,
  a.name,
  a.type,
  a.currency,
  a.include_in_savings,
  a.is_archived,
  a.initial_balance
    + coalesce((select sum(case t.kind when 'income' then t.amount else -t.amount end)
                  from public.transactions t
                 where t.account_id = a.id and t.user_id = a.user_id and t.deleted_at is null), 0)
    + coalesce((select sum(tr.to_amount)
                  from public.transfers tr
                 where tr.to_account_id = a.id and tr.user_id = a.user_id and tr.deleted_at is null), 0)
    - coalesce((select sum(tr.from_amount)
                  from public.transfers tr
                 where tr.from_account_id = a.id and tr.user_id = a.user_id and tr.deleted_at is null), 0)
    as balance
from public.accounts a
where a.deleted_at is null;

-- ---------- 7) Reportes (funciones con los permisos del usuario => RLS aplica) ----------
-- Totales de ingresos y egresos del periodo, por moneda. Las transferencias NO cuentan.
create or replace function public.report_period_summary(p_from date, p_to date)
returns table (currency text, income_total numeric, expense_total numeric)
language sql
stable
set search_path = ''
as $$
  select a.currency,
         coalesce(sum(t.amount) filter (where t.kind = 'income'), 0),
         coalesce(sum(t.amount) filter (where t.kind = 'expense'), 0)
    from public.transactions t
    join public.accounts a on a.id = t.account_id and a.user_id = t.user_id
   where t.user_id = (select auth.uid())
     and t.deleted_at is null
     and a.deleted_at is null
     and t.occurred_on between p_from and p_to
   group by a.currency
   order by a.currency;
$$;

-- Ranking por categoría. p_kind = 'expense' (dónde gastas más) o 'income' (fuentes de ingreso).
create or replace function public.report_by_category(p_kind text, p_from date, p_to date)
returns table (category_id uuid, category_name text, color text, currency text, total numeric, movements bigint)
language sql
stable
set search_path = ''
as $$
  select c.id, c.name, c.color, a.currency, sum(t.amount), count(*)
    from public.transactions t
    join public.accounts a   on a.id = t.account_id   and a.user_id = t.user_id
    join public.categories c on c.id = t.category_id  and c.user_id = t.user_id
   where t.user_id = (select auth.uid())
     and t.kind = p_kind
     and t.deleted_at is null
     and a.deleted_at is null
     and t.occurred_on between p_from and p_to
   group by c.id, c.name, c.color, a.currency
   order by a.currency, sum(t.amount) desc;
$$;

-- ---------- 8) Permisos ----------
revoke all on public.profiles, public.exchange_rates, public.accounts, public.categories,
              public.transactions, public.transfers, public.account_balances from anon;

grant select, update on public.profiles to authenticated;
grant select, insert, update, delete on public.exchange_rates to authenticated;
grant select, insert, update on public.accounts, public.categories,
                                public.transactions, public.transfers to authenticated;
grant select on public.account_balances to authenticated;

revoke execute on function public.report_period_summary(date, date) from public, anon;
revoke execute on function public.report_by_category(text, date, date) from public, anon;
grant execute on function public.report_period_summary(date, date) to authenticated;
grant execute on function public.report_by_category(text, date, date) to authenticated;

-- ---------- 9) Alta de usuario: perfil + categorías iniciales + cuenta "Efectivo" ----------
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  insert into public.profiles (id) values (new.id);

  insert into public.categories (user_id, name, kind, icon, color) values
    (new.id, 'Alimentación',   'expense', 'restaurant',    '#0E7490'),
    (new.id, 'Transporte',     'expense', 'directions_bus','#B45309'),
    (new.id, 'Vivienda',       'expense', 'home',          '#1D4ED8'),
    (new.id, 'Salud',          'expense', 'health',        '#7C3AED'),
    (new.id, 'Ocio',           'expense', 'sports_esports','#BE185D'),
    (new.id, 'Otros',          'expense', 'more',          '#64748B'),
    (new.id, 'Sueldo',         'income',  'work',          '#0B7A5A'),
    (new.id, 'Freelance',      'income',  'laptop',        '#0E7490'),
    (new.id, 'Otros ingresos', 'income',  'more',          '#64748B');

  insert into public.accounts (user_id, name, type, currency)
  values (new.id, 'Efectivo', 'cash', 'PEN');

  return new;
end;
$$;

revoke execute on function public.handle_new_user() from public, anon, authenticated;

create trigger on_auth_user_created
after insert on auth.users
for each row execute function public.handle_new_user();
