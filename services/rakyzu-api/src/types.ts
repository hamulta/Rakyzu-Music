export interface RakyzuApiEnv {
  MEDIA: R2Bucket;
  ALLOWED_ORIGINS: string;
  SUPABASE_URL: string;
  SUPABASE_PUBLISHABLE_KEY: string;
}

export interface ListenerIdentity {
  userId: string;
}

export interface StaffContext {
  isStaff: boolean;
  role: string | null;
  displayRole: string | null;
  fullAccess: boolean;
  permissions: string[];
}

export type AdminRpcName =
  | "admin_assign_staff"
  | "admin_assign_staff_by_email"
  | "admin_create_album"
  | "admin_create_artist"
  | "admin_create_moderation_case"
  | "admin_create_track"
  | "admin_list_catalog_drafts"
  | "admin_list_moderation_cases"
  | "admin_list_staff"
  | "admin_moderate_case"
  | "admin_publish_album"
  | "admin_record_track_media";

export interface RequestDependencies {
  playlistAccess(
    id: string,
    token: string,
    env: RakyzuApiEnv,
  ): Promise<{ ownerId: string; canEdit: boolean } | null>;
  verifyListener(token: string, env: RakyzuApiEnv): Promise<ListenerIdentity>;
  canStreamTrack(trackId: string, token: string, env: RakyzuApiEnv): Promise<boolean>;
  canAccessAlbumArtwork(albumId: string, token: string, env: RakyzuApiEnv): Promise<boolean>;
  staffContext(token: string, env: RakyzuApiEnv): Promise<StaffContext>;
  adminRpc(
    name: AdminRpcName,
    payload: Record<string, unknown>,
    token: string,
    env: RakyzuApiEnv,
  ): Promise<unknown>;
}
