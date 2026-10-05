# 03 · Modelo de datos (Supabase / Postgres)

Este documento contiene el esquema completo y el SQL de las migraciones. **Cómo aplicarlas**: `docs/04-guia-supabase.md`.

## 1. Resumen

| Tabla | Para qué | Versión |
|---|---|---|
| `profiles` | Preferencias del usuario (moneda de visualización, modo de cambio) | MVP |
| `exchange_rates` | Tipos de cambio: manuales del usuario (y luego globales desde una API) | MVP |
| `accounts` | Las "tarjetas": efectivo, débito, ahorro… cada una con su moneda | MVP |
| `categories` | Categorías de ingreso y de egreso | MVP |
| `transactions` | Ingresos y egresos | MVP |
| `transfers` | Transferencias entre cuentas (con conversión) | MVP |
| `credit_purchases` | Compras de tarjeta de crédito pendientes o pagadas | v1.1 |
| vista `account_balances` | Saldo calculado por cuenta | MVP |
| funciones `report_*` | Reportes por periodo | MVP |
| funciones `pay_credit_purchase`, `unpay_credit_purchase` | Pagar / deshacer pago de crédito (atómicas) | v1.1 |

## 2. Convenciones
- **Montos**: `NUMERIC(14,2)`. **Tipos de cambio**: `NUMERIC(18,6)`.
- **Monedas**: texto de 3 letras mayúsculas (`PEN`, `USD`).
- **IDs**: `uuid` con `default gen_random_uuid()`; el cliente puede enviar el suyo.
- Toda tabla de usuario tiene `user_id uuid not null default auth.uid()`, `created_at`, `updated_at` (se actualiza por trigger) y `deleted_at` (borrado lógico, preparado para sincronizar offline).
- **No hay políticas `DELETE`** en tablas de datos: el borrado desde la app es **lógico** (`update ... set deleted_at = now()`). Así no se pierde historial por accidente.
- **Integridad entre usuarios**: las claves foráneas son **compuestas** `(id, user_id)`, de modo que un movimiento nunca puede apuntar a una cuenta o categoría de otra persona.
- El saldo **no se guarda**: sale de `initial_balance` + movimientos + transferencias.
- Las vistas usan `security_invoker = true` para respetar RLS.
- Las columnas de moneda del movimiento **no se duplican**: un movimiento hereda la moneda de su cuenta.

## 3. Migración `0001_init.sql` (MVP)

Crear con `npx supabase migration new init` y pegar este contenido.

```sql
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
```

## 4. Migración `0002_credit_purchases.sql` (v1.1)

```sql
-- =====================================================================
-- 0002_credit_purchases.sql — Compras con tarjeta de crédito (v1.1)
-- Mientras estén 'pending' NO afectan saldos ni reportes (no son movimientos).
-- =====================================================================

create table public.credit_purchases (
  id              uuid primary key default gen_random_uuid(),
  user_id         uuid not null default auth.uid() references auth.users (id) on delete cascade,
  description     text not null default '' check (length(description) <= 500),
  category_id     uuid not null,
  amount          numeric(14, 2) not null check (amount > 0),
  currency        text not null default 'PEN' check (currency ~ '^[A-Z]{3}$'),
  purchase_date   date not null default current_date,
  due_date        date not null,
  status          text not null default 'pending' check (status in ('pending', 'paid')),
  paid_on         date,
  paid_account_id uuid,
  paid_amount     numeric(14, 2) check (paid_amount is null or paid_amount > 0),
  transaction_id  uuid references public.transactions (id) on delete set null,
  created_at      timestamptz not null default now(),
  updated_at      timestamptz not null default now(),
  deleted_at      timestamptz,
  check (due_date >= purchase_date),
  check (status = 'paid' or (paid_on is null and paid_account_id is null
                             and paid_amount is null and transaction_id is null)),
  constraint credit_purchases_category_fk foreign key (category_id, user_id)
    references public.categories (id, user_id) on delete cascade,
  constraint credit_purchases_paid_account_fk foreign key (paid_account_id, user_id)
    references public.accounts (id, user_id) on delete cascade
);

create trigger credit_purchases_set_updated_at
before update on public.credit_purchases
for each row execute function public.set_updated_at();

create index credit_purchases_user_status_due_idx
  on public.credit_purchases (user_id, status, due_date) where deleted_at is null;

alter table public.credit_purchases enable row level security;

create policy credit_purchases_select_own on public.credit_purchases
  for select to authenticated using (user_id = (select auth.uid()));
create policy credit_purchases_insert_own on public.credit_purchases
  for insert to authenticated with check (user_id = (select auth.uid()));
create policy credit_purchases_update_own on public.credit_purchases
  for update to authenticated
  using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));

revoke all on public.credit_purchases from anon;
grant select, insert, update on public.credit_purchases to authenticated;

-- Pagar: crea el egreso y marca la compra como pagada, todo en una sola transacción.
-- p_paid_amount = monto REAL descontado, en la moneda de la cuenta de pago.
create or replace function public.pay_credit_purchase(
  p_purchase_id uuid,
  p_account_id  uuid,
  p_paid_amount numeric,
  p_paid_on     date default current_date
)
returns uuid
language plpgsql
set search_path = ''
as $$
declare
  v_purchase public.credit_purchases%rowtype;
  v_tx_id    uuid;
begin
  select * into v_purchase
    from public.credit_purchases
   where id = p_purchase_id
     and user_id = (select auth.uid())
     and deleted_at is null
   for update;

  if not found then
    raise exception 'Compra no encontrada';
  end if;
  if v_purchase.status = 'paid' then
    raise exception 'La compra ya está pagada';
  end if;
  if p_paid_amount is null or p_paid_amount <= 0 then
    raise exception 'El monto pagado debe ser mayor que cero';
  end if;

  insert into public.transactions (user_id, account_id, category_id, kind, amount, occurred_on, description)
  values (v_purchase.user_id, p_account_id, v_purchase.category_id, 'expense',
          round(p_paid_amount, 2), p_paid_on, v_purchase.description)
  returning id into v_tx_id;

  update public.credit_purchases
     set status = 'paid',
         paid_on = p_paid_on,
         paid_account_id = p_account_id,
         paid_amount = round(p_paid_amount, 2),
         transaction_id = v_tx_id
   where id = p_purchase_id;

  return v_tx_id;
end;
$$;

-- Deshacer un pago: borra (lógicamente) el egreso y deja la compra otra vez pendiente.
create or replace function public.unpay_credit_purchase(p_purchase_id uuid)
returns void
language plpgsql
set search_path = ''
as $$
declare
  v_purchase public.credit_purchases%rowtype;
begin
  select * into v_purchase
    from public.credit_purchases
   where id = p_purchase_id
     and user_id = (select auth.uid())
     and deleted_at is null
   for update;

  if not found then
    raise exception 'Compra no encontrada';
  end if;
  if v_purchase.status <> 'paid' then
    raise exception 'La compra no está pagada';
  end if;

  update public.credit_purchases
     set status = 'pending', paid_on = null, paid_account_id = null,
         paid_amount = null, transaction_id = null
   where id = p_purchase_id;

  update public.transactions
     set deleted_at = now()
   where id = v_purchase.transaction_id and user_id = v_purchase.user_id;
end;
$$;

revoke execute on function public.pay_credit_purchase(uuid, uuid, numeric, date) from public, anon;
revoke execute on function public.unpay_credit_purchase(uuid) from public, anon;
grant execute on function public.pay_credit_purchase(uuid, uuid, numeric, date) to authenticated;
grant execute on function public.unpay_credit_purchase(uuid) to authenticated;
```

## 5. Cálculos (se implementan en `domain`, con pruebas unitarias)

**Saldo de una cuenta** = `initial_balance` + ingresos − egresos + transferencias entrantes − transferencias salientes (sin borrados lógicos). Lo entrega `account_balances`.

**Ahorro total** (en la moneda de visualización `D`):
```
total = Σ convertir(saldo_i, moneda_i → D)   para cuentas con include_in_savings = true,
                                              is_archived = false
convertir(x, A → A) = x
convertir(x, A → B) = x × rate(A→B)          si existe rate(A→B)
                    = x ÷ rate(B→A)          si solo existe rate(B→A)
redondeo final: HALF_UP a 2 decimales (se redondea una sola vez, al sumar)
```
El tipo de cambio vigente es la fila más reciente de `exchange_rates` del usuario para el par (ordenar por `rate_date desc, created_at desc`). Si no hay ninguna, la app pide configurarlo en Ajustes antes de mostrar conversiones.

**Transferencia entre monedas** (tres campos enlazados: `out`, `rate`, `in`):
- Cambia `out` o `rate` → `in = round(out × rate, 2)`.
- Cambia `in` → `rate = in ÷ out` (guardar con 6 decimales; mostrar 2–4).
- Se persisten los tres: `from_amount`, `to_amount`, `exchange_rate`.
- Misma moneda: `rate` no aplica (`null`), `to_amount = from_amount`.

**Pagar compra de crédito**: `pay_credit_purchase(purchase, cuenta, monto_real, fecha)`.
- Si la moneda de la compra ≠ moneda de la cuenta, el monto real lo escribe el usuario (el banco aplica su propio cambio).
- Si coinciden, se sugiere el monto de la compra.

## 6. Consultas que usa la app (PostgREST)
| Necesidad | Cómo |
|---|---|
| Saldos | `from("account_balances").select()` |
| Últimos movimientos | `from("transactions").select("*, category:categories(name,color), account:accounts(name,currency)")` con `deleted_at is null`, `order occurred_on desc, created_at desc`, `range(0, 19)` |
| Transferencias | `from("transfers").select("*, from:accounts!transfers_from_account_fk(name,currency), to:accounts!transfers_to_account_fk(name,currency)")` |
| Reporte | `rpc("report_period_summary", {p_from, p_to})` y `rpc("report_by_category", {p_kind, p_from, p_to})` |
| Pagar crédito | `rpc("pay_credit_purchase", {...})` |
| Borrado lógico | `update({deleted_at: now}) where id = ...` |
Las listas se combinan (movimientos + transferencias) y se ordenan por fecha en el cliente, con paginación.

## 7. Datos de ejemplo (usar en pruebas y previews)
Cambio manual: **1 US$ = S/ 3.20**.

| Cuenta | Moneda | Saldo | ¿Cuenta en ahorros? |
|---|---|---|---|
| Efectivo | PEN | 320.50 | sí |
| Débito principal | PEN | 1,245.80 | sí |
| Ahorros | PEN | 4,800.00 | sí |
| Cuenta Dólares | USD | 250.00 | sí (= S/ 800.00) |
| Billetera digital | PEN | 85.00 | **no** |

- **Ahorro total** = 320.50 + 1,245.80 + 4,800.00 + 800.00 = **S/ 7,166.30** = **US$ 2,239.47** (7,166.30 ÷ 3.20 = 2,239.46875 → redondeo HALF_UP).
- **Septiembre 2026**: ingresos S/ 4,200.00 (Sueldo 3,500.00 · Freelance 600.00 · Otros ingresos 100.00); gastos S/ 2,148.60 (Vivienda 800.00 · Alimentación 612.40 · Transporte 215.20 · Ocio 190.00 · Otros 183.00 · Salud 148.00); balance **+S/ 2,051.40**.
- **Transferencia**: Cuenta Dólares → Débito principal, US$ 20.00 a 3.20 = **S/ 64.00**. Si el banco da S/ 63.40, el cambio queda 3.17.
- **Crédito pendiente**: Pasajes S/ 240.00 (vence 5 oct), Cena S/ 96.00 y Audífonos S/ 189.90 (vencen 15 oct) → S/ 525.90; Suscripción US$ 12.00 (15 oct).
- **Pagar Pasajes** desde Débito principal por S/ 240.00: saldo 1,245.80 → **1,005.80**.

## 8. Pruebas de seguridad (RLS)
Crear dos usuarios (A y B) y ejecutar en el SQL Editor (cada bloque simula a un usuario):
```sql
begin;
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"<UUID_DE_A>","role":"authenticated"}', true);
select count(*) from public.accounts;                      -- solo las cuentas de A
insert into public.accounts (user_id, name) values ('<UUID_DE_B>', 'intruso');  -- debe FALLAR (RLS)
rollback;
```
Esperado: A no ve filas de B, no puede insertar con el `user_id` de B ni apuntar movimientos a cuentas de B (falla la clave foránea compuesta), y `anon` no puede leer nada.
