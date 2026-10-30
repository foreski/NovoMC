// Novo Client site — small, dependency-free behaviour.
(function () {
  const head = document.getElementById('siteHead');
  if (head) {
    const onScroll = () => head.classList.toggle('stuck', window.scrollY > 8);
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
  }

  // The small-screen menu is a <details>. Left to itself it stays open after a
  // jump, which hides the section it just scrolled to.
  const menu = document.querySelector('.menu');
  if (menu) {
    menu.querySelectorAll('a').forEach((link) => {
      link.addEventListener('click', () => {
        menu.open = false;
      });
    });
    document.addEventListener('click', (event) => {
      if (menu.open && !menu.contains(event.target)) menu.open = false;
    });
    document.addEventListener('keydown', (event) => {
      if (event.key === 'Escape') menu.open = false;
    });
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
  // Captured before anything rewrites it: the version the markup itself ships
  // with, which is what the release script keeps current.
  const pageVersion = (() => {
    const mark = document.querySelector('[data-version]');
    return mark ? mark.textContent.trim() : '';
  })();

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

  // A cached answer can be older than the page carrying it: the release script
  // rewrites the markup's version on every release, so markup newer than the
  // cache means the cache is simply out of date and must not win. Without this,
  // someone who visited shortly before a release keeps the old download link
  // for the rest of the TTL.
  const isNewer = (left, right) => {
    const a = String(left).split('.').map(Number);
    const b = String(right).split('.').map(Number);
    for (let i = 0; i < Math.max(a.length, b.length); i += 1) {
      const diff = (a[i] || 0) - (b[i] || 0);
      if (diff) return diff > 0;
    }
    return false;
  };

  const syncRelease = () => {
    let cached = cachedRelease();
    if (cached && isNewer(pageVersion, cached.version)) cached = null;
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

  // Release history. Same API and the same per-IP budget as the version above,
  // so it uses the same shape: paint from the cached copy, refresh behind it.
  // A hundred is the API's page size, and it has to be that wide: the download
  // total below only adds up if every release is in the answer. The changelog
  // then shows the newest few of them.
  const RELEASES_API = 'https://api.github.com/repos/foreski/NovoMC/releases?per_page=100';
  const RELEASES_CACHE = 'novo.releases';
  const CHANGELOG_ITEMS = 6;

  const list = document.getElementById('releases');
  const status = document.getElementById('releasesStatus');

  const setStatus = (text) => {
    if (!status) return;
    status.textContent = text || '';
    status.hidden = !text;
  };

  // The notes arrive as Markdown. Only the part these releases actually use is
  // honoured: blank lines, `-`/`*` bullets, headings and inline emphasis. Every
  // string goes in as a text node, so a note can never inject markup.
  const plain = (text) =>
    text
      .replace(/\*\*(.+?)\*\*/g, '$1')
      .replace(/`([^`]+)`/g, '$1')
      .replace(/\[([^\]]+)\]\([^)]+\)/g, '$1');

  const renderNotes = (target, body) => {
    let bullets = null;
    for (const raw of String(body || '').split(/\r?\n/)) {
      const line = raw.trim();
      if (!line) {
        bullets = null;
        continue;
      }
      const bullet = /^[-*+]\s+(.*)$/.exec(line);
      if (bullet) {
        if (!bullets) {
          bullets = document.createElement('ul');
          target.appendChild(bullets);
        }
        const item = document.createElement('li');
        item.textContent = plain(bullet[1]);
        bullets.appendChild(item);
        continue;
      }
      bullets = null;
      const paragraph = document.createElement('p');
      paragraph.textContent = plain(line.replace(/^#+\s*/, ''));
      target.appendChild(paragraph);
    }
  };

  // Every release re-downloads the whole installer, so this counts downloads
  // rather than people. It is what GitHub counts and what everyone quotes.
  const installerDownloads = (releases) =>
    releases.reduce(
      (sum, release) =>
        sum +
        (release.assets || []).reduce(
          (count, asset) => count + (/_x64-setup\.exe$/i.test(asset.name || '') ? Number(asset.download_count) || 0 : 0),
          0
        ),
      0
    );

  const applyDownloads = (total) => {
    if (!Number.isFinite(total) || total <= 0) return;
    document.querySelectorAll('[data-downloads]').forEach((el) => {
      el.textContent = total.toLocaleString();
    });
  };

  const renderReleases = (releases) => {
    if (!list || !Array.isArray(releases)) return false;
    const shown = releases
      .filter((release) => release && release.tag_name && !release.draft && !release.prerelease)
      .slice(0, CHANGELOG_ITEMS);
    if (!shown.length) return false;
    list.textContent = '';
    shown.forEach((release, index) => {
      const row = document.createElement('li');
      row.className = index === 0 ? 'release release-latest' : 'release';

      const head = document.createElement('div');
      head.className = 'release-head';
      const version = document.createElement('span');
      version.className = 'release-ver';
      version.textContent = String(release.tag_name).replace(/^v/, '');
      head.appendChild(version);
      if (index === 0) {
        const badge = document.createElement('span');
        badge.className = 'release-tag';
        badge.textContent = 'latest';
        head.appendChild(badge);
      }
      const published = release.published_at ? new Date(release.published_at) : null;
      if (published && !Number.isNaN(published.getTime())) {
        const when = document.createElement('time');
        when.className = 'release-date';
        when.dateTime = published.toISOString().slice(0, 10);
        when.textContent = published.toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' });
        head.appendChild(when);
      }
      row.appendChild(head);
      renderNotes(row, release.body);
      list.appendChild(row);
    });
    list.hidden = false;
    return true;
  };

  const cachedReleases = () => {
    try {
      const cached = JSON.parse(localStorage.getItem(RELEASES_CACHE) || 'null');
      return cached && Array.isArray(cached.items) ? cached : null;
    } catch (error) {
      return null;
    }
  };

  const syncReleases = () => {
    const cached = cachedReleases();
    if (cached && renderReleases(cached.items)) setStatus('');
    if (cached) applyDownloads(cached.total);
    // Same staleness test as the download link: if the page is showing a newer
    // version than the cached list knows about, the list has to be re-read.
    const newestCached = cached && cached.items[0] ? String(cached.items[0].tag_name || '').replace(/^v/, '') : '';
    const behind = newestCached && isNewer(pageVersion, newestCached);
    // Caches written before the total existed have no `total`, so they are
    // stale for that too.
    if (cached && !behind && cached.total != null && Date.now() - cached.at < RELEASE_TTL) return;
    fetch(RELEASES_API, { headers: { Accept: 'application/vnd.github+json' } })
      .then((response) => (response.ok ? response.json() : Promise.reject(new Error(String(response.status)))))
      .then((releases) => {
        const all = (Array.isArray(releases) ? releases : []).filter(
          (release) => release && release.tag_name && !release.draft && !release.prerelease
        );
        const items = all.slice(0, CHANGELOG_ITEMS).map((release) => ({
          tag_name: release.tag_name,
          published_at: release.published_at,
          body: String(release.body || '').slice(0, 1200),
        }));
        const total = installerDownloads(all);
        if (!renderReleases(items)) return;
        applyDownloads(total);
        setStatus('');
        try {
          localStorage.setItem(RELEASES_CACHE, JSON.stringify({ items, total, at: Date.now() }));
        } catch (error) {
          /* private mode: the list just reloads next visit */
        }
      })
      .catch(() => {
        if (!list || list.hidden) {
          setStatus("The release history didn't load just now. The launcher's update check still finds new versions on its own.");
        }
      });
  };

  syncReleases();
})();
