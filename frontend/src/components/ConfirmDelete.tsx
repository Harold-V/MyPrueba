import { useState } from 'react';
import { Button } from './ui';

/** Two-step delete: deleting is permanent, so it always asks first. */
export function ConfirmDelete({
  label,
  question,
  onConfirm,
  size = 'md',
}: {
  label: string;
  question: string;
  onConfirm: () => Promise<void> | void;
  size?: 'md' | 'sm';
}) {
  const [asking, setAsking] = useState(false);
  if (!asking) {
    return (
      <Button variant="secondary" size={size} aria-label={label} onClick={() => setAsking(true)}>
        Eliminar
      </Button>
    );
  }
  return (
    <div role="group" aria-label={question} className="flex flex-wrap items-center gap-2">
      <span className="text-sm font-medium">{question}</span>
      <Button variant="danger" size={size} onClick={() => void onConfirm()}>
        Sí, eliminar
      </Button>
      <Button variant="secondary" size={size} onClick={() => setAsking(false)}>
        Cancelar
      </Button>
    </div>
  );
}
