import {
  createContext,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { api, setToken } from "../api/client";
import type { AuthResponse } from "../api/types";

interface AuthUser {
  userId: number;
  name: string;
  email: string;
}

interface AuthContextValue {
  user: AuthUser | null;
  login: (email: string, password: string) => Promise<void>;
  register: (name: string, email: string, password: string) => Promise<void>;
  logout: () => void;
}

const USER_KEY = "acr_user";
const AuthContext = createContext<AuthContextValue | undefined>(undefined);

function loadStoredUser(): AuthUser | null {
  const raw = localStorage.getItem(USER_KEY);
  return raw ? (JSON.parse(raw) as AuthUser) : null;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(loadStoredUser);

  const persist = (data: AuthResponse) => {
    setToken(data.token);
    const u = { userId: data.userId, name: data.name, email: data.email };
    localStorage.setItem(USER_KEY, JSON.stringify(u));
    setUser(u);
  };

  const login = async (email: string, password: string) => {
    const { data } = await api.post<AuthResponse>("/auth/login", { email, password });
    persist(data);
  };

  const register = async (name: string, email: string, password: string) => {
    const { data } = await api.post<AuthResponse>("/auth/register", {
      name,
      email,
      password,
    });
    persist(data);
  };

  const logout = () => {
    setToken(null);
    localStorage.removeItem(USER_KEY);
    setUser(null);
  };

  const value = useMemo(
    () => ({ user, login, register, logout }),
    [user],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return ctx;
}
