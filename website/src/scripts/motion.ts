export function initMotion(): void {
  const reduced = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;
  if (reduced) {
    document.querySelectorAll<HTMLElement>('[data-reveal]').forEach((el) => {
      el.classList.add('is-visible');
    });
    return;
  }

  document.documentElement.classList.add('js-motion');

  // 1. Choreographed Hero Load Sequence
  const hero = document.querySelector<HTMLElement>('[data-hero]');
  if (hero) {
    requestAnimationFrame(() => {
      requestAnimationFrame(() => {
        hero.classList.add('hero-loaded');
      });
    });
  }

  // 2. Scroll-Triggered Staggered Reveals
  const revealNodes = Array.from(document.querySelectorAll<HTMLElement>('[data-reveal]'));
  if ('IntersectionObserver' in window && revealNodes.length > 0) {
    const isCompact = window.innerWidth < 768;
    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            entry.target.classList.add('is-visible');
            observer.unobserve(entry.target);
          }
        }
      },
      {
        threshold: isCompact ? 0.04 : 0.1,
        rootMargin: isCompact ? '0px 0px -12px 0px' : '0px 0px -42px 0px',
      }
    );
    revealNodes.forEach((node) => observer.observe(node));
  } else {
    revealNodes.forEach((node) => node.classList.add('is-visible'));
  }

  // 3. Top Reading Progress Hairline + Scroll-Scrubbed Triptych Parallax
  const progressBar = document.querySelector<HTMLElement>('[data-scroll-progress]');
  const stage = document.querySelector<HTMLElement>('[data-triptych-stage]');
  const wingLeft = stage?.querySelector<HTMLElement>('[data-wing-left]');
  const centerDuo = stage?.querySelector<HTMLElement>('[data-center-duo]');
  const wingRight = stage?.querySelector<HTMLElement>('[data-wing-right]');

  let ticking = false;

  const onFrame = () => {
    ticking = false;

    if (progressBar) {
      const maxScroll = Math.max(1, document.documentElement.scrollHeight - window.innerHeight);
      const ratio = Math.min(1, Math.max(0, window.scrollY / maxScroll));
      progressBar.style.transform = `scaleX(${ratio.toFixed(4)})`;
    }

    if (stage && wingLeft && centerDuo && wingRight) {
      if (window.innerWidth < 1080) {
        wingLeft.style.transform = '';
        centerDuo.style.transform = '';
        wingRight.style.transform = '';
        return;
      }
      const rect = stage.getBoundingClientRect();
      const vh = window.innerHeight || 1;
      if (rect.bottom < -60 || rect.top > vh + 60) return;

      // Smooth normalized scroll progress across the hero stage
      const p = Math.min(1, Math.max(-0.4, (vh * 0.72 - rect.top) / (vh + rect.height * 0.55)));
      const spread = p * 22;
      const lift = p * -14;

      centerDuo.style.transform = `translate3d(0, ${(lift * 0.6).toFixed(2)}px, 0)`;
      wingLeft.style.transform = `translate3d(${(-spread).toFixed(2)}px, ${(34 + p * 12).toFixed(2)}px, 0) rotate(${(-4.5 - p * 1.4).toFixed(2)}deg) scale(0.95)`;
      wingRight.style.transform = `translate3d(${spread.toFixed(2)}px, ${(34 + p * 12).toFixed(2)}px, 0) rotate(${(4.5 + p * 1.4).toFixed(2)}deg) scale(0.95)`;
    }
  };

  const schedule = () => {
    if (!ticking) {
      ticking = true;
      requestAnimationFrame(onFrame);
    }
  };

  schedule();
  window.addEventListener('scroll', schedule, { passive: true });
  window.addEventListener('resize', schedule, { passive: true });
}
