/* eslint-disable react-refresh/only-export-components -- fournisseur + hook/aides exportés ensemble, perte de HMR acceptée */
import { useEffect, useRef } from 'react';

declare global {
  interface Window {
    grecaptcha?: {
      render: (
        container: HTMLElement,
        options: {
          sitekey: string;
          callback: (token: string) => void;
          'expired-callback': () => void;
        },
      ) => number;
    };
    vetspaceRecaptchaOnLoad?: () => void;
  }
}

const SITE_KEY: string = import.meta.env.VITE_RECAPTCHA_SITE_KEY || '';

/** Jeton factice envoyé quand le widget est désactivé (dev : RECAPTCHA_ENABLED=false côté serveur). */
export const DEV_RECAPTCHA_TOKEN = 'dev-recaptcha-disabled';

export function recaptchaEnabled(): boolean {
  return SITE_KEY.length > 0;
}

interface RecaptchaProps {
  onToken: (token: string | null) => void;
}

/** Widget reCAPTCHA v2. Sans clé configurée, il ne rend rien : mode dev. */
export function Recaptcha({ onToken }: RecaptchaProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const renderedRef = useRef(false);

  useEffect(() => {
    if (!recaptchaEnabled()) {
      return;
    }
    const renderWidget = () => {
      if (renderedRef.current || !containerRef.current || !window.grecaptcha) {
        return;
      }
      renderedRef.current = true;
      window.grecaptcha.render(containerRef.current, {
        sitekey: SITE_KEY,
        callback: (token) => onToken(token),
        'expired-callback': () => onToken(null),
      });
    };

    if (window.grecaptcha) {
      renderWidget();
      return;
    }
    window.vetspaceRecaptchaOnLoad = renderWidget;
    const script = document.createElement('script');
    script.src = 'https://www.google.com/recaptcha/api.js?onload=vetspaceRecaptchaOnLoad&render=explicit';
    script.async = true;
    document.head.appendChild(script);
  }, [onToken]);

  if (!recaptchaEnabled()) {
    return null;
  }
  return <div ref={containerRef} className="flex justify-center" />;
}
