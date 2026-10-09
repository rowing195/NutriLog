import 'lenis/dist/lenis.css';
import './style.css';
import gsap from 'gsap';
import { ScrollTrigger } from 'gsap/ScrollTrigger';
import Lenis from 'lenis';

gsap.registerPlugin(ScrollTrigger);
// 手機網址列伸縮會改變視窗高，進而觸發整站 refresh；忽略它，捲動中才不會抖
ScrollTrigger.config({ ignoreMobileResize: true });

const $ = (sel, root = document) => root.querySelector(sel);
const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];
const clamp01 = gsap.utils.clamp(0, 1);
const pageY = (el) => el.getBoundingClientRect().top + window.scrollY;
// 不算 transform 的版面位置：正在淡入上移的元素，量 getBoundingClientRect 會跟著捲動位置差幾十 px
const layoutY = (el) => {
  let y = 0;
  for (let e = el; e; e = e.offsetParent) y += e.offsetTop;
  return y;
};

const MQ = {
  desktop: '(min-width: 900px)',
  mobile: '(max-width: 899.98px)',
  motion: '(prefers-reduced-motion: no-preference)',
  reduce: '(prefers-reduced-motion: reduce)',
};
const reduceQuery = window.matchMedia(MQ.reduce);

// 深色那一套是 Theme.kt 的 Paper.Dark*。淺色那一套不在這裡另抄一份，直接讀 style.css 的 :root ——
// 改色票本來就要改 Theme.kt、values*/colors.xml、style.css 三處，這裡只多一張深色表。
const DARK = {
  '--bg': '#17150F',
  '--raised': '#1E1B14',
  '--container': '#232016',
  '--track': '#2C2820',
  '--ink': '#EFE9DC',
  '--ink2': '#BDB5A2',
  '--muted': '#A8A08C',
  '--faint': '#7C7565',
  '--hairline': '#2F2B21',
  '--field': '#4A4636',
  '--vermilion': '#F2705A',
  '--ochre': '#D9A24E',
};

// 「減少動態」拿掉的是位移，不是整段動畫：淡入淡出照舊，只是不飄（同 html-games/fhibichubi-nono）。
// 位移、視差、接管捲動會誘發前庭不適；純透明度不會，留著它敘事才不會變成一面不動的長頁。
const RISE = 40; // 整塊（小示範、截圖）進場時往上飄的距離 (px)
const LIFT = 30; // 整塊離場時再往上飄的距離 (px)

// 每一幕的節奏，單位是時間軸的秒；一秒佔 UNIT_SVH 的捲動距離。桌面版整天接成一條、手機版每一幕
// 各自一條，用的是同一組數字。改節奏只改這裡，高度跟著時間軸總長算，不用回頭改 CSS。
// 這組數字是在示範頁（逐字掃法比較）上和使用者一起挑的「釘住＋大動作」。
const SCENE = {
  LEAD: 0.5, //       換到這一幕的手機畫面，字還沒出來
  CHAR_IN: 0.35, //   一個字彈完要多久（要留時間給最後那一下回彈）
  CHAR_STEP: 0.03, // 字與字錯開多少
  LINE_MAX: 1.0, //   一行最多花多久掃完：長段落只是字與字更緊，不會拖
  LINE_GAP: 0.12, //  上一行掃完之後，下一行多久開始
  BLOCK_IN: 0.6, //   小示範整塊淡入
  DEMO: 1.8, //       小示範（底線、540→503、份數、熱量條）
  HOLD: 1.2, //       全部都在、停著讓人讀
  CHAR_OUT: 0.3,
  OUT_STEP: 0.016,
  OUT_MAX: 1.2, //    整幕最多花多久飄完
  GAP: 0.15, //       兩幕之間
};
const UNIT_SVH = 24;

// 鬆手之後把半路的那一幕播完（settleOnRest）。全部 scrub 的話，停在哪裡就看每個人捲動的習慣，
// 字飛到一半就停住是常態。鬆手的那一刻就接手：把這一下原本會停的位置改成下一個「整幕都在」的位置，
// 從當下的速度接著走、減速停進去。試過、被換掉的：「先停下來、等 0.5 秒、再從零起步」（看起來像網頁掉幀），
// 以及「一次手勢播一幕、播放中不收輸入」（每個人看到的最一致，但不能自由前後滑，使用者要能前後滑）。
// RELEASE：滾輪多久沒有下一格就算鬆手（觸控直接看 touchend）。IDLE：鍵盤、拖捲軸沒有鬆手可看，捲動停了多久才接手。
// RATE：接手後的平均速度，用時間軸的秒算。1 是照 SCENE 的秒數播，實測一段要 3～8 秒，使用者選了 1.5 倍；
// 鬆手當下比這個快的話照當下的速度走，不會在交棒那一下突然變慢。
const SETTLE = { RELEASE: 120, IDLE: 300, RATE: 1.5 };

// 換畫面的方向照 app 的規則：「記一筆」開出來的由上往下蓋，報頭圖示（月曆）與底部面板由下往上。
// 減少動態時一律改成淡入：移動的那條邊也是位移。
const FULL = 'inset(0% 0% 0% 0%)';
const SHOT = {
  top: { from: { clipPath: 'inset(0% 0% 100% 0%)' }, to: { clipPath: FULL } },
  bottom: { from: { clipPath: 'inset(100% 0% 0% 0%)' }, to: { clipPath: FULL } },
  fade: { from: { opacity: 0 }, to: { opacity: 1 } },
};
const SHOT_REDUCED = { from: { opacity: 0, clipPath: FULL }, to: { opacity: 1, clipPath: FULL } };

// 晚一點才會被叫到的 callback（onEnter 之類）裡建立的 tween 也要記進 matchMedia 的 context，條件一變
// 才會跟著還原。ctx.add(fn) 會「當場」執行 fn，要拿到包好、晚點再叫的函式得用具名的形式。
let deferredCount = 0;
const deferIn = (ctx, fn) => ctx.add(`deferred${++deferredCount}`, fn);

// 桌面版的場景疊在同一個位置，錨點與入夜的位置不能量元素，要從時間軸換算（stageDay 填、還原時清掉）。
// rests 是停手後可以停的位置：由上往下排的 [起, 迄] 捲動範圍，兩段之間就是「動畫的半路」。
const layout = { anchorY: null, duskRange: null, rests: null };

/* ---------- 截圖還沒拍好的位置 ----------
   圖片載入失敗就換成虛線框，寫出它在等哪個檔名。版面照常佔位，拍好放進 public/shots/ 就自動換成圖。 */

for (const img of $$('.shot > img')) {
  const missing = () => img.parentElement.classList.add('is-missing');
  if (img.complete && img.naturalWidth === 0) missing();
  else img.addEventListener('error', missing, { once: true });
}

/* ---------- 1. Lenis ---------- */

const mm = gsap.matchMedia();

mm.add(MQ.motion, () => {
  // syncTouch：觸控也交給 Lenis。原生的慣性滑動沒辦法中途改目的地，鬆手接手（settleOnRest）在手機上就接不起來
  const lenis = new Lenis({ anchors: true, syncTouch: true });
  const raf = (time) => lenis.raf(time * 1000);
  lenis.on('scroll', ScrollTrigger.update);
  gsap.ticker.add(raf);
  gsap.ticker.lagSmoothing(0);
  const stopSettle = settleOnRest(lenis);
  return () => {
    stopSettle();
    gsap.ticker.remove(raf);
    gsap.ticker.lagSmoothing(500, 33);
    lenis.destroy();
  };
});

// 鬆手的那一刻，看這一下原本會停在哪（Lenis 的 targetScroll）：停在兩個可以停的位置中間，
// 就往捲的方向改停到下一個。原本就停得進可以停的範圍、或在故事以外（第一段之前、最後一段之後）就不管。
// 自動捲動中一碰滾輪或手指，Lenis 就把捲動交還給使用者，不需要另外處理。
// 減少動態時不會走到這裡：整段只在有 Lenis 的時候掛上，而自動捲動本身就是位移。
function settleOnRest(lenis) {
  let releaseTimer = 0;
  let idleTimer = 0;
  let dir = 0;
  // 速度自己量（px/ms），用最近 64ms 的位移：lenis.velocity 是每一幀的位移，螢幕更新率不同就差一倍；
  // 只看最後兩個事件的話，觸控時一幀的雜訊就會讓起步忽快忽慢
  const samples = [];
  const speedNow = () => {
    const t = performance.now();
    // 最後一次移動已經是 50ms 以前，代表手指停住了：起步就是 0，不然按住不動再放開會拿舊的速度衝出去
    if (samples.length < 2 || t - samples[samples.length - 1].t > 50) return 0;
    const a = samples[0];
    const b = samples[samples.length - 1];
    return (b.y - a.y) / (b.t - a.t);
  };

  const settle = () => {
    if (lenis.isTouching) return;
    const rests = layout.rests?.();
    if (!rests?.length) return;
    const y = lenis.animatedScroll;
    const dest = lenis.targetScroll;
    const target = restTarget(rests, dest, Math.sign(dest - y) || dir);
    if (target === null) return;
    const d = Math.abs(target - y);
    if (d < 1) return;
    // 照節奏該走多久；鬆手當下比節奏快就縮短，讓起步的速度剛好接上（handoff 的起點斜率最多 3）
    const v = Math.max(0, speedNow() * 1000 * Math.sign(target - y));
    let duration = d / (((window.innerHeight * UNIT_SVH) / 100) * SETTLE.RATE);
    let s0 = (v * duration) / d;
    if (s0 > 3) {
      duration = (3 * d) / v;
      s0 = 3;
    }
    lenis.scrollTo(target, { duration, easing: handoff(s0) });
  };

  const offScroll = lenis.on('scroll', () => {
    const now = performance.now();
    samples.push({ t: now, y: lenis.animatedScroll });
    while (now - samples[0].t > 64) samples.shift();
    if (lenis.direction) dir = lenis.direction;
    // 鍵盤、拖捲軸這類原生捲動沒有鬆手可看，只能等它停
    if (lenis.isScrolling === 'native') {
      clearTimeout(idleTimer);
      idleTimer = setTimeout(settle, SETTLE.IDLE);
    }
  });
  const offVirtual = lenis.on('virtual-scroll', ({ deltaY, event }) => {
    if (event.ctrlKey) return; // 觸控板縮放
    clearTimeout(releaseTimer);
    clearTimeout(idleTimer);
    if (deltaY) dir = Math.sign(deltaY);
    // 這個事件是在 Lenis 處理它之前發的；手指離開時慣性要等 Lenis 算完，排到微任務才讀得到它要停在哪
    if (event.type === 'touchend') queueMicrotask(settle);
    else if (event.type === 'wheel') releaseTimer = setTimeout(settle, SETTLE.RELEASE);
  });
  return () => {
    clearTimeout(releaseTimer);
    clearTimeout(idleTimer);
    offScroll();
    offVirtual();
  };
}

// 起點斜率 s0、終點斜率 0 的三次 Hermite：從鬆手當下的速度接著走、減速停進去。s0 在 0～3 之間才不會衝過頭
function handoff(s0) {
  return (t) => s0 * (t * t * t - 2 * t * t + t) + 3 * t * t - 2 * t * t * t;
}

// 停在 y、剛剛往 dir 捲：要捲到哪裡（null 是不用動）
function restTarget(rests, y, dir) {
  const EPS = 2;
  for (let i = 0; i < rests.length; i++) {
    const [a, b] = rests[i];
    if (y >= a - EPS && y <= b + EPS) return null;
    const next = rests[i + 1];
    if (!next || y >= next[0]) continue;
    if (y < a) return null;
    if (dir > 0) return next[0];
    if (dir < 0) return b;
    return y - b < next[0] - y ? b : next[0];
  }
  return null;
}

/* ---------- 2. 章節裡的小示範 ----------
   每個都回傳一條暫停的時間軸，接進那一幕的時間軸裡（sceneTimeline），在字全部進來之後播。 */

function matchTimeline(demo) {
  const tl = gsap.timeline({ paused: true, defaults: { ease: 'none' } });
  const hit = $('[data-row="hit"]', demo);
  const miss = $('[data-row="miss"]', demo);

  // 「烤」「肉」兩個字分開出現在「煎烤豬肉排」裡：底線一個字一個字畫
  for (const b of $$('.name b', hit)) tl.fromTo(b, { '--u': 0 }, { '--u': 1, duration: 1 });
  tl.fromTo($('.verdict', hit), { opacity: 0 }, { opacity: 1, duration: 0.6 });

  // 「咖啡」只中「咖」一個字：畫完那條線，整列退淡、標「不算」
  for (const b of $$('.name b', miss)) tl.fromTo(b, { '--u': 0 }, { '--u': 1, duration: 1 }, '+=0.3');
  const dimAt = tl.duration();
  tl.fromTo($('.verdict', miss), { opacity: 0 }, { opacity: 1, duration: 0.6 });

  // class 沒辦法補間，用時間點判斷，倒捲回去才會跟著拿掉
  tl.eventCallback('onUpdate', () => miss.classList.toggle('is-dim', tl.time() >= dimAt));
  return { tl, reset: () => miss.classList.remove('is-dim') };
}

function stampsTimeline(demo) {
  const tl = gsap.timeline({ paused: true, defaults: { ease: 'none' } });
  const count = $('[data-count]', demo);
  const n = { v: 540 };
  tl.fromTo($('[data-row="search"]', demo), { opacity: 0 }, { opacity: 1, duration: 0.5 });
  tl.fromTo(
    n,
    { v: 540 },
    {
      v: 503,
      duration: 1,
      onUpdate: () => {
        count.textContent = String(Math.round(n.v));
      },
    },
  );
  return { tl, reset: () => (count.textContent = '540') };
}

function portionTimeline(demo) {
  const multEl = $('[data-mult]', demo);
  const servingEl = $('[data-serving]', demo);
  const kcalEl = $('[data-portion-kcal]', demo);
  const plus = $('[data-plus]', demo);
  const minus = $('[data-minus]', demo);
  const timers = new Map();

  // 倍率用「十分之一」的整數算，1.1 × 250 這種小數乘法才不會冒出 274.99999
  const paint = (tenths) => {
    const mult = tenths % 10 === 0 ? String(tenths / 10) : (tenths / 10).toFixed(1);
    multEl.textContent = mult;
    servingEl.textContent = `${mult} 碗 (${25 * tenths} g)`;
    kcalEl.textContent = String(35 * tenths);
  };
  const press = (key) => {
    if (!key) return;
    key.classList.add('press');
    clearTimeout(timers.get(key));
    timers.set(key, setTimeout(() => key.classList.remove('press'), 150));
  };

  const state = { p: 0 };
  let step = 0;
  paint(10);
  const tl = gsap.timeline({ paused: true }).to(state, {
    p: 1,
    duration: 1,
    ease: 'none',
    onUpdate: () => {
      const next = Math.round(state.p * 5);
      if (next === step) return;
      // 倒捲回去時數字是往下掉的，按的應該是 −0.1
      press(next > step ? plus : minus);
      step = next;
      paint(10 + step);
    },
  });

  const reset = () => {
    for (const [key, id] of timers) {
      clearTimeout(id);
      key.classList.remove('press');
    }
    paint(10);
  };
  return { tl, reset };
}

function budgetTimeline(demo) {
  const bar = $('.bar', demo);
  // 比例尺是 2400 kcal：目標 1944 → 81%、吃了 2183 → 91%、目標 + 300 → 93.5%。
  // 紅段在目標線越過 91%（吃了多少）那一刻剛好退完：(91 − 81) / (93.5 − 81) = 0.8
  const tl = gsap
    .timeline({ paused: true, defaults: { ease: 'none' } })
    .fromTo(bar, { '--target': 81 }, { '--target': 93.5, duration: 1 }, 0)
    .fromTo(bar, { '--red': 1 }, { '--red': 0, duration: 0.25 }, 0.55);
  return { tl, reset: () => {} };
}

const DEMOS = { match: matchTimeline, stamps: stampsTimeline, portion: portionTimeline, budget: budgetTimeline };

/* ---------- 3. 逐字進出場 ----------
   每一幕的字照閱讀順序一個一個彈進來、停著讓人讀、再一個一個飄走。不用 GSAP 的 SplitText：
   它的 specialChars 在把字黏回去時只比長度不比內容，在示範頁上實測把「煎烤豬肉排」改成
   「烤烤豬肉排」、「咖啡」改成「烤啡」。這裡只把字包進 span，一個字都不動。 */

// 一個單位＝一個中文字，或一整段數字／英文。標點黏在前一個字上、開括號黏在後一個字上，
// 不然每個字各自 inline-block 之後，「，」可能跑到行首。數字整段一起，Neucha 的字距才不會散。
const UNIT = /[「『（(]*[0-9A-Za-z+][0-9A-Za-z.:%+\-]*[，。、！？；：」』）)]*|[「『（(]*[^\s「『（(，。、！？；：」』）)][，。、！？；：」』）)…]*/gu;

// 拆開的字對讀螢幕軟體藏起來，另外放一份完整的原文給它念。不用 aria-label：<p> 這種一般元素
// 不允許命名，很多讀螢幕軟體會直接忽略。巢狀的 .num／.label 照原樣保留（數字字型才套得到），<br> 不碰。
function splitUnits(el) {
  const whole = document.createElement('span');
  whole.className = 'sr-only';
  whole.textContent = el.textContent.replace(/\s+/g, ' ').trim();
  const out = [];
  const walk = (node) => {
    for (const child of [...node.childNodes]) {
      if (child.nodeType === Node.ELEMENT_NODE) {
        if (child.tagName !== 'BR') walk(child);
        continue;
      }
      if (child.nodeType !== Node.TEXT_NODE) continue;
      const text = child.textContent;
      const frag = document.createDocumentFragment();
      let last = 0;
      for (const m of text.matchAll(UNIT)) {
        if (m.index > last) frag.append(text.slice(last, m.index));
        const u = document.createElement('span');
        u.className = 'u';
        u.setAttribute('aria-hidden', 'true');
        u.textContent = m[0];
        frag.append(u);
        out.push(u);
        last = m.index + m[0].length;
      }
      if (last < text.length) frag.append(text.slice(last));
      child.replaceWith(frag);
    }
  };
  walk(el);
  el.prepend(whole);
  return out;
}

// 拆一次就好，桌面與手機版共用（換版面時不用重拆）。小示範整塊進出，不拆
const units = new Map();
for (const line of $$('.scene-copy > .float:not([data-demo])')) units.set(line, splitUnits(line));

// 一個字的進出場：彈起來、帶一點旋轉與縮放，最後回彈一下；離場時縮小、往上飄。
// 減少動態時只淡入淡出，順序不變。
function popLook(u, reduce) {
  if (reduce) return { from: { autoAlpha: 0 }, to: { autoAlpha: 1 }, out: { autoAlpha: 0 }, inEase: 'power2.out', outEase: 'none' };
  const f = parseFloat(getComputedStyle(u).fontSize);
  return {
    from: { autoAlpha: 0, y: f * 0.9, scale: 0.55, rotation: -14, transformOrigin: '50% 100%' },
    to: { autoAlpha: 1, y: 0, scale: 1, rotation: 0 },
    out: { autoAlpha: 0, y: -f * 0.8, scale: 0.8, rotation: 10 },
    inEase: 'back.out(2.4)',
    outEase: 'power2.in',
  };
}

// 一幕的時間軸：字照閱讀順序彈進來 → 小示範 → 停著讓人讀 → 照閱讀順序飄走。
// 桌面版接進整天那一條，手機版自己掛一個 ScrollTrigger。onScreen(畫面, 第幾秒) 是桌面版用來換手機畫面的。
function sceneTimeline(scene, reduce, onScreen = () => {}) {
  const tl = gsap.timeline({ defaults: { ease: 'none' } });
  const lines = $$('.scene-copy > .float', scene);
  const rise = reduce ? 0 : RISE;
  const lift = reduce ? 0 : LIFT;
  const resets = [];
  const leaving = []; // 離場的順序：每一個字，或整塊示範
  let cursor = SCENE.LEAD;
  let end = cursor;
  let anchor = null;
  let demo = null;

  const rests = [];

  lines.forEach((line, i) => {
    // 帶著自己畫面的那一行（月曆的第二句）是這一幕的第二段：前面的字到齊先停一站（停手後可以停在這裡），
    // 再換畫面、接著出來。只是空出一段捲動距離的話，捲過去時會像中間卡了一大段什麼都沒有
    if (line.dataset.screen && !line.dataset.demo) {
      if (i > 0) {
        rests.push([end, end + SCENE.HOLD]);
        cursor = end + SCENE.HOLD;
      }
      onScreen(line.dataset.screen, cursor);
    }

    if (line.dataset.demo) {
      tl.fromTo(line, { autoAlpha: 0, y: rise }, { autoAlpha: 1, y: 0, duration: SCENE.BLOCK_IN, ease: 'power3.out' }, cursor);
      leaving.push({ el: line });
      demo = line;
      end = Math.max(end, cursor + SCENE.BLOCK_IN);
      cursor += SCENE.BLOCK_IN / 2;
      return;
    }

    const us = units.get(line) ?? [];
    const spread = Math.min(SCENE.LINE_MAX, Math.max(0, us.length - 1) * SCENE.CHAR_STEP);
    us.forEach((u, j) => {
      const look = popLook(u, reduce);
      const at = cursor + (us.length > 1 ? j / (us.length - 1) : 0) * spread;
      tl.fromTo(u, look.from, { ...look.to, duration: SCENE.CHAR_IN, ease: look.inEase }, at);
      leaving.push({ el: u, look });
    });
    end = Math.max(end, cursor + spread + SCENE.CHAR_IN);
    // 第一行（「幾點」那一行）完整出現時，報頭的時鐘剛好走到這個時間、今日熱量也在這時候跳
    if (anchor === null) anchor = cursor + spread + SCENE.CHAR_IN;
    cursor += spread + SCENE.LINE_GAP;
  });

  if (demo) {
    const build = DEMOS[demo.dataset.demo];
    if (build) {
      const { tl: demoTl, reset } = build(demo);
      resets.push(reset);
      if (demo.dataset.screen) onScreen(demo.dataset.screen, end);
      demoTl.paused(false).timeScale(demoTl.duration() / SCENE.DEMO);
      tl.add(demoTl, end + 0.2);
      end += 0.2 + SCENE.DEMO;
    }
  }

  const outAt = end + SCENE.HOLD;
  const outSpread = Math.min(SCENE.OUT_MAX, Math.max(0, leaving.length - 1) * SCENE.OUT_STEP);
  leaving.forEach(({ el, look }, k) => {
    const at = outAt + (leaving.length > 1 ? k / (leaving.length - 1) : 0) * outSpread;
    if (look) tl.to(el, { ...look.out, duration: SCENE.CHAR_OUT, ease: look.outEase }, at);
    else tl.to(el, { autoAlpha: 0, y: -lift, duration: SCENE.CHAR_OUT * 1.5 }, at);
  });

  // rests：字和小示範都到齊、還沒開始飄走的那一段（分兩段的那一幕有兩段），停手後就停在這裡
  rests.push([end, outAt]);
  return { tl, anchor: scene.dataset.time ? anchor : null, rests, resets };
}

/* ---------- 4. 桌面版：釘住的一天 ----------
   左欄和右邊的手機都釘住，捲動只推進一條時間軸：每一幕接在上一幕後面，手機跟著換畫面。
   字在出現的時候，旁邊沒有任何東西在移動。
   最後一幕走完，手機在原地淡出，看不見了才解除釘住 —— 不會一路捲出畫面。 */

function stageDay(ctx, reduce) {
  const day = $('.day');
  const scenes = $$('.day-text > .scene');
  const shots = new Map($$('.screen .shot').map((s) => [s.dataset.shot, s]));
  const lift = reduce ? 0 : LIFT;
  const resets = [];

  day.classList.add('is-staged');
  const tl = gsap.timeline({ defaults: { ease: 'none' } });
  const anchorTime = new Map();

  const swap = (name, at) => {
    const shot = shots.get(name);
    if (!shot) return;
    const v = reduce ? SHOT_REDUCED : SHOT[shot.dataset.from] || SHOT.fade;
    tl.fromTo(shot, v.from, { ...v.to, duration: SCENE.LEAD }, at);
  };

  // 封面：一打開就在，往下捲時整段往上飄走、淡掉
  const hero = scenes.shift();
  anchorTime.set(hero, 0);
  tl.to($('.scroll-hint', hero), { autoAlpha: 0, duration: 0.3 }, 0.05);
  tl.to($('.hero-copy', hero), { autoAlpha: 0, y: -2 * lift, duration: 0.8 }, 0.3);
  let t = 1.1 + SCENE.GAP;
  let duskAt = null;
  const rests = [[0, 0.3]];

  for (const scene of scenes) {
    const start = t;
    if (scene.dataset.screen) swap(scene.dataset.screen, start);
    if ('dusk' in scene.dataset) duskAt = start;
    const s = sceneTimeline(scene, reduce, (name, at) => swap(name, start + at));
    resets.push(...s.resets);
    tl.add(s.tl, start);
    if (s.anchor !== null) anchorTime.set(scene, start + s.anchor);
    for (const [a, b] of s.rests) rests.push([start + a, start + b]);
    t = start + s.tl.duration() + SCENE.GAP;
  }

  // 一天結束：手機在原地淡出。停在這裡的是空的左欄與看不見的手機，解除釘住時沒有東西在移動
  tl.to('.phone-sticky', { autoAlpha: 0, duration: 0.6 }, t);
  t += 0.6;

  const total = tl.duration();
  day.style.height = `${100 + total * UNIT_SVH}svh`;
  ScrollTrigger.create({ trigger: day, start: 'top top', end: 'bottom bottom', scrub: true, animation: tl });

  // 時間軸上的第幾秒，對應到頁面上的捲動位置
  const at = (time) => pageY(day) + (time / total) * (day.offsetHeight - window.innerHeight);
  layout.anchorY = (el) => (anchorTime.has(el) ? at(anchorTime.get(el)) + window.innerHeight / 2 : null);
  // 入夜：上一幕往上飄走時開始，跑步這一幕的畫面換好時完成
  layout.duskRange = () => (duskAt === null ? null : [at(duskAt - 0.9), at(duskAt + SCENE.LEAD)]);
  // 最後一幕飄走、手機淡出之後那段是空的，停在那裡要往下接到下一段的開頭；從那裡起就不是這條時間軸的事
  layout.rests = () => {
    const end = pageY(day) + day.offsetHeight;
    return [...rests.map(([a, b]) => [at(a), at(b)]), [end, end]];
  };

  return () => {
    layout.anchorY = null;
    layout.duskRange = null;
    layout.rests = null;
    day.classList.remove('is-staged');
    day.style.height = '';
    resets.forEach((fn) => fn());
  };
}

/* ---------- 5. 手機版：每一幕的字自己釘住 ----------
   沒有釘住的那支手機，改成每一幕的字停在畫面中間，逐字彈進來、停著讓人讀、再飄走，
   那一幕的截圖接在後面捲上來。節奏和桌面版同一組，只是每一幕各自一個 ScrollTrigger。
   原本是每一行跟著頁面往上捲、在畫面下緣飄進來，手機上一滑就過去了，使用者說看不出有進出場。 */

function stageMobile(ctx, reduce) {
  const day = $('.day');
  const rise = reduce ? 0 : RISE;
  const resets = [];
  const stages = new Map();

  day.classList.add('is-staged');
  for (const scene of $$('.day-text > .scene:not(.hero)')) {
    const stage = $('.scene-stage', scene);
    const s = sceneTimeline(scene, reduce);
    resets.push(...s.resets);
    const total = s.tl.duration();
    stage.style.height = `${100 + total * UNIT_SVH}svh`;
    ScrollTrigger.create({ trigger: stage, start: 'top top', end: 'bottom bottom', scrub: true, animation: s.tl });
    stages.set(scene, { stage, anchor: s.anchor, rests: s.rests, total, shot: $('.inline-shot', scene) });
  }

  $$('.day-text > .scene:not(.hero) > .inline-shot').forEach((shot) =>
    gsap.fromTo(
      shot,
      { autoAlpha: 0, y: rise },
      { autoAlpha: 1, y: 0, ease: 'power3.out', scrollTrigger: { trigger: shot, start: 'top 95%', end: 'top 65%', scrub: true } },
    ),
  );

  // 封面往上捲走時，大標與文字往上飄、淡掉
  const hero = $('.hero');
  gsap.to('.hero-copy', {
    autoAlpha: 0,
    y: reduce ? 0 : -60,
    ease: 'none',
    scrollTrigger: { trigger: hero, start: 'top top', end: '45% top', scrub: true },
  });
  gsap.to('.scroll-hint', {
    autoAlpha: 0,
    ease: 'none',
    scrollTrigger: { trigger: hero, start: 'top top', end: '15% top', scrub: true },
  });

  // 時鐘和桌面版一樣：「幾點」那一行完整出現時走到這個時間
  layout.anchorY = (el) => {
    const s = stages.get(el);
    if (!s || s.anchor === null) return null;
    return pageY(s.stage) + (s.anchor / s.total) * (s.stage.offsetHeight - window.innerHeight) + window.innerHeight / 2;
  };

  // 可以停的位置：封面最上面、每一幕的字到齊時，以及接在後面的那張截圖整張露出來、淡入也走完時。
  // 截圖也要算一站：不然從字直接捲到下一幕，截圖只會一閃而過
  layout.rests = () => {
    const vh = window.innerHeight;
    const top = $('.masthead').offsetHeight;
    const out = [[0, 0]];
    for (const s of stages.values()) {
      const at = (time) => pageY(s.stage) + (time / s.total) * (s.stage.offsetHeight - vh);
      for (const [a, b] of s.rests) out.push([at(a), at(b)]);
      if (!s.shot) continue;
      // 截圖上緣落在報頭下面、且整張在畫面裡（淡入在上緣到 65% 時走完）；太矮的螢幕就貼齊報頭
      const y = layoutY(s.shot);
      const b = y - top;
      out.push([Math.min(b, y - Math.min(0.65 * vh, vh - s.shot.offsetHeight - 16)), b]);
    }
    return out;
  };

  return () => {
    layout.anchorY = null;
    layout.rests = null;
    day.classList.remove('is-staged');
    for (const { stage } of stages.values()) stage.style.height = '';
    resets.forEach((fn) => fn());
  };
}

// 換版面（跨過 900px、轉平板、切換減少動態）時，ScrollTrigger 會把捲動位置歸零。兩種版面的同一個
// 捲動值也不是同一段內容，所以記的是「報頭的時鐘走到幾點」，新版面量好之後捲回同一個時間。
let story = null;
let resumeAt = null;
ScrollTrigger.addEventListener('refresh', () => {
  if (resumeAt === null || !story) return;
  const y = story.scrollFor(resumeAt);
  resumeAt = null;
  window.scrollTo(0, y);
});

mm.add({ desktop: MQ.desktop, mobile: MQ.mobile, reduce: MQ.reduce }, (ctx) => {
  const { desktop, reduce } = ctx.conditions;
  const undo = desktop ? stageDay(ctx, reduce) : stageMobile(ctx, reduce);
  return () => {
    if (story) resumeAt = story.minutes();
    undo();
  };
});

/* ---------- 6. 一天之後：逐字點亮、藝廊、天亮、下載 ---------- */

// 「沒有帳號，沒有後端。」拆成一個字一個 .glyph，捲到哪裡亮到哪裡。
// 那是顏色不是位移，減少動態時照樣跑。
const glyphs = [];
for (const span of $$('.privacy .big .line > span')) {
  const chars = [...span.textContent];
  span.textContent = '';
  for (const c of chars) {
    const g = document.createElement('span');
    g.className = 'glyph';
    g.textContent = c;
    span.append(g);
    glyphs.push(g);
  }
}
if (glyphs.length) {
  let lit = -1;
  const light = (p) => {
    const n = Math.round(p * glyphs.length);
    if (n === lit) return;
    glyphs.forEach((g, i) => g.classList.toggle('lit', i < n));
    lit = n;
  };
  ScrollTrigger.create({
    trigger: '.privacy .big',
    start: 'top 85%',
    end: 'top 30%',
    onUpdate: (self) => light(self.progress),
    onRefresh: (self) => light(self.progress),
  });
}

mm.add({ motion: MQ.motion, reduce: MQ.reduce }, (ctx) => {
  const { reduce } = ctx.conditions;
  const rise = reduce ? 0 : RISE;

  // 由自己在視窗裡的位置驅動，往回捲會倒帶
  const floatIn = (els, start = 'top 92%', end = 'top 62%') =>
    els.forEach((el) =>
      gsap.fromTo(
        el,
        { autoAlpha: 0, y: rise },
        { autoAlpha: 1, y: 0, ease: 'power3.out', scrollTrigger: { trigger: el, start, end, scrub: true } },
      ),
    );

  floatIn($$('.privacy .lede-sm, .ledger li'));
  // 細線跟著那一列一起畫出來
  $$('.ledger li').forEach((li) =>
    gsap.fromTo(
      $('.hair', li),
      { scaleX: 0 },
      { scaleX: 1, ease: 'none', scrollTrigger: { trigger: li, start: 'top 90%', end: 'top 55%', scrub: true } },
    ),
  );
  floatIn($$('.paper .when, .paper .big, .specimen, .icon-aside'));

  // 頁尾最後幾行捲不到「位置驅動」的終點，會卡在半透明；這幾個沿用參考站的作法，進畫面播一次
  const tail = $$('.download .big, .download-row, .notes li');
  gsap.set(tail, { autoAlpha: 0, y: rise });
  ScrollTrigger.batch(tail, {
    start: 'top 95%',
    once: true,
    onEnter: deferIn(ctx, (batch) =>
      gsap.to(batch, { autoAlpha: 1, y: 0, duration: 0.8, ease: 'power2.out', stagger: 0.08, overwrite: true }),
    ),
  });

  // 橫向藝廊：往下捲換算成往左推。橫推是位移，減少動態時不做（CSS 已經改成一般的往下排）
  if (!reduce) {
    const track = $('.gallery-track');
    gsap.to(track, {
      x: () => -Math.max(0, track.scrollWidth - window.innerWidth),
      ease: 'none',
      scrollTrigger: {
        trigger: '.gallery',
        start: 'top top',
        end: 'bottom bottom',
        scrub: 0.35,
        invalidateOnRefresh: true,
      },
    });
  }
});

/* ---------- 7. 封面進場（載入時跑一次，不綁捲動） ----------
   手機只淡入、不位移：字正在升起的時候，旁邊不該有東西在動。 */

if (!reduceQuery.matches) {
  gsap
    .timeline({ defaults: { ease: 'power3.out' } })
    .from('.title .ch', { yPercent: 110, duration: 1, stagger: 0.09 }, 0)
    .from('.hero-rule', { scaleX: 0, duration: 0.9, ease: 'power2.inOut' }, 0.35)
    .from(['.hero .lede', '.hero .cta'], { opacity: 0, y: 18, duration: 0.8, stagger: 0.12 }, 0.6)
    .from('.day-phone .phone', { opacity: 0, duration: 1.1 }, 0.3);
} else {
  gsap.from(['.hero-copy', '.day-phone .phone'], { opacity: 0, duration: 0.8 });
}

/* ---------- 8. 捲動狀態：時鐘、今日熱量、入夜、進度線 ----------
   全部由一個函式從捲動位置直接算出來，不是每一幕各掛一個 ScrollTrigger 去改同一個值 ——
   那樣倒捲回去時，最後觸發的那一個會蓋掉該有的值。 */

function scrollState() {
  const root = document.documentElement;
  const toMinutes = (hhmm) => {
    const [h, m] = hhmm.split(':').map(Number);
    return h * 60 + m;
  };

  // 沒寫 data-kcal / data-bonus 的錨點沿用前一個；分鐘數往後遇到比前一個小的就跨過午夜（+1440）
  let kcal = 0;
  let bonus = 0;
  let prevMinutes = -Infinity;
  const anchors = $$('[data-time]').map((el) => {
    let minutes = toMinutes(el.dataset.time);
    while (minutes < prevMinutes) minutes += 1440;
    prevMinutes = minutes;
    if (el.dataset.kcal !== undefined) kcal = Number(el.dataset.kcal);
    if (el.dataset.bonus !== undefined) bonus = Number(el.dataset.bonus);
    return { el, minutes, kcal, bonus, y: 0 };
  });

  const clocks = $$('[data-clock]');
  // 錨點自己也帶 data-kcal / data-bonus，寫數字時一定要排除，不然整幕的內容會被換成一個數字
  const kcalEls = $$('[data-kcal]:not([data-time])');
  const bonusEls = $$('[data-bonus]:not([data-time])');
  const today = $('.ticker .today');
  const target = $('.ticker .target');
  const baseTarget = Number($('.ticker .target .num:not([data-bonus])').textContent);
  const progress = $('.masthead .progress');
  const dusk = $('[data-dusk]');
  const dawn = $('[data-dawn]');
  const themeMeta = $('meta[name="theme-color"]');

  const style = getComputedStyle(root);
  const light = Object.fromEntries(Object.keys(DARK).map((k) => [k, style.getPropertyValue(k).trim()]));
  const mix = Object.fromEntries(Object.keys(DARK).map((k) => [k, gsap.utils.interpolate(light[k], DARK[k])]));

  let maxScroll = 1;
  let measuredWidth = window.innerWidth;
  let duskFrom = Infinity;
  let duskTo = Infinity;
  let dawnFrom = Infinity;
  let dawnTo = Infinity;

  function measure() {
    measuredWidth = window.innerWidth;
    const vh = window.innerHeight;
    maxScroll = Math.max(1, ScrollTrigger.maxScroll(window));
    // 錨點夾進「畫面中線摸得到」的範圍：封面的頂端是 0，但捲到最上面時中線已經在半個視窗高，
    // 不夾的話一打開網頁時鐘就不是 07:00。
    const lo = vh / 2;
    const hi = maxScroll + lo;
    for (const a of anchors) a.y = Math.min(Math.max(layout.anchorY?.(a.el) ?? pageY(a.el), lo), hi);

    // 入夜：桌面版從時間軸換算；手機版是跑步那一章的頂端從視窗 85% 走到 25%
    const range = layout.duskRange?.() ?? (dusk ? [pageY(dusk) - 0.85 * vh, pageY(dusk) - 0.25 * vh] : null);
    [duskFrom, duskTo] = range ?? [Infinity, Infinity];
    // 天亮：「紙與墨」那一段的頂端從 90% 走到 35%
    [dawnFrom, dawnTo] = dawn ? [pageY(dawn) - 0.9 * vh, pageY(dawn) - 0.35 * vh] : [Infinity, Infinity];
  }

  /* 時鐘 */
  let clockText = '';
  function paintClock(minutes) {
    const m = Math.floor(minutes) % 1440;
    const text = `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`;
    if (text === clockText) return;
    clockText = text;
    for (const el of clocks) el.textContent = text;
  }

  /* 今日熱量與運動額度 */
  const shown = { v: 0 };
  let kcalGoal = null;
  let bonusNow = 0;
  let kcalText = '';
  function paintKcal() {
    const v = Math.round(shown.v);
    const text = String(v); // 不加千分位，app 也不加
    if (text !== kcalText) {
      kcalText = text;
      for (const el of kcalEls) el.textContent = text;
    }
    today?.classList.toggle('over', v > baseTarget + bonusNow);
  }
  function setKcal(goal) {
    if (goal === kcalGoal) return;
    kcalGoal = goal;
    gsap.killTweensOf(shown);
    if (reduceQuery.matches) {
      shown.v = goal;
      paintKcal();
    } else {
      gsap.to(shown, { v: goal, duration: 0.9, ease: 'power2.out', onUpdate: paintKcal });
    }
  }
  function setBonus(b) {
    if (b === bonusNow) return;
    bonusNow = b;
    target?.classList.toggle('has-bonus', b > 0);
    if (b > 0) for (const el of bonusEls) el.textContent = `+${b}`;
    paintKcal(); // 額度一變，同一個熱量可能就從超標變成沒超標（跑步那一章的重點）
  }

  /* 入夜程度 */
  let night = 0;
  function paintNight(n) {
    n = Math.round(n * 1000) / 1000;
    if (n === night) return;
    night = n;
    for (const k in mix) {
      if (n === 0) root.style.removeProperty(k);
      else root.style.setProperty(k, mix[k](n));
    }
    if (themeMeta) themeMeta.content = n === 0 ? light['--bg'] : mix['--bg'](n);
  }

  const between = (y, from, to) => (to > from ? clamp01((y - from) / (to - from)) : y >= to ? 1 : 0);

  let minutesNow = anchors[0]?.minutes ?? 0;
  // 換版面時要捲回去的那個時間。寬度一變，舊版面的高度先變、瀏覽器把捲動位置夾掉，那一下的捲動
  // 會用舊版面量好的錨點算出錯的時間（實測手機 → 桌面從 21:38 掉到 13:48）。所以寬度和上次量的
  // 不一樣時不更新它，等新版面量好再說。
  let storyMinutes = minutesNow;

  function update() {
    const scrollY = window.scrollY;
    const pos = scrollY + window.innerHeight / 2;

    let i = 0;
    while (i + 1 < anchors.length && anchors[i + 1].y <= pos) i++;
    const a = anchors[i];
    const b = anchors[i + 1];
    const t = b && b.y > a.y ? clamp01((pos - a.y) / (b.y - a.y)) : 0;
    minutesNow = b ? a.minutes + (b.minutes - a.minutes) * t : a.minutes;
    if (window.innerWidth === measuredWidth) storyMinutes = minutesNow;
    paintClock(minutesNow);

    setBonus(a.bonus);
    setKcal(a.kcal);

    if (progress) progress.style.transform = `scaleX(${clamp01(scrollY / maxScroll)})`;
    paintNight(between(scrollY, duskFrom, duskTo) * (1 - between(scrollY, dawnFrom, dawnTo)));
  }

  // 時鐘的反函數：要走到這個時間，該捲到哪裡
  function scrollFor(minutes) {
    let i = 0;
    while (i + 1 < anchors.length && anchors[i + 1].minutes <= minutes) i++;
    const a = anchors[i];
    const b = anchors[i + 1];
    const t = b && b.minutes > a.minutes ? clamp01((minutes - a.minutes) / (b.minutes - a.minutes)) : 0;
    const pos = b ? a.y + (b.y - a.y) * t : a.y;
    return Math.min(Math.max(pos - window.innerHeight / 2, 0), maxScroll);
  }

  measure();
  update();
  ScrollTrigger.create({
    start: 0,
    end: 'max',
    onUpdate: update,
    onRefresh: () => {
      measure();
      update();
    },
  });
  return { minutes: () => storyMinutes, scrollFor };
}

// 放在桌面／手機版的版面建好之後：桌面版的錨點位置要從那條時間軸換算
story = scrollState();

// 粉圓是 Google Fonts 非同步載進來的，換字之後行高會變，量好的位置要重量一次
document.fonts?.ready.then(() => ScrollTrigger.refresh());

/* ---------- 9. 下載鈕：接上最新一版的 APK ----------
   版號不寫死在 HTML：網站只在 site/ 有改動時才重新部署，寫死的版號會過期。 */

async function linkLatestApk() {
  try {
    const res = await fetch('https://api.github.com/repos/rowing195/NutriLog/releases/latest');
    if (!res.ok) return;
    const release = await res.json();

    if (release.html_url) {
      for (const el of $$('[data-release-notes]')) el.href = release.html_url;
    }

    const apk = release.assets?.find((a) => a.name.toLowerCase().endsWith('.apk'));
    if (!apk) return;
    for (const el of $$('[data-apk]')) el.href = apk.browser_download_url;
    for (const el of $$('[data-apk-label]')) el.textContent = `下載 ${release.tag_name}`;
    for (const el of $$('[data-apk-meta]')) el.textContent = `${(apk.size / 1024 / 1024).toFixed(1)} MB`;
  } catch {
    // 查不到就維持原樣：HTML 裡預設的連結已經指向 releases/latest 頁
  }
}

linkLatestApk();
