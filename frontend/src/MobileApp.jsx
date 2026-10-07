import React, { useEffect, useState } from 'react';

export function MobileApp() {
  const [prompt, setPrompt] = useState(null);
  const [help, setHelp] = useState(false);
  const [installed, setInstalled] = useState(() => window.matchMedia('(display-mode: standalone)').matches || navigator.standalone);
  const [offline, setOffline] = useState(!navigator.onLine);
  const [update, setUpdate] = useState(null);

  useEffect(() => {
    const install = event => { event.preventDefault(); setPrompt(event); };
    const done = () => { setInstalled(true); setPrompt(null); setHelp(false); };
    const connection = () => setOffline(!navigator.onLine);
    const ready = event => setUpdate(event.detail);
    window.addEventListener('beforeinstallprompt', install);
    window.addEventListener('appinstalled', done);
    window.addEventListener('online', connection);
    window.addEventListener('offline', connection);
    window.addEventListener('app-update', ready);
    return () => {
      window.removeEventListener('beforeinstallprompt', install);
      window.removeEventListener('appinstalled', done);
      window.removeEventListener('online', connection);
      window.removeEventListener('offline', connection);
      window.removeEventListener('app-update', ready);
    };
  }, []);

  async function install() {
    if (!prompt) { setHelp(!help); return; }
    await prompt.prompt();
    await prompt.userChoice;
    setPrompt(null);
  }

  return <div className="mobileApp">
    {!installed && <button className="ghost" type="button" onClick={install}>Instalar aplicativo</button>}
    {help && <p className="installHelp" role="status">No iPhone: abra no Safari, toque em Compartilhar e em “Adicionar à Tela de Início”. No Android: abra no Chrome e escolha “Instalar aplicativo” ou “Adicionar à tela inicial” no menu. Use o endereço HTTPS do aplicativo.</p>}
    {offline && <p className="alert" role="status">Você está sem conexão. Reconecte para consultar dados ou salvar lançamentos.</p>}
    {update && <button className="ghost" onClick={() => { if (confirm('Atualizar o aplicativo? Salve seu formulário antes de continuar.')) update.postMessage({ type: 'SKIP_WAITING' }); }}>Nova versão disponível · Atualizar</button>}
  </div>;
}

export function registerAppWorker() {
  if (!import.meta.env.PROD || !('serviceWorker' in navigator)) return;
  let refreshing = false;
  navigator.serviceWorker.addEventListener('controllerchange', () => {
    if (!refreshing) { refreshing = true; window.location.reload(); }
  });
  navigator.serviceWorker.register('/sw.js').then(registration => {
    const notify = () => { if (registration.waiting) window.dispatchEvent(new CustomEvent('app-update', { detail: registration.waiting })); };
    notify();
    registration.addEventListener('updatefound', () => {
      const worker = registration.installing;
      worker?.addEventListener('statechange', () => { if (worker.state === 'installed' && navigator.serviceWorker.controller) notify(); });
    });
  }).catch(error => console.warn('Instalação offline indisponível:', error.message));
}
