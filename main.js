// Novo Client site — small, dependency-free behaviour.
(function () {
  const head = document.getElementById('siteHead');
  if (head) {
    const onScroll = () => head.classList.toggle('stuck', window.scrollY > 8);
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
  }

  const year = document.getElementById('year');
  if (year) year.textContent = new Date().getFullYear();

  const revealables = document.querySelectorAll('.reveal');
  if ('IntersectionObserver' in window) {
    const io = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            entry.target.classList.add('in');
            io.unobserve(entry.target);
          }
        }
      },
      { rootMargin: '0px 0px -8% 0px', threshold: 0.08 }
    );
    revealables.forEach((el) => io.observe(el));
  } else {
    revealables.forEach((el) => el.classList.add('in'));
  }

  const lightbox = document.getElementById('lightbox');
  const lightboxImg = document.getElementById('lightboxImg');
  const lightboxClose = document.getElementById('lightboxClose');

  const close = () => {
    if (!lightbox) return;
    lightbox.hidden = true;
    lightboxImg.removeAttribute('src');
    document.body.style.overflow = '';
  };

  document.querySelectorAll('.shot').forEach((shot) => {
    const img = shot.querySelector('img');
    if (!img || !lightbox) return;
    shot.addEventListener('click', () => {
      lightboxImg.src = img.src;
      lightboxImg.alt = img.alt;
      lightbox.hidden = false;
      document.body.style.overflow = 'hidden';
    });
  });

  if (lightboxClose) lightboxClose.addEventListener('click', close);
  if (lightbox) {
    lightbox.addEventListener('click', (event) => {
      if (event.target === lightbox) close();
    });
  }
  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape') close();
  });
})();
