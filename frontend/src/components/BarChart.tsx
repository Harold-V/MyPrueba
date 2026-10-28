import { useLayoutEffect, useRef, useState } from 'react';

/**
 * One series of columns: square bars, 3–5 faint gridlines, round ticks that top out above the
 * largest value, no y-axis line, x labels on one step. The highlighted bars (the current period)
 * take the accent; the rest are muted. Drawn in SVG, no chart library.
 */
export function BarChart({
  labels,
  values,
  highlight = [],
  format,
  label,
  height = 200,
}: {
  labels: string[];
  values: number[];
  highlight?: number[];
  format: (value: number) => string;
  /** What the chart shows, for screen readers. */
  label: string;
  height?: number;
}) {
  const box = useRef<HTMLDivElement>(null);
  const [width, setWidth] = useState(600);
  useLayoutEffect(() => {
    const el = box.current;
    if (!el) return;
    const update = () => setWidth(Math.max(240, el.clientWidth));
    update();
    const observer = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(update);
    observer?.observe(el);
    return () => observer?.disconnect();
  }, []);

  const steps = ticks(Math.max(0, ...values));
  const top = steps[steps.length - 1]!;
  const tickWidth = Math.max(...steps.map((t) => format(t).length)) * 7 + 8;
  const left = tickWidth;
  const bottom = 22;
  const plotW = width - left;
  const plotH = height - bottom - 6;
  const slot = plotW / Math.max(1, values.length);
  const bar = Math.max(3, Math.min(28, slot * 0.6));
  const y = (v: number) => 6 + plotH - (v / top) * plotH;
  // Label every k-th bar so the labels fit, on one step from the first.
  const longest = Math.max(...labels.map((l) => l.length), 1);
  const every = Math.max(
    1,
    Math.ceil(labels.length / Math.max(2, Math.floor(plotW / (longest * 7 + 16)))),
  );
  const highlighted = new Set(highlight);

  return (
    <div ref={box} className="w-full min-w-0">
      <svg
        viewBox={`0 0 ${width} ${height}`}
        width="100%"
        height={height}
        role="img"
        aria-label={label}
        className="block"
      >
        {steps.map((t) => (
          <g key={t}>
            <line x1={left} x2={width} y1={y(t)} y2={y(t)} stroke="var(--color-rule)" />
            <text
              x={left - 8}
              y={y(t) + 4}
              textAnchor="end"
              fontSize="12"
              fill="var(--color-muted)"
            >
              {format(t)}
            </text>
          </g>
        ))}
        <line x1={left} x2={width} y1={y(0)} y2={y(0)} stroke="var(--color-control)" />
        {values.map((v, i) => (
          <rect
            key={i}
            x={left + i * slot + (slot - bar) / 2}
            y={y(v)}
            width={bar}
            height={Math.max(0, y(0) - y(v))}
            fill={highlighted.has(i) ? 'var(--color-accent)' : 'var(--color-control)'}
          >
            <title>{`${labels[i]}: ${format(v)}`}</title>
          </rect>
        ))}
        {labels.map((l, i) =>
          i % every === 0 ? (
            <text
              key={l + i}
              x={left + i * slot + slot / 2}
              y={height - 4}
              textAnchor="middle"
              fontSize="12"
              fill="var(--color-muted)"
            >
              {l}
            </text>
          ) : null,
        )}
      </svg>
    </div>
  );
}

/** Round ticks (1, 2, 2.5 or 5 × 10ⁿ) from zero to above the largest value. */
function ticks(max: number, count = 4): number[] {
  if (max <= 0) return [0, 1];
  const raw = max / count;
  const magnitude = 10 ** Math.floor(Math.log10(raw));
  const step = [1, 2, 2.5, 5, 10].map((m) => m * magnitude).find((s) => s >= raw)!;
  let top = Math.ceil(max / step) * step;
  if (top <= max) top += step;
  const out: number[] = [];
  for (let v = 0; v <= top + step / 2; v += step) out.push(Math.round(v));
  return out;
}
