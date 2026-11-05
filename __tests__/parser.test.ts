import { parseREQ, applyEdit } from '../src/engine/parser';
import * as fs from 'fs';

test('spec example ignores monetary value', () => {
  const r = parseREQ('Transaction 001\nREQ: 1500\n\n10-20-30-40\n\nTransaction 002\nREQ: 2500\n\n5-15-25-35\n');
  expect(r.entries.map(e => e.total)).toEqual([100, 80]);
  expect(r.summary.grandTotal).toBe(180);
});
test('missing / incomplete numbers need verification, never guessed', () => {
  const r = parseREQ('REQ: 2,000.00 DZD\n10-20-30\nREQ: 3,000.00 DZD\nCARD: 1\n');
  expect(r.entries.map(e => e.status)).toEqual(['verify', 'verify']);
  expect(r.summary.grandTotal).toBe(0);
});
test('stops at next REQ, handles spacing, empty, duplicates, ESC codes, Arabic digits', () => {
  expect(parseREQ('').summary.detected).toBe(0);
  const r = parseREQ(' 1 01/01/26 ATM\r\x1b[020tREQ:5.00 DZD\r  ١ -  2 - 3 -   4\rREQ:0.00 DZD\r\r');
  expect(r.entries[0].total).toBe(10);
  expect(r.entries[1].status).toBe('no_dispense');
});
test('manual edit recalculates', () => {
  const e = parseREQ('REQ: 1\n1-1-1\n').entries[0];
  expect(applyEdit(e, [1, 2, 3, 4]).total).toBe(10);
});
test('large document (20k entries)', () => {
  const doc = Array.from({ length: 20000 }, (_, i) => ` ${i} 04/11/26 ATM\nREQ:2,000.00 DZD\n  1 - 0 - 0 - 0\n`).join('');
  const t = Date.now(); const r = parseREQ(doc);
  expect(r.summary.grandTotal).toBe(20000); expect(Date.now() - t).toBeLessThan(5000);
});
const sample = '/mnt/user-data/uploads/1793903237208_EJ______11042026.330';
(fs.existsSync(sample) ? test : test.skip)('real EJ log: verified totals', () => {
  const r = parseREQ(fs.readFileSync(sample, 'latin1'));
  expect(r.summary).toMatchObject({ detected: 842, valid: 497, grandTotal: 7289 });
});
