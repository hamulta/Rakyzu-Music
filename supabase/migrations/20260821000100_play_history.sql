-- Play history table: mencatat setiap kali user memutar lagu.
-- Digunakan untuk "Recently Played" di fase 0.4.x/0.5.x.

CREATE TABLE IF NOT EXISTS public.play_history (
  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  song_id UUID NOT NULL REFERENCES public.songs(id) ON DELETE CASCADE,
  played_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Index untuk query "recently played" per user.
CREATE INDEX IF NOT EXISTS idx_play_history_user_played
  ON public.play_history(user_id, played_at DESC);

-- RLS: user hanya bisa melihat history sendiri.
ALTER TABLE public.play_history ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can view own play history"
  ON public.play_history FOR SELECT
  USING (auth.uid() = user_id);

CREATE POLICY "Users can insert own play history"
  ON public.play_history FOR INSERT
  WITH CHECK (auth.uid() = user_id);
