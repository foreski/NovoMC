// Builds the NovoMC Client Forge mod and copies the jar to
// src-tauri/resources/novomc-client.jar, where the launcher bundles it.
//
//   npm run mod
//
// Needs JDK 17+ for Gradle (JAVA_HOME, or a Temurin 17+ install) and JDK 8
// for compiling (found automatically by Gradle).
import { execFileSync } from 'node:child_process';
import { copyFileSync, existsSync, mkdirSync, readdirSync, readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const modDir = join(root, 'client-mod');

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

/** A JDK 17+ for Gradle: JAVA_HOME if it is new enough (the JDK 8 installer may have set it), else Temurin. */
function findJdk() {
  if (process.env.JAVA_HOME && javaMajor(process.env.JAVA_HOME) >= 17) return process.env.JAVA_HOME;
  const base = 'C:\\Program Files\\Eclipse Adoptium';
  if (existsSync(base)) {
    const jdk = readdirSync(base)
      .filter((d) => /^jdk-(1[7-9]|2\d)/.test(d))
      .sort()
      .pop();
    if (jdk) return join(base, jdk);
  }
  throw new Error('Install a JDK 17 or newer, or point JAVA_HOME at one');
}

const env = { ...process.env, JAVA_HOME: findJdk() };
const gradlew = join(modDir, process.platform === 'win32' ? 'gradlew.bat' : 'gradlew');
execFileSync(`"${gradlew}"`, ['build', '--no-daemon', '-q'], { cwd: modDir, env, stdio: 'inherit', shell: true });

const version = /^version=(.+)$/m.exec(readFileSync(join(modDir, 'gradle.properties'), 'utf8'))[1].trim();
const jar = join(modDir, 'build', 'libs', `novomc-${version}.jar`);
const out = join(root, 'src-tauri', 'resources', 'novomc-client.jar');
mkdirSync(dirname(out), { recursive: true });
copyFileSync(jar, out);
console.log(`Copied ${jar} -> ${out}`);
