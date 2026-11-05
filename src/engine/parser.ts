export type Status = 'valid' | 'no_dispense' | 'verify';
export interface ReqEntry {
  ref: string; time: string; type: string; reqAmount: number;
  numbers: number[]; total: number; status: Status; note: string; raw: string;
}
export interface ParseResult {
  entries: ReqEntry[];
  summary: { detected: number; valid: number; verify: number; noDispense: number; grandTotal: number };
}

const ESC = /\x1b\[[0-9;]*[A-Za-z]|\x1b[()][0-9A-Za-z]|\x1b[A-Za-z]\d*|[\x00-\x08\x0b\x0c\x0e-\x1f]/g;
export const cleanText = (t: string) =>
  t.replace(ESC, '').replace(/\r\n?/g, '\n').replace(/[٠-٩]/g, d => String('٠١٢٣٤٥٦٧٨٩'.indexOf(d)));

const REQ = /REQ\s*:\s*([\d.,]+)/;
const FOUR = /^\s*(\d+)\s*[-–—,;|]\s*(\d+)\s*[-–—,;|]\s*(\d+)\s*[-–—,;|]\s*(\d+)\s*$/;
const ANY_DASH = /^\s*\d+(\s*[-–—,;|]\s*\d+)+\s*$/;
const HEADER = /^\s*(\d+)\s+\d\d\/\d\d\/\d\d/;
const RC = /\bRC\s+(\d{3})\b/;
const amount = (s: string) => parseFloat(s.replace(/,/g, '').replace(/\.$/, '')) || 0;

export function parseREQ(input: string): ParseResult {
  const L = cleanText(input).split('\n');
  const idx: number[] = [];
  L.forEach((l, i) => REQ.test(l) && idx.push(i));
  const seen = new Set<string>();
  const entries: ReqEntry[] = [];

  idx.forEach((i, k) => {
    const end = Math.min(idx[k + 1] ?? L.length, i + 14); // stop at next REQ
    let ref = `REQ ${String(k + 1).padStart(3, '0')}`, time = '', type = '';
    for (let j = i - 1; j >= Math.max(0, i - 4); j--) {
      const h = L[j].match(HEADER);
      if (h) { ref = h[1]; break; }
      const t = L[j].match(/^\s*(\d\d:\d\d:\d\d)\s+([A-Z ]+?)\s*$/);
      if (t) { time = t[1]; type = t[2]; }
    }
    const amt = amount(L[i].match(REQ)![1]);
    const block = L.slice(i, end);
    // the numbers must be on the first non-empty line(s) right below REQ:
    let nums: number[] = [], bad = '';
    for (let j = i + 1; j < Math.min(end, i + 4); j++) {
      if (!L[j].trim()) continue;
      const m = L[j].match(FOUR);
      if (m) { nums = m.slice(1).map(Number); }
      else if (ANY_DASH.test(L[j])) bad = L[j].trim();
      break;
    }
    const rc = block.join('\n').match(RC)?.[1];
    const raw = block.join('\n').trim();
    let status: Status, note = '';
    if (nums.length === 4) {
      status = 'valid';
      const np = raw.match(/NOTES PRESENTED ([\d,]+)/);
      if (np && np[1].split(',').map(Number).reduce((a, b) => a + b, 0) !== nums.reduce((a, b) => a + b, 0)) {
        status = 'verify'; note = 'Sum differs from NOTES PRESENTED';
      }
    } else if (bad) { status = 'verify'; note = `Expected 4 numbers, found: ${bad}`; }
    else if (amt === 0 || rc) { status = 'no_dispense'; note = rc ? `Declined (RC ${rc})` : 'No cash requested'; }
    else { status = 'verify'; note = 'REQ amount > 0 but no four-number line'; }

    const key = `${ref}|${time}|${amt}|${nums.join('-')}`;
    if (seen.has(key)) return; // duplicate protection
    seen.add(key);
    entries.push({ ref, time, type, reqAmount: amt, numbers: nums, total: nums.reduce((a, b) => a + b, 0), status, note, raw });
  });
  return { entries, summary: summarize(entries) };
}

export function summarize(entries: ReqEntry[]) {
  const ok = entries.filter(e => e.status === 'valid');
  return {
    detected: entries.length, valid: ok.length,
    verify: entries.filter(e => e.status === 'verify').length,
    noDispense: entries.filter(e => e.status === 'no_dispense').length,
    grandTotal: ok.reduce((s, e) => s + e.total, 0),
  };
}

/** Manual correction: replace numbers and recompute. */
export function applyEdit(e: ReqEntry, numbers: number[]): ReqEntry {
  const total = numbers.reduce((a, b) => a + b, 0);
  return { ...e, numbers, total, status: numbers.length === 4 ? 'valid' : 'verify', note: 'Edited manually' };
}
