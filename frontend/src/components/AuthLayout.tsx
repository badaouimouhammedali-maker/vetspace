import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { Logo } from './Logo';
import { t } from '../i18n/fr';
import { Card } from './Card';

/**
 * Gabarit des écrans d'authentification, dans l'esprit de la référence :
 * panneau illustré bleu marine à gauche, carte de formulaire à droite,
 * logo en tête de la carte.
 */
export function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-screen">
      <aside className="relative hidden w-1/2 flex-col items-center justify-center bg-brand-navy p-12 lg:flex">
        <div className="absolute inset-0 opacity-10 [background:radial-gradient(circle_at_30%_20%,#0F766E_0,transparent_45%),radial-gradient(circle_at_75%_70%,#0F766E_0,transparent_40%)]" />
        <div className="relative z-10 flex flex-col items-center text-center">
          <Logo variant="full" theme="dark" size="lg" className="mb-8" />
          <h2 className="max-w-md text-h1 leading-snug text-white">{t('auth.welcome')}</h2>
          <p className="mt-3 max-w-sm text-body text-white/70">{t('auth.tagline')}</p>
        </div>
      </aside>

      <main className="flex w-full items-center justify-center px-4 py-10 lg:w-1/2">
        <div className="w-full max-w-md">
          <Link to="/login" className="mb-8 flex justify-center">
            <Logo variant="full" theme="light" size="lg" />
          </Link>
          <Card className="p-8">{children}</Card>
        </div>
      </main>
    </div>
  );
}
