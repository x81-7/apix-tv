export type Json =
  | string
  | number
  | boolean
  | null
  | { [key: string]: Json | undefined }
  | Json[]

export type Database = {
  // Allows to automatically instantiate createClient with right options
  // instead of createClient<Database, { PostgrestVersion: 'XX' }>(URL, KEY)
  __InternalSupabase: {
    PostgrestVersion: "14.5"
  }
  public: {
    Tables: {
      app_notifications: {
        Row: {
          action: Json | null
          body: string
          created_at: string
          expires_at: string
          id: string
          title: string
        }
        Insert: {
          action?: Json | null
          body: string
          created_at?: string
          expires_at?: string
          id?: string
          title: string
        }
        Update: {
          action?: Json | null
          body?: string
          created_at?: string
          expires_at?: string
          id?: string
          title?: string
        }
        Relationships: []
      }
      app_users: {
        Row: {
          app_version: string | null
          ban_reason: string | null
          ban_until: string | null
          city: string | null
          country: string | null
          created_at: string
          custom_name: string | null
          device_id: string
          dex_checksum: string | null
          id: string
          install_count: number
          ip_address: string | null
          last_seen_at: string
          last_strike_at: string | null
          region: string | null
          signature_hash: string | null
          status: string
          strike_count: number
          updated_at: string
        }
        Insert: {
          app_version?: string | null
          ban_reason?: string | null
          ban_until?: string | null
          city?: string | null
          country?: string | null
          created_at?: string
          custom_name?: string | null
          device_id: string
          dex_checksum?: string | null
          id?: string
          install_count?: number
          ip_address?: string | null
          last_seen_at?: string
          last_strike_at?: string | null
          region?: string | null
          signature_hash?: string | null
          status?: string
          strike_count?: number
          updated_at?: string
        }
        Update: {
          app_version?: string | null
          ban_reason?: string | null
          ban_until?: string | null
          city?: string | null
          country?: string | null
          created_at?: string
          custom_name?: string | null
          device_id?: string
          dex_checksum?: string | null
          id?: string
          install_count?: number
          ip_address?: string | null
          last_seen_at?: string
          last_strike_at?: string | null
          region?: string | null
          signature_hash?: string | null
          status?: string
          strike_count?: number
          updated_at?: string
        }
        Relationships: []
      }
      backup_history: {
        Row: {
          created_at: string
          id: string
          notes: string | null
          size_bytes: number | null
          source: string
          storage_path: string | null
        }
        Insert: {
          created_at?: string
          id?: string
          notes?: string | null
          size_bytes?: number | null
          source: string
          storage_path?: string | null
        }
        Update: {
          created_at?: string
          id?: string
          notes?: string | null
          size_bytes?: number | null
          source?: string
          storage_path?: string | null
        }
        Relationships: []
      }
      ban_history: {
        Row: {
          ban_until: string | null
          created_at: string
          device_id: string
          id: string
          ip_address: string | null
          reason: string | null
          status: string
        }
        Insert: {
          ban_until?: string | null
          created_at?: string
          device_id: string
          id?: string
          ip_address?: string | null
          reason?: string | null
          status: string
        }
        Update: {
          ban_until?: string | null
          created_at?: string
          device_id?: string
          id?: string
          ip_address?: string | null
          reason?: string | null
          status?: string
        }
        Relationships: []
      }
      categories: {
        Row: {
          created_at: string
          hidden: boolean
          id: string
          legacy_id: string | null
          name: string
          sort_order: number
          updated_at: string
        }
        Insert: {
          created_at?: string
          hidden?: boolean
          id?: string
          legacy_id?: string | null
          name: string
          sort_order?: number
          updated_at?: string
        }
        Update: {
          created_at?: string
          hidden?: boolean
          id?: string
          legacy_id?: string | null
          name?: string
          sort_order?: number
          updated_at?: string
        }
        Relationships: []
      }
      channels: {
        Row: {
          action_type: string
          android_action_type: string | null
          android_stream: Json | null
          cache_version: number
          category_id: string | null
          created_at: string
          external_url: string | null
          hidden: boolean
          id: string
          image_url: string | null
          ios_action_type: string | null
          ios_player_type: string | null
          ios_stream: Json | null
          legacy_id: string | null
          name: string
          offline_cache_enabled: boolean
          pin_code: string | null
          preferred_player: string | null
          side_menu_id: string | null
          sort_order: number
          updated_at: string
          web_stream: Json | null
          windows_action_type: string | null
          windows_stream: Json | null
        }
        Insert: {
          action_type?: string
          android_action_type?: string | null
          android_stream?: Json | null
          cache_version?: number
          category_id?: string | null
          created_at?: string
          external_url?: string | null
          hidden?: boolean
          id?: string
          image_url?: string | null
          ios_action_type?: string | null
          ios_player_type?: string | null
          ios_stream?: Json | null
          legacy_id?: string | null
          name: string
          offline_cache_enabled?: boolean
          pin_code?: string | null
          preferred_player?: string | null
          side_menu_id?: string | null
          sort_order?: number
          updated_at?: string
          web_stream?: Json | null
          windows_action_type?: string | null
          windows_stream?: Json | null
        }
        Update: {
          action_type?: string
          android_action_type?: string | null
          android_stream?: Json | null
          cache_version?: number
          category_id?: string | null
          created_at?: string
          external_url?: string | null
          hidden?: boolean
          id?: string
          image_url?: string | null
          ios_action_type?: string | null
          ios_player_type?: string | null
          ios_stream?: Json | null
          legacy_id?: string | null
          name?: string
          offline_cache_enabled?: boolean
          pin_code?: string | null
          preferred_player?: string | null
          side_menu_id?: string | null
          sort_order?: number
          updated_at?: string
          web_stream?: Json | null
          windows_action_type?: string | null
          windows_stream?: Json | null
        }
        Relationships: [
          {
            foreignKeyName: "channels_category_id_fkey"
            columns: ["category_id"]
            isOneToOne: false
            referencedRelation: "categories"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "channels_side_menu_id_fkey"
            columns: ["side_menu_id"]
            isOneToOne: false
            referencedRelation: "side_menus"
            referencedColumns: ["id"]
          },
        ]
      }
      custom_ads: {
        Row: {
          created_at: string
          hidden: boolean
          id: string
          name: string
          sort_order: number
          updated_at: string
          video_url: string
        }
        Insert: {
          created_at?: string
          hidden?: boolean
          id?: string
          name: string
          sort_order?: number
          updated_at?: string
          video_url: string
        }
        Update: {
          created_at?: string
          hidden?: boolean
          id?: string
          name?: string
          sort_order?: number
          updated_at?: string
          video_url?: string
        }
        Relationships: []
      }
      encryption_keys: {
        Row: {
          activated_at: string | null
          algorithm: string
          created_at: string
          encrypted_key: string
          id: string
          is_active: boolean
          key_version: number
          rotated_at: string | null
        }
        Insert: {
          activated_at?: string | null
          algorithm?: string
          created_at?: string
          encrypted_key: string
          id?: string
          is_active?: boolean
          key_version: number
          rotated_at?: string | null
        }
        Update: {
          activated_at?: string | null
          algorithm?: string
          created_at?: string
          encrypted_key?: string
          id?: string
          is_active?: boolean
          key_version?: number
          rotated_at?: string | null
        }
        Relationships: []
      }
      integrity_logs: {
        Row: {
          created_at: string
          details: Json | null
          device_id: string
          dex_checksum: string | null
          id: string
          ip_address: string | null
          signature_hash: string | null
          threat_type: string
        }
        Insert: {
          created_at?: string
          details?: Json | null
          device_id: string
          dex_checksum?: string | null
          id?: string
          ip_address?: string | null
          signature_hash?: string | null
          threat_type: string
        }
        Update: {
          created_at?: string
          details?: Json | null
          device_id?: string
          dex_checksum?: string | null
          id?: string
          ip_address?: string | null
          signature_hash?: string | null
          threat_type?: string
        }
        Relationships: []
      }
      side_menus: {
        Row: {
          created_at: string
          id: string
          legacy_id: string | null
          name: string
          pin_code: string | null
          sort_order: number
          updated_at: string
        }
        Insert: {
          created_at?: string
          id?: string
          legacy_id?: string | null
          name: string
          pin_code?: string | null
          sort_order?: number
          updated_at?: string
        }
        Update: {
          created_at?: string
          id?: string
          legacy_id?: string | null
          name?: string
          pin_code?: string | null
          sort_order?: number
          updated_at?: string
        }
        Relationships: []
      }
      sub_channels: {
        Row: {
          android_action_type: string | null
          android_stream: Json | null
          cache_version: number
          created_at: string
          hidden: boolean
          id: string
          image_url: string | null
          ios_action_type: string | null
          ios_stream: Json | null
          legacy_id: string | null
          name: string
          offline_cache_enabled: boolean
          pin_code: string | null
          preferred_player: string | null
          side_menu_id: string
          sort_order: number
          updated_at: string
          web_stream: Json | null
          windows_action_type: string | null
          windows_stream: Json | null
        }
        Insert: {
          android_action_type?: string | null
          android_stream?: Json | null
          cache_version?: number
          created_at?: string
          hidden?: boolean
          id?: string
          image_url?: string | null
          ios_action_type?: string | null
          ios_stream?: Json | null
          legacy_id?: string | null
          name: string
          offline_cache_enabled?: boolean
          pin_code?: string | null
          preferred_player?: string | null
          side_menu_id: string
          sort_order?: number
          updated_at?: string
          web_stream?: Json | null
          windows_action_type?: string | null
          windows_stream?: Json | null
        }
        Update: {
          android_action_type?: string | null
          android_stream?: Json | null
          cache_version?: number
          created_at?: string
          hidden?: boolean
          id?: string
          image_url?: string | null
          ios_action_type?: string | null
          ios_stream?: Json | null
          legacy_id?: string | null
          name?: string
          offline_cache_enabled?: boolean
          pin_code?: string | null
          preferred_player?: string | null
          side_menu_id?: string
          sort_order?: number
          updated_at?: string
          web_stream?: Json | null
          windows_action_type?: string | null
          windows_stream?: Json | null
        }
        Relationships: [
          {
            foreignKeyName: "sub_channels_side_menu_id_fkey"
            columns: ["side_menu_id"]
            isOneToOne: false
            referencedRelation: "side_menus"
            referencedColumns: ["id"]
          },
        ]
      }
      system_settings: {
        Row: {
          created_at: string
          description: string | null
          id: string
          key: string
          updated_at: string
          value: Json | null
        }
        Insert: {
          created_at?: string
          description?: string | null
          id?: string
          key: string
          updated_at?: string
          value?: Json | null
        }
        Update: {
          created_at?: string
          description?: string | null
          id?: string
          key?: string
          updated_at?: string
          value?: Json | null
        }
        Relationships: []
      }
    }
    Views: {
      [_ in never]: never
    }
    Functions: {
      [_ in never]: never
    }
    Enums: {
      [_ in never]: never
    }
    CompositeTypes: {
      [_ in never]: never
    }
  }
}

type DatabaseWithoutInternals = Omit<Database, "__InternalSupabase">

type DefaultSchema = DatabaseWithoutInternals[Extract<keyof Database, "public">]

export type Tables<
  DefaultSchemaTableNameOrOptions extends
    | keyof (DefaultSchema["Tables"] & DefaultSchema["Views"])
    | { schema: keyof DatabaseWithoutInternals },
  TableName extends DefaultSchemaTableNameOrOptions extends {
    schema: keyof DatabaseWithoutInternals
  }
    ? keyof (DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"] &
        DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Views"])
    : never = never,
> = DefaultSchemaTableNameOrOptions extends {
  schema: keyof DatabaseWithoutInternals
}
  ? (DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"] &
      DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Views"])[TableName] extends {
      Row: infer R
    }
    ? R
    : never
  : DefaultSchemaTableNameOrOptions extends keyof (DefaultSchema["Tables"] &
        DefaultSchema["Views"])
    ? (DefaultSchema["Tables"] &
        DefaultSchema["Views"])[DefaultSchemaTableNameOrOptions] extends {
        Row: infer R
      }
      ? R
      : never
    : never

export type TablesInsert<
  DefaultSchemaTableNameOrOptions extends
    | keyof DefaultSchema["Tables"]
    | { schema: keyof DatabaseWithoutInternals },
  TableName extends DefaultSchemaTableNameOrOptions extends {
    schema: keyof DatabaseWithoutInternals
  }
    ? keyof DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"]
    : never = never,
> = DefaultSchemaTableNameOrOptions extends {
  schema: keyof DatabaseWithoutInternals
}
  ? DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"][TableName] extends {
      Insert: infer I
    }
    ? I
    : never
  : DefaultSchemaTableNameOrOptions extends keyof DefaultSchema["Tables"]
    ? DefaultSchema["Tables"][DefaultSchemaTableNameOrOptions] extends {
        Insert: infer I
      }
      ? I
      : never
    : never

export type TablesUpdate<
  DefaultSchemaTableNameOrOptions extends
    | keyof DefaultSchema["Tables"]
    | { schema: keyof DatabaseWithoutInternals },
  TableName extends DefaultSchemaTableNameOrOptions extends {
    schema: keyof DatabaseWithoutInternals
  }
    ? keyof DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"]
    : never = never,
> = DefaultSchemaTableNameOrOptions extends {
  schema: keyof DatabaseWithoutInternals
}
  ? DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"][TableName] extends {
      Update: infer U
    }
    ? U
    : never
  : DefaultSchemaTableNameOrOptions extends keyof DefaultSchema["Tables"]
    ? DefaultSchema["Tables"][DefaultSchemaTableNameOrOptions] extends {
        Update: infer U
      }
      ? U
      : never
    : never

export type Enums<
  DefaultSchemaEnumNameOrOptions extends
    | keyof DefaultSchema["Enums"]
    | { schema: keyof DatabaseWithoutInternals },
  EnumName extends DefaultSchemaEnumNameOrOptions extends {
    schema: keyof DatabaseWithoutInternals
  }
    ? keyof DatabaseWithoutInternals[DefaultSchemaEnumNameOrOptions["schema"]]["Enums"]
    : never = never,
> = DefaultSchemaEnumNameOrOptions extends {
  schema: keyof DatabaseWithoutInternals
}
  ? DatabaseWithoutInternals[DefaultSchemaEnumNameOrOptions["schema"]]["Enums"][EnumName]
  : DefaultSchemaEnumNameOrOptions extends keyof DefaultSchema["Enums"]
    ? DefaultSchema["Enums"][DefaultSchemaEnumNameOrOptions]
    : never

export type CompositeTypes<
  PublicCompositeTypeNameOrOptions extends
    | keyof DefaultSchema["CompositeTypes"]
    | { schema: keyof DatabaseWithoutInternals },
  CompositeTypeName extends PublicCompositeTypeNameOrOptions extends {
    schema: keyof DatabaseWithoutInternals
  }
    ? keyof DatabaseWithoutInternals[PublicCompositeTypeNameOrOptions["schema"]]["CompositeTypes"]
    : never = never,
> = PublicCompositeTypeNameOrOptions extends {
  schema: keyof DatabaseWithoutInternals
}
  ? DatabaseWithoutInternals[PublicCompositeTypeNameOrOptions["schema"]]["CompositeTypes"][CompositeTypeName]
  : PublicCompositeTypeNameOrOptions extends keyof DefaultSchema["CompositeTypes"]
    ? DefaultSchema["CompositeTypes"][PublicCompositeTypeNameOrOptions]
    : never

export const Constants = {
  public: {
    Enums: {},
  },
} as const
