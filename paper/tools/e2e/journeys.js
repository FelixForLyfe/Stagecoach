// Crash safety with the real client: quit, /stop and a kill mid-journey, checked in the saved .dat file.
// Usage: node journeys.js <server dir> <server jar> <rcon port>
'use strict';
const { spawn } = require('child_process');
const fs = require('fs');
const path = require('path');
const net = require('net');
const crypto = require('crypto');
const nbt = require('prismarine-nbt');

const [SERVER_DIR, SERVER_JAR, RCON_PORT] = process.argv.slice(2);
const JAVA = 'C:/Program Files/Eclipse Adoptium/jdk-21.0.11.10-hotspot/bin/java.exe';
const DEVCLIENT = path.resolve(__dirname, '../../devclient');
const P = 'Shooter';
const X = 200, Z = 200, Y = -60;
const results = [];
let server = null, serverLog = [], client = null;

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
async function waitFor(pred, ms, label) {
  const end = Date.now() + ms;
  while (Date.now() < end) {
    try { if (await pred()) return true; } catch (e) { /* retry */ }
    await sleep(250);
  }
  throw new Error('timed out waiting for ' + label);
}
const check = (c, label) => { if (!c) throw new Error('check failed: ' + label); };

function rcon(command) {
  return new Promise((resolve, reject) => {
    const s = net.createConnection({ host: '127.0.0.1', port: Number(RCON_PORT) });
    const pkt = (id, type, body) => {
      const b = Buffer.from(body, 'utf8'); const out = Buffer.alloc(14 + b.length);
      out.writeInt32LE(10 + b.length, 0); out.writeInt32LE(id, 4); out.writeInt32LE(type, 8); b.copy(out, 12); return out;
    };
    let buf = Buffer.alloc(0); let authed = false;
    s.on('data', (d) => {
      buf = Buffer.concat([buf, d]);
      while (buf.length >= 4 && buf.length >= 4 + buf.readInt32LE(0)) {
        const len = buf.readInt32LE(0); const id = buf.readInt32LE(4);
        const body = buf.slice(12, 4 + len - 2).toString('utf8'); buf = buf.slice(4 + len);
        if (!authed) { if (id === -1) { s.destroy(); return reject(new Error('rcon auth')); } authed = true; s.write(pkt(2, 2, command)); }
        else { s.end(); resolve(body.replace(/§./g, '')); }
      }
    });
    s.on('error', reject);
    s.on('connect', () => s.write(pkt(1, 3, 'stagecoach')));
    s.setTimeout(15000, () => { s.destroy(); reject(new Error('rcon timeout')); });
  });
}
const say = (cmd) => rcon(`tellraw ${P} {"text":"#${cmd}"}`);

function startServer() {
  serverLog = [];
  server = spawn(JAVA, ['-Xmx2G', '-jar', SERVER_JAR, 'nogui'], { cwd: SERVER_DIR });
  const on = (d) => { for (const l of d.toString().split(/\r?\n/)) if (l) serverLog.push(l); };
  server.stdout.on('data', on); server.stderr.on('data', on);
  server.exited = new Promise((r) => server.on('exit', r));
  return waitFor(() => serverLog.some((l) => /Done \(/.test(l)), 300000, 'server start');
}
async function stopServer() { const s = server; await rcon('stop').catch(() => {}); await Promise.race([s.exited, sleep(60000)]); }
async function crashServer() { const s = server; s.kill('SIGKILL'); await s.exited; }
const logHas = (re) => serverLog.some((l) => re.test(l));

/** The harness client, hidden and muted; resolves once it is in the game. */
async function launchClient() {
  client = spawn(path.join(DEVCLIENT, 'gradlew.bat'), ['runHarness', '--console=plain'], { cwd: DEVCLIENT, shell: true });
  client.exited = new Promise((r) => client.on('exit', r));
  await waitFor(async () => (await rcon('list')).includes(P), 180000, 'the harness client to join');
  await sleep(5000);
}
async function quitClient() {
  await say('quit').catch(() => {});
  await Promise.race([client.exited, sleep(30000)]);
  await sleep(1000);
}

function offlineUuid(name) {
  const h = crypto.createHash('md5').update('OfflinePlayer:' + name).digest();
  h[6] = (h[6] & 0x0f) | 0x30; h[8] = (h[8] & 0x3f) | 0x80;
  const x = h.toString('hex');
  return `${x.slice(0, 8)}-${x.slice(8, 12)}-${x.slice(12, 16)}-${x.slice(16, 20)}-${x.slice(20)}`;
}
async function playerFile() {
  const { parsed } = await nbt.parse(fs.readFileSync(path.join(SERVER_DIR, 'world/playerdata', offlineUuid(P) + '.dat')));
  return nbt.simplify(parsed);
}
const mode = async (m) => (await rcon(`execute if entity @a[name=${P},gamemode=${m}]`)).includes('passed');
const level = async () => Number((await rcon(`xp query ${P} levels`)).match(/(\d+) experience levels/)[1]);

async function scenario(name, fn) {
  process.stdout.write('- ' + name + ' ... ');
  try { await fn(); results.push([name, 'PASS']); console.log('PASS'); }
  catch (e) { results.push([name, 'FAIL: ' + e.message]); console.log('FAIL\n    ' + e.message); }
}

/** Riverrun at (X, Z), Harrenhal 1500 blocks east - placed and named through the real screens. */
async function placeCoaches() {
  await rcon(`gamemode survival ${P}`);
  await rcon(`xp set ${P} 30 levels`);
  for (const [x, name] of [[X, 'Riverrun'], [X + 1500, 'Harrenhal']]) {
    await rcon(`clear ${P}`);
    await rcon(`fill ${x - 8} ${Y} ${Z - 30} ${x + 8} ${Y + 6} ${Z + 8} minecraft:air`);
    await rcon(`tp ${P} ${x + 0.5} ${Y} ${Z + 0.5} 180 40`);
    await sleep(2500);
    await rcon(`stagecoach give ${P} oak 1`);
    await sleep(800);
    await say(`use ${x} ${Y - 1} ${Z - 3} up`);
    await sleep(1500);
    await say(`type ${name}`);
    await say('click Confirm');
    await waitFor(async () => (await rcon('stagecoach list')).includes(name), 8000, name + ' placed');
  }
}

/** From Riverrun: the list, Harrenhal, Set off - until the server has them in spectator. */
async function setOff() {
  await rcon(`tp ${P} ${X + 0.5} ${Y} ${Z + 0.5} 180 10`);
  await sleep(2500);
  await say(`use ${X} ${Y} ${Z - 3} south`);
  await sleep(1500);
  await say('click Harrenhal');
  await sleep(1500);
  await say('click Set off');
  await waitFor(() => mode('spectator'), 6000, 'the journey to start');
}

async function main() {
  fs.rmSync(path.join(SERVER_DIR, 'world'), { recursive: true, force: true });
  fs.rmSync(path.join(SERVER_DIR, 'plugins/Stagecoach'), { recursive: true, force: true });
  await startServer();
  await launchClient();
  await placeCoaches();

  await scenario('a journey from the screens pays, and quitting mid-way saves the player back at the start', async () => {
    await setOff();
    check(await level() === 28, 'paid 2 levels');
    check(JSON.stringify((await playerFile()).BukkitValues || {}).includes('stagecoach:journey'), 'mirror in the file');
    await quitClient();
    await waitFor(() => logHas(/Shooter's journey to 'Harrenhal' ended early: DISCONNECTED/), 5000, 'the quit to end it');
    const f = await playerFile();
    check(f.playerGameType === 0 && f.XpLevel === 30, `file survival/30, got ${f.playerGameType}/${f.XpLevel}`);
    check(Math.abs(f.Pos[2] - (Z - 3 - 3.5)) < 0.7, 'saved in front of Riverrun: ' + f.Pos);
    check(!JSON.stringify(f.BukkitValues || {}).includes('stagecoach:journey'), 'no mirror left');
  });

  await scenario('/stop mid-journey writes the player back before the shutdown save', async () => {
    await launchClient();
    await setOff();
    await stopServer();
    await Promise.race([client.exited, sleep(20000)]);
    check(logHas(/ended early: SERVER_STOPPING/), 'SERVER_STOPPING logged');
    const f = await playerFile();
    check(f.playerGameType === 0 && f.XpLevel === 30, `file survival/30, got ${f.playerGameType}/${f.XpLevel}`);
  });

  await scenario('a hard crash mid-journey is recovered at the next login', async () => {
    await startServer();
    await launchClient();
    await setOff();
    await crashServer();
    await Promise.race([client.exited, sleep(15000)]);
    if (client.exitCode === null) { client.kill(); await sleep(2000); }
    const f = await playerFile();
    check(f.playerGameType === 3 && f.XpLevel === 28, `the file really says spectator/28: ${f.playerGameType}/${f.XpLevel}`);
    await startServer();
    await launchClient();
    await waitFor(() => mode('survival'), 8000, 'recovered to survival');
    check(await level() === 30, 'levels back');
    check(logHas(/Recovered Shooter's interrupted journey/), 'recovery logged');
  });

  await quitClient();
  await stopServer();
  const errors = serverLog.filter((l) => /ERROR|Exception/.test(l));
  await scenario('no errors in the last server log', async () => check(errors.length === 0, errors.slice(0, 5).join('\n')));
}

main().catch((e) => { console.error('HARNESS ERROR', e); results.push(['harness', 'FAIL: ' + e.message]); })
  .finally(async () => {
    if (client && client.exitCode === null) client.kill();
    if (server && server.exitCode === null) { try { await stopServer(); } catch (e) { server.kill('SIGKILL'); } }
    const failed = results.filter((r) => r[1] !== 'PASS');
    console.log(`\n${results.length - failed.length}/${results.length} passed`);
    for (const f of failed) console.log('FAILED: ' + f[0] + ' -> ' + f[1]);
    process.exit(failed.length ? 1 : 0);
  });
