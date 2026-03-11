import * as THREE from 'https://unpkg.com/three@0.164.1/build/three.module.js';
import { PointerLockControls } from 'https://unpkg.com/three@0.164.1/examples/jsm/controls/PointerLockControls.js';

const scene = new THREE.Scene();
scene.background = new THREE.Color(0x89b7e0);
scene.fog = new THREE.Fog(0x89b7e0, 50, 360);

const camera = new THREE.PerspectiveCamera(75, window.innerWidth / window.innerHeight, 0.1, 1000);
camera.position.set(0, 3, 10);

const renderer = new THREE.WebGLRenderer({ antialias: true });
renderer.setSize(window.innerWidth, window.innerHeight);
renderer.shadowMap.enabled = true;
renderer.shadowMap.type = THREE.PCFSoftShadowMap;
renderer.outputColorSpace = THREE.SRGBColorSpace;
document.body.appendChild(renderer.domElement);

const ambient = new THREE.AmbientLight(0xffffff, 0.45);
scene.add(ambient);

const sun = new THREE.DirectionalLight(0xfff3cf, 1.25);
sun.position.set(90, 130, 45);
sun.castShadow = true;
sun.shadow.mapSize.set(2048, 2048);
sun.shadow.camera.left = -220;
sun.shadow.camera.right = 220;
sun.shadow.camera.top = 220;
sun.shadow.camera.bottom = -220;
scene.add(sun);

const groundGeo = new THREE.PlaneGeometry(520, 520, 120, 120);
const groundPos = groundGeo.attributes.position;
for (let i = 0; i < groundPos.count; i++) {
  const x = groundPos.getX(i);
  const y = groundPos.getY(i);
  const h = Math.sin(x * 0.03) * 1.6 + Math.cos(y * 0.027) * 1.9;
  groundPos.setZ(i, h);
}
groundGeo.computeVertexNormals();

const groundMat = new THREE.MeshStandardMaterial({
  color: 0x5f8050,
  roughness: 0.96,
  metalness: 0.03,
});
const ground = new THREE.Mesh(groundGeo, groundMat);
ground.rotation.x = -Math.PI / 2;
ground.receiveShadow = true;
scene.add(ground);

const controls = new PointerLockControls(camera, renderer.domElement);
scene.add(controls.getObject());

const overlay = document.getElementById('overlay');
overlay.addEventListener('click', () => controls.lock());
controls.addEventListener('lock', () => (overlay.style.display = 'none'));
controls.addEventListener('unlock', () => (overlay.style.display = 'grid'));

const move = { forward: false, back: false, left: false, right: false, sprint: false };
let canJump = false;
const velocity = new THREE.Vector3();
const direction = new THREE.Vector3();
const playerHeight = 3;

const keyMap = {
  KeyW: 'forward',
  KeyS: 'back',
  KeyA: 'left',
  KeyD: 'right',
  ShiftLeft: 'sprint',
  ShiftRight: 'sprint',
};

window.addEventListener('keydown', (e) => {
  if (keyMap[e.code] !== undefined) move[keyMap[e.code]] = true;
  if (e.code === 'Space' && canJump) {
    velocity.y = 9.2;
    canJump = false;
  }
});
window.addEventListener('keyup', (e) => {
  if (keyMap[e.code] !== undefined) move[keyMap[e.code]] = false;
});

const raycaster = new THREE.Raycaster();
const bullets = [];
const enemies = [];
const destructibles = [];
const particles = [];

function makeTree(x, z) {
  const tree = new THREE.Group();
  const trunk = new THREE.Mesh(
    new THREE.CylinderGeometry(0.4, 0.55, 5, 10),
    new THREE.MeshStandardMaterial({ color: 0x6d4d2b, roughness: 0.9 })
  );
  trunk.position.y = 2.5;
  trunk.castShadow = true;
  tree.add(trunk);

  const leaves = new THREE.Mesh(
    new THREE.SphereGeometry(2.6, 12, 10),
    new THREE.MeshStandardMaterial({ color: 0x2f6c3b, roughness: 0.95 })
  );
  leaves.position.y = 6.4;
  leaves.castShadow = true;
  tree.add(leaves);

  tree.position.set(x, 0, z);
  scene.add(tree);
}

function makeHouse(x, z, angle = 0) {
  const g = new THREE.Group();
  const wall = new THREE.Mesh(
    new THREE.BoxGeometry(10, 5.5, 8),
    new THREE.MeshStandardMaterial({ color: 0xc3b49e, roughness: 0.92 })
  );
  wall.position.y = 2.75;
  wall.castShadow = true;
  wall.receiveShadow = true;

  const roof = new THREE.Mesh(
    new THREE.ConeGeometry(7.2, 3, 4),
    new THREE.MeshStandardMaterial({ color: 0x7b2f22, roughness: 0.84 })
  );
  roof.rotation.y = Math.PI / 4;
  roof.position.y = 6.8;
  roof.castShadow = true;

  g.add(wall, roof);
  g.position.set(x, 0, z);
  g.rotation.y = angle;
  scene.add(g);

  destructibles.push({
    mesh: wall,
    hp: 8,
    name: 'Casa',
    onDestroyed: () => {
      wall.visible = false;
      roof.visible = false;
      spawnExplosion(g.position, 28, 0xa78e7a);
    },
  });
}

function makeCrate(x, z) {
  const c = new THREE.Mesh(
    new THREE.BoxGeometry(2.2, 2.2, 2.2),
    new THREE.MeshStandardMaterial({ color: 0x8b6c41, roughness: 0.9 })
  );
  c.castShadow = true;
  c.receiveShadow = true;
  c.position.set(x, 1.1, z);
  scene.add(c);
  destructibles.push({
    mesh: c,
    hp: 3,
    name: 'Caja',
    onDestroyed: () => {
      c.visible = false;
      spawnExplosion(c.position, 10, 0x946f42);
    },
  });
}

function makeEnemy(x, z) {
  const enemy = new THREE.Group();
  const body = new THREE.Mesh(
    new THREE.CapsuleGeometry(1, 2.2, 6, 10),
    new THREE.MeshStandardMaterial({ color: 0x415488, roughness: 0.8 })
  );
  body.castShadow = true;
  body.position.y = 2;
  enemy.add(body);

  const head = new THREE.Mesh(
    new THREE.SphereGeometry(0.7, 10, 8),
    new THREE.MeshStandardMaterial({ color: 0xd3b69a, roughness: 0.9 })
  );
  head.position.y = 3.8;
  head.castShadow = true;
  enemy.add(head);

  enemy.position.set(x, 0, z);
  scene.add(enemy);
  enemies.push({ mesh: enemy, hp: 6, cooldown: Math.random() * 2, alive: true });
}

function makeTank() {
  const tank = new THREE.Group();
  const base = new THREE.Mesh(
    new THREE.BoxGeometry(8, 2, 12),
    new THREE.MeshStandardMaterial({ color: 0x334531, roughness: 0.8 })
  );
  base.position.y = 1;
  base.castShadow = true;
  tank.add(base);

  const turret = new THREE.Mesh(
    new THREE.CylinderGeometry(2.3, 2.7, 1.6, 20),
    new THREE.MeshStandardMaterial({ color: 0x41573f, roughness: 0.78 })
  );
  turret.position.y = 2.35;
  turret.castShadow = true;
  tank.add(turret);

  const cannon = new THREE.Mesh(
    new THREE.CylinderGeometry(0.35, 0.35, 7, 12),
    new THREE.MeshStandardMaterial({ color: 0x263626, roughness: 0.55, metalness: 0.3 })
  );
  cannon.rotation.z = Math.PI / 2;
  cannon.position.set(3.3, 2.35, 0);
  cannon.castShadow = true;
  tank.add(cannon);

  tank.position.set(-35, 0, -45);
  scene.add(tank);
}

function makePlane() {
  const plane = new THREE.Group();
  const body = new THREE.Mesh(
    new THREE.CylinderGeometry(0.8, 1.2, 10, 14),
    new THREE.MeshStandardMaterial({ color: 0xa5aeb8, roughness: 0.6, metalness: 0.45 })
  );
  body.rotation.z = Math.PI / 2;
  plane.add(body);

  const wings = new THREE.Mesh(
    new THREE.BoxGeometry(2, 0.2, 14),
    new THREE.MeshStandardMaterial({ color: 0x7d8995, roughness: 0.64, metalness: 0.35 })
  );
  plane.add(wings);

  plane.position.set(0, 42, -90);
  plane.userData = { t: 0 };
  scene.add(plane);
  return plane;
}

for (let i = 0; i < 70; i++) {
  const angle = Math.random() * Math.PI * 2;
  const radius = 30 + Math.random() * 210;
  makeTree(Math.cos(angle) * radius, Math.sin(angle) * radius);
}

makeHouse(20, -25, 0.2);
makeHouse(42, -36, -0.5);
makeHouse(-25, -10, 0.7);
makeHouse(-30, 26, 1.3);

for (let i = 0; i < 18; i++) {
  makeCrate(Math.random() * 80 - 40, Math.random() * 80 - 40);
}

makeEnemy(14, -16);
makeEnemy(28, -31);
makeEnemy(-20, 15);
makeEnemy(-32, -18);
makeEnemy(6, 32);

makeTank();
const plane = makePlane();

function spawnExplosion(pos, count, color) {
  for (let i = 0; i < count; i++) {
    const p = new THREE.Mesh(
      new THREE.SphereGeometry(0.15 + Math.random() * 0.2, 6, 6),
      new THREE.MeshStandardMaterial({ color, emissive: color, emissiveIntensity: 0.25 })
    );
    p.position.copy(pos);
    p.position.y += 2;
    p.castShadow = true;
    scene.add(p);
    particles.push({
      mesh: p,
      vel: new THREE.Vector3(
        (Math.random() - 0.5) * 12,
        Math.random() * 9,
        (Math.random() - 0.5) * 12
      ),
      ttl: 0.6 + Math.random() * 0.7,
    });
  }
}

function fireBullet() {
  if (!controls.isLocked) return;
  raycaster.setFromCamera(new THREE.Vector2(0, 0), camera);
  const bullet = new THREE.Mesh(
    new THREE.SphereGeometry(0.11, 8, 8),
    new THREE.MeshStandardMaterial({ color: 0xfff2bb, emissive: 0xffaa33, emissiveIntensity: 0.6 })
  );
  bullet.position.copy(camera.position);
  bullet.castShadow = true;
  scene.add(bullet);
  bullets.push({
    mesh: bullet,
    dir: raycaster.ray.direction.clone(),
    life: 2,
    speed: 120,
  });
}

window.addEventListener('mousedown', fireBullet);

const stats = document.getElementById('stats');
const clock = new THREE.Clock();
let enemyKills = 0;

function updatePlayer(dt) {
  velocity.x -= velocity.x * 9 * dt;
  velocity.z -= velocity.z * 9 * dt;
  velocity.y -= 20 * dt;

  direction.z = Number(move.forward) - Number(move.back);
  direction.x = Number(move.right) - Number(move.left);
  direction.normalize();

  const speed = move.sprint ? 70 : 42;
  if (move.forward || move.back) velocity.z -= direction.z * speed * dt;
  if (move.left || move.right) velocity.x -= direction.x * speed * dt;

  controls.moveRight(-velocity.x * dt);
  controls.moveForward(-velocity.z * dt);
  controls.getObject().position.y += velocity.y * dt;

  if (controls.getObject().position.y < playerHeight) {
    velocity.y = 0;
    controls.getObject().position.y = playerHeight;
    canJump = true;
  }
}

function updateBullets(dt) {
  for (let i = bullets.length - 1; i >= 0; i--) {
    const b = bullets[i];
    b.life -= dt;
    b.mesh.position.addScaledVector(b.dir, b.speed * dt);
    if (b.life <= 0) {
      scene.remove(b.mesh);
      bullets.splice(i, 1);
      continue;
    }

    for (const target of destructibles) {
      if (!target.mesh.visible || target.hp <= 0) continue;
      if (b.mesh.position.distanceTo(target.mesh.getWorldPosition(new THREE.Vector3())) < 2.8) {
        target.hp -= 1;
        spawnExplosion(b.mesh.position, 5, 0xffa047);
        scene.remove(b.mesh);
        bullets.splice(i, 1);
        if (target.hp <= 0) target.onDestroyed();
        break;
      }
    }

    for (const e of enemies) {
      if (!e.alive) continue;
      if (b.mesh.position.distanceTo(e.mesh.position.clone().add(new THREE.Vector3(0, 2, 0))) < 2) {
        e.hp -= 1;
        spawnExplosion(b.mesh.position, 4, 0xff4545);
        scene.remove(b.mesh);
        bullets.splice(i, 1);
        if (e.hp <= 0) {
          e.alive = false;
          e.mesh.visible = false;
          enemyKills += 1;
          spawnExplosion(e.mesh.position, 20, 0xd45454);
        }
        break;
      }
    }
  }
}

function updateEnemies(dt, time) {
  for (const e of enemies) {
    if (!e.alive) continue;
    const dir = controls.getObject().position.clone().sub(e.mesh.position);
    const dist = dir.length();
    if (dist > 2) {
      dir.y = 0;
      dir.normalize();
      e.mesh.position.addScaledVector(dir, dt * (3 + Math.sin(time + e.mesh.position.x) * 0.5));
      e.mesh.lookAt(controls.getObject().position.x, e.mesh.position.y, controls.getObject().position.z);
    }

    e.cooldown -= dt;
    if (e.cooldown <= 0 && dist < 90) {
      e.cooldown = 1.2 + Math.random() * 1.1;
      spawnExplosion(controls.getObject().position, 1, 0xff2222);
    }
  }
}

function updateParticles(dt) {
  for (let i = particles.length - 1; i >= 0; i--) {
    const p = particles[i];
    p.ttl -= dt;
    p.vel.y -= 17 * dt;
    p.mesh.position.addScaledVector(p.vel, dt);
    p.mesh.scale.multiplyScalar(0.985);
    if (p.ttl <= 0) {
      scene.remove(p.mesh);
      particles.splice(i, 1);
    }
  }
}

function animate() {
  const dt = Math.min(clock.getDelta(), 0.033);
  const t = clock.elapsedTime;

  if (controls.isLocked) updatePlayer(dt);
  updateBullets(dt);
  updateEnemies(dt, t);
  updateParticles(dt);

  plane.userData.t += dt;
  plane.position.x = Math.sin(plane.userData.t * 0.22) * 120;
  plane.position.z = -90 + Math.cos(plane.userData.t * 0.2) * 80;
  plane.position.y = 44 + Math.sin(plane.userData.t * 0.6) * 4;
  plane.rotation.y = Math.atan2(Math.cos(plane.userData.t * 0.22), -Math.sin(plane.userData.t * 0.2));

  const alive = enemies.filter((e) => e.alive).length;
  stats.innerHTML = `
    Vida: <b>100</b><br>
    Enemigos vivos: <b>${alive}</b><br>
    Eliminaciones: <b>${enemyKills}</b><br>
    Destruibles: <b>${destructibles.filter((d) => d.mesh.visible).length}</b>
  `;

  renderer.render(scene, camera);
  requestAnimationFrame(animate);
}
animate();

window.addEventListener('resize', () => {
  camera.aspect = window.innerWidth / window.innerHeight;
  camera.updateProjectionMatrix();
  renderer.setSize(window.innerWidth, window.innerHeight);
});
