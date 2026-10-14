// Drives the harness client through the plugin's whole look over RCON, saving screenshots of each screen.
// Usage: node shots.js <rcon port>   (with the server running and the harness client joining as "Shooter")
'use strict';
const net = require('net');

const RCON_PORT = Number(process.argv[2] || 25591);
const P = 'Shooter';
const X = 200, Z = 200, Y = -60; // a clean patch of the flat world
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function rcon(command) {
  return new Promise((resolve, reject) => {
    const s = net.createConnection({ host: '127.0.0.1', port: RCON_PORT });
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
  });
}

const say = (cmd) => rcon(`tellraw ${P} {"text":"#${cmd.replace(/"/g, '\\"')}"}`);

async function step(label, fn, pause = 1500) {
  console.log('- ' + label);
  await fn();
  await sleep(pause);
}

async function main() {
  for (let i = 0; i < 120; i++) {
    if ((await rcon('list')).includes(P)) break;
    await sleep(1000);
  }
  await sleep(6000); // the world finishes loading
  await rcon(`gamemode survival ${P}`);
  await rcon(`xp set ${P} 30 levels`);
  await rcon(`clear ${P}`);
  await rcon('time set noon');
  await rcon('gamerule doDaylightCycle false');
  await rcon(`fill ${X - 8} ${Y} ${Z - 30} ${X + 8} ${Y + 6} ${Z + 8} minecraft:air`);
  await rcon(`fill ${X + 1492} ${Y} ${Z - 30} ${X + 1508} ${Y + 6} ${Z + 8} minecraft:air`);
  await rcon(`stagecoach give ${P} oak 1`);
  await rcon(`stagecoach give ${P} cherry 1`);

  await step('ghost: aim at free ground', async () => {
    await rcon(`tp ${P} ${X + 0.5} ${Y} ${Z + 3.5} 180 28`);
    await sleep(1500);
    console.log('  ghost displays on the server: ' + await rcon('execute if entity @e[type=minecraft:item_display]'));
    await say('shot ghost_fits');
  });
  await step('ghost: something in the way', async () => {
    await rcon(`setblock ${X + 1} ${Y} ${Z - 1} minecraft:stone`);
    await sleep(800);
    await say('shot ghost_blocked');
    await sleep(500);
    await rcon(`setblock ${X + 1} ${Y} ${Z - 1} minecraft:air`);
  });
  await step('place Riverrun (naming screen)', async () => {
    await rcon(`tp ${P} ${X + 0.5} ${Y} ${Z + 0.5} 180 40`); // within reach of the block below
    await sleep(800);
    await say(`use ${X} ${Y - 1} ${Z - 3} up`);
    await sleep(1500);
    await say('shot naming');
    await say('type Riverrun');
    await sleep(300);
    await say('shot naming_typed');
    await say('click Confirm');
    await sleep(1500);
    console.log('  coach list: ' + (await rcon('stagecoach list')).split('\n').join(' / '));
  }, 1000);
  await step('the coach from outside', async () => {
    await rcon(`tp ${P} ${X + 5.5} ${Y + 1.5} ${Z + 3.5} 140 15`);
    await sleep(1500);
    await say('shot coach_front_quarter');
    await rcon(`tp ${P} ${X - 5.5} ${Y + 2} ${Z - 8.5} -60 20`);
    await sleep(1500);
    await say('shot coach_back_quarter');
    await rcon(`tp ${P} ${X + 7.5} ${Y + 0.5} ${Z - 2.5} 90 5`);
    await sleep(1500);
    await say('shot coach_side');
  }, 1000);
  await step('place Harrenhal 1500 blocks east', async () => {
    await rcon(`tp ${P} ${X + 1500.5} ${Y} ${Z + 0.5} 180 40`);
    await sleep(3000);
    await rcon(`clear ${P}`);
    await rcon(`stagecoach give ${P} cherry 1`);
    await sleep(500);
    await say(`use ${X + 1500} ${Y - 1} ${Z - 3} up`);
    await sleep(1500);
    await say('type Harrenhal');
    await say('click Confirm');
  }, 2500);
  await step('back at Riverrun: the destination list', async () => {
    await rcon(`tp ${P} ${X + 0.5} ${Y} ${Z + 0.5} 180 10`);
    await sleep(3000);
    await say(`use ${X} ${Y} ${Z - 3} south`);
    await sleep(1500);
    await say('shot list');
    await say('buttons');
    await say('click Harrenhal');
  }, 2500);
  await step('confirm the journey', async () => {
    await say('shot confirm');
    await say('buttons');
    await say('click Set off');
  }, 2000);
  await step('on the road', async () => {
    await say('shot travel_1');
    await sleep(3000);
    await say('shot travel_2');
    await say('buttons');
    await say('click Cancel journey');
    await sleep(1500);
    await say('shot abandon');
    await say('click Keep travelling');
    await sleep(1500);
    await say('shot travel_3');
  }, 1000);
  await step('arrive', async () => {
    for (let i = 0; i < 40; i++) {
      const out = await rcon(`execute if entity @a[name=${P},gamemode=survival]`);
      if (out.includes('passed')) break;
      await sleep(1000);
    }
    await sleep(1500);
    await say('shot arrived');
  }, 1000);
  await step('quit', async () => {
    await say('quit');
  }, 0);
}

main().catch((e) => { console.error(e); process.exit(1); });
