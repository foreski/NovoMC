import { motion } from 'motion/react';
import { Box, ExternalLink, FlaskConical, Swords } from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { api, type NewsItem } from '../lib/api';
import { formatDate } from '../lib/format';
import { useT, type Key } from '../lib/i18n';

const TAGS: Record<NewsItem['tag'], { label: Key | null; icon: LucideIcon; chip: string }> = {
  novomc: { label: null, icon: Swords, chip: 'bg-brand-b/25 text-[#d8b4fe]' },
  release: { label: 'kind.release', icon: Box, chip: 'bg-play/20 text-play' },
  snapshot: { label: 'kind.snapshot', icon: FlaskConical, chip: 'bg-brand-c/20 text-brand-c' },
};

function hue(id: string): number {
  let h = 0;
  for (let i = 0; i < id.length; i++) h = (h * 31 + id.charCodeAt(i)) >>> 0;
  return h % 360;
}

/** News card: the post's own picture if it has one, else generated artwork. */
export function NewsCard({ item, index }: { item: NewsItem; index: number }) {
  const t = useT();
  const tag = TAGS[item.tag] ?? TAGS.novomc;
  const Icon = tag.icon;
  const h = item.tag === 'novomc' ? 255 + (hue(item.id) % 60) : item.tag === 'release' ? 140 : 190;
  const clickable = !!item.url;

  return (
    <motion.button
      type="button"
      initial={{ opacity: 0, y: 14 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: 0.4 + index * 0.06, duration: 0.4, ease: [0.22, 1, 0.36, 1] }}
      onClick={() => item.url && api.openUrl(item.url).catch(() => {})}
      disabled={!clickable}
      className="glass group flex flex-col overflow-hidden rounded-2xl text-left transition-all enabled:hover:-translate-y-1 enabled:hover:border-novo-border-strong disabled:cursor-default"
    >
      <div
        className="relative h-24 overflow-hidden"
        style={{ background: `linear-gradient(135deg, hsl(${h} 70% 45% / 0.55), hsl(${(h + 50) % 360} 70% 30% / 0.35))` }}
      >
        {item.image ? (
          <img src={item.image} alt="" loading="lazy" className="absolute inset-0 h-full w-full object-cover transition-transform duration-500 group-hover:scale-105" />
        ) : (
          <div className="grid-bg absolute inset-0 opacity-70" />
        )}
        <Icon
          size={88}
          strokeWidth={1.2}
          className={`absolute -right-3 -bottom-4 text-white/20 transition-transform duration-500 group-hover:scale-110 group-hover:-rotate-6 ${
            item.image ? 'hidden' : ''
          }`}
        />
        <span className={`absolute top-3 left-3 rounded-md px-2 py-0.5 text-[10px] font-bold tracking-wider uppercase ${tag.chip}`}>
          {tag.label ? t(tag.label) : 'Novo Client'}
        </span>
      </div>
      <div className="flex flex-1 flex-col p-4">
        <div className="flex items-start justify-between gap-2">
          <h3 className="font-bold leading-snug">{item.title}</h3>
          {clickable && <ExternalLink size={14} className="mt-0.5 shrink-0 text-novo-muted group-hover:text-accent" />}
        </div>
        <p className="mt-1 line-clamp-2 text-sm text-novo-muted">{item.body}</p>
        <p className="mt-auto pt-3 text-xs text-novo-muted/70">{formatDate(item.date)}</p>
      </div>
    </motion.button>
  );
}
