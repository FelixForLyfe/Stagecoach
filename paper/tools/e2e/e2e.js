// World rules on a real server with bot clients; the screens need a real client (journeys.js, shots.js)
// Usage (after npm install): node e2e.js <server dir> <server jar> <game port> <rcon port>
// The world is deleted first, so every run starts from a fresh flat world.
'use strict';
const { spawn } = require('child_process');
const fs = require('fs');
const path = require('path');
const net = require('net');
const crypto = require('crypto');
const mineflayer = require('mineflayer');
const nbt = require('prismarine-nbt');
const { Vec3 } = require('vec3');

const [SERVER_DIR, SERVER_JAR, PORT, RCON_PORT] = process.argv.slice(2);
const JAVA = 'C:/Program Files/Eclipse Adoptium/jdk-21.0.11.10-hotspot/bin/java.exe';
const Y = -60; // feet height on the flat world (grass at -61)
const results = [];
let server = null;
let serverLog = [];

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function waitFor(pred, ms, label) {
  const end = Date.now() + ms;
  while (Date.now() < end) {
    try { const v = await pred(); if (v) return v; } catch (e) { /* retry */ }
    await sleep(100);
  }
  throw new Error('timed out waiting for ' + label);
}

function check(cond, label) {
  if (!cond) throw new Error('check failed: ' + label);
}

async function scenario(name, fn) {
  process.stdout.write('- ' + name + ' ... ');
  try {
    await fn();
    results.push([name, 'PASS']);
    console.log('PASS');
  } catch (e) {
    results.push([name, 'FAIL: ' + e.message]);
    console.log('FAIL\n    ' + e.message);
    if (afterFail) await afterFail().catch((err) => console.log('    (clean-up failed: ' + err.message + ')'));
  }
}

/** Puts Alice back to a known state so one failure can't fail every scenario after it. */
let afterFail = null;

function rcon(command) {
  return new Promise((resolve, reject) => {
    const s = net.createConnection({ host: '127.0.0.1', port: Number(RCON_PORT) });
    const pkt = (id, type, body) => {
      const b = Buffer.from(body, 'utf8');
      const out = Buffer.alloc(14 + b.length);
      out.writeInt32LE(10 + b.length, 0); out.writeInt32LE(id, 4); out.writeInt32LE(type, 8);
      b.copy(out, 12); return out;
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
    s.setTimeout(15000, () => { s.destroy(); reject(new Error('rcon timeout: ' + command)); });
  });
}

function offlineUuid(name) {
  const h = crypto.createHash('md5').update('OfflinePlayer:' + name).digest();
  h[6] = (h[6] & 0x0f) | 0x30; h[8] = (h[8] & 0x3f) | 0x80;
  const x = h.toString('hex');
  return `${x.slice(0, 8)}-${x.slice(8, 12)}-${x.slice(12, 16)}-${x.slice(16, 20)}-${x.slice(20)}`;
}

async function playerFile(name) {
  const f = path.join(SERVER_DIR, 'world', 'playerdata', offlineUuid(name) + '.dat');
  const { parsed } = await nbt.parse(fs.readFileSync(f));
  return nbt.simplify(parsed);
}

function startServer() {
  serverLog = [];
  server = spawn(JAVA, ['-Xmx2G', '-jar', SERVER_JAR, 'nogui'], { cwd: SERVER_DIR });
  const onData = (d) => { for (const l of d.toString().split(/\r?\n/)) if (l) serverLog.push(l); };
  server.stdout.on('data', onData); server.stderr.on('data', onData);
  const exited = new Promise((r) => server.on('exit', r));
  server.exited = exited;
  return waitFor(() => serverLog.some((l) => /Done \(/.test(l)), 300000, 'server start');
}

async function stopServer() {
  const s = server;
  await rcon('stop').catch(() => {});
  await Promise.race([s.exited, sleep(60000)]);
}

async function crashServer() {
  const s = server;
  s.kill('SIGKILL'); // only the JVM this script started
  await s.exited;
}

const logHas = (re) => serverLog.some((l) => re.test(l));

async function join(name) {
  const bot = mineflayer.createBot({ host: '127.0.0.1', port: Number(PORT), username: name, version: '1.21.11', auth: 'offline' });
  bot.chats = []; bot.bars = []; bot.titles = []; bot.cameraTarget = null;
  bot._client.on('error', (e) => console.log('\n    [' + name + ' client error] ' + e.message));
  bot.on('message', (m, pos) => { (pos === 'game_info' ? bot.bars : bot.chats).push(m.toString()); });
  bot.on('actionBar', (m) => bot.bars.push(m.toString()));
  bot.on('title', (t) => bot.titles.push(String(t)));
  bot._client.on('camera', (p) => { bot.cameraTarget = p.cameraId; });
  bot.dialogs = [];
  bot._client.on('show_dialog', (d) => bot.dialogs.push(d));
  // Paper sends action-bar text as its own packet (an NBT text component): keep its words, in order.
  bot._client.on('action_bar', (p) => bot.bars.push(flatText(p.text)));
  await new Promise((res, rej) => { bot.once('spawn', res); bot.once('kicked', (r) => rej(new Error('kicked: ' + r))); bot.once('error', rej); });
  await sleep(1500);
  return bot;
}

async function leave(bot) {
  const done = new Promise((r) => bot.once('end', r));
  bot.quit();
  await Promise.race([done, sleep(5000)]);
  await sleep(1000);
}

/** The plain text of an NBT text component (a translated name stays as its key, e.g. entity.minecraft.pig). */
function flatText(tag) {
  const walk = (c) => {
    if (c == null) return '';
    if (typeof c === 'string') return c;
    if (Array.isArray(c)) return c.map(walk).join('');
    return (c.text || '') + (c.translate || '') + (c[''] || '') + walk(c.extra);
  };
  try { return walk(nbt.simplify(tag)); } catch (e) { return ''; }
}

const lastBar = (bot) => bot.bars[bot.bars.length - 1] || '';
const text = (item) => (item ? JSON.stringify(item) : '');

async function goTo(bot, x, z, yLook, lookX, lookZ) {
  await rcon(`tp ${bot.username} ${x} ${Y} ${z}`);
  await waitFor(() => bot.entity.position.distanceTo(new Vec3(x, Y, z)) < 0.6, 10000, 'teleport ' + bot.username);
  await sleep(600);
  if (lookX !== undefined) await bot.lookAt(new Vec3(lookX, yLook, lookZ), true);
}

async function holdCoach(bot) {
  await waitFor(() => bot.inventory.items().some((i) => i.name === 'oak_chest_boat'), 5000, 'coach item');
  await bot.equip(bot.inventory.items().find((i) => i.name === 'oak_chest_boat'), 'hand');
}

/** From (x, z) facing north, onto the grass at (x, -61, z - 3). */
async function place(bot, x, z) {
  await goTo(bot, x + 0.5, z + 0.5, -61, x + 0.5, z - 2.5);
  await holdCoach(bot);
  bot.bars.length = 0;
  await bot.activateBlock(bot.blockAt(new Vec3(x, -61, z - 3)), new Vec3(0, 1, 0));
  await sleep(800);
}

async function coachId(name) {
  const out = await rcon('stagecoach list');
  const m = out.match(new RegExp('#(\\d+) ' + name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + ' '));
  return m ? Number(m[1]) : null;
}

async function displays(x, z) {
  const out = await rcon(`execute if entity @e[type=minecraft:item_display,x=${x + 0.5},y=${Y},z=${z + 0.5},distance=..0.1]`);
  const m = out.match(/count: (\d+)/i);
  return m ? Number(m[1]) : 0;
}

function hit(bot, x, y, z) {
  bot._seq = (bot._seq || 1000) + 1;
  bot._client.write('block_dig', { status: 0, location: { x, y, z }, face: 3, sequence: bot._seq });
  bot._client.write('block_dig', { status: 1, location: { x, y, z }, face: 3, sequence: bot._seq + 1 });
  bot._seq++;
}

async function main() {
  for (const d of ['world', 'world_nether', 'world_the_end', 'plugins/Stagecoach']) {
    fs.rmSync(path.join(SERVER_DIR, d), { recursive: true, force: true });
  }
  fs.copyFileSync(path.join(__dirname, 'protect-plugin/E2EProtect.jar'), path.join(SERVER_DIR, 'plugins/E2EProtect.jar'));
  await startServer();
  check(logHas(/\[Stagecoach\] Enabling Stagecoach/), 'plugin enabled');
  // Fast journeys for the test: 3000 blocks in 3 s, clamped to the 5 s minimum.
  const cfgPath = path.join(SERVER_DIR, 'plugins/Stagecoach/config.yml');
  fs.writeFileSync(cfgPath, fs.readFileSync(cfgPath, 'utf8').replace(/blocksPerSecond: 75/, 'blocksPerSecond: 1000').replace(/send: true/, 'send: false'));
  console.log(await rcon('stagecoach reload'));
  await rcon('gamerule sendCommandFeedback false');
  await rcon('forceload add -16 -32 16 16');
  await rcon('forceload add 2992 -32 3008 16');

  let alice = await join('Alice');
  let bob = await join('Bob');
  const A = 'Alice';
  await rcon('gamemode survival Bob'); // the test server makes newcomers creative, for a person testing by hand
  afterFail = async () => {
    if (alice.currentWindow) alice.closeWindow(alice.currentWindow);
    if (alice.game.gameMode === 'spectator') alice.chat('/stagecoach cancel confirm');
    await sleep(1000);
    await rcon(`gamemode survival ${A}`);
    await rcon(`xp set ${A} 28 levels`);
    await sleep(500);
  };

  await scenario('config.yml and messages.yml are written on first start', async () => {
    check(fs.existsSync(cfgPath), 'config.yml');
    check(fs.existsSync(path.join(SERVER_DIR, 'plugins/Stagecoach/messages.yml')), 'messages.yml');
  });

  await scenario('give: a stagecoach item with the right name, model and tag', async () => {
    await rcon(`gamemode survival ${A}`);
    await rcon(`xp set ${A} 30 levels`);
    await rcon(`stagecoach give ${A} oak 3`);
    const item = await waitFor(() => alice.inventory.items().find((i) => i.name === 'oak_chest_boat'), 5000, 'item');
    const t = text(item);
    check(item.count === 3, 'count 3, got ' + item.count);
    check(t.includes('"Oak"') && t.includes('Stagecoach'), 'name: ' + t);
    check(t.includes('oak_chest_boat') && t.includes('stagecoach:wood'), 'model + tag: ' + t);
  });

  await scenario('preview: a see-through coach (99 pieces + 45 cell boxes) while aiming at free ground', async () => {
    await goTo(alice, 0.5, 0.5, -61, 0.5, -2.5);
    await holdCoach(alice);
    await waitFor(async () => /count: 144/i.test(await rcon('execute if entity @e[type=minecraft:item_display]')), 5000,
      'the ghost on the server');
  });

  await scenario('place: 28 barriers, 99 displays, registered, item used, naming screen', async () => {
    await place(alice, 0, 0);
    const id = await waitFor(() => coachId('Stagecoach 1'), 5000, 'registered');
    check(id === 1, 'id 1');
    check((await rcon('execute if block 0 -60 -3 minecraft:barrier')).includes('passed'), 'controller barrier');
    check((await displays(0, -3)) === 99, 'displays: ' + (await displays(0, -3)));
    let barriers = 0;
    for (let x = -1; x <= 1; x++) for (let y = -60; y <= -58; y++) for (let z = -6; z <= -1; z++) {
      if ((await rcon(`execute if block ${x} ${y} ${z} minecraft:barrier`)).includes('passed')) barriers++;
    }
    check(barriers === 28, 'barriers: ' + barriers);
    check(alice.inventory.items().find((i) => i.name === 'oak_chest_boat').count === 2, 'one item used');
    check(alice.dialogs.length > 0, 'the naming screen was shown');
  });

  await scenario('placement is refused where another stagecoach stands, with the reason', async () => {
    await goTo(alice, 0.5, 1.5, -61, 0.5, -1.5);
    await holdCoach(alice);
    alice.bars.length = 0;
    await alice.activateBlock(alice.blockAt(new Vec3(0, -61, -2)), new Vec3(0, 1, 0));
    await waitFor(() => alice.bars.some((b) => b.includes('another stagecoach')), 3000, 'refusal: ' + alice.bars);
    check((await coachId('Stagecoach 2')) === null, 'nothing registered');
  });

  await scenario('placement is refused with a mob in the way, naming it', async () => {
    await rcon('summon minecraft:pig 20.5 -60 -5.5 {NoAI:1b}');
    await place(alice, 20, 0);
    await waitFor(() => alice.bars.some((b) => /(Pig|entity\.minecraft\.pig) is in the way/.test(b)), 3000, 'pig refusal: ' + alice.bars);
    await rcon('kill @e[type=minecraft:pig]');
  });

  await scenario('a second coach 3000 blocks away', async () => {
    await place(alice, 3000, 0);
    await waitFor(async () => (await coachId('Stagecoach 2')) === 2, 5000, 'the second coach');
    check((await displays(3000, -3)) === 99, '99 displays');
  });

  await scenario('discover: Bob right-clicks the coach (action bar), then gets its destination list', async () => {
    await goTo(bob, 2.5, 0.5, Y, 1, -2);
    bob.bars.length = 0;
    await bob.activateBlock(bob.blockAt(new Vec3(1, Y, -2)), new Vec3(1, 0, 0));
    await waitFor(() => bob.bars.some((b) => b.includes('Stagecoach discovered: Stagecoach 1')), 3000, 'discover: ' + bob.bars);
    bob.dialogs.length = 0;
    await bob.activateBlock(bob.blockAt(new Vec3(1, Y, -2)), new Vec3(1, 0, 0));
    await waitFor(() => bob.dialogs.length > 0, 3000, 'the destination list');
  });

  await scenario('only the owner can take a coach down', async () => {
    bob.bars.length = 0;
    for (let i = 0; i < 4; i++) { hit(bob, 1, Y, -2); await sleep(150); }
    await waitFor(() => bob.bars.some((b) => b.includes('Only the owner')), 3000, 'owner refusal: ' + bob.bars);
    check((await coachId('Stagecoach 1')) === 1, 'still there');
  });

  await scenario('a land-protection plugin that refuses building refuses the coach too, leaving nothing behind', async () => {
    await rcon(`stagecoach give ${A} oak 1`);
    await place(alice, 110, 0);
    await waitFor(() => alice.bars.some((b) => b.includes("can't build on")), 3000, 'protected: ' + alice.bars);
    check((await rcon('execute if block 110 -60 -3 minecraft:air')).includes('passed'), 'controller cell left empty');
    check((await rcon('execute if block 111 -60 -2 minecraft:air')).includes('passed'), 'no stray barrier');
    check((await displays(110, -3)) === 0, 'no displays');
  });

  await scenario('water, pistons and TNT leave the coach alone', async () => {
    // Water in front would flow into the empty cells around the shaft tip.
    await rcon('setblock 3000 -60 -7 minecraft:water');
    await sleep(2500);
    check((await rcon('execute if block 3000 -60 -6 minecraft:air')).includes('passed'), 'water stayed out');
    await rcon('setblock 3000 -60 -7 minecraft:air');
    // A piston pushing a block into the same cell.
    await rcon('setblock 3000 -60 -7 minecraft:stone');
    await rcon('setblock 3000 -60 -8 minecraft:piston[facing=south]');
    await rcon('setblock 3000 -60 -9 minecraft:redstone_block');
    await sleep(1000);
    check((await rcon('execute if block 3000 -60 -6 minecraft:air')).includes('passed'), 'piston did not push in');
    check((await rcon('execute if block 3000 -60 -7 minecraft:stone')).includes('passed'), 'stone stayed put');
    await rcon('fill 3000 -60 -9 3000 -60 -7 minecraft:air');
    await rcon('summon minecraft:tnt 3003.5 -60 -2.5 {fuse:1}');
    await sleep(1500);
    check((await rcon('execute if block 3000 -60 -3 minecraft:barrier')).includes('passed'), 'barriers survive');
    check((await displays(3000, -3)) === 99, 'all 99 displays survive');
    check((await coachId('Stagecoach 2')) !== null, 'still registered');
    await rcon('fill 2996 -61 -8 3006 -61 2 minecraft:grass_block replace minecraft:air');
    await rcon('fill 2996 -63 -8 3006 -62 2 minecraft:dirt replace minecraft:air');
  });

  await scenario('the owner takes the first coach down in survival: 3 hits, item dropped, blocks and displays gone', async () => {
    await goTo(alice, 0.5, 0.5, Y, 0, -3);
    for (let i = 0; i < 3; i++) { hit(alice, 0, Y, -3); await sleep(250); }
    await waitFor(async () => (await coachId('Stagecoach 1')) === null, 3000, 'removed');
    check((await rcon('execute if block 0 -60 -3 minecraft:barrier')).includes('failed'), 'barrier gone');
    check((await displays(0, -3)) === 0, 'displays gone');
    const drop = await rcon('execute if entity @e[type=minecraft:item,x=0.5,y=-60,z=-2.5,distance=..3,'
      + 'nbt={Item:{id:"minecraft:oak_chest_boat",components:{"minecraft:custom_data":{PublicBukkitValues:{"stagecoach:wood":"oak"}}}}}]');
    check(/count: 1/i.test(drop), 'one stagecoach item dropped: ' + drop);
  });

  await scenario('a dispenser will not launch the boat under a stagecoach item', async () => {
    await rcon('setblock 10 -60 10 minecraft:dispenser[facing=up]{Items:[{Slot:0b,id:"minecraft:oak_chest_boat",count:1,components:{"minecraft:custom_data":{PublicBukkitValues:{"stagecoach:wood":"oak"}}}}]}');
    await rcon('setblock 11 -60 10 minecraft:redstone_block');
    await sleep(1000);
    check((await rcon('execute if entity @e[type=minecraft:oak_chest_boat]')).includes('failed'), 'no boat');
  });

  await scenario('displays that go missing are put back when the chunk loads again', async () => {
    await rcon('kill @e[type=minecraft:item_display,x=3000.5,y=-60,z=-2.5,distance=..0.1,limit=10]');
    check((await displays(3000, -3)) === 89, '89 left');
    await rcon('forceload remove 2992 -32 3008 16');
    await goTo(alice, 0.5, 0.5, Y, 0, -3);
    await goTo(bob, 0.5, 2.5, Y, 0, -3);
    await sleep(8000); // let the chunk unload
    await goTo(alice, 3000.5, 0.5, Y, 3000, -3);
    await waitFor(async () => (await displays(3000, -3)) === 99, 8000, 'repaired');
  });

  await leave(alice);
  await leave(bob);
  await stopServer();
  const errors = serverLog.filter((l) => /ERROR|Exception/.test(l) && !/No key layers/.test(l));
  await scenario('no errors or exceptions in the server log', async () => {
    check(errors.length === 0, errors.slice(0, 8).join('\n'));
  });
}

main().catch((e) => { console.error('HARNESS ERROR', e); results.push(['harness', 'FAIL: ' + e.message]); })
  .finally(async () => {
    if (server && server.exitCode === null) { try { await stopServer(); } catch (e) { server.kill('SIGKILL'); } }
    fs.writeFileSync(path.join(SERVER_DIR, 'e2e-server.log'), serverLog.join('\n'));
    fs.rmSync(path.join(SERVER_DIR, 'plugins/E2EProtect.jar'), { force: true });
    const failed = results.filter((r) => r[1] !== 'PASS');
    console.log(`\n${results.length - failed.length}/${results.length} passed`);
    for (const f of failed) console.log('FAILED: ' + f[0] + ' -> ' + f[1]);
    process.exit(failed.length ? 1 : 0);
  });
