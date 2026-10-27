import React, { createContext, useContext, useEffect, useState } from 'react';
import { api, storage } from '../../services/api';
import type { UserProfile } from '../../types/api';

interface AuthContextValue {
  user: UserProfile | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (payload: {
    email: string;
    password: string;
    displayName: string;
    targetRole: string;
    experienceLevel: string;
  }) => Promise<void>;
  logout: () => Promise<void>;
  refreshProfile: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<UserProfile | null>(() => storage.getUser());
  const [loading, setLoading] = useState<boolean>(Boolean(storage.getToken()));

  useEffect(() => {
    const token = storage.getToken();
    if (!token) {
      setLoading(false);
      return;
    }
    api.users
      .getMe()
      .then((profile) => {
        setUser(profile);
        storage.setUser(profile);
      })
      .catch(() => {
        storage.clearToken();
        setUser(null);
      })
      .finally(() => setLoading(false));
  }, []);

  const login = async (email: string, password: string) => {
    const res = await api.auth.login({ email, password });
    storage.setToken(res.accessToken);
    storage.setUser(res.user);
    setUser(res.user);
  };

  const register = async (payload: {
    email: string;
    password: string;
    displayName: string;
    targetRole: string;
    experienceLevel: string;
  }) => {
    const res = await api.auth.register(payload);
    storage.setToken(res.accessToken);
    storage.setUser(res.user);
    setUser(res.user);
  };

  const logout = async () => {
    try {
      await api.auth.logout();
    } catch {
      // ignore network error on logout
    } finally {
      storage.clearToken();
      setUser(null);
    }
  };

  const refreshProfile = async () => {
    const profile = await api.users.getMe();
    setUser(profile);
    storage.setUser(profile);
  };

  return (
    <AuthContext.Provider value={{ user, loading, login, register, logout, refreshProfile }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = (): AuthContextValue => {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth must be used within AuthProvider');
  }
  return ctx;
};
