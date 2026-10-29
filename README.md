# Novo Client website

This branch is the public site. It is deliberately not on `main`: the source repo stays clean, and GitHub
Pages can serve this branch as-is.

## What's here

```
index.html      the whole page
styles.css      all styling; palette comes from assets/branding/BRANDING.md
main.js         sticky header, scroll reveals, screenshot lightbox, footer year
assets/         logo, launcher and in-game screenshots, the showcase video
.nojekyll       tells Pages to serve the files untouched
```

Plain HTML, CSS and JavaScript, no build step and no dependencies. Edit a file, commit, and that is the
deploy.

## Preview locally

Any static server pointed at this directory works:

```bash
npx serve .          # or: python -m http.server 8080
```

Opening `index.html` from disk works too, apart from the video.

## Publishing

1. Push this branch.
2. On GitHub: Settings → Pages → Source: "Deploy from a branch", branch `website`, folder `/ (root)`.
3. The site appears at `https://foreski.github.io/NovoMC/`, and once the custom domain below resolves, that
   URL redirects to `https://novoclient.cc`.

## Custom domain: novoclient.cc via Cloudflare

The `CNAME` file at the root of this branch holds `novoclient.cc`, so Pages treats that as the canonical
address and `foreski.github.io/NovoMC` redirects to it. Everything below is the Cloudflare side.

### 1. Put the domain on Cloudflare

The domain is registered at Namecheap and still uses Namecheap's nameservers, so Cloudflare has to be added
first: Cloudflare dashboard → Add a site → `novoclient.cc` → Free plan. Cloudflare gives you a pair like
`ada.ns.cloudflare.com` and `bob.ns.cloudflare.com`. At Namecheap → Domain List → `novoclient.cc` → Nameservers
→ Custom DNS, replace the two registrar nameservers with that pair. Propagation is usually minutes, up to 24h.

### 2. DNS records in Cloudflare

Add these with the proxy **off** (grey cloud, "DNS only") — GitHub has to reach the record itself to issue the
TLS certificate, and a proxied record blocks that.

| Type | Name | Content | Proxy |
| :-- | :-- | :-- | :-- |
| A | `@` | `185.199.108.153` | DNS only |
| A | `@` | `185.199.109.153` | DNS only |
| A | `@` | `185.199.110.153` | DNS only |
| A | `@` | `185.199.111.153` | DNS only |
| CNAME | `www` | `foreski.github.io` | DNS only |

Delete the parking records Cloudflare imports (the `A`/`CNAME` pointing at Namecheap's parking page) or they
will shadow the GitHub ones.

If GitHub asks you to verify the domain, it shows a `TXT` record named
`_github-pages-challenge-foreski.novoclient.cc` with a token — add that too, DNS only, then press Verify.

### 3. Cloudflare SSL/TLS

SSL/TLS → Overview → **Full**. Not Flexible: Flexible makes Cloudflare talk to GitHub over plain HTTP while
GitHub redirects to HTTPS, which produces a redirect loop. "Full (strict)" only starts working after GitHub
has issued its own certificate for the domain.

### 4. Order of operations

DNS first, then the custom domain, or the `github.io` URL redirects to a domain that does not answer yet. In
Pages settings the flow is: enter `novoclient.cc` → Save → wait for "DNS check successful" → tick
**Enforce HTTPS** once the certificate is issued.

### 5. The `www` certificate

GitHub issues a single certificate per custom domain, and it only includes `www.novoclient.cc` if the `www`
CNAME already resolved to Pages when the order was placed. Ours was proxied to Cloudflare at that moment, so the
certificate covers `novoclient.cc` alone:

```
novoclient.cc      -> CN=novoclient.cc          (valid)
www.novoclient.cc  -> CN=*.github.io            (name mismatch)
```

GitHub reuses that certificate until it expires (2027-01-27) rather than re-ordering it, so removing and
re-entering the custom domain does not help. Until then `https://www.novoclient.cc` warns, while
`http://www.novoclient.cc` correctly 301s to the apex. Two ways to close the gap:

- Let GitHub renew, or open a Pages support ticket to force a re-issue now.
- Handle `www` at Cloudflare instead: set the `www` CNAME to proxied (orange cloud), add a Redirect Rule
  sending `www.novoclient.cc` 301 to `https://novoclient.cc`, and keep SSL/TLS on Full. Visitors then get
  Cloudflare's edge certificate. GitHub will flag `www` as improperly configured again — cosmetic, since the
  redirect answers before GitHub is reached.

### 6. Optional: turn the apex proxy on

Once GitHub reports the certificate as issued, you can flip the apex records to proxied (orange cloud) for
Cloudflare's caching and WAF. Keep SSL/TLS on Full (strict) — GitHub's certificate is valid for the name, so
strict works. GitHub may then show the certificate as unmanaged; visitors get Cloudflare's edge certificate.
Most people leave the apex on DNS only and keep it simple.

### Checking it

```bash
nslookup novoclient.cc            # expect the four 185.199.10x.153 addresses
nslookup www.novoclient.cc        # expect foreski.github.io
curl -sI https://novoclient.cc    # expect 200 and server: GitHub.com
```

## The download buttons

The three Download buttons point straight at the installer asset rather than at the releases page, so a click
starts the download. The asset name contains the version, so bump it with each release:

```
releases/download/v0.4.1/NovoMC_0.4.1_x64-setup.exe
```

Grep `index.html` for `x64-setup.exe` to find all three.

## Keeping the screenshots current

`assets/` holds copies. Refresh them from `main` when the UI changes:

```bash
git checkout main -- docs/images/launcher-home.png docs/images/ingame-menu.png \
  docs/images/ingame-mods.png docs/images/ingame-video.png docs/images/ingame-packs.png
git show main:docs/media/novomc-showcase.mp4 > assets/showcase.mp4
```

(Adjust the names if the screenshots on `main` change.)
