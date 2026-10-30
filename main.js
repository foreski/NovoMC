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

  // Version and installer link follow the newest release, so a page nobody
  // touched after a release still points at the current build. The version in
  // the markup is the fallback for a failed request, or for no JavaScript at
  // all. Release assets send no CORS headers, so this reads the API instead;
  // that is rate limited per IP, hence the cached copy and the long interval.
  const RELEASE_API = 'https://api.github.com/repos/foreski/NovoMC/releases/latest';
  const RELEASE_CACHE = 'novo.release';
  const RELEASE_TTL = 3 * 60 * 60 * 1000;

  const applyRelease = (release) => {
    if (!release || !release.version || !release.url) return;
    document.querySelectorAll('[data-download]').forEach((link) => {
      link.href = release.url;
    });
    const current = document.querySelector('[data-version]');
    if (current && current.textContent !== release.version) current.textContent = release.version;
  };

  const cachedRelease = () => {
    try {
      const cached = JSON.parse(localStorage.getItem(RELEASE_CACHE) || 'null');
      return cached && cached.version && cached.url ? cached : null;
    } catch (error) {
      return null;
    }
  };

  const storeRelease = (release) => {
    try {
      localStorage.setItem(RELEASE_CACHE, JSON.stringify(release));
    } catch (error) {
      /* private mode: the request just runs again next visit */
    }
  };

  const syncRelease = () => {
    const cached = cachedRelease();
    if (cached) applyRelease(cached);
    if (cached && Date.now() - cached.at < RELEASE_TTL) return;
    fetch(RELEASE_API, { headers: { Accept: 'application/vnd.github+json' } })
      .then((response) => (response.ok ? response.json() : Promise.reject(new Error(String(response.status)))))
      .then((release) => {
        const version = String(release.tag_name || '').replace(/^v/, '');
        const asset = (release.assets || []).find((candidate) => /_x64-setup\.exe$/i.test(candidate.name || ''));
        if (!/^\d+\.\d+\.\d+/.test(version) || !asset || !asset.browser_download_url) return;
        const found = { version, url: asset.browser_download_url, at: Date.now() };
        storeRelease(found);
        applyRelease(found);
      })
      .catch(() => {
        /* offline, rate limited, or renamed asset: the markup stays as it shipped */
      });
  };

  syncRelease();
})();
