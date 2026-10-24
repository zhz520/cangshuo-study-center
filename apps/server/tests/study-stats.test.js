const {test}=require('node:test');
const assert=require('node:assert/strict');
const {dayKey,consecutiveDays}=require('../services/studyStats');
test('China study day stays correct across UTC midnight and month boundaries',()=>{
 assert.equal(dayKey(new Date('2026-10-22T17:00:00Z')),'2026-10-23');
 assert.equal(dayKey(new Date('2026-10-22T15:59:59Z')),'2026-10-22');
 assert.equal(dayKey(new Date('2026-12-31T16:01:00Z')),'2027-01-01');
});
test('streak survives before the first practice today and resets on a missed day',()=>{
 assert.equal(consecutiveDays(['2026-10-23','2026-10-22','2026-10-21'],'2026-10-23'),3);
 assert.equal(consecutiveDays(['2026-10-22','2026-10-21'],'2026-10-23'),2);
 assert.equal(consecutiveDays(['2026-10-21','2026-10-20'],'2026-10-23'),0);
 assert.equal(consecutiveDays([],'2026-10-23'),0);
 assert.equal(consecutiveDays(['2027-01-01','2026-12-31'],'2027-01-01'),2);
});
