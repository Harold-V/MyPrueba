import { createContext, use } from 'react';
import type { Me } from '../api/types';

export type SessionState =
  | { status: 'loading'; me: null }
  | { status: 'anonymous'; me: null }
  | { status: 'authenticated'; me: Me };

export interface SessionValue {
  state: SessionState;
  signIn: (me: Me) => void;
  signOut: () => Promise<void>;
  updateMe: (me: Me) => void;
}

export const SessionContext = createContext<SessionValue | null>(null);

export function useSession(): SessionValue {
  const value = use(SessionContext);
  if (!value) throw new Error('useSession must be used inside SessionProvider');
  return value;
}

/** The signed-in owner; only valid under the protected layout. */
export function useMe(): Me {
  const { state } = useSession();
  if (state.status !== 'authenticated') throw new Error('No authenticated owner');
  return state.me;
}
