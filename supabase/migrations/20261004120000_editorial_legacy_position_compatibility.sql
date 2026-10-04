begin;

-- Existing staff drafts may use the original 0..1000 position space. The
-- authoritative RPC still restricts new or edited cards to their supported
-- Home/Explore ranges, while preserving legacy rows for cumulative migrations
-- and read-only archival workflows.
alter table public.editorial_shelves
  drop constraint if exists editorial_shelves_supported_placement;

comment on table public.editorial_shelves is
  'Ordered Rakyzu Music programming. Legacy positions remain readable; all new staff mutations use governed placement ranges.';

commit;
