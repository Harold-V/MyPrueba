import { useEffect, useState, type ReactNode } from 'react';
import { api, ApiError } from '../api/client';
import type { Me } from '../api/types';
import { SessionContext, type SessionState, type SessionValue } from './session-context';

export function SessionProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<SessionState>({ status: 'loading', me: null });

  useEffect(() => {
    let active = true;
    api
      .get<Me>('/api/me')
      .then((me) => active && setState({ status: 'authenticated', me }))
      .catch((error: unknown) => {
        if (active && error instanceof ApiError) setState({ status: 'anonymous', me: null });
      });
    return () => {
      active = false;
    };
  }, []);

  const value: SessionValue = {
    state,
    signIn: (me) => setState({ status: 'authenticated', me }),
    updateMe: (me) => setState({ status: 'authenticated', me }),
    signOut: async () => {
      await api.post('/api/auth/logout').catch(() => undefined);
      setState({ status: 'anonymous', me: null });
    },
  };
  return <SessionContext value={value}>{children}</SessionContext>;
}
