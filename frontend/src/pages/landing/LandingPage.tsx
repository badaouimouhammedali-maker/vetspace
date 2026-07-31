import { useQuery } from '@tanstack/react-query';
import { Button, CardGridSkeleton, Disclosure } from '../../components/ui';
import { useEffect, useState, type ReactNode } from 'react';
import { Helmet } from 'react-helmet-async';
import { Link } from 'react-router-dom';
import { fetchPacks, fetchPublicStats } from '../../lib/endpoints';
import type { PublicPack } from '../../lib/schemas';
import { Logo } from '../../components/Logo';

const NAV = [
  { href: '#accueil', label: 'Accueil' },
  { href: '#fonctionnalites', label: 'Fonctionnalités' },
  { href: '#specifications', label: 'Spécifications' },
  { href: '#abonnements', label: 'Abonnements' },
];

export function LandingPage() {
  return (
    <div className="min-h-screen bg-surface font-sans text-brand-navy">
      <Helmet>
        <html lang="fr" />
        <title>VetSpace — La plateforme de QCM des étudiants vétérinaires</title>
        <meta
          name="description"
          content="Cours gratuits et QCM sur abonnement pour étudiants vétérinaires : supports de cours en accès libre, milliers de QCM corrigés, annales d'examens, MindMaps et suivi de progression."
        />
        <meta property="og:type" content="website" />
        <meta property="og:title" content="VetSpace — QCM pour étudiants vétérinaires" />
        <meta
          property="og:description"
          content="QCM corrigés, annales d'examens et MindMaps pour préparer vos examens vétérinaires sereinement."
        />
        <meta property="og:image" content="/brand/logo.svg" />
        <meta name="theme-color" content="#12355B" />
      </Helmet>

      <Header />
      <main>
        <Hero />
        <Features />
        <Specs />
        <Pricing />
        <Faq />
      </main>
      <Footer />
    </div>
  );
}

// ---------------------------------------------------------------------
// Header
// ---------------------------------------------------------------------

function Header() {
  const [open, setOpen] = useState(false);
  return (
    <header className="sticky top-0 z-40 bg-brand-navy text-white shadow-subtle">
      <div className="mx-auto flex h-16 max-w-6xl items-center justify-between px-4 lg:px-8">
        <a href="#accueil" className="flex items-center gap-2">
          <Logo variant="full" theme="dark" size="md" />
        </a>
        <nav className="hidden items-center gap-7 text-sm font-semibold md:flex">
          {NAV.map((n) => (
            <a key={n.href} href={n.href} className="text-white/80 transition hover:text-white">
              {n.label}
            </a>
          ))}
          <Link to="/login" className="text-white/80 transition hover:text-white">
            Connexion
          </Link>
          <Link
            to="/register"
            className="rounded-lg bg-brand-green px-4 py-2 font-bold text-white transition hover:bg-brand-green-hover"
          >
            S'inscrire
          </Link>
        </nav>
        <Button
          variant="ghost"
          size="sm"
          className="md:hidden md:hidden"

          aria-label="Ouvrir le menu"
          onClick={() => setOpen((o) => !o)}
        >
          ☰
        </Button>
      </div>
      {open ? (
        <nav className="space-y-1 border-t border-white/10 px-4 pb-4 md:hidden">
          {NAV.map((n) => (
            <a
              key={n.href}
              href={n.href}
              onClick={() => setOpen(false)}
              className="block rounded-lg px-3 py-2 text-sm font-semibold text-white/80 hover:bg-surface/10"
            >
              {n.label}
            </a>
          ))}
          <Link
            to="/login"
            className="block rounded-lg px-3 py-2 text-sm font-semibold text-white/80 hover:bg-surface/10"
          >
            Connexion
          </Link>
          <Link
            to="/register"
            className="mt-1 block rounded-lg bg-brand-green px-3 py-2 text-center text-sm font-bold text-white"
          >
            S'inscrire
          </Link>
        </nav>
      ) : null}
    </header>
  );
}

// ---------------------------------------------------------------------
// Hero + animated counters
// ---------------------------------------------------------------------

function useCountUp(target: number, durationMs = 1400): number {
  const [value, setValue] = useState(0);
  useEffect(() => {
    if (target <= 0) {
      setValue(0);
      return;
    }
    let raf = 0;
    const start = performance.now();
    const tick = (now: number) => {
      const p = Math.min((now - start) / durationMs, 1);
      const eased = 1 - Math.pow(1 - p, 3);
      setValue(Math.round(eased * target));
      if (p < 1) {
        raf = requestAnimationFrame(tick);
      }
    };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [target, durationMs]);
  return value;
}

function Counter({ target, label }: { target: number; label: string }) {
  const value = useCountUp(target);
  return (
    <div className="text-center">
      <div className="text-display text-white sm:text-4xl">
        {value.toLocaleString('fr-FR')}
        <span className="text-brand-green">+</span>
      </div>
      <div className="mt-1 text-xs font-semibold uppercase tracking-wide text-white/60">
        {label}
      </div>
    </div>
  );
}

function Hero() {
  const stats = useQuery({ queryKey: ['public', 'stats'], queryFn: fetchPublicStats });
  const s = stats.data ?? { questions: 0, examens: 0, mindmaps: 0 };
  return (
    <section
      id="accueil"
      className="scroll-mt-16 bg-gradient-to-br from-brand-navy to-[#0c2542] px-4 pb-16 pt-16 text-white lg:px-8 lg:pt-24"
    >
      <div className="mx-auto max-w-4xl text-center">
        <span className="inline-block rounded-full bg-surface/10 px-4 py-1.5 text-xs font-semibold uppercase tracking-wide text-white/80">
          La plateforme de QCM des étudiants vétérinaires
        </span>
        <h1 className="mt-6 text-4xl font-bold leading-tight sm:text-5xl lg:text-6xl">
          Réussissez vos examens vétérinaires,{' '}
          <span className="text-brand-green">une question à la fois.</span>
        </h1>
        <p className="mx-auto mt-6 max-w-2xl text-lg text-white/70">
          Des milliers de QCM corrigés, des annales d'examens et des MindMaps, avec un suivi de
          progression détaillé — pour réviser efficacement et aborder vos épreuves en confiance.
        </p>
        <div className="mt-8 flex flex-col items-center justify-center gap-3 sm:flex-row">
          <Link
            to="/register"
            className="w-full rounded-xl bg-brand-green px-6 py-3 text-center font-bold text-white transition hover:bg-brand-green-hover sm:w-auto"
          >
            S'inscrire gratuitement
          </Link>
          <Link
            to="/login"
            className="w-full rounded-xl border border-white/30 px-6 py-3 text-center font-bold text-white transition hover:bg-surface/10 sm:w-auto"
          >
            Se connecter
          </Link>
        </div>
      </div>
      <div className="mx-auto mt-14 grid max-w-3xl grid-cols-3 gap-4 rounded-lg border border-white/10 bg-surface/5 px-4 py-6 sm:gap-8">
        <Counter target={s.questions} label="Questions" />
        <Counter target={s.examens} label="Examens" />
        <Counter target={s.mindmaps} label="MindMaps" />
      </div>
    </section>
  );
}

// ---------------------------------------------------------------------
// Fonctionnalités — alternating blocks
// ---------------------------------------------------------------------

const FEATURES: { title: string; body: string; image: string; alt: string }[] = [
  {
    title: "Des sessions d'entraînement sur mesure",
    body: "Filtrez par module, cours, source d'examen ou difficulté, choisissez le nombre de questions et lancez-vous. Chaque réponse est évaluée côté serveur, proposition par proposition.",
    image: '/landing/sessions.svg',
    alt: "Écran de session d'entraînement",
  },
  {
    title: 'Une correction claire et commentée',
    body: 'Après chaque question, retrouvez les bonnes réponses et des explications riches (mise en forme, couleurs, schémas). Consultez la correction ou rejouez vos erreurs autant que nécessaire.',
    image: '/landing/correction.svg',
    alt: "Correction commentée d'une question",
  },
  {
    title: 'Suivez votre progression',
    body: "Statistiques hebdomadaires, taux de réussite par cours, historique de vos sessions : visualisez ce qui est acquis et ce qu'il reste à travailler avant l'examen.",
    image: '/landing/stats.svg',
    alt: 'Tableau de statistiques',
  },
  {
    title: 'Mémorisez avec les MindMaps',
    body: "Des cartes mentales et schémas synthétiques par cours pour ancrer l'essentiel et réviser vite les points clés la veille de l'épreuve.",
    image: '/landing/mindmaps.svg',
    alt: 'MindMaps et schémas',
  },
  {
    // The acquisition argument: something valuable before any payment is asked for.
    title: 'Vos cours, gratuitement',
    body: "Polycopiés, schémas et supports de cours en accès libre, classés par année et par module : téléchargeables dès la création de votre compte, sans abonnement. Les QCM et leurs corrections restent sur abonnement.",
    image: '/landing/mindmaps.svg',
    alt: 'Bibliothèque de cours gratuits',
  },
];

function Features() {
  return (
    <section id="fonctionnalites" className="scroll-mt-16 px-4 py-20 lg:px-8">
      <div className="mx-auto max-w-6xl">
        <SectionHeading eyebrow="Fonctionnalités" title="Tout pour réviser efficacement" />
        <div className="mt-14 space-y-20">
          {FEATURES.map((f, i) => (
            <div
              key={f.title}
              className={`flex flex-col items-center gap-8 lg:gap-14 ${
                i % 2 === 1 ? 'lg:flex-row-reverse' : 'lg:flex-row'
              }`}
            >
              <div className="flex-1">
                <h3 className="text-h1 text-brand-navy">{f.title}</h3>
                <p className="mt-3 text-brand-gray">{f.body}</p>
              </div>
              <div className="w-full flex-1">
                <img
                  src={f.image}
                  alt={f.alt}
                  loading="lazy"
                  className="w-full rounded-lg shadow-pop"
                />
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}

// ---------------------------------------------------------------------
// Spécifications — 6 checklist cards (1 accent)
// ---------------------------------------------------------------------

const SPECS: { icon: string; title: string; body: string; accent?: boolean }[] = [
  {
    icon: '⚡',
    title: 'Performance',
    body: 'Correction instantanée et interface rapide, pensée pour des sessions de révision intensives.',
  },
  {
    icon: '🔄',
    title: 'Mises à jour',
    body: "Une banque de questions enrichie et corrigée en continu par l'équipe pédagogique.",
  },
  {
    icon: '📚',
    title: 'Contenu',
    body: "QCM par module et par cours, annales d'examens et propositions expliquées en détail.",
  },
  {
    icon: '🔔',
    title: 'Notification',
    body: 'Soyez prévenu des nouveaux contenus et des annonces importantes, ciblées par année.',
  },
  {
    icon: '🧠',
    title: 'Schématisation',
    body: "Des MindMaps synthétiques pour mémoriser l'essentiel et réviser plus vite.",
    accent: true,
  },
  {
    icon: '💬',
    title: 'Support',
    body: 'Une équipe réactive et un système de signalement pour toute erreur repérée.',
  },
];

function Specs() {
  return (
    <section id="specifications" className="scroll-mt-16 bg-canvas px-4 py-20 lg:px-8">
      <div className="mx-auto max-w-6xl">
        <SectionHeading eyebrow="Spécifications" title="Une plateforme complète et fiable" />
        <div className="mt-12 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {SPECS.map((c) => (
            <div
              key={c.title}
              className={`rounded-lg p-6 ring-1 ${
                c.accent
                  ? 'bg-brand-green text-white ring-brand-green'
                  : 'bg-surface text-brand-navy ring-subtle'
              }`}
            >
              <div className="text-3xl" aria-hidden>
                {c.icon}
              </div>
              <h3 className="mt-3 flex items-center gap-2 text-lg font-bold">
                <span className={c.accent ? 'text-white' : 'text-brand-green'}>✓</span>
                {c.title}
              </h3>
              <p className={`mt-2 text-sm ${c.accent ? 'text-white/85' : 'text-brand-gray'}`}>
                {c.body}
              </p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}

// ---------------------------------------------------------------------
// Abonnements — packs grouped by école
// ---------------------------------------------------------------------

function Pricing() {
  const packs = useQuery({ queryKey: ['public', 'packs'], queryFn: () => fetchPacks(null) });

  // Packs are national: one flat grid ordered by study year, all-years packs last.
  // Grouping by école used to be the organising idea here and no longer means anything.
  const sorted: PublicPack[] = [...(packs.data ?? [])].sort(
    (a, b) => (a.studyYear ?? 99) - (b.studyYear ?? 99) || a.name.localeCompare(b.name, 'fr'),
  );

  return (
    <section id="abonnements" className="scroll-mt-16 px-4 py-20 lg:px-8">
      <div className="mx-auto max-w-6xl">
        <SectionHeading
          eyebrow="Abonnements"
          title="Choisissez votre pack"
          subtitle="Un accès complet jusqu'à la fin de l'année universitaire, pour votre année d'étude."
        />

        {packs.isLoading ? (
          <div className="mt-10">
            <CardGridSkeleton count={3} />
          </div>
        ) : sorted.length === 0 ? (
          <p className="mt-10 text-center text-brand-gray">
            Les offres seront bientôt disponibles. Créez votre compte pour être prévenu.
          </p>
        ) : (
          <div className="mt-12 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
            {sorted.map((p) => (
              <div
                key={p.id}
                className="flex flex-col rounded-lg border border-gray-100 bg-surface p-6 shadow-subtle"
              >
                <h4 className="text-base font-bold text-brand-navy">{p.name}</h4>
                <p className="mt-1 text-xs font-semibold uppercase tracking-wide text-brand-gray">
                  {p.studyYear == null ? 'Toutes les années' : `Année ${p.studyYear}`} ·{' '}
                  {p.academicYear}
                </p>
                <div className="mt-4 flex items-baseline gap-1">
                  <span className="text-display text-brand-green">
                    {p.priceDa.toLocaleString('fr-FR')}
                  </span>
                  <span className="font-semibold text-brand-gray">DA</span>
                </div>
                <p className="mt-1 text-sm text-brand-gray">
                  Jusqu'à la fin de l'année universitaire
                </p>
                <Link
                  to="/register"
                  className="mt-5 rounded-lg bg-brand-green px-4 py-2.5 text-center text-sm font-bold text-white transition hover:bg-brand-green-hover"
                >
                  Choisir ce pack
                </Link>
              </div>
            ))}
          </div>
        )}
        <p className="mt-8 text-center text-sm text-brand-gray">
          Paiement par codes d'activation (CCP / BaridiMob). Un code = un abonnement à activer
          depuis votre compte.
        </p>
      </div>
    </section>
  );
}

// ---------------------------------------------------------------------
// FAQ accordion
// ---------------------------------------------------------------------

const FAQ: { q: string; a: string }[] = [
  {
    q: 'Comment choisir mon pack ?',
    a: "Sélectionnez le pack correspondant à votre école et à votre année d'étude. Chaque pack donne accès à tout le contenu de cette année jusqu'à la fin de l'année universitaire.",
  },
  {
    q: "Comment payer ? (codes d'activation, CCP, BaridiMob)",
    a: "Le paiement se fait par code d'activation. Réglez par CCP ou BaridiMob pour recevoir un code, puis activez-le depuis la page Abonnement de votre compte pour débloquer immédiatement votre accès.",
  },
  {
    q: "J'ai oublié mon mot de passe, que faire ?",
    a: 'Cliquez sur « Mot de passe oublié ? » sur la page de connexion. Vous recevrez un e-mail avec un lien sécurisé pour en choisir un nouveau.',
  },
  {
    q: 'Puis-je utiliser VetSpace sur mobile ?',
    a: "Oui. L'interface est entièrement responsive et s'adapte au téléphone, à la tablette et à l'ordinateur — révisez où que vous soyez.",
  },
  {
    q: "J'ai repéré une erreur dans une question, comment la signaler ?",
    a: "Depuis l'écran de jeu, utilisez le bouton de signalement pour nous transmettre le problème. L'équipe pédagogique le traite et vous répond.",
  },
  {
    q: 'Existe-t-il des guides pour bien démarrer ?',
    a: 'Une fois connecté, votre tableau de bord vous guide pas à pas : créez une première session, consultez vos statistiques et explorez les MindMaps.',
  },
];

function Faq() {
  const [openIndex, setOpenIndex] = useState<number | null>(0);
  return (
    <section className="bg-canvas px-4 py-20 lg:px-8">
      <div className="mx-auto max-w-3xl">
        <SectionHeading eyebrow="FAQ" title="Questions fréquentes" />
        <div className="mt-10 space-y-3">
          {FAQ.map((item, i) => {
            const isOpen = openIndex === i;
            return (
              <div
                key={item.q}
                className="overflow-hidden rounded-xl border border-gray-100 bg-surface"
              >
                <Disclosure
                  open={isOpen}
                  onClick={() => setOpenIndex(isOpen ? null : i)}
                  className="px-5 py-4"
                >
                  {item.q}
                </Disclosure>
                {isOpen ? <p className="px-5 pb-5 text-sm text-brand-gray">{item.a}</p> : null}
              </div>
            );
          })}
        </div>
      </div>
    </section>
  );
}

// ---------------------------------------------------------------------
// Footer
// ---------------------------------------------------------------------

function Footer() {
  return (
    <footer className="bg-brand-navy px-4 py-12 text-white lg:px-8">
      <div className="mx-auto flex max-w-6xl flex-col gap-8 md:flex-row md:items-start md:justify-between">
        <div className="max-w-sm">
          <Logo variant="full" theme="dark" size="md" />
          <p className="mt-3 text-sm text-white/60">
            La plateforme de QCM des étudiants vétérinaires. Révisez, progressez, réussissez.
          </p>
        </div>
        <div className="grid grid-cols-2 gap-8 text-sm">
          <div>
            <h4 className="font-bold text-white">Contact</h4>
            <ul className="mt-3 space-y-2 text-white/70">
              <li>
                <a href="mailto:support@vetspace.local" className="hover:text-white">
                  support@vetspace.local
                </a>
              </li>
              <li>
                <Link to="/login" className="hover:text-white">
                  Espace étudiant
                </Link>
              </li>
            </ul>
          </div>
          <div>
            <h4 className="font-bold text-white">Suivez-nous</h4>
            <ul className="mt-3 space-y-2 text-white/70">
              <li>
                <a
                  href="https://www.instagram.com"
                  target="_blank"
                  rel="noopener"
                  className="hover:text-white"
                >
                  Instagram
                </a>
              </li>
              <li>
                <a
                  href="https://www.facebook.com"
                  target="_blank"
                  rel="noopener"
                  className="hover:text-white"
                >
                  Facebook
                </a>
              </li>
            </ul>
          </div>
        </div>
      </div>
      <div className="mx-auto mt-10 max-w-6xl border-t border-white/10 pt-6 text-center text-xs text-white/40">
        © {new Date().getFullYear()} VetSpace. Tous droits réservés.
      </div>
    </footer>
  );
}

// ---------------------------------------------------------------------

function SectionHeading({
  eyebrow,
  title,
  subtitle,
}: {
  eyebrow: string;
  title: string;
  subtitle?: ReactNode;
}) {
  return (
    <div className="mx-auto max-w-2xl text-center">
      <span className="text-xs font-bold uppercase tracking-widest text-brand-green">
        {eyebrow}
      </span>
      <h2 className="mt-2 text-display text-brand-navy sm:text-4xl">{title}</h2>
      {subtitle ? <p className="mt-3 text-brand-gray">{subtitle}</p> : null}
    </div>
  );
}
