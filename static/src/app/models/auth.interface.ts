/** Mirrors the authenticated user JSON from GET /api/private/auth/me */
export interface CurrentUser {
  username?: string;
  email?: string | null;
  id?: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}
