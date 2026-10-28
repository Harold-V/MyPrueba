import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router';
import { api, ApiError } from '../api/client';
import type { Receipt } from '../api/types';
import { useMe } from '../auth/session-context';
import { Button, FormAlert, Loading } from '../components/ui';
import { formMessage, paymentMethods } from '../i18n/es';
import { formatDate, formatDateTime } from '../lib/dates';
import { formatMoney } from '../lib/money';

const hasDiscount = (amount: string | undefined) => amount !== undefined && amount !== '0.00';

/** A due date is a calendar day: read it at noon so no time zone moves it. */
const dueLabel = (dueDate: string, timeZone: string) =>
  formatDate(`${dueDate}T12:00:00Z`, timeZone);

/** WhatsApp needs the number with country code: a Colombian mobile (3xx xxx xxxx) gets 57. */
function whatsappNumber(phone: string | null | undefined): string {
  const digits = (phone ?? '').replace(/\D/g, '');
  return digits.length === 10 && digits.startsWith('3') ? `57${digits}` : digits;
}

function receiptText(receipt: Receipt, timeZone: string): string {
  const lines = receipt.items.map((item) => {
    const discount = hasDiscount(item.discount) ? ` (−${formatMoney(item.discount!)})` : '';
    return `${item.quantity} × ${item.name}: ${formatMoney(item.lineTotal)}${discount}`;
  });
  return [
    receipt.business.name,
    `Comprobante #${receipt.number} · ${formatDateTime(receipt.occurredAt, timeZone)}`,
    '',
    ...lines,
    '',
    hasDiscount(receipt.discountTotal)
      ? `Descuentos: −${formatMoney(receipt.discountTotal)}`
      : null,
    `Total: ${formatMoney(receipt.total)}`,
    receipt.status === 'CREDIT' ? 'Fiado' : paymentMethods[receipt.paymentMethod],
    '',
    '¡Gracias por tu compra!',
  ]
    .filter((line) => line !== null)
    .join('\n');
}

export function ReceiptPage() {
  const { id = '' } = useParams();
  const { business } = useMe();
  const [receipt, setReceipt] = useState<Receipt | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api
      .get<Receipt>(`/api/sales/${id}/receipt`)
      .then(setReceipt, (e: unknown) => setError(formMessage(e instanceof ApiError ? e.code : '')));
  }, [id]);

  if (!receipt) return error ? <FormAlert message={error} /> : <Loading />;

  const { business: shop, customer } = receipt;
  const whatsapp = `https://wa.me/${whatsappNumber(customer?.phone)}?text=${encodeURIComponent(
    receiptText(receipt, business.timeZone),
  )}`;

  return (
    <div className="mx-auto max-w-md space-y-4">
      <h1 className="text-xl font-bold print:hidden">Comprobante de venta #{receipt.number}</h1>
      <article
        aria-label="Comprobante"
        className="space-y-4 rounded-xl bg-white p-5 shadow-sm print:shadow-none"
      >
        <header className="space-y-0.5 text-center">
          <p className="text-lg font-bold">{shop.name}</p>
          {shop.document && <p className="text-sm">NIT {shop.document}</p>}
          {(shop.phone || shop.address) && (
            <p className="text-sm text-slate-600">
              {[shop.address, shop.phone].filter(Boolean).join(' · ')}
            </p>
          )}
        </header>
        <div className="border-y border-dashed border-slate-300 py-2 text-sm">
          <p>
            Venta #{receipt.number} · {formatDateTime(receipt.occurredAt, business.timeZone)}
          </p>
          {customer && (
            <p>
              Cliente:{' '}
              {[customer.name, customer.document && `Doc. ${customer.document}`, customer.phone]
                .filter(Boolean)
                .join(' · ')}
            </p>
          )}
        </div>
        <ul className="space-y-2">
          {receipt.items.map((item, index) => (
            <li key={`${item.productId ?? 'line'}-${index}`} className="text-sm">
              <div className="flex justify-between gap-2">
                <span>
                  {item.quantity} × {item.name}
                </span>
                <span>{formatMoney(item.lineTotal)}</span>
              </div>
              <p className="text-xs text-slate-600">{formatMoney(item.unitPrice)} c/u</p>
              {hasDiscount(item.discount) && (
                <p className="text-xs text-emerald-800">Descuento −{formatMoney(item.discount!)}</p>
              )}
            </li>
          ))}
        </ul>
        <dl className="space-y-1 border-t border-dashed border-slate-300 pt-2 text-sm">
          <div className="flex justify-between">
            <dt>Subtotal</dt>
            <dd>{formatMoney(receipt.subtotal)}</dd>
          </div>
          {hasDiscount(receipt.discountTotal) && (
            <div className="flex justify-between">
              <dt>Descuentos</dt>
              <dd>−{formatMoney(receipt.discountTotal)}</dd>
            </div>
          )}
          <div className="flex justify-between text-lg font-bold">
            <dt>Total</dt>
            <dd>{formatMoney(receipt.total)}</dd>
          </div>
        </dl>
        <div className="text-sm">
          {receipt.status === 'CREDIT' ? (
            <p>
              {receipt.dueDate
                ? `Fiado · vence el ${dueLabel(receipt.dueDate, business.timeZone)}`
                : 'Fiado'}
            </p>
          ) : (
            <>
              <p>Pago: {paymentMethods[receipt.paymentMethod]}</p>
              {receipt.received && receipt.change && (
                <p>
                  Recibido {formatMoney(receipt.received)} · Cambio {formatMoney(receipt.change)}
                </p>
              )}
            </>
          )}
          {receipt.note && <p>Nota: {receipt.note}</p>}
        </div>
        <p className="text-center text-sm text-slate-600">¡Gracias por tu compra!</p>
      </article>
      <div className="grid gap-2 print:hidden sm:grid-cols-2">
        <Button variant="secondary" onClick={() => window.print()}>
          Imprimir o guardar PDF
        </Button>
        <a
          href={whatsapp}
          target="_blank"
          rel="noreferrer"
          className="rounded-lg bg-emerald-50 px-4 py-2.5 text-center font-semibold text-emerald-900"
        >
          Enviar por WhatsApp
        </a>
        <Link
          to="/vender"
          className="rounded-lg bg-emerald-700 px-4 py-2.5 text-center font-semibold text-white"
        >
          Nueva venta
        </Link>
        <Link
          to={`/ventas/${receipt.id}`}
          className="rounded-lg px-4 py-2.5 text-center font-semibold text-emerald-800 underline"
        >
          Editar venta
        </Link>
      </div>
    </div>
  );
}
