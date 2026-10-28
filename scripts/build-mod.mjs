// Builds both Novo Client mods and copies the jars into src-tauri/resources/,
// where the launcher bundles them:
//
//   client-mod/         1.8.9 Forge  -> novomc-client.jar
//   client-mod-modern/  26.3 Fabric  -> novomc-client-modern.jar
//
//   npm run mod
//
// The 1.8.9 build runs Gradle 8 on JDK 17-21 (and compiles with JDK 8, found
// by Gradle); the modern build needs JDK 25. Temurin installs are found
// automatically; JAVA_HOME is used when it fits.
import { execFileSync } from 'node:child_process';
import { copyFileSync, existsSync, mkdirSync, readdirSync, readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');

/** Major Java version of a JDK folder, from its `release` file. */
function javaMajor(home) {
  try {
    const v = /JAVA_VERSION="([^"]+)"/.exec(readFileSync(join(home, 'release'), 'utf8'))[1];
    const [first, second] = v.split('.').map(Number);
    return first === 1 ? second : first;
  } catch {
    return 0;
  }
}

/** A JDK whose major version is within [min, max]: JAVA_HOME if it fits, else the newest fitting Temurin. */
function findJdk(min, max) {
  const fits = (home) => {
    const v = javaMajor(home);
    return v >= min && v <= max;
  };
  if (process.env.JAVA_HOME && fits(process.env.JAVA_HOME)) return process.env.JAVA_HOME;
  const base = 'C:\\Program Files\\Eclipse Adoptium';
  if (existsSync(base)) {
    const jdk = readdirSync(base)
      .map((d) => join(base, d))
      .filter(fits)
      .sort((a, b) => javaMajor(a) - javaMajor(b))
      .pop();
    if (jdk) return jdk;
  }
  throw new Error(`Install a JDK ${min === max ? min : `${min}-${max}`} (Temurin), or point JAVA_HOME at one`);
}

function build(dir, jdk, jarName, out) {
  const env = { ...process.env, JAVA_HOME: jdk };
  const gradlew = join(dir, process.platform === 'win32' ? 'gradlew.bat' : 'gradlew');
  execFileSync(`"${gradlew}"`, ['build', '--no-daemon', '-q'], { cwd: dir, env, stdio: 'inherit', shell: true });
  const version = /^version=(.+)$/m.exec(readFileSync(join(dir, 'gradle.properties'), 'utf8'))[1].trim();
  const jar = join(dir, 'build', 'libs', jarName(version));
  const dest = join(root, 'src-tauri', 'resources', out);
  mkdirSync(dirname(dest), { recursive: true });
  copyFileSync(jar, dest);
  console.log(`Copied ${jar} -> ${dest}`);
}

build(join(root, 'client-mod'), findJdk(17, 21), (v) => `novomc-${v}.jar`, 'novomc-client.jar');
build(join(root, 'client-mod-modern'), findJdk(25, 25), (v) => `novomc-modern-${v}.jar`, 'novomc-client-modern.jar');
