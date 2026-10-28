import { useId, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes } from 'react';
import { fieldMessage } from '../i18n/es';

const control =
  'mt-1 block w-full rounded-lg border bg-white px-3 py-2.5 text-base text-slate-900 shadow-sm ' +
  'aria-[invalid=true]:border-red-600 border-slate-300';

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
      <label htmlFor={id} className="block text-sm font-medium text-slate-800">
        {label}
      </label>
      {children}
      {hint && !error && (
        <p id={`${id}-hint`} className="mt-1 text-sm text-slate-600">
          {hint}
        </p>
      )}
      {error && (
        <p id={`${id}-error`} className="mt-1 text-sm text-red-700">
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
  className = '',
  ...props
}: {
  variant?: 'primary' | 'secondary' | 'danger';
} & React.ButtonHTMLAttributes<HTMLButtonElement>) {
  const styles = {
    primary: 'bg-emerald-700 text-white hover:bg-emerald-800',
    secondary: 'bg-white text-slate-900 border border-slate-300 hover:bg-slate-100',
    danger: 'bg-red-700 text-white hover:bg-red-800',
  }[variant];
  return (
    <button
      type="button"
      className={`rounded-lg px-4 py-2.5 text-base font-semibold disabled:opacity-60 ${styles} ${className}`}
      {...props}
    />
  );
}

export function FormAlert({ message }: { message: string | null }) {
  if (!message) return null;
  return (
    <p role="alert" className="rounded-lg bg-red-50 p-3 text-sm text-red-800">
      {message}
    </p>
  );
}

export function Loading() {
  return (
    <p className="p-6 text-center text-slate-600" aria-live="polite">
      Cargando…
    </p>
  );
}
