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
3. The site appears at `https://<user>.github.io/<repo>/`.

## Custom domain

Add a file named `CNAME` at the root of this branch containing just the domain, for example:

```
novoclient.gg
```

Then in Settings → Pages → Custom domain, enter the same name and enable HTTPS once the certificate is
ready. At your DNS provider point the record at GitHub Pages:

- apex domain (`example.com`): four `A` records to `185.199.108.153`, `.109.153`, `.110.153`, `.111.153`
- `www` (or any subdomain): `CNAME` to `<user>.github.io`

If you later publish to a custom domain, the `github.io/<repo>/` URL keeps working with a redirect. Links in
`index.html` are all either absolute (GitHub, releases) or relative to this directory, so both URLs work
without changes.

## Keeping the screenshots current

`assets/` holds copies. Refresh them from `main` when the UI changes:

```bash
git checkout main -- docs/images/launcher-home.png docs/images/ingame-menu.png \
  docs/images/ingame-mods.png docs/images/ingame-video.png docs/images/ingame-packs.png
git show main:docs/media/novomc-showcase.mp4 > assets/showcase.mp4
```

(Adjust the names if the screenshots on `main` change.)
