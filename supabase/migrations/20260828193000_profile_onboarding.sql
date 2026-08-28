begin;

alter table public.profiles
add column onboarding_completed boolean not null default false;

comment on column public.profiles.onboarding_completed is
  'True after the profile owner completes the required Android onboarding step.';

grant update (onboarding_completed) on table public.profiles to authenticated;

commit;
