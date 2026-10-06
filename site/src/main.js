import 'lenis/dist/lenis.css';
import './style.css';
import gsap from 'gsap';
import { ScrollTrigger } from 'gsap/ScrollTrigger';
import Lenis from 'lenis';

gsap.registerPlugin(ScrollTrigger);

const $ = (sel, root = document) => root.querySelector(sel);
const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];
const clamp01 = gsap.utils.clamp(0, 1);

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

// 換畫面的方向照 app 的規則：「記一筆」開出來的由上往下蓋，報頭圖示（月曆）開出來的由下往上
const SHOT = {
  top: { from: { clipPath: 'inset(0% 0% 100% 0%)' }, to: { clipPath: 'inset(0% 0% 0% 0%)' } },
  bottom: { from: { clipPath: 'inset(100% 0% 0% 0%)' }, to: { clipPath: 'inset(0% 0% 0% 0%)' } },
  fade: { from: { opacity: 0 }, to: { opacity: 1 } },
};

// 晚一點才會被叫到的 callback（onEnter 之類）裡建立的 tween 也要記進 matchMedia 的 context，條件一變
// 才會跟著還原。ctx.add(fn) 會「當場」執行 fn，要拿到包好、晚點再叫的函式得用具名的形式。
let deferredCount = 0;
const deferIn = (ctx, fn) => ctx.add(`deferred${++deferredCount}`, fn);

// 到某一點才一次切換（reduced motion 用）。不能用 onToggle：捲到底時 progress 是 1、isActive 是 false，
// 會被當成「離開」而切回去。四個 callback 都接同一個判斷，一次跳過整段時 ScrollTrigger 會連叫兩個
// （例如 onEnterBack 接 onLeaveBack），最後一個就是對的。載入時已經捲在下面的話 callback 不會被叫，
// 所以 refresh 時自己對一次。
function stepAt(trigger, start, apply) {
  const sync = (self) => apply(self.progress > 0);
  return ScrollTrigger.create({
    trigger,
    start,
    onEnter: sync,
    onLeave: sync,
    onEnterBack: sync,
    onLeaveBack: sync,
    onRefresh: (self) => apply(self.scroll() > self.start),
  });
}

/* ---------- 1. Lenis ---------- */

const mm = gsap.matchMedia();

mm.add(MQ.motion, () => {
  const lenis = new Lenis({ anchors: true });
  const raf = (time) => lenis.raf(time * 1000);
  lenis.on('scroll', ScrollTrigger.update);
  gsap.ticker.add(raf);
  gsap.ticker.lagSmoothing(0);
  return () => {
    gsap.ticker.remove(raf);
    gsap.ticker.lagSmoothing(500, 33);
    lenis.destroy();
  };
});

/* ---------- 2. 捲動狀態：時鐘、今日熱量、入夜 ----------
   全部由一個函式從捲動位置直接算出來，不是每章各掛一個 ScrollTrigger 去改同一個值 ——
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
  // 錨點自己也帶 data-kcal / data-bonus，寫數字時一定要排除，不然整章的內容會被換成一個數字
  const kcalEls = $$('[data-kcal]:not([data-time])');
  const bonusEls = $$('[data-bonus]:not([data-time])');
  const today = $('.ticker .today');
  const target = $('.ticker .target');
  const baseTarget = Number($('.ticker .target .num:not([data-bonus])').textContent);
  const dusk = $('[data-dusk]');
  const dawn = $('[data-dawn]');
  const themeMeta = $('meta[name="theme-color"]');

  const style = getComputedStyle(root);
  const light = Object.fromEntries(Object.keys(DARK).map((k) => [k, style.getPropertyValue(k).trim()]));
  const mix = Object.fromEntries(Object.keys(DARK).map((k) => [k, gsap.utils.interpolate(light[k], DARK[k])]));

  let duskY = 0;
  let dawnY = 0;

  const pageY = (el) => el.getBoundingClientRect().top + window.scrollY;

  function measure() {
    // 錨點夾進「畫面中線摸得到」的範圍：封面的頂端是 0，但捲到最上面時中線已經在半個視窗高，
    // 不夾的話一打開網頁時鐘就不是 07:00。
    const lo = window.innerHeight / 2;
    const hi = ScrollTrigger.maxScroll(window) + lo;
    for (const a of anchors) a.y = Math.min(Math.max(pageY(a.el), lo), hi);
    if (dusk) duskY = pageY(dusk);
    if (dawn) dawnY = pageY(dawn);
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
    paintKcal(); // 額度一變，同一個熱量可能就從超標變成沒超標（第 05 章的重點）
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

  function update() {
    const scrollY = window.scrollY;
    const vh = window.innerHeight;
    const pos = scrollY + vh / 2;

    let i = 0;
    while (i + 1 < anchors.length && anchors[i + 1].y <= pos) i++;
    const a = anchors[i];
    const b = anchors[i + 1];
    const t = b && b.y > a.y ? clamp01((pos - a.y) / (b.y - a.y)) : 0;
    paintClock(b ? a.minutes + (b.minutes - a.minutes) * t : a.minutes);

    setBonus(a.bonus);
    setKcal(a.kcal);

    // 元件頂端在視窗裡的位置（0 = 頂、1 = 底）
    const at = (y) => (y - scrollY) / vh;
    const toDark = dusk ? clamp01((0.85 - at(duskY)) / (0.85 - 0.25)) : 0;
    const toLight = dawn ? clamp01((0.9 - at(dawnY)) / (0.9 - 0.35)) : 0;
    paintNight(toDark * (1 - toLight));
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
}

scrollState();

/* ---------- 3. 右邊手機換畫面（只有桌面版有那支釘住的手機） ---------- */

mm.add({ desktop: MQ.desktop, reduce: MQ.reduce }, (ctx) => {
  const { desktop, reduce } = ctx.conditions;
  if (!desktop) return;

  for (const mark of $$('.reveal-mark[data-reveal]')) {
    const img = $(`.screen img[data-shot="${mark.dataset.reveal}"]`);
    const shot = img && SHOT[img.dataset.from];
    if (!shot) continue;

    if (reduce) {
      stepAt(mark, 'top 60%', deferIn(ctx, (on) => gsap.set(img, on ? shot.to : shot.from)));
    } else {
      gsap.fromTo(img, shot.from, {
        ...shot.to,
        ease: 'none',
        scrollTrigger: { trigger: mark, start: 'top 80%', end: 'top 45%', scrub: true },
      });
    }
  }
});

/* ---------- 4. 章節裡的小示範 ---------- */

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
  return tl;
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
  return tl;
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
  return gsap
    .timeline({ paused: true, defaults: { ease: 'none' } })
    .fromTo(bar, { '--target': 81 }, { '--target': 93.5, duration: 1 }, 0)
    .fromTo(bar, { '--red': 1 }, { '--red': 0, duration: 0.25 }, 0.55);
}

mm.add({ motion: MQ.motion, reduce: MQ.reduce }, (ctx) => {
  const { reduce } = ctx.conditions;
  const drive = (demo, tl) => {
    if (reduce) {
      stepAt(demo, 'top 60%', (on) => tl.progress(on ? 1 : 0));
    } else {
      ScrollTrigger.create({ trigger: demo, animation: tl, start: 'top 80%', end: 'top 35%', scrub: true });
    }
  };

  const cleanups = [];
  for (const demo of $$('[data-demo]')) {
    switch (demo.dataset.demo) {
      case 'match':
        drive(demo, matchTimeline(demo));
        break;
      case 'stamps':
        drive(demo, stampsTimeline(demo));
        break;
      case 'portion': {
        const { tl, reset } = portionTimeline(demo);
        drive(demo, tl);
        cleanups.push(reset);
        break;
      }
      case 'budget':
        drive(demo, budgetTimeline(demo));
        break;
    }
  }
  return () => cleanups.forEach((fn) => fn());
});

/* ---------- 5. 封面進場（載入時跑一次，不綁捲動） ---------- */

if (!reduceQuery.matches) {
  gsap
    .timeline({ defaults: { ease: 'power3.out' } })
    .from('.title .ch', { yPercent: 110, duration: 1, stagger: 0.09 }, 0)
    .from('.hero-rule', { scaleX: 0, duration: 0.9, ease: 'power2.inOut' }, 0.35)
    .from(['.hero .lede', '.hero .cta'], { opacity: 0, y: 18, duration: 0.8, stagger: 0.12 }, 0.6)
    .from('.day-phone .phone', { opacity: 0, y: 60, duration: 1.1 }, 0.3);
}

/* ---------- 6. 一般進場 ---------- */

// 進畫面時淡入上移。同一批進來的錯開一點，但已經整個在視窗上方的直接到位：一次跳過好幾章時
// （在半途重新整理、按 End 鍵）被跳過的元素也會擠進同一批，不先排掉的話，真正停下來的那一章
// 要等前面二十幾個都演完才出現。
function appear(ctx, els, { y = 28, start = 'top 88%', duration = 0.8, stagger = 0.08 } = {}) {
  if (!els.length) return;
  gsap.set(els, { opacity: 0, y });
  ScrollTrigger.batch(els, {
    start,
    once: true,
    onEnter: deferIn(ctx, (batch) => {
      const passed = batch.filter((el) => el.getBoundingClientRect().bottom <= 0);
      if (passed.length) gsap.set(passed, { opacity: 1, y: 0, overwrite: true });
      const shown = batch.filter((el) => !passed.includes(el));
      if (shown.length) gsap.to(shown, { opacity: 1, y: 0, duration, ease: 'power2.out', stagger, overwrite: true });
    }),
  });
}

mm.add(MQ.motion, (ctx) => {
  // 只挑直接子項：示範裡也有 <p>（圖例、小註），那些跟著 .demo 一起進來就好，不要再各動一次
  appear(ctx, $$('.chapter > .when, .chapter > h2, .chapter > p, .chapter > .demo, .chapter .beat > p'));
  appear(ctx, $$('.specimen'), { y: 0 });

  gsap.from('.privacy .big .line > span', {
    yPercent: 100,
    duration: 1,
    ease: 'power3.out',
    stagger: 0.12,
    scrollTrigger: { trigger: '.privacy .big', start: 'top 85%', once: true },
  });

  gsap.from('.ledger .hair', {
    scaleX: 0,
    duration: 0.9,
    ease: 'power2.inOut',
    stagger: 0.12,
    scrollTrigger: { trigger: '.ledger', start: 'top 85%', once: true },
  });
});

// 手機版沒有釘住的那支手機，截圖跟著章節走
mm.add(`${MQ.mobile} and ${MQ.motion}`, (ctx) => {
  appear(ctx, $$('.inline-shot'), { y: 40, start: 'top 92%', duration: 0.9 });
});

// 粉圓是 Google Fonts 非同步載進來的，換字之後行高會變，量好的位置要重量一次
document.fonts?.ready.then(() => ScrollTrigger.refresh());

/* ---------- 7. 下載鈕：接上最新一版的 APK ----------
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
