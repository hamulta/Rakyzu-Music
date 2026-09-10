export interface RakyzuApiEnv {
  MEDIA: R2Bucket;
  ALLOWED_ORIGINS: string;
  SUPABASE_URL: string;
  SUPABASE_PUBLISHABLE_KEY: string;
}

export interface ListenerIdentity {
  userId: string;
}

export interface RequestDependencies {
  playlistAccess(
    id: string,
    token: string,
    env: RakyzuApiEnv,
  ): Promise<{ ownerId: string; canEdit: boolean } | null>;
  verifyListener(token: string, env: RakyzuApiEnv): Promise<ListenerIdentity>;
  canStreamTrack(trackId: string, token: string, env: RakyzuApiEnv): Promise<boolean>;
  canAccessAlbumArtwork(albumId: string, token: string, env: RakyzuApiEnv): Promise<boolean>;
}
