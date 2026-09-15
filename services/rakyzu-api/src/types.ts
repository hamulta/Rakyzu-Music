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
  | "admin_apply_content_enforcement"
  | "admin_assign_staff"
  | "admin_assign_staff_by_email"
  | "admin_assign_catalog_team_by_email"
  | "admin_create_album"
  | "admin_create_artist"
  | "admin_create_catalog_label"
  | "admin_create_moderation_case"
  | "admin_create_track"
  | "admin_decide_catalog_review"
  | "admin_export_audit"
  | "admin_governance_dashboard"
  | "admin_list_catalog_drafts"
  | "admin_list_moderation_cases"
  | "admin_list_staff"
  | "admin_link_catalog_label_artist"
  | "admin_moderate_case"
  | "admin_publish_album"
  | "admin_record_album_artwork"
  | "admin_record_track_media"
  | "admin_schedule_album"
  | "admin_set_audit_retention"
  | "admin_submit_catalog_review";

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
