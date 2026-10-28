import { useId, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes } from 'react';
import { fieldMessage } from '../i18n/es';

// 44px on phones (touch), 40px from sm up; white with a control-strength edge even on grey panels.
const control =
  'mt-1.5 block h-11 w-full rounded-control border border-control bg-surface px-3 text-base text-ink ' +
  'sm:h-10 aria-[invalid=true]:border-negative';

interface FieldProps {
  label: string;
  error?: string;
  hint?: string;
}

function Described({
  id,
  label,
  error,
  hint,
  children,
}: FieldProps & { id: string; children: ReactNode }) {
  return (
    <div>
      <label htmlFor={id} className="block text-sm font-medium text-ink">
        {label}
      </label>
      {children}
      {hint && !error && (
        <p id={`${id}-hint`} className="mt-1 text-sm text-muted">
          {hint}
        </p>
      )}
      {error && (
        <p id={`${id}-error`} className="mt-1 text-sm text-negative">
          {fieldMessage(error)}
        </p>
      )}
    </div>
  );
}

function describedBy(id: string, error?: string, hint?: string) {
  if (error) return `${id}-error`;
  return hint ? `${id}-hint` : undefined;
}

export function TextField({
  label,
  error,
  hint,
  ...props
}: FieldProps & InputHTMLAttributes<HTMLInputElement>) {
  const id = useId();
  return (
    <Described id={id} label={label} error={error} hint={hint}>
      <input
        id={id}
        className={control}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy(id, error, hint)}
        {...props}
      />
    </Described>
  );
}

export function SelectField({
  label,
  error,
  hint,
  options = {},
  groups,
  placeholder,
  ...props
}: FieldProps & {
  options?: Record<string, string>;
  /** Grouped options, rendered as labelled optgroups. */
  groups?: Record<string, Record<string, string>>;
  placeholder?: string;
} & SelectHTMLAttributes<HTMLSelectElement>) {
  const id = useId();
  return (
    <Described id={id} label={label} error={error} hint={hint}>
      <select
        id={id}
        className={control}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy(id, error, hint)}
        {...props}
      >
        {placeholder !== undefined && <option value="">{placeholder}</option>}
        {Object.entries(options).map(([value, text]) => (
          <option key={value} value={value}>
            {text}
          </option>
        ))}
        {Object.entries(groups ?? {}).map(([groupLabel, groupOptions]) => (
          <optgroup key={groupLabel} label={groupLabel}>
            {Object.entries(groupOptions).map(([value, text]) => (
              <option key={value} value={value}>
                {text}
              </option>
            ))}
          </optgroup>
        ))}
      </select>
    </Described>
  );
}

export function Button({
  variant = 'primary',
  size = 'md',
  className = '',
  ...props
}: {
  variant?: 'primary' | 'secondary' | 'danger';
  /** `sm` for actions inside a row, so a list does not read as a stack of buttons. */
  size?: 'md' | 'sm';
} & React.ButtonHTMLAttributes<HTMLButtonElement>) {
  const styles = {
    // The primary is near-black: colour is kept for data and status.
    primary: 'border border-ink bg-ink text-white hover:bg-black',
    secondary: 'border border-control bg-surface text-ink hover:bg-raised',
    danger: 'border border-negative bg-negative text-white hover:bg-pill-negative-ink',
  }[variant];
  return (
    <button
      type="button"
      className={`inline-flex items-center justify-center gap-2 rounded-control font-medium whitespace-nowrap disabled:opacity-60 ${
        size === 'sm' ? 'h-9 px-3 text-sm sm:h-8' : 'h-11 px-4 text-base sm:h-10'
      } ${styles} ${className}`}
      {...props}
    />
  );
}

export function FormAlert({ message }: { message: string | null }) {
  if (!message) return null;
  return (
    <p role="alert" className="rounded-control bg-pill-negative p-3 text-sm text-pill-negative-ink">
      {message}
    </p>
  );
}

export function Loading() {
  return (
    <p className="p-6 text-center text-muted" aria-live="polite">
      Cargando…
    </p>
  );
}
