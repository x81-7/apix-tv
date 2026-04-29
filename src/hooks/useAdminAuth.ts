import { useState, useEffect } from 'react';
import { supabase } from '@/integrations/supabase/client';
import type { User } from '@supabase/supabase-js';

export interface AuthState {
  user: User | null;
  loading: boolean;
  error: string | null;
  isAuthorized: boolean;
}

export const useAdminAuth = () => {
  const [authState, setAuthState] = useState<AuthState>({
    user: null,
    loading: true,
    error: null,
    isAuthorized: false,
  });

  useEffect(() => {
    const { data: { subscription } } = supabase.auth.onAuthStateChange((_event, session) => {
      const user = session?.user ?? null;
      setAuthState({
        user,
        loading: false,
        error: null,
        isAuthorized: !!user,
      });
    });

    supabase.auth.getSession().then(({ data: { session } }) => {
      const user = session?.user ?? null;
      setAuthState({
        user,
        loading: false,
        error: null,
        isAuthorized: !!user,
      });
    });

    return () => subscription.unsubscribe();
  }, []);

  const login = async (email: string, password: string) => {
    setAuthState((prev) => ({ ...prev, loading: true, error: null }));
    const { error } = await supabase.auth.signInWithPassword({ email, password });
    if (error) {
      setAuthState((prev) => ({
        ...prev,
        loading: false,
        error: error.message === 'Invalid login credentials'
          ? 'البريد الإلكتروني أو كلمة المرور غير صحيحة.'
          : `فشل تسجيل الدخول: ${error.message}`,
      }));
      return false;
    }
    return true;
  };

  const logout = async () => {
    await supabase.auth.signOut();
  };

  const resetPassword = async (email: string) => {
    const { error } = await supabase.auth.resetPasswordForEmail(email, {
      redirectTo: `${window.location.origin}/admin-h`,
    });
    if (error) {
      return { success: false, message: `فشل إرسال رابط إعادة التعيين: ${error.message}` };
    }
    return { success: true, message: 'تم إرسال رابط إعادة تعيين كلمة المرور إلى بريدك.' };
  };

  return { ...authState, login, logout, resetPassword };
};
