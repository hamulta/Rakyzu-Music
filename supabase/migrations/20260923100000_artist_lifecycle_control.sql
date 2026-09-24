begin;

-- 0.5.27-0.5.30: scoped analytics, commerce, account lifecycle, and security controls.
insert into public.staff_role_permissions (role, permission) values
  ('supervisor', 'account.enforce'),
  ('supervisor', 'appeal.review'),
  ('manager', 'analytics.view'),
  ('manager', 'analytics.export'),
  ('manager', 'commerce.view'),
  ('manager', 'account.enforce'),
  ('manager', 'appeal.review'),
  ('manager', 'data.export'),
  ('c_level_executive', 'analytics.view'),
  ('c_level_executive', 'analytics.export'),
  ('c_level_executive', 'commerce.view'),
  ('c_level_executive', 'account.enforce'),
  ('c_level_executive', 'appeal.review'),
  ('c_level_executive', 'data.export'),
  ('c_level_executive', 'deletion.approve'),
  ('c_level_executive', 'security.manage'),
  ('ceo', 'analytics.view'),
  ('ceo', 'analytics.export'),
  ('ceo', 'commerce.view'),
  ('ceo', 'account.enforce'),
  ('ceo', 'appeal.review'),
  ('ceo', 'data.export'),
  ('ceo', 'deletion.approve'),
  ('ceo', 'security.manage')
on conflict do nothing;

create table public.trusted_play_events (
  id uuid primary key default gen_random_uuid(),
  source_event_id text not null unique,
  user_id uuid references auth.users(id) on delete set null,
  track_id uuid not null references public.tracks(id) on delete restrict,
  artist_id uuid not null references public.artists(id) on delete restrict,
  occurred_at timestamptz not null,
  listening_ms integer not null,
  completed boolean not null,
  country_code text,
  received_at timestamptz not null default now(),
  constraint trusted_play_source_id check (char_length(source_event_id) between 12 and 160),
  constraint trusted_play_listening_ms check (listening_ms between 10000 and 86400000),
  constraint trusted_play_country check (country_code is null or country_code ~ '^[A-Z]{2}$'),
  constraint trusted_play_clock check (occurred_at between now() - interval '31 days' and now() + interval '5 minutes')
);
create index trusted_play_artist_time_idx on public.trusted_play_events (artist_id, occurred_at desc);
create index trusted_play_track_time_idx on public.trusted_play_events (track_id, occurred_at desc);

create table public.commerce_event_ledger (
  id uuid primary key default gen_random_uuid(),
  provider text not null,
  provider_event_id text not null,
  event_type text not null,
  user_id uuid references auth.users(id) on delete set null,
  external_customer_ref text,
  product_ref text,
  amount_minor bigint,
  currency text,
  occurred_at timestamptz not null,
  received_at timestamptz not null default now(),
  payload_sha256 text not null,
  payload jsonb not null default '{}'::jsonb,
  unique(provider, provider_event_id),
  constraint commerce_provider check (provider ~ '^[a-z][a-z0-9_]{1,31}$'),
  constraint commerce_event_type check (event_type in ('subscription_started','subscription_renewed','subscription_canceled','purchase_completed','refund_completed','dispute_opened','dispute_won','dispute_lost')),
  constraint commerce_amount check (amount_minor is null or amount_minor >= 0),
  constraint commerce_currency check (currency is null or currency ~ '^[A-Z]{3}$'),
  constraint commerce_hash check (payload_sha256 ~ '^[a-f0-9]{64}$'),
  constraint commerce_payload_object check (jsonb_typeof(payload) = 'object')
);

create table public.commerce_entitlements (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  provider text not null,
  product_ref text not null,
  status text not null,
  valid_from timestamptz not null,
  valid_until timestamptz,
  source_event_id uuid not null references public.commerce_event_ledger(id) on delete restrict,
  updated_at timestamptz not null default now(),
  unique(user_id, provider, product_ref),
  constraint commerce_entitlement_status check (status in ('active','canceled','refunded','disputed','expired')),
  constraint commerce_entitlement_window check (valid_until is null or valid_until >= valid_from)
);

create table public.commerce_receipts (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  ledger_event_id uuid not null unique references public.commerce_event_ledger(id) on delete restrict,
  receipt_reference text not null,
  amount_minor bigint not null,
  currency text not null,
  status text not null,
  created_at timestamptz not null default now(),
  constraint commerce_receipt_status check (status in ('paid','refunded','disputed')),
  constraint commerce_receipt_amount check (amount_minor >= 0),
  constraint commerce_receipt_currency check (currency ~ '^[A-Z]{3}$'),
  constraint commerce_receipt_reference check (char_length(receipt_reference) between 6 and 160)
);

create table public.account_enforcement_events (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete restrict,
  action text not null,
  reason text not null,
  expires_at timestamptz,
  actor_id uuid not null references auth.users(id) on delete restrict,
  created_at timestamptz not null default now(),
  constraint account_enforcement_action check (action in ('suspend','ban','reinstate')),
  constraint account_enforcement_reason check (char_length(btrim(reason)) between 12 and 1000),
  constraint account_enforcement_expiry check ((action = 'suspend' and expires_at > created_at) or (action <> 'suspend' and expires_at is null))
);
create index account_enforcement_user_time_idx on public.account_enforcement_events(user_id, created_at desc, id desc);

create table public.account_appeals (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete restrict,
  enforcement_id uuid not null references public.account_enforcement_events(id) on delete restrict,
  statement text not null,
  status text not null default 'pending',
  decision_notes text,
  decided_by uuid references auth.users(id) on delete restrict,
  created_at timestamptz not null default now(),
  decided_at timestamptz,
  unique(user_id, enforcement_id),
  constraint account_appeal_statement check (char_length(btrim(statement)) between 20 and 2000),
  constraint account_appeal_status check (status in ('pending','accepted','rejected')),
  constraint account_appeal_decision check ((status = 'pending' and decided_by is null and decided_at is null) or (status <> 'pending' and decided_by is not null and decided_at is not null and char_length(btrim(decision_notes)) between 12 and 1000))
);

create table public.account_deletion_requests (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete restrict,
  reason text not null,
  status text not null default 'pending',
  first_approved_by uuid references auth.users(id) on delete restrict,
  first_approved_at timestamptz,
  second_approved_by uuid references auth.users(id) on delete restrict,
  second_approved_at timestamptz,
  execute_after timestamptz,
  created_at timestamptz not null default now(),
  canceled_at timestamptz,
  constraint deletion_request_reason check (char_length(btrim(reason)) between 12 and 1000),
  constraint deletion_request_status check (status in ('pending','first_approved','approved','canceled','completed')),
  constraint deletion_distinct_approvers check (second_approved_by is null or second_approved_by <> first_approved_by)
);
create unique index account_deletion_one_open_idx on public.account_deletion_requests(user_id) where status in ('pending','first_approved','approved');

create table public.security_alerts (
  id uuid primary key default gen_random_uuid(),
  severity text not null,
  category text not null,
  summary text not null,
  details jsonb not null default '{}'::jsonb,
  status text not null default 'open',
  acknowledged_by uuid references auth.users(id) on delete set null,
  acknowledged_at timestamptz,
  created_at timestamptz not null default now(),
  constraint security_alert_severity check (severity in ('info','warning','critical')),
  constraint security_alert_category check (category ~ '^[a-z][a-z0-9_.-]{2,63}$'),
  constraint security_alert_summary check (char_length(btrim(summary)) between 8 and 240),
  constraint security_alert_status check (status in ('open','acknowledged','resolved')),
  constraint security_alert_details check (jsonb_typeof(details) = 'object')
);

create table public.privileged_action_requests (
  id uuid primary key default gen_random_uuid(),
  action_type text not null,
  target_type text not null,
  target_id uuid not null,
  reason text not null,
  payload jsonb not null default '{}'::jsonb,
  requested_by uuid not null references auth.users(id) on delete restrict,
  approved_by uuid references auth.users(id) on delete restrict,
  status text not null default 'pending',
  created_at timestamptz not null default now(),
  decided_at timestamptz,
  expires_at timestamptz not null default (now() + interval '24 hours'),
  constraint privileged_action_type check (action_type in ('account.delete','catalog.permanent_delete','audit.retention_reduce')),
  constraint privileged_action_reason check (char_length(btrim(reason)) between 12 and 1000),
  constraint privileged_action_status check (status in ('pending','approved','rejected','expired','executed')),
  constraint privileged_action_separation check (approved_by is null or approved_by <> requested_by),
  constraint privileged_action_payload check (jsonb_typeof(payload) = 'object')
);

alter table public.trusted_play_events enable row level security;
alter table public.trusted_play_events force row level security;
alter table public.commerce_event_ledger enable row level security;
alter table public.commerce_event_ledger force row level security;
alter table public.commerce_entitlements enable row level security;
alter table public.commerce_entitlements force row level security;
alter table public.commerce_receipts enable row level security;
alter table public.commerce_receipts force row level security;
alter table public.account_enforcement_events enable row level security;
alter table public.account_enforcement_events force row level security;
alter table public.account_appeals enable row level security;
alter table public.account_appeals force row level security;
alter table public.account_deletion_requests enable row level security;
alter table public.account_deletion_requests force row level security;
alter table public.security_alerts enable row level security;
alter table public.security_alerts force row level security;
alter table public.privileged_action_requests enable row level security;
alter table public.privileged_action_requests force row level security;

revoke all on public.trusted_play_events, public.commerce_event_ledger,
  public.commerce_entitlements, public.commerce_receipts, public.account_enforcement_events,
  public.account_appeals, public.account_deletion_requests, public.security_alerts,
  public.privileged_action_requests from anon, authenticated;
grant all on public.trusted_play_events, public.commerce_event_ledger,
  public.commerce_entitlements, public.commerce_receipts, public.account_enforcement_events,
  public.account_appeals, public.account_deletion_requests, public.security_alerts,
  public.privileged_action_requests to service_role;
grant select on public.commerce_entitlements, public.commerce_receipts,
  public.account_enforcement_events, public.account_appeals,
  public.account_deletion_requests, public.security_alerts,
  public.privileged_action_requests to authenticated;

create policy commerce_entitlement_owner_select on public.commerce_entitlements
for select to authenticated using (user_id = (select auth.uid()));
create policy commerce_receipt_owner_select on public.commerce_receipts
for select to authenticated using (user_id = (select auth.uid()));
create policy account_enforcement_owner_select on public.account_enforcement_events
for select to authenticated using (user_id = (select auth.uid()) or (select private.has_staff_permission('account.enforce')));
create policy account_appeal_owner_select on public.account_appeals
for select to authenticated using (user_id = (select auth.uid()) or (select private.has_staff_permission('appeal.review')));
create policy account_deletion_owner_select on public.account_deletion_requests
for select to authenticated using (user_id = (select auth.uid()) or (select private.has_staff_permission('deletion.approve')));
create policy security_alert_staff_select on public.security_alerts
for select to authenticated using ((select private.has_staff_permission('security.manage')));
create policy privileged_action_staff_select on public.privileged_action_requests
for select to authenticated using ((select private.has_staff_permission('security.manage')));

create or replace function private.current_account_state(target_user_id uuid)
returns text language sql stable security definer set search_path = '' as $$
  select coalesce((
    select case
      when event.action = 'reinstate' then 'active'
      when event.action = 'suspend' and event.expires_at <= now() then 'active'
      when event.action = 'ban' then 'banned'
      else 'suspended'
    end
    from public.account_enforcement_events event
    where event.user_id = target_user_id
    order by event.created_at desc, event.id desc limit 1
  ), 'active')
$$;
revoke all on function private.current_account_state(uuid) from public, anon, authenticated;
grant execute on function private.current_account_state(uuid) to authenticated;

create or replace function private.resolve_artist_link_for_current_user()
returns public.artist_account_links language plpgsql security definer set search_path = '' as $$
declare actor_id uuid := (select auth.uid()); actor_email text; link public.artist_account_links;
begin
  if actor_id is null or private.current_account_state(actor_id) <> 'active' then
    return null;
  end if;
  select lower(email) into actor_email from auth.users where id = actor_id;
  update public.artist_account_links set user_id = actor_id
  where user_id is null and status = 'pending_consent' and invited_email = actor_email;
  select * into link from public.artist_account_links
    where user_id = actor_id and invited_email = actor_email and status <> 'revoked';
  return link;
end;
$$;
revoke all on function private.resolve_artist_link_for_current_user()
  from public, anon, authenticated;

create or replace function private.current_staff_role()
returns text language sql stable security definer set search_path = '' as $$
  select assignment.role
  from public.staff_assignments assignment
  where assignment.user_id = (select auth.uid()) and assignment.active
    and private.current_account_state((select auth.uid())) = 'active'
$$;

create or replace function private.has_staff_permission(requested_permission text)
returns boolean language sql stable security definer set search_path = '' as $$
  select private.current_account_state((select auth.uid())) = 'active' and exists (
    select 1 from public.staff_assignments assignment
    join public.staff_role_permissions permission on permission.role = assignment.role
    where assignment.user_id = (select auth.uid()) and assignment.active
      and permission.permission = requested_permission
  )
$$;

create or replace function private.artist_workspace_owner(target_artist_id uuid)
returns boolean language sql stable security definer set search_path = '' as $$
  select private.current_account_state((select auth.uid())) = 'active' and exists (
    select 1 from public.artist_account_links link
    where link.artist_id = target_artist_id and link.user_id = (select auth.uid())
      and link.status = 'active'
  )
$$;

create or replace function private.artist_workspace_editor(target_artist_id uuid)
returns boolean language sql stable security definer set search_path = '' as $$
  select private.current_account_state((select auth.uid())) = 'active' and (
    private.artist_workspace_owner(target_artist_id) or (
      exists (select 1 from public.artist_account_links link
        where link.artist_id = target_artist_id and link.status = 'active') and exists (
      select 1 from public.catalog_team_memberships membership
      where membership.scope_type = 'artist' and membership.scope_id = target_artist_id
        and membership.user_id = (select auth.uid()) and membership.active
        and membership.access_level in ('editor', 'admin')
    ))
  )
$$;

create or replace function public.account_lifecycle_context()
returns jsonb language plpgsql stable security definer set search_path = '' as $$
declare current_user_id uuid := (select auth.uid()); latest public.account_enforcement_events;
begin
  if current_user_id is null then raise insufficient_privilege using message = 'Authentication required'; end if;
  select event.* into latest from public.account_enforcement_events event
    where event.user_id = current_user_id
    order by event.created_at desc, event.id desc limit 1;
  return jsonb_build_object(
    'state', private.current_account_state(current_user_id),
    'reason', case when latest.action in ('suspend','ban') then latest.reason else null end,
    'expiresAt', latest.expires_at,
    'appeals', coalesce((select jsonb_agg(jsonb_build_object('id', id, 'status', status,
      'statement', statement, 'decisionNotes', decision_notes, 'createdAt', created_at)
      order by appeal.created_at desc) from public.account_appeals appeal
      where appeal.user_id = current_user_id), '[]'::jsonb),
    'deletionRequests', coalesce((select jsonb_agg(jsonb_build_object('id', id, 'status', status,
      'executeAfter', execute_after, 'createdAt', created_at) order by created_at desc)
      from public.account_deletion_requests deletion
      where deletion.user_id = current_user_id), '[]'::jsonb)
  );
end;
$$;

create or replace function public.artist_analytics(
  target_artist_id uuid, range_start date, range_end date
) returns jsonb language plpgsql stable security definer set search_path = '' as $$
declare allowed boolean; total_streams bigint; unique_listeners bigint; completed_streams bigint;
begin
  if range_start is null or range_end is null or range_end < range_start or range_end - range_start > 366 then
    raise invalid_parameter_value using message = 'Analytics range must span 1 to 367 days';
  end if;
  allowed := private.artist_workspace_owner(target_artist_id)
    or private.artist_workspace_editor(target_artist_id)
    or private.has_staff_permission('analytics.view');
  if not allowed then raise insufficient_privilege using message = 'Analytics access denied'; end if;
  select count(*), count(distinct user_id), count(*) filter (where completed)
    into total_streams, unique_listeners, completed_streams
  from public.trusted_play_events
  where artist_id = target_artist_id and occurred_at >= range_start::timestamptz
    and occurred_at < (range_end + 1)::timestamptz;
  return jsonb_build_object(
    'artistId', target_artist_id, 'rangeStart', range_start, 'rangeEnd', range_end,
    'streams', total_streams,
    'listeners', case when unique_listeners >= 5 then unique_listeners else null end,
    'privacyThresholdMet', unique_listeners >= 5,
    'completedStreams', completed_streams,
    'followers', (select count(*) from public.followed_artists where artist_id = target_artist_id),
    'releases', (select count(*) from public.albums where artist_id = target_artist_id and is_published),
    'geography', coalesce((select jsonb_agg(row_data order by (row_data->>'streams')::bigint desc)
      from (select jsonb_build_object('country', country_code, 'streams', count(*),
        'listeners', count(distinct user_id)) row_data
        from public.trusted_play_events where artist_id = target_artist_id
          and occurred_at >= range_start::timestamptz and occurred_at < (range_end + 1)::timestamptz
          and country_code is not null group by country_code
        having count(distinct user_id) >= 5) countries), '[]'::jsonb)
  );
end;
$$;

create or replace function public.admin_commerce_dashboard()
returns jsonb language plpgsql stable security definer set search_path = '' as $$
begin
  if not private.has_staff_permission('commerce.view') then
    raise insufficient_privilege using message = 'Commerce access denied';
  end if;
  return jsonb_build_object(
    'activeEntitlements', (select count(*) from public.commerce_entitlements where status = 'active' and (valid_until is null or valid_until > now())),
    'grossMinor30d', (select coalesce(sum(amount_minor), 0) from public.commerce_event_ledger where event_type in ('subscription_started','subscription_renewed','purchase_completed') and received_at > now() - interval '30 days'),
    'refunds30d', (select count(*) from public.commerce_event_ledger where event_type = 'refund_completed' and received_at > now() - interval '30 days'),
    'disputes30d', (select count(*) from public.commerce_event_ledger where event_type = 'dispute_opened' and received_at > now() - interval '30 days'),
    'recent', coalesce((select jsonb_agg(item) from (select jsonb_build_object(
      'id', id, 'provider', provider, 'type', event_type, 'product', product_ref,
      'amountMinor', amount_minor, 'currency', currency, 'occurredAt', occurred_at,
      'userReference', case when user_id is null then null else left(user_id::text, 8) end
    ) item from public.commerce_event_ledger order by occurred_at desc limit 50) events), '[]'::jsonb)
  );
end;
$$;

create or replace function public.admin_account_dashboard()
returns jsonb language plpgsql stable security definer set search_path = '' as $$
begin
  if not (private.has_staff_permission('account.enforce') or private.has_staff_permission('appeal.review')) then
    raise insufficient_privilege using message = 'Account governance access denied';
  end if;
  return jsonb_build_object(
    'enforcements', coalesce((select jsonb_agg(item) from (select jsonb_build_object(
      'id', event.id, 'userId', event.user_id, 'action', event.action, 'reason', event.reason,
      'expiresAt', event.expires_at, 'createdAt', event.created_at
    ) item from public.account_enforcement_events event order by created_at desc limit 50) rows), '[]'::jsonb),
    'appeals', coalesce((select jsonb_agg(item) from (select jsonb_build_object(
      'id', appeal.id, 'userId', appeal.user_id, 'status', appeal.status,
      'statement', appeal.statement, 'createdAt', appeal.created_at
    ) item from public.account_appeals appeal order by created_at desc limit 50) rows), '[]'::jsonb),
    'deletions', coalesce((select jsonb_agg(item) from (select jsonb_build_object(
      'id', request.id, 'userId', request.user_id, 'status', request.status,
      'executeAfter', request.execute_after, 'createdAt', request.created_at
    ) item from public.account_deletion_requests request order by created_at desc limit 50) rows), '[]'::jsonb)
  );
end;
$$;

create or replace function public.admin_security_dashboard()
returns jsonb language plpgsql stable security definer set search_path = '' as $$
begin
  if not private.has_staff_permission('security.manage') then
    raise insufficient_privilege using message = 'Security access denied';
  end if;
  return jsonb_build_object(
    'alerts', coalesce((select jsonb_agg(item) from (select jsonb_build_object('id', id,
      'severity', severity, 'category', category, 'summary', summary, 'status', status,
      'createdAt', created_at) item from public.security_alerts order by created_at desc limit 50) rows), '[]'::jsonb),
    'approvals', coalesce((select jsonb_agg(item) from (select jsonb_build_object('id', id,
      'actionType', action_type, 'targetType', target_type, 'targetId', target_id,
      'reason', reason, 'status', status, 'expiresAt', expires_at, 'createdAt', created_at)
      item from public.privileged_action_requests order by created_at desc limit 50) rows), '[]'::jsonb)
  );
end;
$$;

create or replace function public.account_submit_appeal(target_enforcement_id uuid, requested_statement text)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare current_user_id uuid := (select auth.uid()); saved public.account_appeals;
begin
  if current_user_id is null then raise insufficient_privilege using message = 'Authentication required'; end if;
  if char_length(btrim(coalesce(requested_statement, ''))) not between 20 and 2000 then
    raise invalid_parameter_value using message = 'Appeal statement must contain 20 to 2000 characters';
  end if;
  if not exists (select 1 from public.account_enforcement_events event
    where event.id = target_enforcement_id and event.user_id = current_user_id
      and event.action in ('suspend','ban')) then
    raise invalid_parameter_value using message = 'Enforcement is unavailable';
  end if;
  insert into public.account_appeals(user_id, enforcement_id, statement)
    values (current_user_id, target_enforcement_id, btrim(requested_statement)) returning * into saved;
  return jsonb_build_object('id', saved.id, 'status', saved.status);
end;
$$;

create or replace function public.account_request_deletion(requested_reason text)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare current_user_id uuid := (select auth.uid()); saved public.account_deletion_requests;
begin
  if current_user_id is null then raise insufficient_privilege using message = 'Authentication required'; end if;
  insert into public.account_deletion_requests(user_id, reason)
    values (current_user_id, btrim(requested_reason)) returning * into saved;
  return jsonb_build_object('id', saved.id, 'status', saved.status);
end;
$$;

create or replace function public.admin_enforce_account(
  target_user_id uuid, requested_action text, requested_reason text, requested_expires_at timestamptz default null
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare saved public.account_enforcement_events; target_is_staff boolean;
begin
  if not private.has_staff_permission('account.enforce') then raise insufficient_privilege using message = 'Account enforcement denied'; end if;
  if target_user_id = (select auth.uid()) then
    raise invalid_parameter_value using message = 'Self-enforcement is not allowed';
  end if;
  select exists(select 1 from public.staff_assignments where user_id = target_user_id and active) into target_is_staff;
  if target_is_staff and private.current_staff_role() <> 'ceo' then
    raise insufficient_privilege using message = 'Only CEO may enforce a staff account';
  end if;
  insert into public.account_enforcement_events(user_id, action, reason, expires_at, actor_id)
    values (target_user_id, requested_action, btrim(requested_reason), requested_expires_at, (select auth.uid())) returning * into saved;
  if requested_action = 'ban' then
    insert into public.security_alerts(severity, category, summary, details)
    values ('warning', 'account.ban', 'A permanent account ban was recorded',
      jsonb_build_object('userId', target_user_id, 'enforcementId', saved.id));
  end if;
  perform private.write_staff_audit('account.' || requested_action, 'user', target_user_id,
    jsonb_build_object('reason', requested_reason, 'expiresAt', requested_expires_at));
  return jsonb_build_object('id', saved.id, 'state', private.current_account_state(target_user_id));
end;
$$;

create or replace function public.admin_decide_appeal(target_appeal_id uuid, decision text, requested_notes text)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare appeal public.account_appeals; target_is_staff boolean;
begin
  if not private.has_staff_permission('appeal.review') then raise insufficient_privilege using message = 'Appeal review denied'; end if;
  if decision not in ('accepted','rejected') then raise invalid_parameter_value using message = 'Invalid appeal decision'; end if;
  select item.* into appeal from public.account_appeals item
    where item.id = target_appeal_id and item.status = 'pending' for update;
  if appeal.id is null then raise invalid_parameter_value using message = 'Pending appeal not found'; end if;
  select exists(select 1 from public.staff_assignments assignment
    where assignment.user_id = appeal.user_id and assignment.active) into target_is_staff;
  if target_is_staff and private.current_staff_role() <> 'ceo' then
    raise insufficient_privilege using message = 'Only CEO may review a staff account appeal';
  end if;
  update public.account_appeals set status = decision, decision_notes = btrim(requested_notes),
    decided_by = (select auth.uid()), decided_at = now()
    where id = target_appeal_id returning * into appeal;
  if decision = 'accepted' then
    insert into public.account_enforcement_events(user_id, action, reason, actor_id)
      values (appeal.user_id, 'reinstate', 'Appeal accepted: ' || btrim(requested_notes), (select auth.uid()));
  end if;
  perform private.write_staff_audit('account.appeal_' || decision, 'account_appeal', appeal.id,
    jsonb_build_object('userId', appeal.user_id));
  return jsonb_build_object('id', appeal.id, 'status', decision);
end;
$$;

create or replace function public.admin_approve_deletion(target_request_id uuid)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare saved public.account_deletion_requests; actor_id uuid := (select auth.uid());
  target_is_staff boolean;
begin
  if not private.has_staff_permission('deletion.approve') then raise insufficient_privilege using message = 'Deletion approval denied'; end if;
  select * into saved from public.account_deletion_requests where id = target_request_id for update;
  if saved.id is null then raise invalid_parameter_value using message = 'Deletion request not found'; end if;
  if saved.user_id = actor_id then
    raise invalid_parameter_value using message = 'Account owners cannot approve their own deletion';
  end if;
  select exists(select 1 from public.staff_assignments assignment
    where assignment.user_id = saved.user_id and assignment.active) into target_is_staff;
  if target_is_staff and private.current_staff_role() <> 'ceo' then
    raise insufficient_privilege using message = 'Only CEO may approve a staff account deletion';
  end if;
  if saved.status = 'pending' then
    update public.account_deletion_requests set status = 'first_approved', first_approved_by = actor_id,
      first_approved_at = now() where id = target_request_id returning * into saved;
  elsif saved.status = 'first_approved' and saved.first_approved_by <> actor_id then
    update public.account_deletion_requests set status = 'approved', second_approved_by = actor_id,
      second_approved_at = now(), execute_after = now() + interval '14 days'
      where id = target_request_id returning * into saved;
    insert into public.privileged_action_requests(action_type, target_type, target_id, reason, payload, requested_by, approved_by, status, decided_at)
      values ('account.delete', 'user', saved.user_id, saved.reason,
        jsonb_build_object('deletionRequestId', saved.id), saved.first_approved_by, actor_id, 'approved', now());
    insert into public.security_alerts(severity, category, summary, details)
    values ('critical', 'account.deletion_approved', 'An account deletion completed dual approval',
      jsonb_build_object('userId', saved.user_id, 'deletionRequestId', saved.id,
        'executeAfter', saved.execute_after));
  else
    raise invalid_parameter_value using message = 'A distinct second approver is required';
  end if;
  perform private.write_staff_audit('account.deletion_approval', 'account_deletion', saved.id,
    jsonb_build_object('status', saved.status));
  return jsonb_build_object('id', saved.id, 'status', saved.status, 'executeAfter', saved.execute_after);
end;
$$;

create or replace function public.admin_acknowledge_security_alert(target_alert_id uuid, resolved boolean default false)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare saved public.security_alerts;
begin
  if not private.has_staff_permission('security.manage') then raise insufficient_privilege using message = 'Security management denied'; end if;
  update public.security_alerts set status = case when resolved then 'resolved' else 'acknowledged' end,
    acknowledged_by = (select auth.uid()), acknowledged_at = now()
    where id = target_alert_id and status <> 'resolved' returning * into saved;
  if saved.id is null then raise invalid_parameter_value using message = 'Open security alert not found'; end if;
  perform private.write_staff_audit('security.alert_' || saved.status, 'security_alert', saved.id, '{}'::jsonb);
  return jsonb_build_object('id', saved.id, 'status', saved.status);
end;
$$;

create or replace function public.admin_export_account_data(target_user_id uuid)
returns jsonb language plpgsql stable security definer set search_path = '' as $$
begin
  if target_user_id <> (select auth.uid()) and not private.has_staff_permission('data.export') then
    raise insufficient_privilege using message = 'Account export denied';
  end if;
  return jsonb_build_object(
    'profile', (select to_jsonb(profile) - 'avatar_object_key' from public.profiles profile where id = target_user_id),
    'library', jsonb_build_object(
      'likedTrackIds', coalesce((select jsonb_agg(track_id) from public.liked_tracks where user_id = target_user_id), '[]'::jsonb),
      'savedAlbumIds', coalesce((select jsonb_agg(album_id) from public.saved_albums where user_id = target_user_id), '[]'::jsonb),
      'followedArtistIds', coalesce((select jsonb_agg(artist_id) from public.followed_artists where user_id = target_user_id), '[]'::jsonb)),
    'commerce', jsonb_build_object(
      'entitlements', coalesce((select jsonb_agg(to_jsonb(item) - 'source_event_id') from public.commerce_entitlements item where user_id = target_user_id), '[]'::jsonb),
      'receipts', coalesce((select jsonb_agg(to_jsonb(item) - 'ledger_event_id') from public.commerce_receipts item where user_id = target_user_id), '[]'::jsonb)),
    'generatedAt', now()
  );
end;
$$;

-- Service-only ingestion RPCs. They are called by the signed Worker boundary with the managed service key.
create or replace function public.service_record_play_event(
  requested_source_event_id text, requested_user_id uuid, requested_track_id uuid,
  requested_occurred_at timestamptz, requested_listening_ms integer, requested_completed boolean,
  requested_country_code text default null
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare saved public.trusted_play_events; resolved_artist_id uuid;
begin
  select album.artist_id into resolved_artist_id from public.tracks track
    join public.albums album on album.id = track.album_id where track.id = requested_track_id;
  if resolved_artist_id is null then raise invalid_parameter_value using message = 'Track not found'; end if;
  insert into public.trusted_play_events(source_event_id, user_id, track_id, artist_id, occurred_at,
    listening_ms, completed, country_code)
  values (requested_source_event_id, requested_user_id, requested_track_id, resolved_artist_id,
    requested_occurred_at, requested_listening_ms, requested_completed, nullif(upper(requested_country_code), ''))
  on conflict (source_event_id) do nothing
  returning * into saved;
  if saved.id is null then
    select event.* into saved from public.trusted_play_events event
      where event.source_event_id = requested_source_event_id;
    return jsonb_build_object('id', saved.id, 'duplicate', true);
  end if;
  return jsonb_build_object('id', saved.id, 'duplicate', false);
end;
$$;

create or replace function public.service_ingest_commerce_event(
  requested_provider text, requested_event_id text, requested_event_type text,
  requested_user_id uuid, requested_customer_ref text, requested_product_ref text,
  requested_amount_minor bigint, requested_currency text, requested_occurred_at timestamptz,
  requested_payload_sha256 text, requested_payload jsonb
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare saved public.commerce_event_ledger; entitlement_status text;
begin
  insert into public.commerce_event_ledger(provider, provider_event_id, event_type, user_id,
    external_customer_ref, product_ref, amount_minor, currency, occurred_at, payload_sha256, payload)
  values (lower(requested_provider), requested_event_id, requested_event_type, requested_user_id,
    requested_customer_ref, requested_product_ref, requested_amount_minor, upper(requested_currency),
    requested_occurred_at, requested_payload_sha256, requested_payload)
  on conflict (provider, provider_event_id) do nothing
  returning * into saved;
  if saved.id is null then
    select event.* into saved from public.commerce_event_ledger event
      where event.provider = lower(requested_provider)
        and event.provider_event_id = requested_event_id;
    return jsonb_build_object('id', saved.id, 'accepted', true, 'duplicate', true);
  end if;
  if saved.user_id is not null and saved.product_ref is not null then
    entitlement_status := case saved.event_type
      when 'subscription_canceled' then 'canceled' when 'refund_completed' then 'refunded'
      when 'dispute_opened' then 'disputed' when 'dispute_lost' then 'refunded' else 'active' end;
    insert into public.commerce_entitlements(user_id, provider, product_ref, status, valid_from, source_event_id)
      values (saved.user_id, saved.provider, saved.product_ref, entitlement_status, saved.occurred_at, saved.id)
      on conflict (user_id, provider, product_ref) do update set status = excluded.status,
        source_event_id = excluded.source_event_id, updated_at = now();
    if saved.amount_minor is not null and saved.currency is not null and saved.event_type in
      ('subscription_started','subscription_renewed','purchase_completed','refund_completed','dispute_opened') then
      insert into public.commerce_receipts(user_id, ledger_event_id, receipt_reference, amount_minor, currency, status)
        values (saved.user_id, saved.id, saved.provider || ':' || saved.provider_event_id,
          saved.amount_minor, saved.currency, case saved.event_type when 'refund_completed' then 'refunded'
            when 'dispute_opened' then 'disputed' else 'paid' end)
        on conflict (ledger_event_id) do nothing;
    end if;
  end if;
  if saved.event_type = 'dispute_opened' then
    insert into public.security_alerts(severity, category, summary, details)
    values ('warning', 'commerce.dispute', 'A commerce dispute was opened',
      jsonb_build_object('ledgerEventId', saved.id, 'provider', saved.provider));
  end if;
  return jsonb_build_object('id', saved.id, 'accepted', true, 'duplicate', false);
end;
$$;

revoke all on function public.service_record_play_event(text, uuid, uuid, timestamptz, integer, boolean, text) from public, anon, authenticated;
revoke all on function public.service_ingest_commerce_event(text, text, text, uuid, text, text, bigint, text, timestamptz, text, jsonb) from public, anon, authenticated;
grant execute on function public.service_record_play_event(text, uuid, uuid, timestamptz, integer, boolean, text) to service_role;
grant execute on function public.service_ingest_commerce_event(text, text, text, uuid, text, text, bigint, text, timestamptz, text, jsonb) to service_role;

grant execute on function public.account_lifecycle_context() to authenticated;
grant execute on function public.artist_analytics(uuid, date, date) to authenticated;
grant execute on function public.admin_commerce_dashboard() to authenticated;
grant execute on function public.admin_account_dashboard() to authenticated;
grant execute on function public.admin_security_dashboard() to authenticated;
grant execute on function public.account_submit_appeal(uuid, text) to authenticated;
grant execute on function public.account_request_deletion(text) to authenticated;
grant execute on function public.admin_enforce_account(uuid, text, text, timestamptz) to authenticated;
grant execute on function public.admin_decide_appeal(uuid, text, text) to authenticated;
grant execute on function public.admin_approve_deletion(uuid) to authenticated;
grant execute on function public.admin_acknowledge_security_alert(uuid, boolean) to authenticated;
grant execute on function public.admin_export_account_data(uuid) to authenticated;

comment on table public.trusted_play_events is 'Server-ingested, idempotent listening evidence. Artist results suppress listener/geography groups below five distinct accounts.';
comment on table public.commerce_event_ledger is 'Append-only provider webhook evidence; raw payloads and customer references never leave the privileged boundary.';
comment on table public.account_deletion_requests is 'Retention-aware request workflow. Approval never performs immediate physical deletion; two distinct executives and a cooling period are required.';

commit;
